package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

private data class Feature(val key: String, val title: String, val summary: String)

@Composable
private fun SubSettingsScreen(page: String, activity: SubSettingsActivity, highlightKey: String) {
    val tick by activity.refreshSignal
    val masterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MASTER, true)) }
    val colors = MiuixTheme.colorScheme

    // 高亮 key：进入时有效，3 秒后自动淡出
    var activeHighlight by remember { mutableStateOf(highlightKey) }
    LaunchedEffect(highlightKey) {
        if (highlightKey.isNotEmpty()) {
            delay(3000L)
            activeHighlight = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        val title = when (page) {
            PAGE_ADS -> "广告净化"
            PAGE_MINE -> "「我的」页精简"
            PAGE_TABS -> "底部标签栏"
            PAGE_MISC -> "其他界面精简"
            PAGE_TAB_BAR -> "悬浮底栏配置"
            else -> "设置"
        }
        SubTopBar(title = title, onBack = { activity.finish() })
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp),
        ) {
            when (page) {
                PAGE_ADS -> AdsScreen(activity, masterOn, activeHighlight)
                PAGE_MINE -> MineScreen(activity, masterOn, activeHighlight)
                PAGE_TABS -> TabsScreen(activity, masterOn, activeHighlight)
                PAGE_MISC -> MiscScreen(activity, masterOn, activeHighlight)
                PAGE_TAB_BAR -> TabBarConfigScreen(activity, masterOn, activeHighlight)
                else -> Text("未知页面", color = colors.onSurface)
            }
        }
    }
}

// ==================== 广告净化 ====================

@Composable
private fun AdsScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val adFeatures = listOf(
        Feature(Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告"),
        Feature(Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗"),
        Feature(Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏"),
        Feature(Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐"),
        Feature(Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐"),
        Feature(Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位"),
        Feature(Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片"),
    )
    GroupCard {
        adFeatures.forEach { f ->
            HighlightSwitch(activity, f.key, f.title, f.summary, masterOn, hl)
        }
        HighlightSwitch(activity, Settings.KEY_FRUIT, "领水果入口", "隐藏福利活动 gif 动图入口", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_ENTRANCE, "首页活动入口", "隐藏搜索框左侧云控下发的活动小图标", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_DETAIL_EXTRAS, "详情页附加推荐", "详情页拼装推荐、底部多按钮推广栏", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_FLOATING_AD, "主页悬浮广告", "屏蔽主页底部/侧边弹出的悬浮广告", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_AD_BACK_FLOAT, "返回浮窗广告", "屏蔽返回时弹出的「返回今日头条」等悬浮浮窗", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_HOME_PAGE_DIALOG, "首页弹窗推广", "屏蔽进入首页时弹出的 Dialog 推广位", masterOn, hl)
    }
    Footer("Tips：屏蔽后若页面空白，关掉对应页面开关即可恢复")
}

// ==================== 我的页 ====================

@Composable
private fun MineScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var cleanupOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MINE_CLEANUP, true)) }
    var orchardOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_ORCHARD_SKIN, true)) }
    val eligible = cleanupOn && orchardOn

    GroupCard {
        HighlightSwitch(activity, Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "隐藏页面顶部推荐卡片与底部推广列表", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "隐藏页面中间的官方应用管理功能入口 tab", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_CLEANUP, "清理与卸载", "隐藏手机清理与应用卸载入口", masterOn, hl) { cleanupOn = it }
        HighlightSwitch(activity, Settings.KEY_MINE_SUMMARY, "个人信息区", "隐藏头像、昵称、消息、收藏", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_MINE_SECURITY, "安全检测", "隐藏应用安全检测卡片", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "清除升级卡片的果园背景", masterOn, hl) { orchardOn = it }
        if (eligible) {
            HighlightSwitch(activity, Settings.KEY_CARD_EXPAND, "升级卡片横向展开", "需清理与卸载+更新卡片背景同时开启", masterOn, hl)
        }
        HighlightSwitch(activity, Settings.KEY_TAB_BADGE, "底栏角标", "去掉底部标签页的数字角标与红点", masterOn, hl)
    }
    Footer("Tips：改动一般在下次进入界面时生效，不过重启会立刻生效。")
}

// ==================== 标签栏 ====================

