package com.mars.mimarketpurify

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.MainScreen
import com.mars.mimarketpurify.ui.components.AboutContent
import com.mars.mimarketpurify.util.FloatingTabBar
import com.mars.mimarketpurify.util.FloatingTabItem
import com.mars.mimarketpurify.util.FloatingTabLayout
import com.mars.mimarketpurify.util.NavIcons
import com.mars.mimarketpurify.util.UpdateCheckResult
import com.mars.mimarketpurify.util.UpdateChecker
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import android.view.Choreographer
import android.view.View
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import com.mars.mimarketpurify.util.ViewBackdropBounds
import com.mars.mimarketpurify.util.ViewBackdropLayer
import com.mars.mimarketpurify.util.ViewBackdropSampler
import com.mars.mimarketpurify.util.ViewBackdropSnapshot
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

class MainActivity : SettingsBaseActivity() {

    private val KEY_FIRST_LAUNCH_CHECKED = "first_launch_update_checked"

    internal val adKeys = listOf(
        Settings.KEY_SPLASH, Settings.KEY_MAIN_TAB, Settings.KEY_HOME_FEED,
        Settings.KEY_SEARCH, Settings.KEY_UPDATE_DL, Settings.KEY_DETAIL, Settings.KEY_RANK,
        Settings.KEY_FRUIT, Settings.KEY_ENTRANCE, Settings.KEY_DETAIL_EXTRAS,
        Settings.KEY_FLOATING_AD, Settings.KEY_AD_BACK_FLOAT, Settings.KEY_HOME_PAGE_DIALOG,
        Settings.KEY_INSTALL_RECOMMEND
    )
    internal val mineKeys = listOf(
        Settings.KEY_MINE_RECOMMEND, Settings.KEY_MINE_OFFICIAL_TAB, Settings.KEY_MINE_CLEANUP,
        Settings.KEY_MINE_SUMMARY, Settings.KEY_MINE_SECURITY, Settings.KEY_ORCHARD_SKIN,
        Settings.KEY_TAB_BADGE, Settings.KEY_CARD_EXPAND
    )
    internal val miscKeys = listOf(
        Settings.KEY_DETAIL_FEATURED, Settings.KEY_UPDATE_HISTORY, Settings.KEY_SEARCH_ALSO_VIEW,
        Settings.KEY_SUB_TAB_FILTER, Settings.KEY_HIDE_UPDATE_ALL, Settings.KEY_HIDE_AUTO_UPDATE_SWITCH,
        Settings.KEY_PUSH_FLOAT, Settings.KEY_UPDATE_FLOAT_CARD, Settings.KEY_BLOCK_BG_DOWNLOAD,
        Settings.KEY_LONG_PRESS_JUMP
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EntryGuardReceiver.ensureEntryEnabled(this)
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                var tab by remember { mutableStateOf(0) }
                Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    when (tab) {
                        0 -> MainScreen(activity = this@MainActivity)
                        else -> AboutContent(
                            activity = this@MainActivity,
                            onBack = { tab = 0 },
                            floatingBarInset = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp,
                        )
                    }
                    BottomNavBar(selected = tab, modifier = Modifier.align(Alignment.BottomCenter)) { tab = it }
                }
            }
        }
        maybeCheckUpdateOnFirstLaunch()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun maybeCheckUpdateOnFirstLaunch() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)) return
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
        Thread {
            val result = UpdateChecker.check()
            runOnUiThread {
                if (result is UpdateCheckResult.Available) {
                    Toast.makeText(this@MainActivity, "发现新版本 v${result.versionName}，可前往「检查更新」查看", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    internal fun countText(keys: List<String>): String = "已启用 ${keys.count { readLocal(it, true) }}/${keys.size}"
    internal fun tabsText(): String {
        val parts = mutableListOf<String>()
        if (!readLocal(Settings.KEY_TAB_FILTER, true)) parts += "筛选已关闭"
        else {
            val kept = readLocalTabs().size
            val extra = if (readLocal(Settings.KEY_UPDATE_TAB, true)) 1 else 0
            parts += if (kept + extra <= 0) "未开启" else "已开启 ${kept + extra} 个"
        }
        return parts.joinToString(" · ")
    }
    internal fun tabbarText(): String = if (readLocal(Settings.KEY_FLOATING_BAR, false)) "悬浮已开启" else "原版底栏"
    internal fun openPage(page: String) { startActivity(SubSettingsActivity.intent(this, page)) }
}

@Composable
private fun BottomNavBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        FloatingTabItem(key = "home", label = "主页", icon = NavIcons.Home),
        FloatingTabItem(key = "about", label = "关于", icon = NavIcons.Person),
    )
    val showLabel = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)
    val radius = Settings.floatingBarRadiusDp().dp
    val bottomMargin = Settings.floatingBarBottomMarginDp()
    val barColor = run { val v = Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1); if (v == -1) null else Color(v) }
    val indicatorColor = run { val v = Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1); if (v == -1) null else Color(v) }
    val textSelected = run { val v = Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1); if (v == -1) null else Color(v) }
    val textNormal = run { val v = Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1); if (v == -1) null else Color(v) }
    val activity = LocalContext.current as? Activity
    val backdrop = rememberLayerBackdrop()
    var snapshot by remember { mutableStateOf<ViewBackdropSnapshot?>(null) }
    var sampler by remember { mutableStateOf<ViewBackdropSampler?>(null) }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose {}
        val source = act.findViewById<View>(android.R.id.content) ?: return@DisposableEffect onDispose {}
        val s = runCatching {
            ViewBackdropSampler(
                source = source,
                excludedView = null,
                pixelCopyWindow = act.window,
                usePixelCopySampling = { true },
                onSnapshotChanged = { snapshot = it },
            )
        }.getOrNull()
            ?: return@DisposableEffect onDispose {}
        sampler = s
        val choreographer = Choreographer.getInstance()
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 同 AboutGlassCard：不再每帧强制 requestCaptureBurst(300)，避免整窗 PixelCopy 常驻；
                // 初始布局由 setNavigationBounds 触发突发采样，滚动由 OnScrollChangedListener 驱动。
                runCatching { s.onFrame() }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(cb)
        onDispose { choreographer.removeFrameCallback(cb); runCatching { s.dispose() }; sampler = null }
    }

    Box(modifier = modifier.fillMaxWidth().navigationBarsPadding()
        .padding(start = 12.dp, end = 12.dp, bottom = bottomMargin.dp)
        .height(FloatingTabBarDefaults.Height), contentAlignment = Alignment.Center) {
        ViewBackdropLayer(snapshot, backdrop)
        FloatingTabBar(
            items = items, selectedIndex = selected, onSelect = onSelect,
            layout = FloatingTabLayout.Stacked, showLabel = showLabel, radius = radius,
            barColor = barColor, indicatorColor = indicatorColor,
            contentSelectedColor = textSelected, contentNormalColor = textNormal,
            backdrop = if (snapshot != null) backdrop else null, expandWidth = false,
            modifier = Modifier.onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                sampler?.setNavigationBounds(ViewBackdropBounds(pos.x.roundToInt(), pos.y.roundToInt(), coords.size.width, coords.size.height))
            },
        )
    }
}
