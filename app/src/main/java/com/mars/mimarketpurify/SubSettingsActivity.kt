package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.ui.ModuleTheme
import com.mars.mimarketpurify.ui.components.*
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SubSettingsActivity : SettingsBaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val EXTRA_HIGHLIGHT = "highlight"
        const val PAGE_ADS = "ads"
        const val PAGE_MINE = "mine"
        const val PAGE_TABS = "tabs"
        const val PAGE_MISC = "misc"
        const val PAGE_THEME = "theme"

        fun intent(ctx: Context, page: String, highlight: String? = null): Intent =
            Intent(ctx, SubSettingsActivity::class.java)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_HIGHLIGHT, highlight)
    }

    private var page: String = PAGE_MINE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent?.getStringExtra(EXTRA_PAGE) ?: PAGE_MINE
        val title = FeatureRegistry.pageTitles[page] ?: page
        applyWindowTheme()
        setupPredictiveBack { finish() }
        setContent {
            ModuleTheme {
                val tick by refreshSignal
                val masterOn = remember(tick) { mutableStateOf(readLocal(Settings.KEY_MASTER, true)) }
                val hl = intent?.getStringExtra(EXTRA_HIGHLIGHT).orEmpty()
                Column(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                    SubTopBar(title = title, onBack = { finish() })
                    when (page) {
                        PAGE_ADS -> AdsScreen(activity = this@SubSettingsActivity, masterOn = masterOn.value, hl)
                        PAGE_MINE -> MineScreen(activity = this@SubSettingsActivity, masterOn = masterOn.value, hl)
                        PAGE_TABS -> TabsScreen(activity = this@SubSettingsActivity, masterOn = masterOn.value)
                        PAGE_MISC -> MiscScreen(activity = this@SubSettingsActivity, masterOn = masterOn.value, hl)
                        PAGE_THEME -> ThemeScreen(activity = this@SubSettingsActivity)
                    }
                }
            }
        }
    }
}

// ═══════════════ 带闪烁动画的开关行 ═══════════════

@Composable
private fun HighlightSwitch(
    activity: SubSettingsActivity,
    key: String,
    enabled: Boolean,
    hl: String,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val shouldHighlight = hl == key
    val alpha = remember { Animatable(if (shouldHighlight) 0.6f else 0f) }
    LaunchedEffect(shouldHighlight) {
        if (shouldHighlight) {
            alpha.animateTo(0f, animationSpec = tween(durationMillis = 1200))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1976D2).copy(alpha = alpha.value))
    ) {
        PrefSwitch(
            activity = activity,
            key = key,
            title = FeatureRegistry.titleOf(key),
            summary = FeatureRegistry.summaryOf(key),
            default = true,
            enabled = enabled,
            onChanged = onChanged,
        )
    }
}

// ═══════════════ 广告净化 ═══════════════

@Composable
private fun AdsScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
            .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
    ) {
        SettingsSection {
                        HighlightSwitch(activity, Settings.KEY_SPLASH, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_MAIN_TAB, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_HOME_FEED, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_SEARCH, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_UPDATE_DL, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_DETAIL, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_DETAIL_EXTRAS, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_RANK, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_FLOATING_AD, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_AD_BACK_FLOAT, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_HOME_PAGE_DIALOG, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_ENTRANCE, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_FRUIT, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_INSTALL_RECOMMEND, masterOn, hl)
        }
        Footer("广告净化模块负责开屏、首页信息流、搜索、升级/下载页、详情页、榜单广告、领水果入口、活动入口")
    }
}

// ═══════════════ 我的页精简 ═══════════════

@Composable
private fun MineScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    val cleanupOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MINE_CLEANUP, true)) }
    val orchardOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_ORCHARD_SKIN, true)) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
            .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
    ) {
        SettingsSection {
                        HighlightSwitch(activity, Settings.KEY_MINE_RECOMMEND, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_MINE_OFFICIAL_TAB, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_MINE_CLEANUP, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_MINE_SUMMARY, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_MINE_SECURITY, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_ORCHARD_SKIN, masterOn, hl)
        }
        if (cleanupOn && orchardOn) {
            SettingsSection(topLabel = "升级卡片") {
                PrefSwitch(
                    activity = activity,
                    key = Settings.KEY_CARD_EXPAND, title = FeatureRegistry.titleOf(Settings.KEY_CARD_EXPAND), summary = FeatureRegistry.summaryOf(Settings.KEY_CARD_EXPAND),
                    default = false,
                    enabled = masterOn,
                )
            }
        }
        SettingsSection {
                        HighlightSwitch(activity, Settings.KEY_TAB_BADGE, masterOn, hl)
        }
        Footer("「我的」页精简负责隐藏推荐、清理、安全检测、个人信息等内容")
    }
}

