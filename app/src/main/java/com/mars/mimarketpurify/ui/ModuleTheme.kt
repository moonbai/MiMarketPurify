package com.mars.mimarketpurify.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
 */
@Composable
fun ModuleTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = ctx.useDarkTheme()
    val scale = ctx.uiScaleValue()
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale, base.fontScale * scale),
    ) {
        MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
            // 统一安装 Snackbar 宿主：模块内任意页面经 LocalSnackbarHost 即可弹提示，
            // 无需每页手动声明 SnackbarHost（见 LocalSnackbarHost.kt）。
            ProvideSnackbarHost(content)
        }
    }
}
