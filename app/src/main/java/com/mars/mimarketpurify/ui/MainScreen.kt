package com.mars.mimarketpurify.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.App
import com.mars.mimarketpurify.FeatureRegistry
import com.mars.mimarketpurify.MainActivity
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.SearchActivity
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.SubSettingsActivity
import com.mars.mimarketpurify.isNight
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import com.mars.mimarketpurify.ui.components.GroupCard
import com.mars.mimarketpurify.ui.components.NavRow
import com.mars.mimarketpurify.ui.components.SectionHeader
import com.mars.mimarketpurify.ui.components.SwitchRow
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

@Composable
fun MainScreen(activity: MainActivity) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val tick by activity.refreshSignal
    val masterOn = remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MASTER, true)) }
    val adSummary by remember(tick) { mutableStateOf(activity.countText(activity.adKeys)) }
    val tabsSummary by remember(tick) { mutableStateOf(activity.tabsText()) }
    val tabbarSummary by remember(tick) { mutableStateOf(activity.tabbarText()) }
    val mineSummary by remember(tick) { mutableStateOf(activity.countText(activity.mineKeys)) }
    val miscSummary by remember(tick) { mutableStateOf(activity.countText(activity.miscKeys)) }

    // 推荐开关：用独立 mutableStateOf，toggle 时立刻更新，不等 tick
    var recommendEnabled by remember(tick) {
        mutableStateOf(activity.readLocal(Settings.KEY_RECOMMENDATIONS_ENABLED, true))
    }
    val showRecommendations = masterOn.value && recommendEnabled

    // 15 秒自动刷新推荐
    var recommendSeed by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showRecommendations) {
        if (showRecommendations) {
            while (true) {
                delay(15_000L)
                recommendSeed = System.currentTimeMillis()
            }
        }
    }
    val recommendations = remember(recommendSeed) {
        FeatureRegistry.recommend(count = 3, seed = recommendSeed)
    }

    // service 状态
    var service by remember { mutableStateOf<XposedService?>(App.mService) }
    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(s: XposedService?) { service = s }
        }
        App.addServiceStateListener(listener, true)
        onDispose { App.removeServiceStateListener(listener) }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // ═══════════ Progressive Blur 顶栏 ═══════════
        BlurHeader(activity = activity)

        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp)
                .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
        ) {
            // ═══════════ 搜索栏 → 打开 SearchActivity ═══════════
            MiuiXSearchBar(
                onClick = { context.startActivity(SearchActivity.intent(context)) },
            )
            Spacer(Modifier.height(12.dp))  // 搜索栏与下方内容的间距

            StatusCard(service = service, night = activity.isNight())
            Spacer(Modifier.height(12.dp))

            // ═══════════ 发现好用（可开关 + 15s 自动刷新） ═══════════
            if (showRecommendations) {
                SectionHeader("推荐功能", "每 15 秒自动刷新")
                GroupCard {
                    recommendations.forEachIndexed { index, feature ->
                        RecommendRow(
                            feature = feature,
                            onClick = { activity.openPage(feature.page) },
                        )
                        if (index < recommendations.lastIndex) {
                            HorizontalDivider(
                                color = colors.dividerLine,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ═══════════ 总开关 ═══════════
            GroupCard {
                SwitchRow(
                    title = "总开关",
                    summary = "关闭后所有功能均不生效",
                    checked = masterOn.value,
                    enabled = true,
                ) { on ->
                    masterOn.value = on
                    activity.writeRemote(Settings.KEY_MASTER, on)
                }
            }

            // ═══════════ 界面设置 ═══════════
            SectionHeader("界面设置", "广告与界面内容清理")
            GroupCard {
                NavRow(title = "广告净化", summary = "开屏、首页信息流、搜索、下载升级、应用详情等一系列广告",
                    value = adSummary, enabled = masterOn.value) { activity.openPage(SubSettingsActivity.PAGE_ADS) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(title = "底栏自定义", summary = "底部标签筛选",
                    value = tabsSummary, enabled = masterOn.value) { activity.openPage(SubSettingsActivity.PAGE_TABS) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(title = "悬浮底栏配置", summary = "底栏颜色、透明度、显示效果参数",
                    value = tabbarSummary, enabled = masterOn.value) { activity.openPage(SubSettingsActivity.PAGE_TAB_BAR) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(title = "「我的」页精简", summary = "我的页应用推荐、官方入口、清理板块",
                    value = mineSummary, enabled = masterOn.value) { activity.openPage(SubSettingsActivity.PAGE_MINE) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(title = "其他界面精简", summary = "升级记录、搜索相关推荐等零散页面",
                    value = miscSummary, enabled = masterOn.value) { activity.openPage(SubSettingsActivity.PAGE_MISC) }
            }

            // ═══════════ 高级功能 ═══════════
            SectionHeader("高级功能", "深度净化与功能增强")
            GroupCard {
                SwitchRow(title = "下载超级岛", summary = "强制让下载进度进入小米超级岛（无视灰度）",
                    checked = activity.readLocal(Settings.KEY_ISLAND, true),
                    enabled = masterOn.value) { activity.writeRemote(Settings.KEY_ISLAND, it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "细节修正", summary = "显示非正版 APP、被隐藏更新等细节处理",
                    checked = activity.readLocal(Settings.KEY_MISC, true),
                    enabled = masterOn.value) { activity.writeRemote(Settings.KEY_MISC, it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "升级提醒弹窗", summary = "不再弹出应用商店的升级提醒对话框",
                    checked = activity.readLocal(Settings.KEY_UPDATE_DIALOG, true),
                    enabled = masterOn.value) { activity.writeRemote(Settings.KEY_UPDATE_DIALOG, it) }
            }

            // ═══════════ 模块功能 ═══════════
            SectionHeader("模块功能", "仅影响本模块的显示方式与调试选项")
            GroupCard {
                SwitchRow(title = "隐藏桌面图标", summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
                    checked = activity.isLauncherIconHidden(), enabled = true) { activity.applyHideIcon(it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "随机推荐", summary = "主页显示随机功能推荐（每 15 秒自动刷新）",
                    checked = recommendEnabled, enabled = masterOn.value) {
                    recommendEnabled = it   // ← 立刻更新本地状态，不等 tick
                    activity.writeRemote(Settings.KEY_RECOMMENDATIONS_ENABLED, it)
                }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "调试模式", summary = "开启后将统一日志输出且进入榜单会主动提示相关信息，日常使用关闭即可",
                    checked = activity.readLocal(Settings.KEY_RANK_DEBUG, false),
                    enabled = true) { activity.writeRemote(Settings.KEY_RANK_DEBUG, it) }
            }

            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            Text(
                text = "Tips：开关实时生效，但还是建议重启应用商店",
                fontSize = MiuiX.MICRO.sp,
                color = colors.onSurfaceSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
            )
        }
    }
}

// ═══════════ Progressive Blur 顶栏 ═══════════

@Composable
private fun BlurHeader(activity: MainActivity) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = MiuiX.PAGE_H.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Mi Market Purify",
                fontSize = MiuiX.HOME_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "小米应用商店净化与增强",
                fontSize = MiuiX.CAPTION.sp,
                color = colors.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ═══════════ MiuiX 风格搜索栏（点击跳转 SearchActivity） ═══════════

@Composable
private fun MiuiXSearchBar(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🔍",
            fontSize = 16.sp,
            color = colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "搜索功能…",
            fontSize = 15.sp,
            color = colors.onSurfaceSecondary,
        )
    }
}

// ═══════════ 推荐行 ═══════════

@Composable
private fun RecommendRow(feature: FeatureRegistry.Feature, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = MiuiX.ROW_PAD_V.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = feature.title, fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(text = feature.summary, fontSize = MiuiX.ROW_SUMMARY.sp,
                color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Text(text = "›", fontSize = 20.sp, color = colors.outline)
    }
}

// ═══════════ 状态卡 ═══════════

@Composable
private fun StatusCard(service: XposedService?, night: Boolean) {
    val colors = MiuixTheme.colorScheme
    val titleText: String
    val titleColor: Color
    val bodyText: String
    val bgColor: Color
    if (service == null) {
        titleText = "模块未激活"
        titleColor = colors.onSurfaceSecondary
        bodyText = "请在 LSPosed 框架中启用本模块，并在作用域里勾选「应用商店」，然后重启应用商店。"
        bgColor = Color(MiuiX.neutralSoft(night))
    } else {
        val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
        titleText = "已激活 · ${service.frameworkName} ${service.frameworkVersion}"
        titleColor = Color(MiuiX.STATE_ACTIVE)
        bodyText = if (remote) {
            "支持远程偏好：开关改动实时生效，一般无需重启应用商店。\n\n插件调试基于应用商店版本：4.126.xx"
        } else {
            "当前框架不支持远程偏好，开关可能不会立即生效，建议重启一次应用商店。"
        }
        bgColor = Color(MiuiX.STATE_ACTIVE_SOFT)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = MiuiX.CARD_PAD_H.dp, vertical = MiuiX.CARD_PAD_V.dp),
    ) {
        Text(text = titleText, fontSize = MiuiX.ROW_TITLE.sp, fontWeight = FontWeight.Bold, color = titleColor)
        Text(text = bodyText, fontSize = MiuiX.ROW_SUMMARY.sp, color = colors.onSurfaceSecondary,
            lineHeight = (MiuiX.ROW_SUMMARY * MiuiX.LINE_SPACING).sp, modifier = Modifier.padding(top = 4.dp))
    }
}
