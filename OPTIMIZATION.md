# MiMarketPurify Hook 代码优化说明

> 基于 master 分支（commit `70d14f4`）的 hook 代码优化。
> 共改动 **13 个文件**、新增 **1 个文件**（`init/ResIdHiderHook.kt`）。
> 全部改动**不改变对外行为**（类名、对象名、prefKey、开关语义均保持不变），只做性能、健壮性与可维护性优化。

## 一、新增文件

### `app/src/main/java/com/mars/mimarketpurify/init/ResIdHiderHook.kt`（新）

按资源 id 隐藏 View 的通用模板基类，收敛三个重复 hook 的公共逻辑：
- `@Volatile + synchronized` 惰性解析资源 id（只解析一次）；
- `View.onAttachedToWindow` 命中即 GONE + 禁用点击；
- `View.setVisibility(VISIBLE)` 强拦回 GONE（防滑动/刷新闪现）；
- 可覆写 `fallbackHit(view)` 做文案兜底（对抗资源名漂移）。

## 二、改动文件

### 1. `Settings.kt` — 偏好读取缓存 + 日志收敛
- **远程偏好加 500ms TTL 缓存**：`getRemotePrefs()` 原是每次调用都跨进程 binder 获取，高频拦截点（setVisibility 等）每次都触发。缓存后开关切换最多延迟 0.5s 生效，体验无感。
- **目标 SP 文件按 mtime 缓存**：`readFromTargetSp()` 原来每次调用都读盘 + XML 全量解析，现按文件 `lastModified()` 判断是否重解析，整个文件解析结果复用。
- **移除 `mine_` 前缀逐次 INFO 日志**（调试残留，每次 isEnabled 都打一条）。

### 2. `hooks/market/RankAds.kt` — 诊断扫描收进调试开关（P0）
- `diagnosticScan()`（dex 全量枚举 + 逐类反射打印方法签名 + 给所有 onBindData 挂日志拦截器）原来**每次启动无条件执行**，现收进 `if (isDebug())`，生产路径零诊断开销。
- `compute` 拦截改为**按真实返回类型返回安全空值**（List→`emptyList()`，Boolean→`false`，其余→null），避免下游拿到 null NPE。
- 移除未使用 import。

### 3. `hooks/market/UiCleanup.kt` — onResume 补扫任务去重（P0）
- 原实现每次 `onResume` 无条件 postDelayed 300/800/1500/3000ms 四档任务，快速进出页面会堆积 Handler 任务并反复全树扫描。
- 现引入 `pendingRescanTasks`：onResume 时**先取消上一批任务再重新调度**（debounce 语义），同一时刻只保留最新一轮扫描。
- 1500/3000ms 的精确 hide 复查语义保留；运行时 INFO/WARN 日志收敛为 `debugLog`。

### 4. `hooks/market/ConfigBackupRestore.kt` — org.json 重写（P1）
- 原手写 JSON 序列化/解析器：`Set<String>` 会被 `toString()` 存成字符串、恢复时类型丢失；转义与数字精度处理不完整。
- 改用 Android 自带 **org.json**（零新增依赖），完整覆盖 SharedPreferences 全部值类型：boolean / int / long / float / String / Set&lt;String&gt;。

### 5. `hooks/market/SearchAds.kt` — 全局布局监听节流 + 上限（P1）
- 原 `onGlobalLayout` 每次布局变化都全树 DFS 且监听**永不移除**（动画/滚动期间每帧触发）。
- 现加 **400ms 节流** + **30 次扫描上限**（约 12 秒窗口，足够覆盖「点击安装后动态插入」），超限自动移除监听，防止无限空转。

### 6. `hooks/market/EnableSuperIsland.kt` — 类型判断兼容（P1）
- 回退匹配条件原来只认包装类型 `java.lang.Boolean`，若商店方法返回 primitive boolean 会漏匹配。新增 `isBooleanType()` 同时兼容 `Boolean` 与 `boolean`。

