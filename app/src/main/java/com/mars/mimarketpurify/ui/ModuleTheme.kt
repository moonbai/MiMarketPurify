package com.mars.mimarketpurify.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import com.mars.mimarketpurify.uiScaleValue
import com.mars.mimarketpurify.useDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 模块统一主题壳：在 [MiuixTheme] 之外，按 [uiScaleValue] 用 [LocalDensity] 缩放整页 Compose UI。
 *
 * - 明暗由 [useDarkTheme]（主题模式 + 系统）决定；
 * - 缩放只作用于本插件 Compose 页面（不触及目标 App），改动后 Activity 重建即生效。
 *
 * 各设置页用 [ModuleTheme] 取代原 `MiuixTheme(colors = if (dark) ...)`，
 * 以集中管理「主题 + 缩放」，避免散落重复。
 *
 * 同时在此统一安装两个宿主：
 *  - [ProvideSnackbarHost]：任意位置经 [LocalSnackbarHost] 弹提示，无需每页声明 [androidx.compose.material3.SnackbarHost]；
 *  - [LocalNavigationEventDispatcherOwner]：MiuiX 的 Window* 组件（如下拉单选 / 多选的
 *    WindowSpinnerPreference、各类 WindowDialog）在展开独立下拉窗时会读取该 CompositionLocal 并处理
 *    返回手势。本模块未使用 MiuiX 的 NavDisplay 导航容器，若不在此提供，点击展开即抛
 *    `IllegalStateException: No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner`。
 *    这里优先链接到 ComponentActivity 自带的 dispatcher（若可用）以保留系统返回手势，否则创建独立根 dispatcher。
 */
@Composable
fun ModuleTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = ctx.useDarkTheme()
    val scale = ctx.uiScaleValue()
    val base = LocalDensity.current
    // 解析宿主 Activity 自带的 NavigationEventDispatcherOwner（仅当视图已挂入窗口且 Activity 已提供时非空）。
    // 链接到它即可让展开的下拉窗继承系统返回手势；为空则退化为独立根 dispatcher（仍可消除崩溃）。
    val view = LocalView.current
    val activityOwner = remember(view) { view.findViewTreeNavigationEventDispatcherOwner() }
    val navOwner = rememberNavigationEventDispatcherOwner(parent = activityOwner)
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale * scale),
        LocalNavigationEventDispatcherOwner provides navOwner,
    ) {
        MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
            // 统一安装 Snackbar 宿主：模块内任意页面经 LocalSnackbarHost 即可弹提示，
            // 无需每页手动声明 SnackbarHost（见 LocalSnackbarHost.kt）。
            ProvideSnackbarHost(content)
        }
    }
}
