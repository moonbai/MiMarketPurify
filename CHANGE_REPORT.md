# Mi Market Purify 优化改动说明

> 针对用户的四个问题：
> ① 主页看不见内容，需完全重构主页主题；
> ② 悬浮底栏仍是自建的，未按 AritxOnly/HyperModifier 引入第三方组件；
> ③ 构建后 APP 体积过大（33M）；
> ④ 同步参考仓库逻辑，添加检查更新功能。
>
> 约束（来自项目说明）：复用原有架构、不重构整体代码、新增代码风格与仓库一致、改动最小化。
> 其中 ① 与 ② 经与你确认后采用 **"整页改用 Compose"** 与 **"移植 HyperModifier 的 deadliner 浮底组件"** 方案。

---

## 一、问题 ③：构建体积过大（33M）

### 根因（本次定位）
33M 的真因是 **CI 长期只打 `assembleDebug`**——debug 构建不做 R8 混淆 / 不裁剪资源，自然巨大。
与 Compose / Miuix 本身无关：参考项目 HyperModifier 同样用 Compose + Miuix，但只打 release，
体积仅几 MB。上一轮"移除 material3 + R8 full mode"的方向被本次推翻——material3 必须重新引入
（悬浮底栏忠实移植 HyperModifier 组件要直接用 `MaterialTheme`/`Icon`/`Text`），体积改由
**release 构建的 R8 + shrinkResources** 兜底。

### 改动
1. **`.github/workflows/build.yml`**：`assembleDebug` → **`assembleRelease`**；上传产物改为 `release/*.apk`；
   产物名 `MiMarketPurify-release`；新增"还原签名密钥"步骤（仅当配置了 `SIGNING_KEY` secret 时执行）。
2. **`app/proguard-rules.pro`**：对齐 HyperModifier 的极简规则——仅 `-keep` Xposed 入口子类、
   `io.github.libxposed.service.**`、manifest 声明的 4 个组件 + App + EntryGuardReceiver；
   仅 `-dontwarn`；**删除** `-repackageclasses` / `-overloadaggressively` / `obf-dictionary` /
   `-keepattributes` 等激进项；保留 Kotlin/JDK/Log 死代码消除。并删除遗留的 `obf-dict.txt`。
3. **`app/build.gradle.kts`**：
   - **重新引入 `material3`**（BOM 管理版本），用于悬浮底栏与主页 Compose；
   - release 构建 `isMinifyEnabled = true` + `isShrinkResources = true` 保持不变；
   - **条件式 release 签名**：仅在 CI 注入 `SIGNING_KEY` / `SIGNING_KEY_ALIAS` /
     `SIGNING_KEY_PASSWORD` / `SIGNING_PASSWORD` 时才签名（对齐 HyperModifier 方案）。
     密钥库（base64）在 CI 步骤里解码为 jks，路径经 `-PmimarketSigningStoreFile` 传入；
     未配置密钥时仍产出已裁剪的**未签名**小体积包（仅无法安装）。

### 关键片段
```yaml
# .github/workflows/build.yml
env:
  HAS_SIGNING_KEY: ${{ secrets.SIGNING_KEY != '' }}
# ...
- name: Restore signing key
  if: ${{ env.HAS_SIGNING_KEY == 'true' }}
  run: echo "$SIGNING_KEY" | base64 -d > "$RUNNER_TEMP/mimarket.jks"
- name: Build Release APK
  run: |
    set -o pipefail
    if [ "$HAS_SIGNING_KEY" = "true" ]; then
      ./gradlew assembleRelease -PmimarketSigningStoreFile="$RUNNER_TEMP/mimarket.jks" 2>&1 | tee gradle-build.log
    else
      ./gradlew assembleRelease 2>&1 | tee gradle-build.log
    fi
```
```kotlin
// app/build.gradle.kts（条件式签名）
val signingStoreFile = providers.gradleProperty("mimarketSigningStoreFile").orNull
val hasReleaseSigning = !signingStoreFile.isNullOrBlank() && /* alias/密码均非空 */
// signingConfigs.create("release") { if (hasReleaseSigning) { storeFile=...; ... } }
// buildTypes.release { if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release") }
```

