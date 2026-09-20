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
