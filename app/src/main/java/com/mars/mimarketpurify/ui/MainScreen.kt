package com.mars.mimarketpurify.ui

import android.app.Activity
import android.content.Context
import android.provider.Settings
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.App
import com.mars.mimarketpurify.FeatureRegistry
import com.mars.mimarketpurify.MainActivity
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.SearchActivity
import com.mars.mimarketpurify.SubSettingsActivity
import com.mars.mimarketpurify.isNight
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import com.mars.mimarketpurify.ui.components.SettingsSection
import com.mars.mimarketpurify.ui.components.SettingItem
import com.mars.mimarketpurify.ui.components.SwitchRow
import com.mars.mimarketpurify.ui.components.AboutGlassCard
import com.mars.mimarketpurify.util.MarketRestarter
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

// ═══════════ 重启应用商店工具函数 ═══════════

/** 强制停止应用商店并重新启动（优先 root force-stop，无 root 回退 killBackgroundProcesses） */
private fun restartMarket(context: Context) {
    MarketRestarter.restart(context)
}

@Composable
fun MainScreen(activity: MainActivity) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val tick by activity.refreshSignal
    val masterOn = remember(tick) { mutableStateOf(activity.readLocal(com.mars.mimarketpurify.Settings.KEY_MASTER, true)) }
    val adSummary by remember(tick) { mutableStateOf(activity.countText(activity.adKeys)) }
    val tabsSummary by remember(tick) { mutableStateOf(activity.tabsText()) }
    val tabbarSummary by remember(tick) { mutableStateOf(activity.tabbarText()) }
    val mineSummary by remember(tick) { mutableStateOf(activity.countText(activity.mineKeys)) }
    val miscSummary by remember(tick) { mutableStateOf(activity.countText(activity.miscKeys)) }

    var recommendEnabled by remember(tick) {
        mutableStateOf(activity.readLocal(com.mars.mimarketpurify.Settings.KEY_RECOMMENDATIONS_ENABLED, true))
    }
    val showRecommendations = masterOn.value && recommendEnabled

    var recommendSeed by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showRecommendations) {
        if (showRecommendations) {
            while (true) { delay(15_000L); recommendSeed = System.currentTimeMillis() }
        }
    }
    val recommendations = remember(recommendSeed) {
        FeatureRegistry.recommend(count = 3, seed = recommendSeed)
    }

    var service by remember { mutableStateOf<XposedService?>(App.mService) }
    DisposableEffect(Unit) {
        val listener = object : App.ServiceStateListener {
            override fun onServiceStateChanged(s: XposedService?) { service = s }
        }
        App.addServiceStateListener(listener, true)
        onDispose { App.removeServiceStateListener(listener) }
    }

    // 首页共享的 LayerBackdrop：下方滚动内容作为源，顶栏消费它实现固定高斯模糊。
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    // 初始估计顶栏高度（状态栏 + 内容），首帧布局后立即修正，避免明显跳动
    var headerHeightPx by remember { mutableStateOf(with(density) { 96.dp.roundToPx() }) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // 滚动内容在底层，顶部留出固定顶栏空间
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = headerHeightDp)
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp)
                .padding(bottom = FloatingTabBarDefaults.Height + com.mars.mimarketpurify.Settings.floatingBarBottomMarginDp().dp),
        ) {
            MiuiXSearchBar(onClick = { context.startActivity(SearchActivity.intent(context)) })
            Spacer(Modifier.height(12.dp))

            StatusCard(service = service, night = activity.isNight())
            Spacer(Modifier.height(12.dp))

            if (showRecommendations) {
                SettingsSection(topLabel = "推荐功能（每 15 秒自动刷新）") {
                    recommendations.forEach { feature ->
                        SettingItem(
                            headlineText = feature.title,
                            supportingText = feature.summary,
                            onClick = {
                                activity.startActivity(
                                    SubSettingsActivity.intent(activity, feature.page, feature.key)
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SettingsSection {
                SwitchRow(title = "总开关", summary = "关闭后所有功能均不生效",
                    checked = masterOn.value, enabled = true) { on ->
                    masterOn.value = on; activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_MASTER, on)
                }
            }

            SettingsSection(topLabel = "界面设置") {
                SettingItem(headlineText = "广告净化", supportingText = adSummary,
                    onClick = { activity.openPage(SubSettingsActivity.PAGE_ADS) })
                SettingItem(headlineText = "底栏自定义", supportingText = tabsSummary,
                    onClick = { activity.openPage(SubSettingsActivity.PAGE_TABS) })
                SettingItem(headlineText = "悬浮底栏配置", supportingText = tabbarSummary,
                    onClick = { activity.openPage(SubSettingsActivity.PAGE_TAB_BAR) })
                SettingItem(headlineText = "「我的」页精简", supportingText = mineSummary,
                    onClick = { activity.openPage(SubSettingsActivity.PAGE_MINE) })
                SettingItem(headlineText = "其他界面精简", supportingText = miscSummary,
                    onClick = { activity.openPage(SubSettingsActivity.PAGE_MISC) })
            }

            SettingsSection(topLabel = "高级功能") {
                SwitchRow(title = "下载超级岛", summary = "强制让下载进度进入小米超级岛",
                    checked = activity.readLocal(com.mars.mimarketpurify.Settings.KEY_ISLAND, true),
                    enabled = masterOn.value) { activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_ISLAND, it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "细节修正", summary = "显示非正版 APP、被隐藏更新等细节处理",
                    checked = activity.readLocal(com.mars.mimarketpurify.Settings.KEY_MISC, true),
                    enabled = masterOn.value) { activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_MISC, it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "升级提醒弹窗", summary = "不再弹出应用商店的升级提醒对话框",
                    checked = activity.readLocal(com.mars.mimarketpurify.Settings.KEY_UPDATE_DIALOG, true),
                    enabled = masterOn.value) { activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_UPDATE_DIALOG, it) }
            }

            SettingsSection(topLabel = "模块功能") {
                SwitchRow(title = "隐藏桌面图标", summary = "仅移除桌面抽屉中的图标",
                    checked = activity.isLauncherIconHidden(), enabled = true) { activity.applyHideIcon(it) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "随机推荐", summary = "主页显示随机功能推荐（每 15 秒自动刷新）",
                    checked = recommendEnabled, enabled = masterOn.value) {
                    recommendEnabled = it
                    activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_RECOMMENDATIONS_ENABLED, it)
                }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                SwitchRow(title = "调试模式", summary = "开启后将统一日志输出",
                    checked = activity.readLocal(com.mars.mimarketpurify.Settings.KEY_RANK_DEBUG, false),
                    enabled = true) { activity.writeRemote(com.mars.mimarketpurify.Settings.KEY_RANK_DEBUG, it) }
            }

            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            Text(text = "Tips：开关实时生效，但还是建议重启应用商店",
                fontSize = MiuiX.MICRO.sp, color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp))
        }

        // 固定模糊顶栏：覆盖在滚动内容之上，消费同一 LayerBackdrop 实现实时高斯模糊
        BlurHeader(
            activity = activity,
            backdrop = backdrop,
            onRestartMarket = { restartMarket(context) },
            onHeightChanged = { headerHeightPx = it },
        )
    }
}