// ═══════════════ 底栏自定义（含悬浮底栏配置） ═══════════════

@Composable
private fun TabsScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    val tick by activity.refreshSignal
    val filterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_TAB_FILTER, true)) }
    val floatingOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_FLOATING_BAR, false)) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
            .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
    ) {
        SettingsSection(topLabel = "底栏标签") {
            PrefSwitch(
                activity = activity,
                key = Settings.KEY_TAB_FILTER, title = FeatureRegistry.titleOf(Settings.KEY_TAB_FILTER), summary = FeatureRegistry.summaryOf(Settings.KEY_TAB_FILTER),
                default = true,
                enabled = masterOn,
                onChanged = { activity.refreshSignal.value++ },
            )
            if (filterOn) {
                PrefTabFilterSpinner(activity = activity, enabled = masterOn)
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsSection(topLabel = "增强") {
            PrefSwitch(activity, Settings.KEY_UPDATE_TAB, FeatureRegistry.titleOf(Settings.KEY_UPDATE_TAB), FeatureRegistry.summaryOf(Settings.KEY_UPDATE_TAB), default = false, enabled = masterOn)
            Spacer(Modifier.height(12.dp))
            PrefSwitch(activity, Settings.KEY_TAB_DEEP_CLEAN, FeatureRegistry.titleOf(Settings.KEY_TAB_DEEP_CLEAN), FeatureRegistry.summaryOf(Settings.KEY_TAB_DEEP_CLEAN), default = true, enabled = masterOn)
        }

        Spacer(Modifier.height(12.dp))
        // ═══ 悬浮底栏（商店）配置 ═══
        SettingsSection(topLabel = "悬浮底栏") {
            // Fix: onChanged 触发 refreshSignal，关闭后子项立即隐藏
            PrefSwitch(
                activity = activity,
                key = Settings.KEY_FLOATING_BAR, title = FeatureRegistry.titleOf(Settings.KEY_FLOATING_BAR), summary = FeatureRegistry.summaryOf(Settings.KEY_FLOATING_BAR),
                default = false,
                enabled = masterOn,
                onChanged = { activity.refreshSignal.value++ },
            )
            if (floatingOn) {
                PrefSlider(activity, Settings.KEY_FLOAT_CORNER_RADIUS, "圆角半径", "胶囊圆角半径（dp），0=直角",
                    Settings.FLOATING_RADIUS_MIN, Settings.FLOATING_RADIUS_MAX, Settings.FLOATING_RADIUS_DEFAULT, true, format = { "${it}dp" })
                PrefSlider(activity, Settings.KEY_FLOAT_BAR_ALPHA, "背景透明度", "底栏整体背景透明度（%）",
                    Settings.FLOATING_ALPHA_MIN, Settings.FLOATING_ALPHA_MAX, Settings.STORE_FLOAT_ALPHA_DEFAULT, true, format = { "${it}%" })
                PrefSlider(activity, Settings.KEY_STORE_FLOAT_BOTTOM_MARGIN, "距底栏距离", "悬浮底栏到屏幕底部的间距（dp）",
                    Settings.STORE_FLOAT_BOTTOM_MARGIN_MIN, Settings.STORE_FLOAT_BOTTOM_MARGIN_MAX, Settings.STORE_FLOAT_BOTTOM_MARGIN_DEFAULT, true, format = { "${it}dp" })
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID, FeatureRegistry.titleOf(Settings.KEY_FLOATING_BAR_LIQUID), FeatureRegistry.summaryOf(Settings.KEY_FLOATING_BAR_LIQUID), default = true, enabled = masterOn)
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID_3D, FeatureRegistry.titleOf(Settings.KEY_FLOATING_BAR_LIQUID_3D), FeatureRegistry.summaryOf(Settings.KEY_FLOATING_BAR_LIQUID_3D), default = false, enabled = masterOn)
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_MONOCHROME, FeatureRegistry.titleOf(Settings.KEY_FLOATING_BAR_MONOCHROME), FeatureRegistry.summaryOf(Settings.KEY_FLOATING_BAR_MONOCHROME), default = false, enabled = masterOn)
                Spacer(Modifier.height(12.dp))
                PrefColorRow(activity, "底栏背景色", Settings.KEY_STORE_FLOAT_BG_COLOR,
                    Settings.STORE_FLOAT_BG_COLOR_LIGHT, Settings.STORE_FLOAT_BG_COLOR_DARK)
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                PrefColorRow(activity, "选中项背景色", Settings.KEY_STORE_FLOAT_SELECT_BG_COLOR,
                    Settings.STORE_FLOAT_SELECT_BG_COLOR_LIGHT, Settings.STORE_FLOAT_SELECT_BG_COLOR_DARK)
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                PrefColorRow(activity, "普通文字颜色", Settings.KEY_STORE_FLOAT_TEXT_NORMAL_COLOR,
                    Settings.STORE_FLOAT_TEXT_NORMAL_COLOR_LIGHT, Settings.STORE_FLOAT_TEXT_NORMAL_COLOR_DARK)
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                PrefColorRow(activity, "选中文字颜色", Settings.KEY_STORE_FLOAT_TEXT_SELECT_COLOR,
                    Settings.STORE_FLOAT_TEXT_SELECT_COLOR_LIGHT, Settings.STORE_FLOAT_TEXT_SELECT_COLOR_DARK)
            }
        }

        Footer("选择需要展示的底栏标签，取消勾选后对应标签将被隐藏。")
    }
}