### 7. `hooks/market/AntiSelfDestruct.kt` — 同步写盘改异步（P1）
- `resetCrashCounter()` 原用 `commit()` 同步写盘（初始化阶段阻塞主线程），改 `apply()`。

### 8. `hooks/market/UpdateDownloadAds.kt` — 字段引用缓存（P2）
- 原每次构造 adapter 都通过 `fieldFinder()` 反射查找三个目标字段；现惰性缓存 `Field` 引用数组（`@Volatile + synchronized` 双重检查），构造时零反射查找。

### 9. `hooks/market/MainTabAds.kt` — 安全空值（P2）
- 原对所有被拦方法一律 `hooked { null }`；若方法返回 List（如 `fetchSearchHotList`），调用方拿到 null 可能 NPE。现按每个方法真实返回类型返回安全空值。

### 10. `hooks/market/TabFilter.kt` — 反射缓存 + 日志收敛（P2）
- `hookInitTabs` 原来每次触发都 `getDeclaredField("tabs")`，现缓存 `tabsField`（`@Volatile + synchronized`）。
- 大量 `Log.WARN` 运行时调试日志收敛为受 `KEY_RANK_DEBUG` 控制的 `debugLog`。

### 11. 三个按 id 隐藏 hook — 配置化继承（P2）
- `HideFruitEntry.kt` / `HideUpdateAll.kt` / `HideAutoUpdateSwitch.kt` 原各自重复实现「id 解析 + onAttachedToWindow + setVisibility」约 70 行，现改为继承 `ResIdHiderHook` 的配置化声明（3 行）。
- `HideAutoUpdateSwitch` 保留文案兜底 `fallbackHit`。

## 三、验证情况

- **静态检查**：14 个文件括号/引号/注释平衡检查全部通过；符号引用一致性核对通过（无残留引用被删除符号）。
- **未做**：完整 Gradle 编译（需 Android SDK + 依赖拉取，本地环境不具备）。建议在 Android Studio 中编译验证后再装机测试。
- **行为差异提示**：
  - 三个按 id 隐藏 hook 中，隐藏后额外设置 `isClickable = false`（对 GONE 视图无实际影响，属增强）；
  - `HideAutoUpdateSwitch` 原在 setVisibility 拦截中改写参数后 `proceed()`，现直接设置 GONE 并跳过原调用（最终可见性一致）；
  - `Settings` 缓存使开关生效延迟 ≤0.5s。

## 四、未纳入本次改动的方向（供后续）

- 六个文件重复挂全局 `View.onAttachedToWindow` / `setVisibility` 拦截器 → 可进一步合并为单一全局 View 钩子 + 过滤器分发（改动面大，涉及行为回归风险，未动）。
- `EasyXposedInit` 只处理 `isFirstPackage` → 若商店为多进程，子进程安全网 hook 不生效，需确认商店进程模型。
- `HomeFeed` 每次 bind 的 `invokeAs` 反射 → 可缓存 Method 引用（收益小，未动）。

---

## 五、底栏角标净化：纯原生 View 层重写（参考 HyperModifier）

> 本次改动针对「底部标签栏的数字角标与红点」（`KEY_TAB_BADGE` / `TabBadge`），
> 参考 `AritxOnly/HyperModifier` 中针对应用商店底栏的原生 Hook 思路，
> 用**纯原生代码**（android.view + 反射，零新增依赖）把原来的「单方法层拦截」
> 升级为「方法层 + 原生 View 层」双层净化。共**新增 2 个文件**、**改动 1 个文件**。

### 参考来源（HyperModifier 底栏逻辑）
- `MarketHooks.java`：Hook `MarketTabActivity` 生命周期（onCreate/onDestroy/dispatchTouchEvent），
  `AtomicBoolean` 去重、`ExceptionMode.PROTECTIVE`、`setId` 规范安装；
- `MarketFloatingNavigation.kt`：按资源 id 定位 `tab_container_layout` / `tab_container`，
  反射读取原生 `TabView` 的 `getTabViews/getTitleView/getTabViewTag/hasRedPoint/getNumber/getIconView`；
