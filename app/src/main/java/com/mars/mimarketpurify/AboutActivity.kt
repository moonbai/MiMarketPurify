package com.mars.mimarketpurify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.components.AboutContent
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 关于页（整页 Compose）。内容复用 [com.mars.mimarketpurify.ui.components.AboutContent]，
 * 与主页「关于」标签页完全一致；保留独立 Activity 以兼容外部入口（如框架模块列表）。
 */
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dark = useDarkTheme()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(MiuiX.bg(dark)))
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !dark

        setContent {
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                AboutContent(activity = this@AboutActivity, onBack = { finish() })
            }
        }
    }
}
