# Mi Market Purify 优化改动说明

> 针对三个问题：① 主页未做 MiuiX 风格 ② 打包体积过大 ③ 悬浮底栏未用 MiuiX 样式。
> 全部改动遵循约束：**复用原有架构、不重构整体代码、优先原生 View、新增代码风格与仓库一致、改动最小化**。

---

## 一、问题 1：主页 MiuiX 风格（含暗色自适应）

### 根因
原 `Ui.kt` 维护一套**硬编码浅色配色**（iOS 风卡片圆角、橙色品牌色 `ACCENT = 0xFF0A84FF`、固定文本层级），
既不跟随系统日/夜主题，也与 MiuiX 的 `lightColorScheme/darkColorScheme` 观感不一致。

### 方案（按你的确认：移除 `Ui.kt`，直接引用 MiuiX 配色）
1. **删除 `Ui.kt`**，新建 **`MiuiX.kt`**：颜色全部取自 MiuiX 主题，按日/夜动态解析：
   - 配色函数：`bg / card / onSurface / onSurfaceVariant / outline / outlineVariant / primary / error / onError`
   - 语义色：`STATE_ACTIVE`、`STATE_ACTIVE_SOFT`、`neutralSoft()`、`primarySoft()`、`SWITCH_TRACK_OFF`、`CHECK_OFF`
   - 框架无关常量保留：`HOME_TITLE / PAGE_TITLE / SECTION / ROW_TITLE / ROW_SUMMARY / CAPTION / MICRO`、间距与尺寸、`REPO_URL`
   - 辅助：`isNight()`、`Int.withAlpha()`、`Context.dp/dpf`、`Drawable.tinted(on,off)`（原 `Ui` 的扩展迁移至此，避免删除 `Ui.kt` 后勾选框着色编译失败）
2. `MainActivity / AboutActivity / SubSettingsActivity / SettingsBaseActivity` 中 `Ui.X` 全部改为 `MiuiX.X`，
   颜色按 `MiuiX.onSurface(isNight())` 等**运行时按系统主题解析**（原 `Ui.TEXT_PRIMARY`→`onSurface`、`TEXT_SECONDARY`→`onSurfaceVariant`、`TEXT_TERTIARY`→`outline`、`DIVIDER`→`outlineVariant`、`BG`→`bg`、`CARD`→`card`、`ACCENT`→`primary`）。
3. **暗色自适应**：`AboutActivity` 背景改用 `MiuiX.bg(isNight())` 并补上状态栏图标反色
   （`WindowCompat...isAppearanceLightStatusBars = !isNight()`），与 `SettingsBaseActivity` 行为对齐。
4. **品牌橙弃用**：强调色统一改为 MiuiX 的 `primary`（蓝/紫），不再用 `0xFF0A84FF` 硬编码。

### 关键片段（MiuiX.kt 取色）
```kotlin
fun onSurface(isNight: Boolean): Int =
    (if (isNight) darkColorScheme() else lightColorScheme()).onSurface.value.toInt()
fun primary(isNight: Boolean): Int =
    (if (isNight) darkColorScheme() else lightColorScheme()).primary.value.toInt()
```
### 关键片段（Activity 取色，随暗色切换）
```kotlin
// 旧
setBackgroundColor(Ui.BG); setTextColor(Ui.TEXT_PRIMARY)
// 新
setBackgroundColor(MiuiX.bg(isNight())); setTextColor(MiuiX.onSurface(isNight()))
```

---

## 二、问题 2：打包体积过大

### 根因（核心）
此前 `proguard-rules.pro` 对 Compose / Miuix / 模块自身做了**全量保留**：
```
-keep class androidx.compose.** { *; }
-keep class top.yukonga.miuix.kmp.** { *; }
-keep class com.mars.mimarketpurify.** { *; }
```
R8 因此**完全无法 tree-shake**，再叠加 `-dontobfuscate` / 巨量 keep 规则，才是体积膨胀的真正原因
（而非 Compose/Miuix 本身——参考项目 HyperModifier 同样用 Compose 也能压住体积）。

