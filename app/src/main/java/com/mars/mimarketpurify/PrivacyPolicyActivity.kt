package com.mars.mimarketpurify

import android.os.Build
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.ModuleTheme
import com.mars.mimarketpurify.useDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.res.stringResource
import com.mars.mimarketpurify.R

/**
 * 隐私政策页：通过 WebView 加载本地 HTML 文件。
 * Compose 壳 + AndroidView(WebView) 实现，与模块整体 UI 风格一致。
 */
class PrivacyPolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = useDarkTheme()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(MiuiX.bg(dark)))
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !dark
        setupPredictiveBack { finish() }

        // 跟随系统语言加载对应语种的隐私政策正文
        val isEnglish = resources.configuration.locales[0].language == "en"

        setContent {
            ModuleTheme {
                PrivacyPolicyScreen(dark = dark, isEnglish = isEnglish, onBack = { finish() })
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(dark: Boolean, isEnglish: Boolean, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                fontSize = 28.sp,
                color = colors.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.privacy_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        // WebView 内容（跟随模块主题模式：深色 / 浅色）
        // 关键点：WebView 的 prefers-color-scheme 取决于其 Context 的 uiMode，
        // 而本模块各 Activity 的 AppTheme 为浅色（与 useDarkTheme() 解耦），
        // 因此这里显式把 WebView 的 Context 切成对应 uiMode，使 privacy_policy.html
        // 内 @media (prefers-color-scheme: dark) 的样式在「模块强制深色 / 系统浅色」
        // 场景下也能正确生效。
        AndroidView(
            factory = { context ->
                val webContext = if (dark) {
                    val cfg = Configuration(context.resources.configuration)
                    cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
                        .or(Configuration.UI_MODE_NIGHT_YES)
                    context.createConfigurationContext(cfg)
                } else context
                WebView(webContext).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.defaultTextEncodingName = "UTF-8"

                    // ===== 深色模式适配 =====
                    // isAlgorithmicDarkeningAllowed / forceDark 必须按 dark 显式设置，
                    // 否则默认值可能与期望不一致（尤其在模块强制深色但系统浅色时）。
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settings.isAlgorithmicDarkeningAllowed = dark
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = if (dark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                    }

                    val asset = if (isEnglish) "privacy_policy_en.html" else "privacy_policy.html"
                    loadUrl("file:///android_asset/$asset")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
    }
}