---

## 二、问题 ②：悬浮底栏改用 HyperModifier 组件

### 方案（按你的确认：移植 HyperModifier 的 deadliner 组件）
参考 HyperModifier `deadliner` 包，移植其 **`MiuixFloatingTabBar`** 到本工程的 `ui/deadliner` 包：
- `MiuixFloatingTabBar.kt`（组件本体）、`FloatingTabMotion.kt`（动画/数学，逐字移植）、
  `FloatingNavigationShadow.kt`（投影，用 `Modifier.shadow` 平替其自研 `softGlassShadow`）。
- **依赖平替**（规避 HyperModifier 的私有/内部依赖，保持改动最小化）：
  - 玻璃材质：`SoftGlassSurface` / `GlassMaterialRecipe` / `GlassMaterialInteraction`
    → 改用本工程已有的 **`ViewBackdropLayer` + `LayerBackdrop`** 实时毛玻璃（无快照时回退半透明色块）；
  - 形状：`com.kyant0:shapes` 的 `Capsule()` → **`RoundedCornerShape(percent = 50)`**（不引入 shapes 库）；
  - 主题：`AdvancedMaterial` / `MhmPresetColors` 不需要——内容色取自 `MiuixTheme.colorScheme.onSurfaceContainer`，
    深浅由亮度判定，与宿主 `MiuixTheme(colors=…)` 同源。
- Market 浮底对齐 HyperModifier 的 `MarketFloatingNavigation`：**`Stacked` 布局（图标在上、文字在下）**，
  原生图标设 `preserveOriginalIconColors = true`（沿用应用商店自带配色）。

### 改动文件
- 删除旧 **`ui/floatingbar/FloatingTabBar.kt`**（原自写标签条）；
- 新增 `ui/deadliner/{MiuixFloatingTabBar,FloatingTabMotion,FloatingNavigationShadow}.kt`；
- 改 **`util/ComposeFloatingBarHost.kt`**：`MarketNavigationContent` 改用 `MiuixFloatingTabBar` /
  `MiuixFloatingTabItem`，图标缺失时回退 1×1 透明 `Painter`，`layout = Stacked`。

```kotlin
// util/ComposeFloatingBarHost.kt（节选）
MiuixFloatingTabBar(
    items = items, selectedKey = selectedKey,
    onItemSelected = { onDestinationSelected(it.key.toIntOrNull() ?: 0) },
    backdrop = backdrop, snapshot = backdropSnapshot,
    layout = MiuixFloatingTabLayout.Stacked,
    modifier = Modifier.fillMaxWidth().onGloballyPositioned { /* 毛玻璃取景框 */ },
)
```

---

## 三、问题 ①：主页整页 Compose（修复"看不见内容"）

### 根因
原主页用原生 View 体系，黑屏主因是主题/取色链路在深色或某些 ROM 下未正确生效。
按你确认，直接 **整页改用 Compose**，用 `MiuixTheme.colorScheme` 统一取色，与悬浮底栏同源。

### 方案
- **`SettingsBaseActivity`** 改继承 **`ComponentActivity`**（以支持 `setContent`），并把需跨包访问的
  偏好读写/隐藏图标等方法提为 `internal`；
- **`MainActivity.kt`** 重写 `onCreate`：调用 `setContent { MiuixTheme(colors=…) { MainScreen(…) } }`，
  并设置状态栏图标反色；逻辑方法（`adKeys`/`mineKeys`/`miscKeys`/`countText`/`tabsText`/`tabbarText`/`openPage`）
  保留为 `internal` 供 Compose 复用；