// ═══════════════ 其他界面精简 ═══════════════

@Composable
private fun MiscScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
            .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
    ) {
        SettingsSection(topLabel = "界面精简") {
                        HighlightSwitch(activity, Settings.KEY_DETAIL_FEATURED, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_UPDATE_HISTORY, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_SEARCH_ALSO_VIEW, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_SUB_TAB_FILTER, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_HIDE_UPDATE_ALL, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_PUSH_FLOAT, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_UPDATE_FLOAT_CARD, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_BLOCK_BG_DOWNLOAD, masterOn, hl)
                        HighlightSwitch(activity, Settings.KEY_LONG_PRESS_JUMP, masterOn, hl)
        }
        Footer("其他界面精简负责详情页精选、升级记录、搜索也在看、顶栏推广位等")
    }
}

// ═══════════════ 插件本体悬浮底栏（外观，置于「主题与外观」） ═══════════════

@Composable
private fun PluginFloatingBarSection(activity: SubSettingsActivity) {
    val tick by activity.refreshSignal
    val sliderMax = 29
    val pluginBarOn by remember(tick) {
        mutableStateOf(activity.readLocal(Settings.KEY_FLOAT_BAR_ENABLE, true))
    }
    SettingsSection(topLabel = "插件悬浮底栏") {
        PrefSwitch(
            activity = activity,
            key = Settings.KEY_FLOAT_BAR_ENABLE, title = FeatureRegistry.titleOf(Settings.KEY_FLOAT_BAR_ENABLE), summary = FeatureRegistry.summaryOf(Settings.KEY_FLOAT_BAR_ENABLE),
            default = true,
            enabled = true,
            affectsStore = false,
            onChanged = { activity.refreshSignal.value++ },
        )
        if (pluginBarOn) {
            Spacer(Modifier.height(12.dp))
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LABEL, FeatureRegistry.titleOf(Settings.KEY_FLOATING_BAR_LABEL), FeatureRegistry.summaryOf(Settings.KEY_FLOATING_BAR_LABEL), default = true, enabled = true)
            Spacer(Modifier.height(12.dp))
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_RADIUS, "圆角半径", "胶囊圆角半径（dp），0=直角", 0, sliderMax, Settings.FLOATING_RADIUS_DEFAULT, true, format = { "${it}dp" })
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_ALPHA, "背景透明度", "底栏整体背景透明度（%）", Settings.FLOATING_ALPHA_MIN, Settings.FLOATING_ALPHA_MAX, Settings.FLOATING_ALPHA_DEFAULT, true, format = { "${it}%" })
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_BOTTOM_MARGIN, "距底部外边距", "悬浮底栏到屏幕底部的间距（dp）", Settings.FLOATING_BOTTOM_MARGIN_MIN, Settings.FLOATING_BOTTOM_MARGIN_MAX, Settings.FLOATING_BOTTOM_MARGIN_DEFAULT, true, format = { "${it}dp" })
            Spacer(Modifier.height(12.dp))
            PrefColorRow(activity, "底栏背景色", Settings.KEY_FLOAT_BG_COLOR,
                Settings.FLOAT_BG_COLOR_LIGHT, Settings.FLOAT_BG_COLOR_DARK)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "选中项背景色", Settings.KEY_FLOAT_SELECT_BG_COLOR,
                Settings.FLOAT_SELECT_BG_COLOR_LIGHT, Settings.FLOAT_SELECT_BG_COLOR_DARK)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "文字默认色", Settings.KEY_FLOAT_TEXT_NORMAL_COLOR,
                Settings.FLOAT_TEXT_NORMAL_COLOR_LIGHT, Settings.FLOAT_TEXT_NORMAL_COLOR_DARK)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "文字选中色", Settings.KEY_FLOAT_TEXT_SELECT_COLOR,
                Settings.FLOAT_TEXT_SELECT_COLOR_LIGHT, Settings.FLOAT_TEXT_SELECT_COLOR_DARK)
        }
    }
}