// ═══════════ 顶栏（含重启按钮） ═══════════

@Composable
private fun BlurHeader(
    activity: MainActivity,
    backdrop: LayerBackdrop?,
    onRestartMarket: () -> Unit,
    onHeightChanged: (Int) -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    var showRestartConfirm by remember { mutableStateOf(false) }
    AboutGlassCard(
        backdrop = backdrop,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeightChanged(it.size.height) },
    ) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = MiuiX.PAGE_H.dp, vertical = MiuiX.PAGE_H.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Mi Market Purify", style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(text = "小米应用商店净化与增强", style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp))
            }
            // 重启按钮：圆角小药丸
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainer)
                    .clickable { showRestartConfirm = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = "重启", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    onRestartMarket()
                }) { Text("重启", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text("取消", color = colors.onSurfaceVariantSummary)
                }
            },
            title = { Text("重启应用商店", color = colors.onSurface) },
            text = {
                Text(
                    "将强制停止并重新打开应用商店（优先通过 root 强杀，未保存状态会丢失）。是否继续？",
                    color = colors.onSurface,
                )
            },
        )
    }
}

@Composable
private fun MiuiXSearchBar(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(colors.surfaceContainer).clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text = "🔍", fontSize = 16.sp, color = colors.onSurfaceVariantSummary)
        Spacer(Modifier.width(10.dp))
        Text(text = "搜索功能…", style = MiuixTheme.textStyles.body1, color = colors.onSurfaceVariantSummary)
    }
}

@Composable
private fun RecommendRow(feature: FeatureRegistry.Feature, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .clickable { onClick() }.padding(vertical = MiuiX.ROW_PAD_V.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = feature.title, style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(text = feature.summary, style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp))
        }
        Text(text = "›", fontSize = 20.sp, color = colors.outline)
    }
}

@Composable
private fun StatusCard(service: XposedService?, night: Boolean) {
    val colors = MiuixTheme.colorScheme
    val titleText: String; val titleColor: Color; val bodyText: String; val bgColor: Color
    if (service == null) {
        titleText = "模块未激活"; titleColor = colors.onSurfaceVariantSummary
        bodyText = "请在 LSPosed 框架中启用本模块，并在作用域里勾选「应用商店」，然后重启应用商店。"
        bgColor = Color(MiuiX.neutralSoft(night))
    } else {
        val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
        titleText = "已激活 · ${service.frameworkName} ${service.frameworkVersion}"
        titleColor = Color(MiuiX.STATE_ACTIVE)
        bodyText = if (remote) "支持远程偏好：开关改动实时生效，一般无需重启应用商店。\n\n插件调试基于应用商店版本：4.126.xx"
            else "当前框架不支持远程偏好，开关可能不会立即生效，建议重启一次应用商店。"
        bgColor = Color(MiuiX.STATE_ACTIVE_SOFT)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MiuiX.CARD_PAD_H.dp, vertical = MiuiX.CARD_PAD_V.dp),
        ) {
            Text(text = titleText, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold, color = titleColor)
            Text(text = bodyText, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary,
                lineHeight = (MiuiX.ROW_SUMMARY * MiuiX.LINE_SPACING).sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