### 改动
1. **`app/proguard-rules.pro`**：改为**极简 keep**（与 HyperModifier 一致）——只保留 Xposed 入口子类、
   libxposed service 公共 API、manifest 声明的 4 个 Activity + App + EntryGuardReceiver；
   删除全部 `keep class X.** { *; }`。新增 Kotlin/JDK/Log 死代码消除（`assumenosideeffects`）。
2. **`gradle.properties`**：开启 **R8 full mode**（`android.enableR8.fullMode=true`）——比默认模式更激进地裁剪未引用类/成员。
3. **`app/build.gradle.kts`**：
   - **移除 `material3`**（全工程仅悬浮底栏用过一处 `Text`），改依赖 `androidx.compose.foundation` 用 `BasicText` 替代，**省下约 1MB+**；
   - 新增 `resourceConfigurations += listOf("zh-rCN", "en")`，丢弃 Compose/Miuix 等库自带的其余 locale。
4. **`gradle/libs.versions.toml`**：删除 `material3` 条目，新增 `androidx-compose-foundation`。

### 关键片段
```properties
# gradle.properties
android.enableR8.fullMode=true
```
```kotlin
// app/build.gradle.kts
resourceConfigurations += listOf("zh-rCN", "en")
// 依赖：移除 material3，保留
implementation(libs.androidx.compose.foundation)
```

---

## 三、问题 3：悬浮底栏 MiuiX 样式

### 结论（同步确认）
悬浮底栏**本身已在用 MiuiX 样式**，且**已暗色自适应**，并非"没用 miuix 样式"：
- `ComposeFloatingBarHost.kt` 用 `MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme())`
  包裹内容，`dark` 由 `LocalConfiguration.uiMode` 读取系统日/夜；
- `FloatingTabBar.kt` 取色全部走 `MiuixTheme.colorScheme.primary / onSurface / onSurfaceVariant`，
  毛玻璃优先用 `LayerBackdrop` 快照、否则回退半透明色块。

### 打磨（轻量、一致性增强）
- 未选中项颜色由 `onSurface`（满对比度）改为 **`onSurfaceVariant`**（MiuiX 的静默灰），更贴近 MiuiX 标签栏观感；
- 角标硬编码 iOS 红 `0xFFFF3B30` 改为 **`theme.error`**（MiuiX 角色色）。

```kotlin
// ui/floatingbar/FloatingTabBar.kt
val contentColor = if (selected) theme.primary else theme.onSurfaceVariant
// ...
.background(theme.error)   // 原 Color(0xFFFF3B30)
```

---

## 四、风险点
1. **MiuiX 字段名假设**：`MiuiX.kt` 按 Material3 标准字段（`background/surface/onSurface/onSurfaceVariant/outline/outlineVariant/primary/error/onError`）
   映射 miuix `Colors`。若 0.9.4-rc01 个别字段命名有出入（如 `background`），需按实际字段微调（编译期即可暴露）。
2. **毛玻璃降级**：若宿主 `LayerBackdrop` 采样失败，`FloatingTabBar` 会回退半透明色块（行为不变，仅无真实模糊）。
3. **R8 full mode**：更激进裁剪，建议首次打包后**真机验证**所有开关/页面/悬浮底栏功能正常（full mode 下偶有反射类被砍的风险，已用显式 `-keep` 兜底 Xposed 入口与 manifest 组件）。

## 五、测试注意事项
- [ ] 本地执行 `gradlew assembleRelease`，对比前后 APK 体积（重点验证问题 2 压缩生效）；
- [ ] 浅色 / 暗色系统主题下分别打开主页、关于页、各子设置页，确认配色、状态栏图标反色正确（问题 1）；
- [ ] 开启悬浮底栏，验证选中/未选中态取色、角标颜色、毛玻璃效果（问题 3）；
- [ ] LSPosed 作用域勾选"应用商店"，确认模块入口、远程偏好读写、各 Hook 开关不受影响（R8 full mode 回归）。

> ⚠️ 沙箱内**无 Android SDK**（仅 `gradle`/`java`，缺 `sdkmanager`/`ANDROID_HOME`），无法在此真正打包验证；
> 上述编译级核查（符号引用、import、ProGuard 规则、依赖目录）均已通过，请在本机完成最终 `assembleRelease` 构建与真机回归。
