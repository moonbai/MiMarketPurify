package com.mars.mimarketpurify.ui.components

import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.BuildConfig
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.R
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.SettingsBaseActivity
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局共享的 Compose 构件与页面片段。
 *
 * 把原先散落在 [com.mars.mimarketpurify.ui.MainScreen]（主页）与
 * [com.mars.mimarketpurify.SubSettingsActivity] / [com.mars.mimarketpurify.AboutActivity]
 * （原生 View 实现）里的「卡片 / 开关行 / 导航行 / 区块标题 / 顶栏」统一收口到这里，
 * 主页、二级设置页、关于页共用同一套，从根上消除「两套 UI 语汇」的重复，
 * 也确保配色全部来自 [MiuixTheme.colorScheme]，不再出现字面色被强制深色反色的问题。
 */

// ==================== 卡片容器 ====================

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(horizontal = MiuiX.ROW_PAD_H.dp, vertical = MiuiX.ROW_PAD_V.dp),
    ) { content() }
}

// ==================== 区块标题 ====================

@Composable
fun SectionHeader(title: String, subtitle: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(top = MiuiX.SECTION_TOP.dp, bottom = 6.dp)) {
        Text(
            text = title,
            fontSize = MiuiX.SECTION.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceSecondary,
        )
        Text(
            text = subtitle,
            fontSize = MiuiX.MICRO.sp,
            color = colors.outline,
            modifier = Modifier.padding(start = 4.dp, top = 0.dp),
        )
    }
}

// ==================== 提示脚注 ====================

@Composable
fun Footer(text: String) {
    Text(
        text = text,
        fontSize = MiuiX.MICRO.sp,
        color = MiuixTheme.colorScheme.outline,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
    )
}

// ==================== 子页顶栏（返回 + 标题） ====================

@Composable
fun SubTopBar(title: String, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = MiuiX.PAGE_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }
        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)
    }
}

// ==================== 开关行 ====================

@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 内部持有显示态：手动拨动后保持视觉状态；`checked` 变化（如 service 重连重读）时重新对齐。
    var isChecked by remember(checked) { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { isChecked = !isChecked; onCheckedChange(isChecked) }
            .padding(vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                text = summary,
                fontSize = MiuiX.ROW_SUMMARY.sp,
                color = if (enabled) colors.onSurfaceSecondary else colors.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it; onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(MiuiX.SWITCH_TRACK_OFF),
            ),
        )
    }
}

// ==================== 勾选行（底部标签筛选） ====================

@Composable
fun CheckboxRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = MiuiX.ROW_PAD_H.dp, vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    fontSize = MiuiX.ROW_SUMMARY.sp,
                    color = if (enabled) colors.onSurfaceSecondary else colors.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.primary,
                uncheckedColor = colors.outline,
            ),
        )
    }
}

// ==================== 导航入口行 ====================

@Composable
fun NavRow(
    title: String,
    summary: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                text = summary,
                fontSize = MiuiX.ROW_SUMMARY.sp,
                color = if (enabled) colors.onSurfaceSecondary else colors.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(text = value, fontSize = MiuiX.CAPTION.sp, color = colors.onSurfaceSecondary)
        Spacer(Modifier.width(6.dp))
        Text(text = "›", fontSize = 20.sp, color = colors.outline, modifier = Modifier.size(20.dp, 20.dp))
    }
}

// ==================== 偏好绑定：开关 ====================

@Composable
fun PrefSwitch(
    activity: SettingsBaseActivity,
    key: String,
    title: String,
    summary: String,
    default: Boolean = true,
    enabled: Boolean = true,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val tick by activity.refreshSignal
    var checked by remember(tick) { mutableStateOf(activity.readLocal(key, default)) }
    SwitchRow(title, summary, checked, enabled) { on ->
        checked = on
        activity.writeRemote(key, on)
        onChanged?.invoke(on)
    }
}

// ==================== 偏好绑定：数值滑杆 ====================

@Composable
fun PrefSlider(
    activity: SettingsBaseActivity,
    key: String,
    title: String,
    summary: String,
    min: Int,
    max: Int,
    default: Int,
    enabled: Boolean = true,
    format: (Int) -> String,
) {
    val tick by activity.refreshSignal
    var value by remember(tick) { mutableStateOf(activity.readLocalInt(key, default)) }
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = MiuiX.ROW_PAD_H.dp, vertical = MiuiX.ROW_PAD_V.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = MiuiX.ROW_TITLE.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) colors.onSurface else colors.outline,
                )
                Text(
                    text = summary,
                    fontSize = MiuiX.ROW_SUMMARY.sp,
                    color = if (enabled) colors.onSurfaceSecondary else colors.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = format(value),
                fontSize = MiuiX.CAPTION.sp,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { value = it.toInt() },
            onValueChangeFinished = { activity.writeRemoteInt(key, value) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.outline,
            ),
        )
    }
}

