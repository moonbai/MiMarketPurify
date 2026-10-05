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
        val title = when (page) {
            PAGE_ADS -> "广告净化"
            PAGE_MINE -> "「我的」页精简"
            PAGE_TABS -> "底部标签栏"
            PAGE_MISC -> "其他界面精简"
            PAGE_THEME -> "主题与外观"
            else -> page
        }
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
    title: String,
    summary: String,
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
            title = title,
            summary = summary,
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
            HighlightSwitch(activity, Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_DETAIL_EXTRAS, "详情页附加推荐", "详情页拼装推荐、底部多按钮推广栏", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FLOATING_AD, "主页悬浮广告", "屏蔽主页底部/侧边弹出的悬浮广告", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_AD_BACK_FLOAT, "返回浮窗广告", "屏蔽返回时弹出的「返回今日头条」等浮窗", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_HOME_PAGE_DIALOG, "首页弹窗推广", "屏蔽进入首页时弹出的 Dialog 推广位", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_ENTRANCE, "首页活动入口", "隐藏搜索框左侧云控下发的活动小图标", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_FRUIT, "领水果入口", "隐藏福利活动 gif 动图入口", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_INSTALL_RECOMMEND, "安装后推荐", "拦截点击安装后弹出的「用户还喜欢」推荐弹窗", masterOn, hl)
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
            HighlightSwitch(activity, Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "隐藏页面顶部推荐卡片与底部推广列表", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "隐藏页面中间的官方应用管理功能入口", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_MINE_CLEANUP, "清理与卸载", "隐藏手机清理与应用卸载入口", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_MINE_SUMMARY, "个人信息区", "隐藏头像、昵称、消息、收藏", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_MINE_SECURITY, "安全检测", "隐藏应用安全检测卡片", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "清除升级卡片的果园背景", masterOn, hl)
        }
        if (cleanupOn && orchardOn) {
            SettingsSection(topLabel = "升级卡片") {
                PrefSwitch(
                    activity = activity,
                    key = Settings.KEY_CARD_EXPAND,
                    title = "升级卡片横向展开",
                    summary = "升级卡片展开显示更多应用更新（需同时开启「清理与卸载」与「更新卡片背景」）",
                    default = false,
                    enabled = masterOn,
                )
            }
        }
        SettingsSection {
            HighlightSwitch(activity, Settings.KEY_TAB_BADGE, "底栏角标", "去掉底部标签页的数字角标与红点", masterOn, hl)
        }
        Footer("「我的」页精简负责隐藏推荐、清理、安全检测、个人信息等内容")
    }
}

// ═══════════════ 底部标签栏（含悬浮底栏配置） ═══════════════

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
                key = Settings.KEY_TAB_FILTER,
                title = "筛选底部标签",
                summary = "开启后按下方勾选隐藏不需要的底栏标签（关闭则恢复全部）",
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
            PrefSwitch(activity, Settings.KEY_UPDATE_TAB, "底栏更新入口", "在商店原生底栏注入「更新」入口", default = false, enabled = masterOn)
            Spacer(Modifier.height(12.dp))
            PrefSwitch(activity, Settings.KEY_TAB_DEEP_CLEAN, "顶栏标签深度清理", "清理首页/榜单等页面顶部的推广子标签", default = true, enabled = masterOn)
        }

        Spacer(Modifier.height(12.dp))
        // ═══ 悬浮底栏（商店）配置 —— 原 PAGE_TAB_BAR 内容，合并至此 ═══
        SettingsSection(topLabel = "悬浮底栏") {
            PrefSwitch(
                activity = activity,
                key = Settings.KEY_FLOATING_BAR,
                title = "启用悬浮底栏",
                summary = "替换商店原生贴底栏为居中胶囊导航",
                default = false,
                enabled = masterOn,
            )
            if (floatingOn) {
                PrefSlider(activity, Settings.KEY_FLOAT_CORNER_RADIUS, "圆角半径", "胶囊圆角半径（dp），0=直角",
                    Settings.FLOATING_RADIUS_MIN, Settings.FLOATING_RADIUS_MAX, Settings.FLOATING_RADIUS_DEFAULT, true, format = { "${it}dp" })
                PrefSlider(activity, Settings.KEY_FLOAT_BAR_ALPHA, "背景透明度", "底栏整体背景透明度（%）",
                    Settings.FLOATING_ALPHA_MIN, Settings.FLOATING_ALPHA_MAX, Settings.STORE_FLOAT_ALPHA_DEFAULT, true, format = { "${it}%" })
                PrefSlider(activity, Settings.KEY_STORE_FLOAT_BOTTOM_MARGIN, "距底栏距离", "悬浮底栏到屏幕底部的间距（dp）",
                    Settings.STORE_FLOAT_BOTTOM_MARGIN_MIN, Settings.STORE_FLOAT_BOTTOM_MARGIN_MAX, Settings.STORE_FLOAT_BOTTOM_MARGIN_DEFAULT, true, format = { "${it}dp" })
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "选中项显示跟随移动的液态胶囊", default = true, enabled = masterOn)
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LIQUID_3D, "3D 液态高亮", "在液态基础上叠加阴影，增强立体感", default = false, enabled = masterOn)
                PrefSwitch(activity, Settings.KEY_FLOATING_BAR_MONOCHROME, "单色图标", "图标抽成单色描边、随主题着色", default = false, enabled = masterOn)
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
            HighlightSwitch(activity, Settings.KEY_DETAIL_FEATURED, "详情页「精选」", "按文案匹配，仅在应用详情页生效", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_UPDATE_HISTORY, "升级记录推荐", "隐藏升级记录底部的精选推荐", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_SEARCH_ALSO_VIEW, "搜索页「也在看」", "隐藏搜索结果底部的推荐", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_SUB_TAB_FILTER, "顶栏推广位", "清理首页/榜单等页面顶部的推广子标签", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_HIDE_UPDATE_ALL, "全部升级按钮", "隐藏更新界面全部升级按钮", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, "自动升级开关", "隐藏更新界面自动升级开关", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_PUSH_FLOAT, "Push悬浮通知", "屏蔽 MiPush 推送的悬浮通知", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_UPDATE_FLOAT_CARD, "升级浮窗卡片", "屏蔽检测到新版本时弹出的浮窗升级提示", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_BLOCK_BG_DOWNLOAD, "屏蔽后台静默下载", "禁止商店在后台自动下载应用更新", masterOn, hl)
            HighlightSwitch(activity, Settings.KEY_LONG_PRESS_JUMP, "长按跳转插件", "长按下载按钮跳转到插件主页", masterOn, hl)
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
            key = Settings.KEY_FLOAT_BAR_ENABLE,
            title = "启用插件悬浮底栏",
            summary = "在主页 / 关于页底部显示居中胶囊导航（主页、关于切换栏）",
            default = true,
            enabled = true,
            affectsStore = false,
            onChanged = { activity.refreshSignal.value++ },
        )
        if (pluginBarOn) {
            Spacer(Modifier.height(12.dp))
            PrefSwitch(activity, Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "关闭后悬浮底栏只保留图标", default = true, enabled = true)
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
                key = Settings.KEY_PREDICTIVE_BACK,
                title = "预测性返回",
                summary = "启用系统返回手势的预测动画（Android 13+）；关闭则回退默认返回处理",
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
