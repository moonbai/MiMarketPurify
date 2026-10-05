# MiMarketPurify UI/功能改造 · 变更日志

> 本次改造针对 8 项 UI / 功能需求，引入 MiuiX 偏好组件库（`miuix-preference-android:0.9.4`，与既有 `miuix-ui-android` 同源同版本），并对商店悬浮底栏 Hook 做了「独立参数接线」。
> 涉及第三方库引入、核心 Hook（商店底栏外观读取）变更，均已按项目约定先行与用户确认。

## 一、需求对照与实现

| # | 需求 | 实现方式 | 关键文件 |
|---|------|---------|---------|
| 1 | 外观深浅色单选改用 MiuiX SpinnerPref | `PrefThemeMode` 用 `WindowSpinnerPreference(items, selectedIndex=mode)` 下拉单选替换原 `RadioRow` | `ui/components/SettingsComponents.kt`、`SubSettingsActivity.kt` |
| 2 | 筛选底栏多选改用 MiuiX Grouped Spinner 伪多选 | `PrefTabFilterSpinner` 用 `WindowSpinnerPreference(entries=[DropdownEntry(items)], collapseOnSelection=false)`，逐项勾选 | `ui/components/SettingsComponents.kt`、`SubSettingsActivity.kt` |
| 3 | 商店悬浮底栏「添加回」圆角半径 / 背景透明度 / 距底栏距离 | 新增独立 store key（`KEY_FLOAT_CORNER_RADIUS` / `KEY_FLOAT_BAR_ALPHA` / `KEY_STORE_FLOAT_BOTTOM_MARGIN`）；Hook 改读 `storeBarRadiusDp()` / `storeBarBottomMarginDp()` / `storeBarAlphaPercent()`，透明度烘焙进 `barColor` | `Settings.kt`、`SubSettingsActivity.kt`（`StoreFloatingBarScreen`）、`util/ComposeFloatingBarHost.kt` |
| 4 | 主题与外观中插件悬浮底栏加独立开关，关闭后隐藏相关开关 | `PluginFloatingBarSection` 顶部新增「启用插件悬浮底栏」总开关（`KEY_FLOAT_BAR_ENABLE`）；开启时才显示外观子控件；主页 `BottomNavBar` 随开关显隐 | `SubSettingsActivity.kt`、`MainActivity.kt`、`Settings.kt` |
| 5 | 模块功能中「主题与外观」置首；「随机推荐」移入主题与外观 | 主页「模块功能」分组：主题与外观置顶、删除随机推荐开关；随机推荐开关移入「主题与外观 → 主页推荐」 | `ui/MainScreen.kt`、`SubSettingsActivity.kt` |
| 6 | 界面设置 / 高级功能两组整体整理（保留两组·重排） | 「悬浮底栏（商店）」由「界面设置」移入「高级功能」（它是针对商店的 Hook，与下载超级岛 / 细节修正 / 升级提醒弹窗同类） | `ui/MainScreen.kt` |
| 7 | 滑动条改 Adjust Volume 样式（可滑可改数值） | `PrefSlider` 在 `Slider` 旁加 `BasicTextField`（数字键盘），滑动 / 输入双向联动，回车或松手提交 | `ui/components/SettingsComponents.kt` |
| 8 | 功能开关切换后 Snackbar 提示；需重启商店则带「重启商店」Action；主题与外观内加总开关 | `SwitchRow` 切换后读 `KEY_SWITCH_HINT` 决定弹 Snackbar（`affectsStore && !remotePrefsAvailable()` 时带 Action）；`LocalSnackbarHost` + `ModuleTheme` 统一安装宿主；主题与外观新增「开关操作提示」开关 | `ui/LocalSnackbarHost.kt`、`ui/ModuleTheme.kt`、`ui/components/SettingsComponents.kt`、`SubSettingsActivity.kt` |

## 二、新增 / 修改文件清单

**新增**
- `app/src/main/java/com/mars/mimarketpurify/ui/LocalSnackbarHost.kt` — `CompositionLocal<SnackbarHostState?>` + `ProvideSnackbarHost`（模块统一 Snackbar 宿主）。

**依赖**
- `gradle/libs.versions.toml` — 新增 `miuix-preference-android = { group = "top.yukonga.miuix.kmp", name = "miuix-preference-android", version.ref = "miuixKmp" }`
- `app/build.gradle.kts` — `implementation(libs.miuix.preference.android)`

