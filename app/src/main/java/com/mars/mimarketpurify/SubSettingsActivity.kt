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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.components.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import kotlinx.coroutines.delay

class SubSettingsActivity : SettingsBaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val EXTRA_HIGHLIGHT_KEY = "highlight_key"
        const val PAGE_ADS = "ads"
        const val PAGE_MINE = "mine"
        const val PAGE_TABS = "tabs"
        const val PAGE_MISC = "misc"
        const val PAGE_TAB_BAR = "tab_bar_config"

        fun intent(context: Context, page: String, highlightKey: String = ""): Intent =
            Intent(context, SubSettingsActivity::class.java)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_HIGHLIGHT_KEY, highlightKey)
    }

    private var page: String = PAGE_MINE
    private var highlightKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent?.getStringExtra(EXTRA_PAGE) ?: PAGE_MINE
        highlightKey = intent?.getStringExtra(EXTRA_HIGHLIGHT_KEY) ?: ""

        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                SubSettingsScreen(page = page, activity = this@SubSettingsActivity, highlightKey = highlightKey)
            }
        }
    }
}

@Composable
private fun SubSettingsScreen(page: String, activity: SubSettingsActivity, highlightKey: String) {
    val tick by activity.refreshSignal
    val masterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MASTER, true)) }
    val colors = MiuixTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val title = when (page) {
            SubSettingsActivity.PAGE_ADS -> "广告净化"
            SubSettingsActivity.PAGE_MINE -> "「我的」页精简"
            SubSettingsActivity.PAGE_TABS -> "底部标签栏"
            SubSettingsActivity.PAGE_MISC -> "其他界面精简"
            SubSettingsActivity.PAGE_TAB_BAR -> "悬浮底栏配置"
            else -> "设置"
        }
        SubTopBar(title = title, onBack = { activity.finish() })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp),
        ) {
            when (page) {
                SubSettingsActivity.PAGE_ADS -> AdsScreen(activity, masterOn, highlightKey)
                SubSettingsActivity.PAGE_MINE -> MineScreen(activity, masterOn, highlightKey)
                SubSettingsActivity.PAGE_TABS -> TabsScreen(activity, masterOn, highlightKey)
                SubSettingsActivity.PAGE_MISC -> MiscScreen(activity, masterOn, highlightKey)
                SubSettingsActivity.PAGE_TAB_BAR -> TabBarConfigScreen(activity, masterOn, highlightKey)
                else -> Text("未知页面", color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun AdsScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    GroupCard {
        listOf(
            Settings.KEY_SPLASH to "开屏广告",
            Settings.KEY_MAIN_TAB to "前台广告/推荐",
            Settings.KEY_HOME_FEED to "信息流广告",
            Settings.KEY_SEARCH to "搜索推荐",
            Settings.KEY_UPDATE_DL to "升级/下载推荐",
            Settings.KEY_DETAIL to "详情页广告",
            Settings.KEY_RANK to "榜单广告",
        ).forEach { (k, t) -> HighlightSwitch(activity, k, t, "", masterOn, hl) }
        HighlightSwitch(activity, Settings.KEY_FRUIT, "领水果入口", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_ENTRANCE, "首页活动入口", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_DETAIL_EXTRAS, "详情页附加推荐", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_FLOATING_AD, "主页悬浮广告", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_AD_BACK_FLOAT, "返回浮窗广告", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_HOME_PAGE_DIALOG, "首页弹窗推广", "", masterOn, hl)
    }
}

@Composable
private fun MineScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var cleanupOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MINE_CLEANUP, true)) }
    var orchardOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_ORCHARD_SKIN, true)) }

    GroupCard {
        HighlightSwitch(activity, Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_CLEANUP, "清理与卸载", "", masterOn, hl) { cleanupOn = it }
        HighlightSwitch(activity, Settings.KEY_MINE_SUMMARY, "个人信息区", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_SECURITY, "安全检测", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "", masterOn, hl) { orchardOn = it }
        if (cleanupOn && orchardOn) HighlightSwitch(activity, Settings.KEY_CARD_EXPAND, "升级卡片横向展开", "", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_TAB_BADGE, "底栏角标", "", masterOn, hl)
    }
}