- `EarlyBottomBarSuppressor.kt`：用 `ViewTreeObserver.OnPreDrawListener` 在首帧前压制原底栏防闪烁，
  交接时同步 restore；
- `NativeTabIconSnapshotter.kt` 的 `findNativeTabIconView`：带打分地遍历子树，
  按资源名区分「图标」与「红点 / 角标」ImageView。

> 注：HyperModifier 的覆盖层用 Compose + Miuix KMP 绘制；MiMarketPurify 无该依赖
> （仅 androidx.core + libxposed + ezxhelper），故**只移植其纯原生的 Hook 机制**，
> 不引入任何 UI 框架，符合本项目「原生 View 手写」的既有路线。

### 新增文件

#### 1. `util/NativeTabBar.kt`（新）
应用商店底栏的纯原生访问器，收敛「定位 + 反射读取 + 角标识别」：
- 按资源 id 定位 `tab_container`（TabView 容器）/ `tab_container_layout`（底栏外层），id 结果缓存；
- `tabViews()`：优先反射 `getTabViews()`，失败回退为遍历容器直接子 View；
- 反射读取 TabView 状态：`tagOf/titleOf/hasRedPoint/numberOf/iconViewOf/hasBadge`，
  Method 按「类名 → 方法名」缓存（含负缓存，找不到不反复查）；
