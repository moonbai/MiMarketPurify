package com.mars.mimarketpurify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mars.mimarketpurify.useDarkTheme

/**
 * 跨组件触发 Snackbar 的 [androidx.compose.runtime.CompositionLocal]。
 *
 * 模块内任意 Composable（如 [com.mars.mimarketpurify.ui.components.SwitchRow]）通过
 * [LocalSnackbarHost] 取用宿主 [SnackbarHostState]，即可在不层层传参的前提下弹出提示
 * （如功能开关切换后的 Snackbar 提示）。
 *
 * 宿主本身由 [ProvideSnackbarHost] 在 [ModuleTheme] 内统一安装，无需每个页面手动声明
 * [SnackbarHost]；在 [ModuleTheme] 之外使用时取到的为 `null`（调用方应做空安全处理）。
 */
val LocalSnackbarHost = compositionLocalOf<SnackbarHostState?> { null }

/**
 * 提供一个 [SnackbarHostState] 并在内容底部安装 [SnackbarHost] 容器。
 *
 * 模块内所有 Compose 页面经 [ModuleTheme] 间接调用本函数，因此任意位置的开关 / 按钮都能
 * 通过 [LocalSnackbarHost] 弹出 Snackbar 提示。内容根布局会被包进一层 [Box]（撑满全屏），
 * 以便 Snackbar 悬浮于底部中央、绘制在所有页面内容之上。
 *
 * Snackbar 自身用一套与主题明暗同步的 [MaterialTheme] 着色，避免在深色模式下出现
 * 浅色-on-浅色的不可读问题。
 *
 * @param content 页面内容。
 */
@Composable
fun ProvideSnackbarHost(content: @Composable () -> Unit) {
    val hostState = remember { SnackbarHostState() }
    val dark = LocalContext.current.useDarkTheme()
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSnackbarHost provides hostState) {
            content()
        }
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            SnackbarHost(
                hostState = hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