@Composable
private fun TabsScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var filterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_TAB_FILTER, true)) }
    var kept by remember(tick) { mutableStateOf(activity.readLocalTabs()) }

    GroupCard { HighlightSwitch(activity, Settings.KEY_TAB_FILTER, "筛选底部标签", "", masterOn, hl) { filterOn = it } }
    if (filterOn) {
        GroupCard {
            Settings.TAB_ITEMS.forEach { (tag, label) ->
                CheckboxRow(title = label, summary = "", checked = kept.contains(tag), enabled = masterOn) { on ->
                    kept = if (on) kept + tag else kept - tag
                    activity.writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
                }
            }
        }
        GroupCard { HighlightSwitch(activity, Settings.KEY_TAB_DEEP_CLEAN, "深层精简", "", masterOn, hl) }
    }
    GroupCard { HighlightSwitch(activity, Settings.KEY_UPDATE_TAB, "底栏更新入口", "", masterOn, hl, default = true) }
}

@Composable
private fun MiscScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    GroupCard {
        listOf(
            Settings.KEY_DETAIL_FEATURED to "详情页「精选」",
            Settings.KEY_UPDATE_HISTORY to "升级记录推荐",
            Settings.KEY_SEARCH_ALSO_VIEW to "搜索页「也在看」",
            Settings.KEY_SUB_TAB_FILTER to "顶栏推广位",
            Settings.KEY_HIDE_UPDATE_ALL to "全部升级按钮",
            Settings.KEY_HIDE_AUTO_UPDATE_SWITCH to "自动升级开关",
            Settings.KEY_PUSH_FLOAT to "Push悬浮通知",
            Settings.KEY_UPDATE_FLOAT_CARD to "升级浮窗卡片",
            Settings.KEY_BLOCK_BG_DOWNLOAD to "屏蔽后台静默下载",
        ).forEach { (k, t) -> HighlightSwitch(activity, k, t, "", masterOn, hl) }
    }
}

@Composable
private fun TabBarConfigScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var floatingOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_FLOATING_BAR, false)) }

    GroupCard { HighlightSwitch(activity, Settings.KEY_FLOATING_BAR, "启用悬浮底栏", "", masterOn, hl, default = false) { floatingOn = it } }
    if (floatingOn) {
        GroupCard {
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID_3D, "3D液态效果", "", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_MONOCHROME, "单色图标", "", masterOn, hl, default = true)
        }
        GroupCard {
            PrefColorRow(activity, "底栏背景色", Settings.KEY_FLOAT_BG_COLOR, 0xE6FFFFFF.toInt())
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "选中背景色", Settings.KEY_FLOAT_SELECT_BG_COLOR, MiuiX.primary(activity.isNight()))
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "选中文字/图标高亮色", Settings.KEY_FLOAT_TEXT_SELECT_COLOR, 0xFFFFFFFF.toInt())
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefColorRow(activity, "未选中文字/图标颜色", Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, 0xFF8E8E93.toInt())
        }
        GroupCard {
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_RADIUS, "圆角大小", "胶囊圆角半径",
                min = Settings.FLOATING_RADIUS_MIN, max = Settings.FLOATING_RADIUS_MAX,
                default = Settings.FLOATING_RADIUS_DEFAULT, enabled = masterOn, format = { "${it}dp" })
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_BOTTOM_MARGIN, "距底部高度", "悬浮底栏距屏幕底部的外边距",
                min = Settings.FLOATING_BOTTOM_MARGIN_MIN, max = Settings.FLOATING_BOTTOM_MARGIN_MAX,
                default = Settings.FLOATING_BOTTOM_MARGIN_DEFAULT, enabled = masterOn, format = { "${it}dp" })
        }
    }
}

// ═══════════ 高亮开关组件 ═══════════

@Composable
private fun HighlightSwitch(
    activity: SubSettingsActivity,
    key: String,
    title: String,
    summary: String,
    enabled: Boolean,
    highlightKey: String,
    default: Boolean = true,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val isHighlighted = highlightKey.isNotEmpty() && highlightKey == key

    // 闪烁两次后消失
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            alpha.animateTo(0.35f, tween(200))
            delay(150)
            alpha.animateTo(0f, tween(200))
            delay(150)
            alpha.animateTo(0.35f, tween(200))
            delay(150)
            alpha.animateTo(0f, tween(200))
        } else {
            alpha.snapTo(0f)
        }
    }

    // 遵守 GroupCard 圆角（12dp）
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        modifier = if (alpha.value > 0f)
            Modifier.clip(cardShape).background(Color(0xFF1976D2).copy(alpha = alpha.value))
        else Modifier,
    ) {
        PrefSwitch(
            activity = activity,
            key = key,
            title = title,
            summary = summary,
            default = default,
            enabled = enabled,
            onChanged = onChanged,
        )
    }
}