- 新增 **`ui/MainScreen.kt`**：完整 Compose 主页——固定顶栏（标题+副标题+"关于"胶囊）、滚动内容区、
  状态卡（框架连接状态）、总开关、分类入口（广告净化 / 底栏自定义 / 悬浮底栏配置 / 我的页精简 / 其他界面精简）、
  高级功能（下载超级岛 / 细节修正 / 升级提醒弹窗）、模块功能（隐藏桌面图标 / 调试模式 / 检查更新）。
  交互对齐原原生页：分组圆角卡片、标题+摘要+开关整行可点、总开关置灰其余行。
  远程偏好读写、隐藏桌面图标、入口自愈等逻辑全部复用基类，未新增 Hook 或反射。

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    EntryGuardReceiver.ensureEntryEnabled(this)
    WindowCompat.getInsetsController(window, window.decorView)
        ?.isAppearanceLightStatusBars = !isNight()
    setContent {
        MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
            MainScreen(activity = this@MainActivity)
        }
    }
    maybeCheckUpdateOnFirstLaunch()
}
```

---

## 四、问题 ④：检查更新

### 方案（同步参考仓库 + 对齐 HyperModifier 的 UpdateChecker）
- 新增 **`util/UpdateChecker.kt`**：`UpdateChecker.check()` 读取
  `https://api.github.com/repos/moonbai/MiMarketPurify/releases/latest`，
  语义化版本比对（`compareVersions` / `numericSegments`），超时/异常兜底为 `Unavailable`；
  `UpdateCheckResult` 密封接口：`Available(versionName, releaseUrl)` / `Latest` / `Unavailable`。
- **`MainActivity`** 新增：
  - `checkForUpdates()`：手动检查（子线程请求，主线程 Toast 并在有更新时打开发布页）；
  - `maybeCheckUpdateOnFirstLaunch()`：首次启动静默检查一次（本地 `SharedPreferences` 标记，不依赖远程偏好）；
- **`ui/MainScreen.kt`** 模块功能分组新增 **"检查更新"** 入口行；
- **`AndroidManifest.xml`** 新增 `android.permission.INTERNET`（网络请求所需）。

```kotlin
// util/UpdateChecker.kt（节选）
object UpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/moonbai/MiMarketPurify/releases/latest"
    fun check(): UpdateCheckResult = try { /* …比对 BuildConfig.VERSION_NAME… */ }
        catch (_: Exception) { UpdateCheckResult.Unavailable }
}
```

---

## 五、回归修复：release 构建后的两个运行期问题

上一轮改为 `assembleRelease` + R8 混淆后，真机出现两类运行期问题，已修复。

### 5.1 所有子界面 / 关于页白色字体、看不见内容

### 根因
`AppTheme` 是 `Theme.Material.Light.NoActionBar`（**视图层亮色主题**）。小米 / HyperOS 的
**强制深色模式**会对此类"未声明深色感知"的活动做反色：把自定义着色的**文本**反成白色，却
**不反** `setBackgroundColor(White)` 这种纯色背景 → 最终"白字白底"不可见。
Compose 主页用 `MiuixTheme` 自行着色、`forceDarkAllowed` 对它无效，所以**主页正常、原生子页异常**——
这正好对应"主页能看、子页白字"的现象。

### 改动
1. **`app/src/main/res/values/styles.xml`**：`AppTheme` 增加
   `<item name="android:forceDarkAllowed">false</item>`，禁止系统强制深色对本活动反色；
   明暗改由 `isNight()` 自行决定（浅色主题下也按系统深色渲染）。
2. **`app/src/main/java/com/mars/mimarketpurify/MiuiX.kt`**：`isNight()` 改为
   **优先用 `UiModeManager` 读取系统深色设置**，回退到 `resources.configuration.uiMode`。
   避免亮色视图主题把 `uiMode` 锁成"亮"导致 `isNight()` 恒为 `false`、模块永远浅色；
   现在与 Compose 主页的 `isSystemInDarkTheme()` 一致，正确跟随系统深色模式。

### 5.2 所有 hook 功能失效

### 根因
`proguard-rules.pro` 对 Xposed 入口类用了 **`-keep,allowobfuscation`**。R8 会把入口
`MainHook`（继承 `EasyXposedInit`）**重命名**，但 libxposed 在编译期按**原类名**写入
`META-INF/xposed/*` 注册文件、运行时按该原类名反射加载 → 找不到入口 → **全部 hook 失效**。
（debug 构建无混淆所以此前一直正常，切换到 release 后才暴露。）

