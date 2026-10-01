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

/**
 * 程序主页：**整页 Compose**，并采用底部标签栏（主页 / 关于）两种形态。
 *
 * 主页只保留**高频开关**：总开关、下载超级岛 / 细节修正 / 升级提醒三个高级功能；
 * 其余按「同一个页面」或「带子选项」为维度收进 [SubSettingsActivity]。
 * 「关于」作为底栏第二个标签页，与独立 [AboutActivity] 共用 [AboutContent]。
 *
 * 所有远程偏好读写、入口自愈、隐藏桌面图标等逻辑仍复用 [SettingsBaseActivity]。
 */
class MainActivity : SettingsBaseActivity() {

    private val KEY_FIRST_LAUNCH_CHECKED = "first_launch_update_checked"

    /** 二级页「广告移除」里的开关，用于在主页入口行显示启用数量 */
    internal val adKeys = listOf(
        Settings.KEY_SPLASH,
        Settings.KEY_MAIN_TAB,
        Settings.KEY_HOME_FEED,
        Settings.KEY_SEARCH,
        Settings.KEY_UPDATE_DL,
        Settings.KEY_DETAIL,
        Settings.KEY_RANK,
        Settings.KEY_FRUIT,
        Settings.KEY_ENTRANCE,
        Settings.KEY_DETAIL_EXTRAS,
        Settings.KEY_FLOATING_AD
    )

    /** 二级页「「我的」页」里的开关 */
    internal val mineKeys = listOf(
        Settings.KEY_MINE_RECOMMEND,
        Settings.KEY_MINE_OFFICIAL_TAB,
        Settings.KEY_MINE_CLEANUP,
        Settings.KEY_MINE_SUMMARY,
        Settings.KEY_MINE_SECURITY,
        Settings.KEY_ORCHARD_SKIN,
        Settings.KEY_TAB_BADGE,
        Settings.KEY_CARD_EXPAND
    )

