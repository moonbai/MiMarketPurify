package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mars.mimarketpurify.ui.components.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 二级设置页（整页 Compose，与主页共用 [com.mars.mimarketpurify.ui.components] 的构件）。
 *
 * 原先这里是一套原生 View 构建器（addSwitchRow / addNavRow / groupCard ...），
 * 与主页 Compose 形成「两套 UI 语汇」的重复；现统一为 Compose，
 * 远程偏好读写、service 连接、入口自愈等逻辑仍复用 [SettingsBaseActivity]。
 */
class SubSettingsActivity : SettingsBaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_ADS = "ads"
        const val PAGE_MINE = "mine"
        const val PAGE_TABS = "tabs"
        const val PAGE_MISC = "misc"
        const val PAGE_TAB_BAR = "tab_bar_config"

        fun intent(context: Context, page: String): Intent =
            Intent(context, SubSettingsActivity::class.java).putExtra(EXTRA_PAGE, page)
    }

    private var page: String = PAGE_MINE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent?.getStringExtra(EXTRA_PAGE) ?: PAGE_MINE

        // 暗色模式：状态栏图标随背景反色
        WindowCompat.getInsetsController(window, window.decorView)
            ?.isAppearanceLightStatusBars = !isNight()

        setContent {
            MiuixTheme(colors = if (isNight()) darkColorScheme() else lightColorScheme()) {
                SubSettingsScreen(page = page, activity = this@SubSettingsActivity)
            }
        }
    }
}

private data class Feature(val key: String, val title: String, val summary: String)

@Composable
private fun SubSettingsScreen(page: String, activity: SubSettingsActivity) {
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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp),
        ) {
            when (page) {
                SubSettingsActivity.PAGE_ADS -> AdsScreen(activity, masterOn)
                SubSettingsActivity.PAGE_MINE -> MineScreen(activity, masterOn)
                SubSettingsActivity.PAGE_TABS -> TabsScreen(activity, masterOn)
                SubSettingsActivity.PAGE_MISC -> MiscScreen(activity, masterOn)
                SubSettingsActivity.PAGE_TAB_BAR -> TabBarConfigScreen(activity, masterOn)
                else -> Text("未知页面", color = colors.onSurface)
            }
        }
    }
}

// ==================== 广告净化 ====================

@Composable
private fun AdsScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    val adFeatures = listOf(
        Feature(Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告"),
        Feature(Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗"),
        Feature(Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏"),
        Feature(Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐"),
        Feature(Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐"),
        Feature(Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位"),
        Feature(Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片"),
    )
    SectionHeader("广告净化", "开屏、首页信息流、搜索、下载升级、应用详情等一系列广告")
    GroupCard {
        adFeatures.forEach { f ->
            PrefSwitch(activity = activity, key = f.key, title = f.title, summary = f.summary, enabled = masterOn)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        }
        PrefSwitch(activity, Settings.KEY_FRUIT, "领水果入口", "隐藏福利活动 gif 动图入口", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_ENTRANCE, "首页活动入口", "隐藏搜索框左侧云控下发的活动小图标 / 动图", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_DETAIL_EXTRAS, "详情页广告", "详情页拼装推荐、底部多按钮推广栏、浏览器下载弹窗广告", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_FLOATING_AD, "主页悬浮广告", "屏蔽主页底部/侧边弹出的悬浮广告图标", enabled = masterOn)
    }
    Footer("Tips：屏蔽后若页面空白，关掉对应页面开关即可恢复")
}

// ==================== 「我的」页精简 ====================

@Composable
private fun MineScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    val tick by activity.refreshSignal
    var cleanupOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MINE_CLEANUP, true)) }
    var orchardOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_ORCHARD_SKIN, true)) }
    val eligible = cleanupOn && orchardOn

    SectionHeader("「我的」页精简", "应用推荐、推广、清理板块与个人信息区清理")
    GroupCard {
        PrefSwitch(activity, Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "隐藏页面顶部推荐卡片与底部推广列表", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "隐藏页面中间的官方应用管理功能入口 tab", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_MINE_CLEANUP, "清理与卸载", "隐藏手机清理与应用卸载入口", enabled = masterOn) { cleanupOn = it }
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_MINE_SUMMARY, "个人信息区", "隐藏头像、昵称、消息、收藏", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_MINE_SECURITY, "安全检测", "隐藏应用安全检测卡片", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "清除升级卡片的果园背景", enabled = masterOn) { orchardOn = it }
        if (eligible) {
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSwitch(activity, Settings.KEY_CARD_EXPAND, "升级卡片横向展开", "需清理与卸载+更新卡片背景同时开启，展开时4个图标横向平铺", enabled = masterOn)
        }
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_TAB_BADGE, "底栏角标", "去掉底部标签页的数字角标与「新」字红点", enabled = masterOn)
    }
    Footer("Tips：改动一般在下次进入界面时生效，不过重启会立刻生效。")
}