// ═══════════════ 主题与外观（二级界面） ═══════════════

@Composable
private fun ThemeScreen(activity: SubSettingsActivity) {
    val tick by activity.refreshSignal
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
            .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
    ) {
        PrefThemeMode(activity = activity, onApplied = { activity.recreate() })

        Spacer(Modifier.height(12.dp))
        SettingsSection(topLabel = "手势与显示") {
            PrefSwitch(
                activity = activity,
                key = Settings.KEY_PREDICTIVE_BACK, title = FeatureRegistry.titleOf(Settings.KEY_PREDICTIVE_BACK), summary = FeatureRegistry.summaryOf(Settings.KEY_PREDICTIVE_BACK),
                default = true,
                enabled = true,
                onChanged = { on ->
                    activity.writeLocalBool(Settings.KEY_PREDICTIVE_BACK, on)
                    setPredictiveBackEnabled(activity, on)
                    activity.recreate()
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection(topLabel = "界面缩放") {
            PrefSlider(
                activity = activity,
                key = Settings.KEY_UI_SCALE,
                title = "界面缩放",
                summary = "整体放大 / 缩小本插件界面（仅作用于本插件，不影响应用商店）",
                min = 80, max = 125, default = 100, enabled = true,
                format = { "${it}%" },
                onChanged = { v ->
                    activity.writeLocalInt(Settings.KEY_UI_SCALE, v)
                    activity.recreate()
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        PluginFloatingBarSection(activity = activity)

        Spacer(Modifier.height(12.dp))
        SettingsSection(topLabel = "主页推荐") {
            PrefSwitch(
                activity = activity,
                key = Settings.KEY_RECOMMENDATIONS_ENABLED,
                title = "随机推荐",
                summary = "在模块主页展示 3 条随机功能推荐入口",
                default = true,
                enabled = true,
                affectsStore = false,
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection(topLabel = "操作提示") {
            val hintOn by remember(tick) {
                mutableStateOf(activity.readLocalBoolDirect(Settings.KEY_SWITCH_HINT, true))
            }
            SwitchRow(
                title = "开关操作提示",
                summary = "切换功能开关后弹出 Snackbar 提示；若需重启应用商店生效，提示内附「重启商店」按钮",
                checked = hintOn,
                enabled = true,
                affectsStore = false,
            ) { on ->
                activity.writeLocalBool(Settings.KEY_SWITCH_HINT, on)
            }
        }

        Footer("主题模式与界面缩放改动后立即重建本页生效；预测性返回需 Android 13 及以上系统支持。")
    }
}