### 改动
1. **`app/proguard-rules.pro`**：入口 keep 规则去掉 `allowobfuscation`/`allowoptimization`，
   改为 `-keep public class * extends com.mars.mimarketpurify.init.EasyXposedInit { *; }`
   （保留类名与全部成员、不重命名），保证框架按原类名 `com.mars.mimarketpurify.MainHook` 找到入口。
2. **`app/build.gradle.kts`**：把 `packaging { resources { excludes += "**"; merges += "META-INF/xposed/*" } }`
   从 `buildTypes` 内部**移到 `android {}` 顶层**，确保 `META-INF/xposed/*` 注册文件在 release 包中
   始终被保留（先排除全部资源再单独 merge 回 xposed 注册文件）。

---

## 六、风险点 / 注意事项
1. **MiuiX 字段名（历史坑，已规避）**：miuix-kmp `0.9.4-rc01` 的 `Colors` **不含** `onSurfaceVariant`/`outlineVariant`，
   真实字段为 `onSurfaceSecondary`（次级文本）与 `dividerLine`（分割线）。本工程 `MiuiX.kt` 的方法名保留
   `onSurfaceVariant()`/`outlineVariant()`，仅内部改访问真实字段；Compose 侧直接用 `MiuixTheme.colorScheme.onSurfaceSecondary` 等。
2. **毛玻璃降级**：宿主 `LayerBackdrop` 采样失败时，`MiuixFloatingTabBar` 回退半透明色块（无真实模糊，行为不变）。
3. **R8 裁剪回归**：release 构建更激进裁剪，建议真机验证所有开关/页面/悬浮底栏（full mode 下偶有反射类被砍风险，
   已用显式 `-keep` 兜底 Xposed 入口与 manifest 组件）。
4. **签名**：未配置 `SIGNING_KEY` 等 secret 时，CI 产出的是**未签名** release 包（体积小但无法安装）；
   配置后即可产出已签名可安装包（你已在仓库 Secrets 中配置，构建会自动签名）。
5. **沙箱无 Android SDK**：本环境仅 `java`/`gradle`，缺 `sdkmanager`/`ANDROID_HOME`，无法真机打包验证；
   上述为编译级核查（符号引用、import、ProGuard、依赖、manifest），请在本机/CI 完成最终 `assembleRelease` 与真机回归。

## 七、测试注意事项
- [ ] 本机/CI 执行 `assembleRelease`，确认产物为已签名、可安装、且体积仅几 MB（问题 ③）；
- [ ] 浅色/暗色系统主题下分别打开主页，确认整页 Compose 配色、状态栏图标反色正确，内容可见（问题 ①）；
- [ ] 开启悬浮底栏，验证选中/未选中态、角标、`Stacked` 布局图标+文字、毛玻璃效果（问题 ②）；
- [ ] 点"检查更新"与首次启动，确认版本比对、Toast 提示、跳转发布页正常（问题 ④）；
- [ ] LSPosed 作用域勾选"应用商店"，确认模块入口、远程偏好读写、各 Hook 开关不受影响（R8 回归）。

---

## 八、全局 Compose 统一（主页底栏化 + 子页/关于页 Compose 化 + 悬浮底栏换用 MiuiX 主题）

### 背景
- 上一轮「主页整页 Compose + 子页保留原生 View」的混合架构，导致 **两套 UI 语汇并存**：
  原生 `SettingsBaseActivity` 的 `addSwitchRow/addNavRow/groupCard/...` 与 Compose `MainScreen` 的
  `SwitchRow/NavRow/GroupCard/...` 是同一套卡片/开关/导航的两种实现（即用户感知到的"重复"）。
- 二级页（子设置页、关于页）因为是原生 View + 字面 `setBackgroundColor`，在小米强制深色模式下
  仍可能出现"白字白底"；而 Compose 主页由 `MiuixTheme` 主题感知渲染，天然规避该问题。