// ==================== 底部标签栏 ====================

@Composable
private fun TabsScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    val tick by activity.refreshSignal
    var filterOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_TAB_FILTER, true)) }
    var kept by remember(tick) { mutableStateOf(activity.readLocalTabs()) }

    SectionHeader("底部标签栏", "选择需要展示的底栏标签")
    GroupCard {
        PrefSwitch(activity, Settings.KEY_TAB_FILTER, "筛选底部标签", "选择需要展示的底栏标签", enabled = masterOn) { filterOn = it }
    }
    if (filterOn) {
        GroupCard {
            Settings.TAB_ITEMS.forEach { (tag, label) ->
                CheckboxRow(
                    title = label,
                    summary = "",
                    checked = kept.contains(tag),
                    enabled = masterOn,
                ) { on ->
                    kept = if (on) kept + tag else kept - tag
                    activity.writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
                }
            }
        }
    }
    Footer("Tips：隐藏标签后需重启一次应用商店才会生效；悬浮底栏及其参数为实时生效。")
}

// ==================== 其他界面精简 ====================

@Composable
private fun MiscScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    SectionHeader("其他界面精简", "升级记录、搜索相关推荐等零散页面")
    GroupCard {
        PrefSwitch(activity, Settings.KEY_DETAIL_FEATURED, "详情页「精选」", "按文案匹配，仅在应用详情页生效", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_UPDATE_HISTORY, "升级记录页推荐", "隐藏「升级记录」底部的精选推荐、热门下载、大家还安装了", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_SEARCH_ALSO_VIEW, "搜索页「也在看」", "隐藏搜索结果底部的「搜索 xxx 的人也在看」", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_SUB_TAB_FILTER, "顶栏推广位", "清理首页/榜单等页面顶部的云控推广子标签", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_HIDE_UPDATE_ALL, "更新界面全部升级按钮", "隐藏更新界面全部升级按钮", enabled = masterOn)
        Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
        PrefSwitch(activity, Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, "更新界面自动升级开关", "隐藏更新界面自动升级开关", enabled = masterOn)
    }
    Footer("Tips：隐藏的可能只是标题~")
}

// ==================== 悬浮底栏配置 ====================

@Composable
private fun TabBarConfigScreen(activity: SubSettingsActivity, masterOn: Boolean) {
    val tick by activity.refreshSignal
    var floatingOn by remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_FLOATING_BAR, false)) }

    SectionHeader("悬浮底栏配置", "底栏颜色、透明度、显示效果参数")
    GroupCard {
        PrefSwitch(
            activity,
            Settings.KEY_FLOATING_BAR,
            "启用悬浮底栏",
            "在小米应用商店底部渲染胶囊风格Tab导航栏",
            default = false,
            enabled = masterOn,
        ) { floatingOn = it }
    }
    if (floatingOn) {
        GroupCard {
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "选中项显示跟随移动的液态胶囊（参考 iOS），图标带弹性缩放", enabled = masterOn)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID_3D, "3D液态效果", "选中项显示跟随移动的3D液态胶囊，图标带弹性缩放", enabled = masterOn)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "关闭后悬浮底栏只保留图标，栏体更矮更清爽", enabled = masterOn)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_BADGE, "显示角标", "悬浮栏同步原生红点/数字角标，受底栏角标净化开关控制", enabled = masterOn)
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSwitch(activity, Settings.KEY_UPDATE_TAB, "底栏更新入口（移花接木）", "在商店底栏注入「更新」入口；悬浮态下以右侧独立玻璃按钮呈现，点击直达更新页。关闭即移除该注入", default = true, enabled = masterOn)
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
            PrefSlider(
                activity,
                Settings.KEY_FLOATING_BAR_RADIUS,
                "圆角大小",
                "胶囊圆角半径，0直角，上限建议不超过栏高一半",
                min = Settings.FLOATING_RADIUS_MIN,
                max = Settings.FLOATING_RADIUS_MAX,
                default = Settings.FLOATING_RADIUS_DEFAULT,
                enabled = masterOn,
                format = { "${it}dp" },
            )
            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            PrefSlider(
                activity,
                Settings.KEY_FLOATING_BAR_BOTTOM_MARGIN,
                "距底部高度",
                "悬浮底栏距屏幕底部的外边距；数值越大越「悬空」，便于避开系统手势区",
                min = Settings.FLOATING_BOTTOM_MARGIN_MIN,
                max = Settings.FLOATING_BOTTOM_MARGIN_MAX,
                default = Settings.FLOATING_BOTTOM_MARGIN_DEFAULT,
                enabled = masterOn,
                format = { "${it}dp" },
            )
        }
        Footer("Tips：底栏背景色可用 #AARRGGBB 自定义透明度（如 #CCFFFFFF），实时生效。")
    }
}
