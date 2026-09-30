package com.mars.mimarketpurify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import com.mars.mimarketpurify.MainActivity
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.SubSettingsActivity
import com.mars.mimarketpurify.isNight
import com.mars.mimarketpurify.ui.components.GroupCard
import com.mars.mimarketpurify.ui.components.NavRow
import com.mars.mimarketpurify.ui.components.SectionHeader
import com.mars.mimarketpurify.ui.components.SwitchRow
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主页内容（整页 Compose）。视觉与交互对齐原原生布局：固定顶栏 + 滚动内容区、
 * 分组卡片、标题/摘要/开关整行可点；配色全部取自 [MiuixTheme.colorScheme]，与悬浮底栏同源。
 * 远程偏好读写、隐藏桌面图标等逻辑复用 [MainActivity] / [SettingsBaseActivity] 的方法。
 *
 * 卡片 / 开关行 / 导航行 / 区块标题等构件与二级设置页、关于页共用
 * [com.mars.mimarketpurify.ui.components] 里的同一套实现，避免重复。
 */
@Composable
fun MainScreen(activity: MainActivity, onOpenAbout: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val service = rememberServiceState()
    val masterOn = remember { mutableStateOf(activity.readLocal(Settings.KEY_MASTER, true)) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        MainHeader(activity = activity, onOpenAbout = onOpenAbout, modifier = Modifier.statusBarsPadding())

        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 6.dp),
        ) {
            StatusCard(service = service, night = activity.isNight())
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

            SectionHeader("界面设置", "广告与界面内容清理")
            GroupCard {
                NavRow(
                    title = "广告净化",
                    summary = "开屏、首页信息流、搜索、下载升级、应用详情等一系列广告",
                    value = activity.countText(activity.adKeys),
                    enabled = masterOn.value,
                ) { activity.openPage(SubSettingsActivity.PAGE_ADS) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "底栏自定义",
                    summary = "底部标签筛选",
                    value = activity.tabsText(),
                    enabled = masterOn.value,
                ) { activity.openPage(SubSettingsActivity.PAGE_TABS) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "悬浮底栏配置",
                    summary = "底栏颜色、透明度、显示效果参数",
                    value = activity.tabbarText(),
                    enabled = masterOn.value,
                ) { activity.openPage(SubSettingsActivity.PAGE_TAB_BAR) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "「我的」页精简",
                    summary = "我的页应用推荐、官方入口、清理板块",
                    value = activity.countText(activity.mineKeys),
                    enabled = masterOn.value,
                ) { activity.openPage(SubSettingsActivity.PAGE_MINE) }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "其他界面精简",
                    summary = "升级记录、搜索相关推荐等零散页面",
                    value = activity.countText(activity.miscKeys),
                    enabled = masterOn.value,
                ) { activity.openPage(SubSettingsActivity.PAGE_MISC) }
            }

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
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "检查更新",
                    summary = "对比 GitHub 最新 Release 版本",
                    value = "",
                    enabled = true,
                ) { activity.checkForUpdates() }
            }

            Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
            Text(
                text = "Tips：开关实时生效，但还是建议重启应用商店",
                fontSize = MiuiX.MICRO.sp,
                color = colors.outline,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
            )
        }
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
private fun MainHeader(activity: MainActivity, onOpenAbout: () -> Unit, modifier: Modifier = Modifier) {
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(MiuiX.primarySoft(activity.isNight())))
                .clickable { onOpenAbout() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "关于",
                fontSize = MiuiX.CAPTION.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
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