    /** 二级页「其他界面净化」里的开关 */
    internal val miscKeys = listOf(
        Settings.KEY_DETAIL_FEATURED,
        Settings.KEY_UPDATE_HISTORY,
        Settings.KEY_SEARCH_ALSO_VIEW,
        Settings.KEY_SUB_TAB_FILTER,
        Settings.KEY_HIDE_UPDATE_ALL,
        Settings.KEY_HIDE_AUTO_UPDATE_SWITCH
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 入口自愈：曾被旧版本锁出的设备，覆盖安装后自动恢复
        EntryGuardReceiver.ensureEntryEnabled(this)

        // 暗色模式：状态栏图标随背景反色
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                // 观察 service 连接状态：连接后刷新底栏配置（悬浮底栏配色/标签开关等实时生效）
                @Suppress("UNUSED_VARIABLE")
                val refresh = refreshSignal.value
                var tab by remember { mutableStateOf(0) } // 0 = 主页，1 = 关于
                Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    // 内容区：底部预留悬浮底栏高度，避免内容被遮挡；底栏以浮层形式叠加在底部中央，
                    // 内容可滚动到其下方，呈现「悬浮 + 通透」的效果（与商店一致）。
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = FloatingTabBarDefaults.Height + 12.dp)) {
                        when (tab) {
                            0 -> MainScreen(activity = this@MainActivity)
                            else -> AboutContent(activity = this@MainActivity, onBack = { tab = 0 })
                        }
                    }
                    BottomNavBar(selected = tab, modifier = Modifier.align(Alignment.BottomCenter)) { tab = it }
                }
            }
        }

        // 首次启动静默检查更新（不弹窗，仅在发现新版本时轻提示）
        maybeCheckUpdateOnFirstLaunch()
    }

    /** 仅首次启动静默检查一次；在本地 SharedPreferences 记录标记，不依赖远程偏好（service 可能未连）。 */
    private fun maybeCheckUpdateOnFirstLaunch() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)) return
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
        Thread {
            val result = UpdateChecker.check()
            runOnUiThread {
                if (result is UpdateCheckResult.Available) {
                    Toast.makeText(
                        this@MainActivity,
                        "发现新版本 v${result.versionName}，可前往「检查更新」查看",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }

    // ==================== 入口行摘要（供 MainScreen 复用） ====================

    /** 「已启用 2/3」：一眼看出二级页里开了几项 */
    internal fun countText(keys: List<String>): String =
        "已启用 ${keys.count { readLocal(it, true) }}/${keys.size}"

    internal fun tabsText(): String {
        val parts = mutableListOf<String>()
        if (!readLocal(Settings.KEY_TAB_FILTER, true)) {
            parts += "筛选已关闭"
        } else {
            val kept = readLocalTabs().size
            parts += if (kept <= 0) "未开启" else "已开启 $kept 个"
        }
        return parts.joinToString(" · ")
    }

    internal fun tabbarText(): String {
        return if (readLocal(Settings.KEY_FLOATING_BAR, true)) {
            "悬浮已开启"
        } else {
            "原版底栏"
        }
    }

    internal fun openPage(page: String) {
        startActivity(SubSettingsActivity.intent(this, page))
    }
}

// ==================== 底部标签栏（MiuiX 悬浮底栏 FloatingTabBar）====================
// 采用本仓库 util.FloatingTabBar：胶囊容器 + 滑动胶囊指示器 + 图标文字竖排（Stacked），
// 形态与 AritxOnly/HyperModifier 的 MiuixFloatingTabBar 一致；容器胶囊形状引用 HyperModifier
// 的实际依赖 io.github.kyant0:shapes 的 Capsule。选中态以中性半透明胶囊（暗色白 / 亮色黑）表达，
// 图标与文字随暗色背景自动取白 / 黑，不随主色变化。内容色、指示器、背景色均可在
// 「悬浮底栏配置」页覆盖（主页此处沿用主题默认视觉）。

@Composable
private fun BottomNavBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val items = listOf(
        FloatingTabItem(key = "home", label = "主页", icon = NavIcons.Home),
        FloatingTabItem(key = "about", label = "关于", icon = NavIcons.Person),
    )
    // 与商店悬浮底栏共享同一套配置（颜色 / 圆角 / 标签 / 距底高度），确保视觉一致、外观统一。
    val showLabel = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)
    val radius = Settings.floatingBarRadiusDp().dp
    val bottomMargin = Settings.floatingBarBottomMarginDp()
    val barColor = run { val v = Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1); if (v == -1) null else Color(v) }
    val indicatorColor = run { val v = Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1); if (v == -1) null else Color(v) }
    val textSelected = run { val v = Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1); if (v == -1) null else Color(v) }
    val textNormal = run { val v = Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1); if (v == -1) null else Color(v) }

    // 与商店一致：实时毛玻璃。采样失败时（如本机不支持）自动退化成半透明纯色，底栏始终可用。
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
        }.getOrNull() ?: return@DisposableEffect onDispose {}
        sampler = s
        val choreographer = Choreographer.getInstance()
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                runCatching {
                    s.requestCaptureBurst(300)
                    s.onFrame()
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(cb)
        onDispose {
            choreographer.removeFrameCallback(cb)
            runCatching { s.dispose() }
            sampler = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomMargin.dp)
            // 关键：容器显式固定为底栏高度。ViewBackdropLayer 会按采样区域 requiredSize，
            // 若父容器高度由内容决定，就会被它撑高并随采样范围不断放大（正反馈），最终把底栏
            // 上移并用浮层色块盖住底部内容。与商店侧 ComposeFloatingBarHost 保持同一处理。
            .height(FloatingTabBarDefaults.Height),
        contentAlignment = Alignment.Center,
    ) {
        ViewBackdropLayer(snapshot, backdrop)
        FloatingTabBar(
            items = items,
            selectedIndex = selected,
            onSelect = onSelect,
            layout = FloatingTabLayout.Stacked,
            showLabel = showLabel,
            radius = radius,
            barColor = barColor,
            indicatorColor = indicatorColor,
            contentSelectedColor = textSelected,
            contentNormalColor = textNormal,
            // 仅在采样到位时启用毛玻璃；否则退化成半透明纯色，避免底栏「消失」。
            backdrop = if (snapshot != null) backdrop else null,
            expandWidth = false,
            modifier = Modifier.onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                sampler?.setNavigationBounds(
                    ViewBackdropBounds(
                        left = pos.x.roundToInt(),
                        top = pos.y.roundToInt(),
                        width = coords.size.width,
                        height = coords.size.height,
                    ),
                )
            },
        )
    }
}