- 悬浮底栏（`MiuixFloatingTabBar`）虽已包 `MiuixTheme`，但标签字号与角标颜色仍取自
  `androidx.compose.material3.MaterialTheme`，与主页 `MiuixTheme.colorScheme` 不一致。

### 改动
1. **新增 `ui/components/SettingsComponents.kt`（公共 Compose 构件）**：把 `GroupCard / SectionHeader /
   SwitchRow / NavRow / CheckboxRow / Footer / SubTopBar` 与偏好绑定构件 `PrefSwitch / PrefSlider /
   PrefColorRow`、以及 `AboutContent` 统一收口到此处。**主页、二级设置页、关于页共用同一套**，
   从根上消除重复。
2. **`SubSettingsActivity` 整体 Compose 化**：删除全部原生 View 构建器，改用 `setContent { MiuixTheme { ... } }`，
   按 `page` 分发到 `AdsScreen/MineScreen/TabsScreen/MiscScreen/TabBarConfigScreen`；
   同时删除死代码 `buildModule`/`buildExtra` 与孤儿常量 `PAGE_MODULE`/`PAGE_EXTRA`。
3. **`AboutActivity` 整体 Compose 化**：改为薄壳 `ComponentActivity` + `setContent`，内容复用 `AboutContent`。
4. **`SettingsBaseActivity` 降为纯逻辑基类**：删除全部原生 UI 构建器、`ColorPickerView` 自定义取色器、
   相关数据结构与 `updateGateState` 机制；新增 `refreshSignal`（Compose 观察 service 重连以重读偏好）。
   所有远程偏好读写、service 连接补写、桌面图标隐藏等逻辑原样保留。
5. **`MainActivity` 底栏化**：主页改为底部标签栏，**一栏「主页」、一栏「关于」**（`AboutContent` 作为第二个标签，
   与原独立 `AboutActivity` 共用同一内容）；顶栏"关于"按钮改为切换到底栏关于页。
6. **颜色选择器改用 miuix 组件**：`TabBarConfigScreen` 里的 4 个颜色项改用 `top.yukonga.miuix.kmp.basic.ColorPicker`
   内嵌于 `AlertDialog`，替换原先自绘的 `ColorPickerView` + 原生 `AlertDialog`。
7. **悬浮底栏换用 MiuiX 主题**：`MiuixFloatingTabBar` 删除 `MaterialTheme` 依赖——
   标签字号改用显式 `fontSize`；角标颜色由 `MaterialTheme.colorScheme.{error,onError}` 改为
   `MiuixTheme.colorScheme.{error,onError}`，与主页同源。

### 配色一致性说明
- 所有页面配色统一来自 `MiuixTheme.colorScheme`（miuix-kmp `0.9.4-rc01` 的 `Colors`，含
  `background/surface/onSurface/onSurfaceSecondary/outline/dividerLine/primary/error/onError`）。
- 二级页/关于页改为 Compose 后，**不再依赖原生 `MiuiX.bg()/card()/onSurface()` 等字面色桥接**，
  强制深色反色导致"白字白底"的问题一并消除（与之前 `forceDarkAllowed=false` 的修复互不冲突、双重保险）。

### 测试注意事项
- [ ] `assembleRelease` 编译通过（重点：miuix `ColorPicker(color, onColorChanged)` 签名、各 `MiuixTheme.colorScheme` 字段、material3 控件 `Switch/Checkbox/Slider/AlertDialog` 参数）。
- [ ] 主页底部标签栏：「主页」「关于」切换正常，关于页内容与独立 `AboutActivity` 一致。
- [ ] 二级页（广告/我的/标签/其他/悬浮底栏配置）全部 Compose 渲染，深浅色下文字与背景对比清晰、无白字白底。
- [ ] 悬浮底栏配置页 4 个颜色项点开为 miuix 取色器，选定/恢复默认/取消均正确落盘（#AARRGGBB）。
- [ ] 悬浮底栏（市场 App 内）标签文字大小、角标红点颜色与原先一致，且不再引用 `MaterialTheme`。
- [ ] 总开关关闭时，各二级页开关行置灰禁用；底部标签筛选开关关闭时标签勾选区隐藏；悬浮底栏总开关关闭时参数面板收起。
- [ ] service 重连后，二级页开关状态随远程偏好刷新（依赖 `refreshSignal`）。

