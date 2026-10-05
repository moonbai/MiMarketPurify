package com.mars.mimarketpurify

import android.os.Build
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

        setContent {
            ModuleTheme {
                PrivacyPolicyScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
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
                text = "隐私政策",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }

        // WebView 内容（强制跟随系统深色模式）
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.defaultTextEncodingName = "UTF-8"

                    // ===== 深色模式适配 =====
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // API 33+：使用算法暗化，由系统控制
                        settings.isAlgorithmicDarkeningAllowed = true
                    } else {
                        // API 29-32：强制暗化
                        @Suppress("DEPRECATION")
                        settings.forceDark = WebSettings.FORCE_DARK_ON
                    }

                    loadUrl("file:///android_asset/privacy_policy.html")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
    }
}