// ==================== 偏好绑定：颜色选择（miuix ColorPicker） ====================

@Composable
fun PrefColorRow(
    activity: SettingsBaseActivity,
    title: String,
    key: String,
    defaultColor: Int,
) {
    val tick by activity.refreshSignal
    var stored by remember(tick) { mutableStateOf(activity.readLocalInt(key, -1)) }
    val shown = if (stored == -1) defaultColor else stored
    var pickerColor by remember { mutableStateOf(shown) }
    var open by remember { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(horizontal = MiuiX.ROW_PAD_H.dp, vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = MiuiX.ROW_TITLE.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = "点击选择颜色（支持 #AARRGGBB 带透明度）",
                fontSize = MiuiX.ROW_SUMMARY.sp,
                color = colors.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(shown)),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    activity.writeRemoteInt(key, pickerColor)
                    open = false
                }) { Text("确定", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    activity.writeRemoteInt(key, -1)
                    open = false
                }) { Text("恢复默认", color = colors.onSurfaceSecondary) }
            },
            title = { Text("选择颜色", color = colors.onSurface) },
            text = {
                ColorPicker(
                    color = Color(pickerColor),
                    onColorChanged = { newColor -> pickerColor = newColor.toArgb() },
                )
            },
        )
    }
}

// ==================== 关于页内容（主页「关于」标签页与独立 AboutActivity 共用） ====================

private data class RefProject(val repoName: String, val url: String, val label: String)

@Composable
fun AboutContent(activity: ComponentActivity, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = "关于", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp),
        ) {
            // 应用卡片
            GroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openLink(activity, MiuiX.REPO_URL) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(
                            text = "Mi Market Purify",
                            fontSize = MiuiX.ROW_TITLE.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            fontSize = MiuiX.ROW_SUMMARY.sp,
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            text = "小米应用商店净化与增强",
                            fontSize = MiuiX.MICRO.sp,
                            color = colors.outline,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(text = "›", fontSize = 20.sp, color = colors.outline)
                }
            }

            SectionHeader("功能", "本模块提供的核心能力")
            val features = listOf(
                "广告净化" to "开屏、首页信息流、搜索、升级/下载页、详情页、榜单广告、领水果入口、活动入口",
                "界面精简" to "「我的」页推荐/清理/安全检测/个人信息、详情页精选、底栏角标、升级记录、搜索也在看",
                "功能增强" to "下载超级岛、非正版APP显示、被隐藏更新显示、升级弹窗拦截、悬浮底栏",
            )
            features.forEachIndexed { index, (title, desc) ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                GroupCard {
                    Text(
                        text = title,
                        fontSize = MiuiX.ROW_TITLE.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        text = desc,
                        fontSize = MiuiX.ROW_SUMMARY.sp,
                        color = colors.onSurfaceSecondary,
                        lineHeight = (MiuiX.ROW_SUMMARY * MiuiX.LINE_SPACING).sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            SectionHeader("作者", "")
            val authorCard = GroupCardState("Mars", "点此访问作者主页，点点关注")
            GroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openLink(activity, "https://weibo.com/u/3963594403") }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.avatar_mars),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                        Text(
                            text = authorCard.title,
                            fontSize = MiuiX.ROW_TITLE.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Text(
                            text = authorCard.subtitle,
                            fontSize = MiuiX.ROW_SUMMARY.sp,
                            color = colors.onSurfaceSecondary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(text = "›", fontSize = 20.sp, color = colors.outline)
                }
            }

            SectionHeader("参考项目", "")
            val references = listOf(
                RefProject("callng/NewFuckMarketAds", "https://github.com/callng/NewFuckMarketAds", "GPL-3.0"),
                RefProject("lisrain/NewFuckMarketAds_Fork", "https://github.com/lisrain/NewFuckMarketAds_Fork", "GPL-3.0"),
                RefProject("HowieHChen/XiaomiHelper", "https://github.com/HowieHChen/XiaomiHelper", "GPL-3.0"),
                RefProject("AritxOnly/HyperModifier", "https://github.com/AritxOnly/HyperModifier", "GPL-3.0"),
            )
            GroupCard {
                references.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openLink(activity, item.url) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.repoName,
                                fontSize = MiuiX.ROW_SUMMARY.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                            Text(
                                text = item.label,
                                fontSize = MiuiX.MICRO.sp,
                                color = colors.onSurfaceSecondary,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(text = "›", fontSize = 20.sp, color = colors.outline)
                    }
                    if (index < references.lastIndex) {
                        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)
                    }
                }
            }

            Footer("不乱拉屎的应用商店才是好的应用商店@Mars")
        }
    }
}

private data class GroupCardState(val title: String, val subtitle: String)

private fun openLink(activity: ComponentActivity, url: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
