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