---

## 本轮改动：关于闪退 / 顶栏按钮 / 子标题对比度 / 双底栏统一 MiuiX 方案

> 针对用户反馈的五个问题：
> ① 点击关于界面闪退；
> ② 顶部"关于"按钮可隐藏（与底栏关于 Tab 冗余）；
> ③ 功能分区子标题灰色看不清；
> ④ 程序界面底栏采用 MiuiX 官方 `NavigationBar`；
> ⑤ 商店 hook 的悬浮底栏采用 MiuiX 官方 `FloatingNavigationBar`。

### 改动

1. **`ui/components/SettingsComponents.kt` — 关于页闪退加固**：新增 `SafeDrawableImage`，
   用 `ContextCompat.getDrawable(...).toBitmap().asImageBitmap()` 包 `runCatching` 加载
   `R.mipmap.ic_launcher`（自适应启动图标）与 `R.drawable.avatar_mars`，失败回退灰色块，
   避免 release(R8) 下自适应图标资源缺失导致整页 Compose 崩溃。`SectionHeader` 副标题由
   `colors.outline`（最浅 token）改为 `colors.onSurfaceSecondary`，解决看不清。

2. **`ui/MainScreen.kt` — 隐藏顶部"关于"按钮**：`MainHeader` 移除右上角"关于"胶囊按钮，
   `MainScreen(activity)` 不再接收 `onOpenAbout`；同步清理 `background/clickable/Box` 等未用导入。
   关于入口统一由底栏"关于" Tab 承担。

3. **`MainActivity.kt` — 程序底栏迁移官方 `NavigationBar` + 自绘图标**：删除自绘 `BottomNavBar`/`TabItem`
   （`Row` + `HorizontalDivider` + `Box.clickable`），改用官方 `NavigationBar` 容器 + 自定义
   `ProgramNavItem`（`icon: ImageVector` 形参，随主题 `ColorFilter.tint` 着色）。图标全部来自新增的
   `util/NavIcons.kt`（`Home`/`Person` 等 `ImageVector` 在代码中手绘），**不再打包任何商店栅格 WebP**，
   `ic_nav_home.xml` / `ic_nav_about.xml` 亦已删除。顺带修正此前 `import androidx.compose.ui.graphics.Painter`
   包名写错（正确为 `...graphics.painter.Painter`）导致的 `Unresolved reference 'Painter'` 编译失败。

4. **`util/ComposeFloatingBarHost.kt` — 商店悬浮底栏迁移官方 `FloatingNavigationBar` + 自绘图标**：
   - 容器改用官方 `FloatingNavigationBar`（悬浮圆角 + 阴影 + 窗口边距 + 分隔线），内部自定义
     item 渲染**代码中手绘的自绘矢量图标**（`util/NavIcons` 的 `Home`/`Game`/`Rank`/`Apps`/`Person`/`List`），
     按原生 Tab 的标题/标签映射到对应图标；不再拷贝、不再引用商店任何位图，彻底规避 AndResGuard
     资源混淆、零维护。
   - 图标单色、随主题 `ColorFilter.tint(contentColor)` 着色、随暗色模式自动反色；文字标签、角标与
     `Role.Tab` 无障碍语义保留；选中态为中性半透明胶囊背景。
   - 删除不再使用的 `ui/deadliner/MiuixFloatingTabBar.kt`、`FloatingTabMotion.kt`、
     `FloatingNavigationShadow.kt`、`util/NativeTabIconSnapshotter.kt`（仅被本文件引用）。
   - 毛玻璃沿用既有 `ViewBackdropSampler` + `LayerBackdrop` 实时采样，缺失时由 `FloatingNavigationBar`
     半透明 `surfaceContainer` 色块兜底。
   - 顺带修正 `Image(painter=..., tint=...)` 误用 `tint` 参数（标准 `Image` 仅接受
     `colorFilter = ColorFilter.tint(...)`）导致的编译失败。

