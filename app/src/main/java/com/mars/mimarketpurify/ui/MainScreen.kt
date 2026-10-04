package com.mars.mimarketpurify.ui

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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.App
import com.mars.mimarketpurify.FeatureRegistry
import com.mars.mimarketpurify.MainActivity
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.SubSettingsActivity
import com.mars.mimarketpurify.isNight
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import com.mars.mimarketpurify.ui.components.GroupCard
import com.mars.mimarketpurify.ui.components.NavRow
import com.mars.mimarketpurify.ui.components.SectionHeader
import com.mars.mimarketpurify.ui.components.SwitchRow
import io.github.libxposed.api.XposedService
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(activity: MainActivity) {
    val colors = MiuixTheme.colorScheme
    val service = rememberServiceState()
    val tick by activity.refreshSignal
    val masterOn = remember(tick) { mutableStateOf(activity.readLocal(Settings.KEY_MASTER, true)) }
    val adSummary by remember(tick) { mutableStateOf(activity.countText(activity.adKeys)) }
    val tabsSummary by remember(tick) { mutableStateOf(activity.tabsText()) }
    val tabbarSummary by remember(tick) { mutableStateOf(activity.tabbarText()) }
    val mineSummary by remember(tick) { mutableStateOf(activity.countText(activity.mineKeys)) }
    val miscSummary by remember(tick) { mutableStateOf(activity.countText(activity.miscKeys)) }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(searchQuery) { FeatureRegistry.search(searchQuery) }
    val isSearchActive = searchQuery.isNotBlank()

    // 推荐状态：每次进入用当前时间作为 seed，确保每次不同
    val recommendations = remember {
        FeatureRegistry.recommend(count = 3, seed = System.currentTimeMillis())
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        MainHeader(activity = activity, modifier = Modifier.statusBarsPadding())
        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp)
                .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
        ) {
            // ═══════════ 搜索栏 ═══════════
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = { searchQuery = "" },
            )

            // ═══════════ 搜索结果 ═══════════
            AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    if (searchResults.isEmpty()) {
                        GroupCard {
                            Text(
                                text = "未找到相关功能",
                                fontSize = MiuiX.ROW_TITLE.sp,
                                color = colors.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = MiuiX.ROW_PAD_V.dp),
                            )
                        }
                    } else {
                        GroupCard {
                            searchResults.forEachIndexed { index, feature ->
                                SearchResultRow(
                                    feature = feature,
                                    onClick = { activity.openPage(feature.page) },
                                )
                                if (index < searchResults.lastIndex) {
                                    HorizontalDivider(
                                        color = colors.dividerLine,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ═══════════ 搜索时隐藏常规内容 ═══════════
            if (!isSearchActive) {
                StatusCard(service = service, night = activity.isNight())
                Spacer(Modifier.height(12.dp))

                // ═══════════ 发现好用 ═══════════
                SectionHeader("发现好用", "随机推荐 3 个实用开关")
                GroupCard {
                    recommendations.forEachIndexed { index, feature ->
                        RecommendRow(
                            feature = feature,
                            enabled = masterOn.value,
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

                // ═══════════ 总开关 ═══════════
                Spacer(Modifier.height(12.dp))
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
                    NavRow(
                        title = "广告净化",
                        summary = "开屏、首页信息流、搜索、下载升级、应用详情等一系列广告",
                        value = adSummary,
                        enabled = masterOn.value,
                    ) { activity.openPage(SubSettingsActivity.PAGE_ADS) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    NavRow(
                        title = "底栏自定义",
                        summary = "底部标签筛选",
                        value = tabsSummary,
                        enabled = masterOn.value,
                    ) { activity.openPage(SubSettingsActivity.PAGE_TABS) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    NavRow(
                        title = "悬浮底栏配置",
                        summary = "底栏颜色、透明度、显示效果参数",
                        value = tabbarSummary,
                        enabled = masterOn.value,
                    ) { activity.openPage(SubSettingsActivity.PAGE_TAB_BAR) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    NavRow(
                        title = "「我的」页精简",
                        summary = "我的页应用推荐、官方入口、清理板块",
                        value = mineSummary,
                        enabled = masterOn.value,
                    ) { activity.openPage(SubSettingsActivity.PAGE_MINE) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    NavRow(
                        title = "其他界面精简",
                        summary = "升级记录、搜索相关推荐等零散页面",
                        value = miscSummary,
                        enabled = masterOn.value,
                    ) { activity.openPage(SubSettingsActivity.PAGE_MISC) }
                }

                // ═══════════ 高级功能 ═══════════
                SectionHeader("高级功能", "深度净化与功能增强")
                GroupCard {
                    SwitchRow(
                        title = "下载超级岛",
                        summary = "强制让下载进度进入小米超级岛（无视灰度）",
                        checked = activity.readLocal(Settings.KEY_ISLAND, true),
                        enabled = masterOn.value,
                    ) { activity.writeRemote(Settings.KEY_ISLAND, it) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    SwitchRow(
                        title = "细节修正",
                        summary = "显示非正版 APP、被隐藏更新等细节处理",
                        checked = activity.readLocal(Settings.KEY_MISC, true),
                        enabled = masterOn.value,
                    ) { activity.writeRemote(Settings.KEY_MISC, it) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    SwitchRow(
                        title = "升级提醒弹窗",
                        summary = "不再弹出应用商店的升级提醒对话框",
                        checked = activity.readLocal(Settings.KEY_UPDATE_DIALOG, true),
                        enabled = masterOn.value,
                    ) { activity.writeRemote(Settings.KEY_UPDATE_DIALOG, it) }
                }

                // ═══════════ 模块功能 ═══════════
                SectionHeader("模块功能", "仅影响本模块的显示方式与调试选项")
                GroupCard {
                    SwitchRow(
                        title = "隐藏桌面图标",
                        summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
                        checked = activity.isLauncherIconHidden(),
                        enabled = true,
                    ) { activity.applyHideIcon(it) }
                    Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                    SwitchRow(
                        title = "调试模式",
                        summary = "开启后将统一日志输出且进入榜单会主动提示相关信息，日常使用关闭即可",
                        checked = activity.readLocal(Settings.KEY_RANK_DEBUG, false),
                        enabled = true,
                    ) { activity.writeRemote(Settings.KEY_RANK_DEBUG, it) }
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
}

// ==================== 搜索栏 ====================

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val isActive = query.isNotBlank()
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "搜索功能…",
                fontSize = MiuiX.ROW_TITLE.sp,
                color = colors.onSurfaceSecondary,
            )
        },
        trailingIcon = {
            if (isActive) {
                Text(
                    text = "✕",
                    fontSize = 18.sp,
                    color = colors.onSurfaceSecondary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onClear() }
                        .padding(8.dp),
                )
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.primary,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    )
}

// ==================== 搜索结果行 ====================

@Composable
private fun SearchResultRow(
    feature: FeatureRegistry.Feature,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pageLabel = when (feature.page) {
        SubSettingsActivity.PAGE_ADS -> "广告净化"
        SubSettingsActivity.PAGE_MINE -> "我的页"
        SubSettingsActivity.PAGE_TABS -> "标签栏"
        SubSettingsActivity.PAGE_MISC -> "界面精简"
        SubSettingsActivity.PAGE_TAB_BAR -> "悬浮底栏"
        else -> feature.page
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = MiuiX.ROW_PAD_V.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = feature.summary,
                fontSize = MiuiX.ROW_SUMMARY.sp,
                color = colors.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = pageLabel,
            fontSize = MiuiX.CAPTION.sp,
            color = colors.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "›", fontSize = 20.sp, color = colors.outline)
    }
}

// ==================== 推荐功能行 ====================

@Composable
private fun RecommendRow(
    feature: FeatureRegistry.Feature,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = MiuiX.ROW_PAD_V.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                text = feature.summary,
                fontSize = MiuiX.ROW_SUMMARY.sp,
                color = if (enabled) colors.onSurfaceSecondary else colors.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(text = "›", fontSize = 20.sp, color = colors.outline)
    }
}

// ==================== 观察框架 service 连接状态 ====================

@Composable
private fun rememberServiceState(): XposedService? {
    val state = remember { mutableStateOf(App.mService) }
    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(service: XposedService?) {
                state.value = service
            }
        }
        App.addServiceStateListener(listener, true)
        onDispose { App.removeServiceStateListener(listener) }
    }
    return state.value
}

// ==================== 顶栏 ====================

@Composable
private fun MainHeader(activity: MainActivity, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
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

// ==================== 状态卡 ====================

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
        bodyText = "以下开关暂时改不动远程偏好：请在 LSPosed / 框架中启用本模块，" +
            "并在作用域里勾选「应用商店」，然后重启应用商店。"
        bgColor = Color(MiuiX.neutralSoft(night))
    } else {
        val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
        titleText = "已激活 · ${service.frameworkName} ${service.frameworkVersion}"
        titleColor = Color(MiuiX.STATE_ACTIVE)
        bodyText = if (remote) {
            "支持远程偏好：开关改动实时生效，一般无需重启应用商店。\n\n插件调试基于应用商店版本：4.126.xx，其余版本不保证适用性"
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
        Text(
            text = titleText,
            fontSize = MiuiX.ROW_TITLE.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
        )
        Text(
            text = bodyText,
            fontSize = MiuiX.ROW_SUMMARY.sp,
            color = colors.onSurfaceSecondary,
            lineHeight = (MiuiX.ROW_SUMMARY * MiuiX.LINE_SPACING).sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