**核心代码**
- `Settings.kt` — 新增 store 底栏独立 key（`KEY_FLOAT_CORNER_RADIUS`、`KEY_FLOAT_BAR_ALPHA`、`KEY_STORE_FLOAT_BOTTOM_MARGIN`）、提示开关 `KEY_SWITCH_HINT`、常量与 getter（`storeBarRadiusDp()` / `storeBarAlphaPercent()` / `storeBarBottomMarginDp()` / `isPluginBarEnabled()`）。
- `ui/components/SettingsComponents.kt` — `SwitchRow`（Snackbar 提示 + 重启 Action）、`PrefSwitch`（透传 `affectsStore`）、`PrefThemeMode`（Spinner 单选）、`PrefTabFilterSpinner`（伪多选）、`PrefSlider`（Adjust Volume 样式）。
- `SubSettingsActivity.kt` — `TabsScreen` 伪多选、`StoreFloatingBarScreen` 三项回加控件、`PluginFloatingBarSection` 总开关 + 联动隐藏、`ThemeScreen` 随机推荐 / 提示开关。
- `ui/MainScreen.kt` — 分组重排（需求5/6）、`BlurHeader` 增加「关于」回退按钮。
- `MainActivity.kt` — 主页底栏随插件悬浮底栏开关显隐、关于页回退入口。
- `ui/ModuleTheme.kt` — 接入 `ProvideSnackbarHost`。
- `util/ComposeFloatingBarHost.kt` — `syncNativeState` 改读 store getter，新增 `barAlphaPercent` 字段并混入 `barColor`。

## 三、需用户确认的两点（非阻塞）

1. **需求6 重排方案**：按「保留两组」将「悬浮底栏（商店）」从「界面设置」挪到「高级功能」，使「界面设置」只含 4 个界面精简导航、「高级功能」含全部行为型 Hook。若你希望保留在原位或换一种分法，告诉我即可调整。
2. **需求4 关掉「插件悬浮底栏」的副作用**：关闭后主页/关于底部的「主页·关于」切换栏也会隐藏（这是「禁用底栏」的自然结果）。为不丢失关于页，已在主页顶栏加了一个「关于」按钮兜底。若你更希望底栏常驻、只控制外观，也可改。

## 四、已知限制

- 本沙箱无 Android SDK，未执行 `assembleDebug` 实编译；已做导入、API 用法与解耦关系的逐项静态核对。请在 Android Studio / 本地 `./gradlew assembleDebug` 做最终编译验证。
- 商店底栏三项新参数的「实时生效」依赖 LSPosed 远程偏好通道；不支持远程偏好的框架下需重启商店一次（与既有其它开关行为一致）。

## 五、编译修复（CI 反馈）

CI 的 `compileReleaseKotlin` 报了 `SwitchRow` 相关的 `NO_VALUE_FOR_PARAMETER` / `TOO_MANY_ARGUMENTS` 错误：所有调用点都用**尾随 lambda**（如 `SwitchRow(...) { on -> ... }`），而 Kotlin 的尾随 lambda 会绑定到**最后一个参数**。原签名中 `onCheckedChange` 排在 `affectsStore: Boolean` 之前，尾随 lambda 被误塞给 `affectsStore`，导致 `onCheckedChange` 缺失且类型不匹配。

**修复**：将 `SwitchRow` 的参数顺序对调，使 `onCheckedChange` 成为最后一个参数：

```kotlin
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    /** 是否影响应用商店；决定提示条是否带「重启商店」按钮 */
    affectsStore: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,   // 末位：承接尾随 lambda
)
```

该调整对全部 7 处调用点（含 `PrefSwitch` 内部调用）均向后兼容：具名 `affectsStore` 与尾随 lambda 写法全部继续生效，无需改动调用点。

## 六、运行时崩溃修复（单选 / 多选点击闪退）

用户反馈（Android 17 / API 37，AppErrorsTracking 捕获）：点击「外观深浅色」下拉单选、「筛选底栏」下拉多选等 MiuiX `WindowSpinnerPreference` 时崩溃，异常为：

```
java.lang.IllegalStateException: No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner
```

**根因**：MiuiX 的 `Window*` 类（如下拉单选 / 多选组件）在展开独立下拉窗时，会通过 `WindowNavigationEventScope` 读取 `LocalNavigationEventDispatcherOwner.current` 并处理返回手势；当组合树根部未提供该 owner 时，非空 getter 直接抛异常。MiuiX 的示例 App 靠 `NavDisplay` 导航容器提供它；本模块不使用 `NavDisplay`，且各 Activity 直接以 `ModuleTheme` 为根壳，因此从未提供该 owner，导致点击展开即崩。

**修复**：

1. `ui/ModuleTheme.kt` — 在根部用 `rememberNavigationEventDispatcherOwner(parent = activityOwner)` 创建并经由 `CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navOwner)` 提供。`activityOwner` 优先取宿主 `ComponentActivity` 自带的 dispatcher（视图树解析，保留系统返回手势），为空则退化为独立根 dispatcher，二者都能消除崩溃。
2. `gradle/libs.versions.toml` + `app/build.gradle.kts` — 显式声明 `androidx.navigationevent:navigationevent-compose:1.1.2`（与 MiuiX 0.9.4 传递依赖的版本一致），供模块直接引用该 API。

`ModuleTheme` 被全部 Activity（MainActivity / SubSettingsActivity / About / Privacy / Search）用作根壳，修复一处即覆盖所有页面。

**注意**：本沙箱无 Android SDK 无法实编译，已确认所用 API（`LocalNavigationEventDispatcherOwner`、`rememberNavigationEventDispatcherOwner`、`findViewTreeNavigationEventDispatcherOwner`）均为 navigationevent-compose 1.1.2 的标准导出 API。请重新触发 CI / 本地 `./gradlew assembleRelease` 验证，并在真机点开下拉确认不再闪退、且返回手势可正常收起下拉。