### 关于"应用商店概率闪退"的归因（非本模块进程内崩溃）
提供的崩溃栈属于 **`com.xiaomi.market` 自身进程**：`Resources$NotFoundException: String resource
ID #0x7f090065` 发生在商店底部导航自定义视图的 `createAccessibilityNodeInfo`（被系统无障碍服务
`AccessibilityNodePrefetcher` 遍历触发），是其**自身的缺失字符串资源 + 无障碍节点创建缺陷**，
与本模块 About 闪退（不同进程、不同根因）无关。
缓解：本模块接管悬浮底栏时已对原生底栏容器设置
`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`（见 `updateNativeChromeReplacement`）+ 启动期
`EarlyBottomBarSuppressor` 压制，使无障碍服务不再遍历官方原生底部导航子树，可在浮底激活期间规避该
崩溃。若关闭本模块浮底或处于未接管窗口，仍取决于商店自身修复（如临时关闭 TalkBack/自动化工具有效）。

### 关键片段
```kotlin
// ComposeFloatingBarHost.kt：官方容器 + 打包的官方多色图标
FloatingNavigationBar(
    color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
    cornerRadius = 28.dp, shadowElevation = 1.dp, defaultWindowInsetsPadding = true,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
        state.tabs.forEach { tab ->
            MarketFloatingTabItem(
                selected = ..., onClick = { onDestinationSelected(tab.nativeIndex) },
                iconRes = iconRes(tab.iconKey(), selected, dark), // 已拷贝进本模块 drawable-nodpi
                label = if (showLabel) tab.label else "", badge = tab.badge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
```

### 测试注意事项
- [ ] `assembleRelease` 编译通过（重点：`FloatingNavigationBar(content: @Composable () -> Unit)` 槽容器、
   `MiuixTheme.colorScheme` 字段名、`painterResource` 对 WebP/PNG 的加载）。
- [ ] 关于页点击不闪退；深浅色下头像/启动图标均正常显示，缺失时回退灰色块不崩溃。
- [ ] 主页顶栏无"关于"按钮；底栏"主页/关于"切换正常。
- [ ] 二级页功能分区子标题在深浅色下均清晰可读（不再是最浅灰）。
- [ ] 程序底栏：主页复用打包的官方 `tab_index` 图标、关于复用 `tab_mine` 图标（均为栅格 WebP，n/p 双态、多色原色）；`ic_nav_home.xml` / `ic_nav_about.xml` 已删除，无任何 `ic_nav_*` 引用残留。
- [ ] 商店内悬浮底栏：显示官方多色 Tab 图标（首页/游戏/排行/软件/我的/分类）、选中态、文字标签、
     角标红点；深浅色图标切换正确；毛玻璃/圆角/阴影观感与原先一致。
- [ ] 商店无障碍场景：开启 TalkBack 后进入商店，浮底接管期间不再触发原生底栏 `Resources$NotFound`。
- [ ] 资源：确认 `res/drawable-nodpi/tab_*.{webp,png}` 共 22 个已打入 APK；包体积仍受 release R8 + shrinkResources 控制。

---

## 四、本轮改动（删除 webp 图标 + 代码自绘 + 修编译错误 + CI 脱敏修复）

### 1. 图标全部改为代码自绘，删除打包的 webp
- **删除** `res/drawable-nodpi/` 下全部 22 个 `tab_*.webp` 与 `tab_*_dark.png`（目录已移除）。
- **新增 `app/.../util/NavIcons.kt`**：用 `ImageVector` 在代码里手绘全部导航图标
  （`Home`/`Person`/`Info`/`Game`/`Rank`/`Apps`/`List`），单色、随主题着色、随暗色反色，
  零位图、零 AndResGuard 混淆风险。