@Composable
private fun TabsScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var filterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_TAB_FILTER, true)) }
    var kept by remember(tick) { mutableStateOf(activity.readLocalTabs()) }

    GroupCard {
        HighlightSwitch(activity, Settings.KEY_TAB_FILTER, "筛选底部标签", "选择需要展示的底栏标签", masterOn, hl) { filterOn = it }
    }
    if (filterOn) {
        GroupCard {
            Settings.TAB_ITEMS.forEach { (tag, label) ->
                CheckboxRow(title = label, summary = "", checked = kept.contains(tag), enabled = masterOn) { on ->
                    kept = if (on) kept + tag else kept - tag
                    activity.writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
                }
            }
        }
        GroupCard {
            HighlightSwitch(activity, Settings.KEY_TAB_DEEP_CLEAN, "隐藏标签时跳过数据加载",
                "隐藏的 Tab 不仅不显示，还跳过其内容加载，节省流量和内存", masterOn, hl)
        }
    }
    GroupCard {
        HighlightSwitch(activity, Settings.KEY_UPDATE_TAB, "底栏更新入口",
            "在商店原生底栏注入「更新」入口并直达更新页", masterOn, hl, default = true)
    }
    Footer("Tips：隐藏标签后需重启一次应用商店才会生效。")
}

// ==================== 其他界面精简 ====================

@Composable
private fun MiscScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    GroupCard {
        HighlightSwitch(activity, Settings.KEY_DETAIL_FEATURED, "详情页「精选」", "按文案匹配，仅在应用详情页生效", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_UPDATE_HISTORY, "升级记录推荐", "隐藏升级记录底部的精选推荐", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_SEARCH_ALSO_VIEW, "搜索页「也在看」", "隐藏搜索结果底部的推荐", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_SUB_TAB_FILTER, "顶栏推广位", "清理首页/榜单等页面顶部的推广子标签", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_HIDE_UPDATE_ALL, "全部升级按钮", "隐藏更新界面全部升级按钮", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, "自动升级开关", "隐藏更新界面自动升级开关", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_PUSH_FLOAT, "Push悬浮通知", "屏蔽 MiPush 推送的悬浮通知与游戏推广浮窗", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_UPDATE_FLOAT_CARD, "升级浮窗卡片", "屏蔽检测到新版本时弹出的浮窗升级提示", masterOn, hl)
        HighlightSwitch(activity, Settings.KEY_BLOCK_BG_DOWNLOAD, "屏蔽后台静默下载", "禁止商店在后台自动下载应用更新，节省流量和电量", masterOn, hl)
    }
    Footer("Tips：隐藏的可能只是标题，不过眼不见为净嘛~")
}

// ==================== 悬浮底栏配置 ====================

@Composable
private fun TabBarConfigScreen(activity: SubSettingsActivity, masterOn: Boolean, hl: String = "") {
    val tick by activity.refreshSignal
    var floatingOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_FLOATING_BAR, false)) }

    GroupCard {
        HighlightSwitch(activity, Settings.KEY_FLOATING_BAR, "启用悬浮底栏",
            "在商店底部渲染胶囊风格Tab导航栏", masterOn, hl, default = false) { floatingOn = it }
    }
    if (floatingOn) {
        GroupCard {
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "选中项显示跟随移动的液态胶囊", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID_3D, "3D液态效果", "选中项显示跟随移动的3D液态胶囊", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "关闭后悬浮底栏只保留图标", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_BAR_MONOCHROME, "单色图标", "图标抽成单色描边、随主题着色", masterOn, hl, default = true)
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
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_RADIUS, "圆角大小",
                "胶囊圆角半径，0直角，上限建议不超过栏高一半",
                min = Settings.FLOATING_RADIUS_MIN, max = Settings.FLOATING_RADIUS_MAX,
                default = Settings.FLOATING_RADIUS_DEFAULT, enabled = masterOn, format = { "${it}dp" })
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSlider(activity, Settings.KEY_FLOATING_BAR_BOTTOM_MARGIN, "距底部高度",
                "悬浮底栏距屏幕底部的外边距；数值越大越「悬空」",
                min = Settings.FLOATING_BOTTOM_MARGIN_MIN, max = Settings.FLOATING_BOTTOM_MARGIN_MAX,
                default = Settings.FLOATING_BOTTOM_MARGIN_DEFAULT, enabled = masterOn, format = { "${it}dp" })
        }
        Footer("Tips：底栏背景色实时生效。")
    }
}

// ==================== 高亮开关组件 ====================

@Composable
private fun HighlightSwitch(
    activity: SubSettingsActivity,
    key: String,
    title: String,
    summary: String,
    enabled: Boolean,
    highlightKey: String,
    default: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val isHighlighted = highlightKey.isNotEmpty() && highlightKey == key
    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0x331976D2) else Color.Transparent,
        label = "highlight_bg"
    )
    Box(
        modifier = if (isHighlighted) Modifier.background(bgColor) else Modifier,
    ) {
        PrefSwitch(
            activity = activity,
            key = key,
            title = title,
            summary = summary,

            checked = activity.readLocal(key, default),
            enabled = enabled,
            default = default,
            onCheckedChange = onCheckedChange,
        )
    }
}