- `badgeViews()`：移植 HyperModifier 的打分思路，但目标相反——找**角标**而非图标：
  资源名含 red/badge/point/number/indicator/tag/new，或纯数字 TextView → 角标；
  `getIconView()` 返回的 ImageView 及资源名为 icon/*_icon 者 → **排除**，不误伤图标；
- `hideAllBadges()`：隐藏整个底栏所有 TabView 的角标，返回清除数量。
- 该类不绑定具体功能，后续底栏净化 / tab 读取均可复用。

#### 2. `util/EarlyBadgeSuppressor.kt`（新）
移植 `EarlyBottomBarSuppressor` 的 OnPreDrawListener 机制，目标改为「按帧压制角标防复现」：
- 商店首帧前后会陆续把角标设回 TabView，单方法 Hook 拦不住已 inflate 的角标，也可能闪现一帧；
- 每帧绘制前调用 `NativeTabBar.hideAllBadges()` 把角标压回 GONE；
- **带上限自动移除**：达到 40 帧或 3s 墙钟时间即 `removeOnPreDrawListener`，
  避免旧实现「监听永不移除、动画/滚动期每帧空转」的问题；
- `onPreDraw` 返回 true（不取消绘制），仅顺带修正可见性。

### 改动文件

#### `hooks/market/MarketExtras.kt` — `TabBadge` 双层净化
- **方法层（保留 + 扩充）**：原拦 `setNumber/showNewMessageTag`，
  新增 `setRedPoint/showRedPoint/setRedDot/showRedDot` 等常见角标设置方法名；
- **原生 View 层（新增）**：Hook `MarketTabActivity.onResume`，
  在 decorView post 回调里用 `NativeTabBar.hideAllBadges()` 主动清掉已显示角标，
  并挂 `EarlyBadgeSuppressor` 按帧补刀防复现；
- **生命周期清理**：Hook `onDestroy` 移除该 Activity 的守卫监听；
  守卫存于 `WeakHashMap<Activity, EarlyBadgeSuppressor>`，onResume 复用（先停旧再起新）、
  Activity 回收时自动释放，避免监听堆积与内存泄漏；
- **开关语义不变**：仍受 `KEY_TAB_BADGE` 经 `BaseHook.hooked` 实时门控，
  post 回调内再判一次 `enabled()`，用户中途关掉开关能即时收手。

### 验证情况
- **静态检查**：3 个文件括号 / 引号 / 注释平衡检查通过（{ } 计数：NativeTabBar 33/33、
  EarlyBadgeSuppressor 11/11、MarketExtras 57/57）；符号引用一致性核对通过
  （`NativeTabBar.*` / `EarlyBadgeSuppressor` 定义与引用一一对应，import 完整）。
- **未做**：完整 Gradle 编译（需 Android SDK + 依赖拉取，本地环境不具备），
  建议在 Android Studio 编译后装机验证。
- **行为差异提示**：
  - 角标由「仅拦新设置」变为「主动清除已显示 + 按帧压制」，清除更彻底、无单帧闪现；
  - 每次 onResume 会触发一次底栏子树遍历（TabView 子树很小，开销可忽略），
    并挂一个 ≤40 帧 / ≤3s 自动移除的 OnPreDrawListener；
  - 依赖商店资源名 `tab_container` / `tab_container_layout` 与 TabView 公开方法名，
    若某版本漂移则原生层静默降级，方法层仍生效（与原行为一致）。

### 后续可继续（同一原生底栏基建）
- `TabFilter` / `SubTabFilter` 目前是数据层反射重建 TabInfo，抗漂移弱；
  可评估改用 `NativeTabBar` 直接操作原生 TabView（隐藏 / 重排），
  但涉及导航与埋点归属，改动面大、回归风险高，本次未动。

---

## 六、悬浮底栏（纯原生自绘 + 主页开关）

> 目标：把 HyperModifier 针对应用商店底栏的「悬浮胶囊导航」能力，
> 用**纯原生 View**（零 UI 框架依赖）移植进本模块，并在程序主页提供开关。

### 为什么不能直接照搬
HyperModifier 的 `MarketFloatingNavigation` 浮层是 **Compose + Miuix KMP** 画的
（`MiuixFloatingTabBar` / `rememberLayerBackdrop` 高斯模糊 / `BitmapPainter`）。
本模块依赖只有 `androidx.core + libxposed + ezxhelper`，没有 Compose 运行时，
因此**只移植它的 Hook 机制，浮层全部用 android.view 原生控件手写**。

### 新增文件

#### `util/FloatingBarHost.kt`
悬浮底栏宿主（纯原生），与 HyperModifier 同构的五步流程：
- **定位**：按资源 id 取 `tab_container_layout` / `tab_container` / `fragment_container`
  / `navigation_bar_placeholder` / `tab_basic_mode_container_layout`；
- **读取**：经 `NativeTabBar` 反射枚举原生 TabView，取标题 / tag / 红点 / 数字 / 图标；
- **压制**：原底栏 `alpha=0` + `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`
  （保留原生 View 使 `performClick` 仍有效），内容区底部预留归零、
  手势占位转 GONE，页面内容真正延伸到底栏之下 → 形成「悬浮」而非「换一块底栏」；
- **浮层**：往 `android.R.id.content` 加底部居中圆角胶囊（58dp 高、左右 14dp 边距、
  12dp elevation + `clipToOutline` 圆形阴影），
  用 `ViewCompat.setOnApplyWindowInsetsListener` 把胶囊抬到系统导航条之上；
  图标经 `constantState.newDrawable(resources, theme).mutate()` **拷贝商店自己的 Drawable**
  （不猜图标语义、不污染原生 drawable 状态），红点 / 数字角标照抄；
- **可逆**：`dispose()` 同步还原 alpha / 无障碍 / 内容 margin / 占位可见性并摘除监听。

工程细节：
- **指纹去重**：`buildSignature()` 把 tag、标题、数字、红点、选中项与两个子开关状态
  拼成指纹，**每帧 `OnPreDrawListener` 只在状态真变化时**才重建子 View 与重设属性；
- **点击闭环**：胶囊点击 → 原生 `tab.performClick()` → 下帧从原生读回选中态，
  本模块不自己切页，避免破坏商店的页面栈与埋点；
- **深浅色**：按 `Configuration.UI_MODE_NIGHT_MASK` 取两套胶囊底色，
  强调色优先解析主题 `colorAccent` / `colorControlActivated`，取不到兜底 `#0A84FF`；
- **与净化协同**：`badgesAllowed()` = 「显示角标」子开关 且 未开启「隐藏底栏角标」，
  避免净化与悬浮底栏互相打架；
- **宿主唯一**：以 `WeakHashMap<Activity, FloatingBarHost>` 管理，不泄漏已销毁 Activity。

#### `hooks/market/FloatingBottomBar.kt`
生命周期 Hook 入口，`prefKey = KEY_FLOATING_BAR`：
- Hook `MarketTabActivity` 的 `onCreate` / `onResume` / `onDestroy`，
  并用 `installed` 集合防止同一方法被重复装 hook；
- **刻意不使用 `BaseHook.hooked`**：那套封装在开关关闭时会直接 `proceed()` 跳过整个
  拦截体，导致「关掉开关后浮层仍挂在屏幕上、原生底栏也还原不了」。
  改为在拦截体内**实时读 `enabled()`**：开启→挂载，关闭→释放并还原；
  因此**切换开关无需重启应用商店**；
- `onDestroy` 释放宿主与重试监听；
- onCreate 时底栏常未 inflate，用带**上限**的 `OnPreDrawListener` 按帧重试
  （60 帧或 4s 后自动放弃），避免监听长期驻留空转。

### 改动文件
- `Settings.kt`：新增 `KEY_FLOATING_BAR` / `KEY_FLOATING_BAR_BADGE` / `KEY_FLOATING_BAR_LABEL`；
- `apps/Market.kt`：注册 `FloatingBottomBar`（排在 `TabFilter` 之后，
  保证标签筛选先装、悬浮底栏读到的是筛选后的原生 tab）；
- `MainActivity.kt`：**主页「高级功能」组新增「悬浮底栏」开关**（默认关闭，属视觉增强）；
- `SubSettingsActivity.kt`：PAGE_TABS 新增「显示标签文字」「显示角标」两个子选项，
  主开关关闭时整组隐藏（与既有 `tabSelectBlock` 门控同一模式）；
- `util/NativeTabBar.kt`：补 `viewByResName()` 与 `selectedIndexOf()`
  （优先反射 `getSelectedIndex()`，失败回退按 `isSelected` 查找）。

### 静态检查与已知限制
- 括号 / 圆括号平衡通过；色值字面量超 Int 范围处均已显式 `toInt()`；
  `Drawable.isStateful` 只读（无 setter）已避免非法赋值；可空 drawable 判空写法已修正；
  `dp()` 与 `dpf()` 按 Int / Float 场景分列。
- **仍未做 Gradle 编译**（本地无 Android SDK / JDK），装机前请在 Android Studio 编译。
- 悬浮底栏依赖商店资源名与 TabView 公开方法名，漂移时 `attach()` 返回 null，
  重试超限后静默放弃，**不会**残留半截浮层。

### 构建修复（CI 实测）
首次装机编译（GitHub Actions `:app:compileDebugKotlin`）报 2 个错误，均为同一根因：
```
e: FloatingBarHost.kt:267:35 Unresolved reference 'text'.
e: FloatingBarHost.kt:271:35 Unresolved reference 'text'.
```
- **根因**：`badgeViews` 声明为 `ArrayList<View>`，而赋值处用的是 `TextView` 专有的 `text` 属性；
  `View` 上没有 `text`，Kotlin 无法解析该引用。
- **修复**：集合类型收窄为 `ArrayList<TextView>`（入列元素本就是 `TextView`，无需强转）。
- **同类排查**：`items`（`ArrayList<View>`）仅用于追加与 `clear()`，未访问子类属性，无同类问题；
  本次编译除这 2 处外**零错误零警告**，其余改动均通过。

---

## 七、榜单页底栏回退 + 图标文字错位（装机反馈修复）

### 症状
开启悬浮底栏后进商店正常，**点「榜单」后恢复成原生底栏**；且底栏图标与文字未对齐。
LSPosed 日志：挂载成功 3 次（50.481/50.494/50.495），**之后再无任何悬浮底栏日志**，
全程零异常、零崩溃、无「已卸载」、无「重试超限」。

### 根因：原生 View 引用被长期持有，商店重建底栏后压制错了对象
`attach()` 时一次性 `findViewById` 解析出底栏容器等 View 并存为 `val`，此后从不重查。
关键认知：**`visibility` 只是 View 对象上的一个字段，已脱离视图树的旧实例依然返回 `VISIBLE`**，
所以 `shouldShow()` 检测不到"我盯的那个 View 已经不在屏幕上了"。

进榜单时商店重建底栏 View 实例 → 我们继续把 `alpha=0` 压在那个"幽灵旧实例"上，
新生成的底栏完全不受控 → 原生底栏重现，且**不产生任何异常**（与日志表现一致）。

对照参考实现可确认这是移植时漏掉的防护：HyperModifier 的 `EarlyBottomBarSuppressor`
每次都做 `currentBottomBar?.takeIf(View::isAttachedToWindow) ?: findBottomBar()`
（缓存对象一旦脱离视图树就重新查找），我移植时省掉了这一层。

### 修复
1. **引用可替换**：底栏容器 / `tab_container` / `fragment_container` / 精简模式容器 /
   手势占位 全部由 `val` 改 `var`；构造参数改名 `initXxx` 避免与属性同名的作用域歧义。
2. **每帧刷新 + 换绑即作废本帧**：`refreshViewRefs()` 在引用不再 attached 时重新 `findViewById`，
   并重置原始 alpha / 无障碍快照与 `suppressing`，让新实例下一帧被重新压制；
   真的换过引用则本帧数据已过期，`return` 等下一帧干净重建。
3. **切页强制重查**：部分版本榜单页会**另建一套底栏**，此时旧实例"仍挂在树上"，
   只判 `isAttachedToWindow` 换不过来 → 在状态指纹变化（=切页）时 `force=true` 重查一次。
   只在切页跑，不增加每帧开销。
4. **判定必须带 attach 检查**：`shouldShow` 抽出为 `visibilityBlocker()`，
   把「已脱离视图树」作为独立于「visibility != VISIBLE」的第一类状态。
5. **还原去抖**：判定失败后连续 2 帧才真正还原原生底栏，避免切页瞬间某帧标签数为 0
   就把原生底栏放回去造成抖动。
6. **可诊断性**：阻塞原因与还原动作都经 `debugLog` 输出（暂停原因 / 连续帧数 /
   实例已切换 / 已还原 / 恢复显示）。此前只在挂载与卸载时打日志，
   导致这份日志**无法回答"为什么没了"**——属于我的埋点缺陷。
7. **日志去重**：`tryAttach` 区分"新建"与"复用已有宿主"，消除重复的「悬浮底栏已挂载」。

### 图标与文字错位
1. **`FIT_CENTER` → `CENTER_INSIDE`**：`FIT_CENTER` 会把小图标**放大**填满 22dp，
   商店各图标原始尺寸不同，放大后各 tab 视觉大小不一，即"没对齐"的观感；
   `CENTER_INSIDE` 只缩不放，各图标保持在同一视觉基准。
2. **图标容器 `MATCH_PARENT` → `WRAP_CONTENT` + 水平居中**：原先角标按 `TOP|END`
   定位时被推到整列最右，与居中的图标错开；容器收窄贴住图标后角标自然落在图标右上角。
3. **文字显式 `CENTER_HORIZONTAL`**：标题被 tab 宽度约束成父宽时 TextView 默认左对齐，
   会与居中图标错开；补上 `gravity` 与 `layoutParams.gravity` 后每列几何一致。

### 本次验证边界（重要）
以上是基于日志与代码比对的**推断性根因**，修复本身未经设备实机验证。
但修复后日志会直接给出答案：若榜单页仍回退，新日志会打印出具体阻塞原因
（`底栏容器已脱离视图树` / `tab_container 不可见` / `精简模式底栏占用` / `标签数=N`），
届时可据此精确定位，不再靠猜。