- **`MainActivity.kt`**：程序底栏改用 `NavIcons.Home`/`NavIcons.Person`，移除 `Painter`/`painterResource` 依赖。
- **`util/ComposeFloatingBarHost.kt`**：删除整套 webp 引用（`ICON_PAIRS_LIGHT`/`ICON_PAIRS_DARK`/`iconRes`/
  `resolveIconKey`/`iconKey`），item 改为接收 `ImageVector?` + `colorFilter`；修正 `Image(tint=...)`
  误用为 `colorFilter = ColorFilter.tint(...)`。
- 上文"测试注意事项"中关于"22 个 tab_*.{webp,png} 已打入 APK"的条目**已作废**，以本节为准。

### 2. CI 脱敏修复（GitHub 自带 Secret 遮罩导致的 ****）
- **根因**：`build.yml` 第 56 行 `SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}` 把签名别名作为
  Secret 注入，而该 Secret 的取值 = `MiMarketPurify`（别名起成了应用名）。GitHub 会把**等于任一
  Secret 取值**的字符串在日志 / 产物名 / 下载链接中统一遮罩为 `***`，于是 `MiMarketPurify-release`
  产物名与含 `MiMarketPurify` 的链接全部被替换成 `****`。`build.yml` 中**无任何 sed/replace 脱敏脚本**，
  属 GitHub 自动遮罩副作用，与"商标"无关。
- **修复（不碰 keystore、不破坏覆盖更新）**：
  - `app/build.gradle.kts`：`signingKeyAlias` 改为
    `providers.environmentVariable("SIGNING_KEY_ALIAS").orNull ?: "MiMarketPurify"`，
    别名默认硬编码（与 keystore 真实别名一致），不再强制依赖 Secret。
  - `build.yml`：移除 `Build Release APK` 步骤里的 `SIGNING_KEY_ALIAS` 环境变量注入；
    别名已非敏感项，GitHub 不再有等于 `MiMarketPurify` 的 Secret，遮罩即消失。
- **GitHub 侧配套动作（你来执行）**：仓库 Settings → Secrets 中删除或改名已无用的
  `SIGNING_KEY_ALIAS` Secret（其取值为 `MiMarketPurify`）。仅保留 `SIGNING_KEY` /
  `SIGNING_KEY_PASSWORD` / `SIGNING_PASSWORD` 三条真实密钥。
- **⚠️ 额外排查**：若 `SIGNING_KEY_PASSWORD` 或 `SIGNING_PASSWORD` 的取值也等于/包含 `MiMarketPurify`，
  遮罩仍会残留——需改用 `keytool -storepasswd` / `-keypasswd` 改密码（仅改口令，证书不变，覆盖更新仍可用）。
- **备选方案（若想保留别名作为 Secret）**：在本地持 keystore 的机器执行
  `keytool -changealias -alias MiMarketPurify -destalias mimarket -keystore MiMarketPurify.jks`，
  再把 `SIGNING_KEY_ALIAS` Secret 改为 `mimarket` 并重新提交 base64。仅改别名条目名，密钥对/证书不变。

### 测试注意事项（更新）
- [ ] `assembleRelease` 编译通过（重点：`NavIcons` 的 `ImageVector` 手绘路径、`ImageVector.Builder` 用法、
      `ColorFilter.tint` 用法）。
- [ ] 关于页点击不闪退；深浅色下头像/启动图标均正常显示，缺失时回退灰色块不崩溃。
- [ ] 主页顶栏无"关于"按钮；程序底栏"主页/关于"切换正常，图标为自绘矢量（非 webp）。
- [ ] 商店内悬浮底栏：显示自绘 Tab 图标（首页/游戏/排行/软件/我的/分类）、选中态、文字标签、角标红点；
     深浅色图标切换正确。
- [ ] 资源：`res/drawable-nodpi/` 已无 `tab_*` 文件；包体积仍受 release R8 + shrinkResources 控制。
- [ ] CI：产物名与下载链接中 `MiMarketPurify` 正常显示，不再是 `****`；签名 APK 可正常安装/覆盖更新。
