package com.mars.mimarketpurify.ui.components

import android.content.Context
import android.content.Intent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import com.mars.mimarketpurify.util.UpdateChecker
import com.mars.mimarketpurify.util.UpdateCheckResult
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.BuildConfig
import com.mars.mimarketpurify.MiuiX
import com.mars.mimarketpurify.R
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.SettingsBaseActivity
import com.mars.mimarketpurify.util.FloatingTabBarDefaults
import com.mars.mimarketpurify.PrivacyPolicyActivity
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
            color = colors.onSurface,
        )
        Text(
            text = subtitle,
            fontSize = MiuiX.MICRO.sp,
            color = colors.onSurfaceSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

/**
 * 安全加载 drawable 为 Image：用 [ContextCompat] 取 Drawable 后转 [BitmapPainter]，
 * 避免 [androidx.compose.ui.res.painterResource] 在 release(R8) 下对自适应启动图标 /
 * 缺失资源直接抛异常、导致整页 Compose 崩溃（点击「关于」闪退的根因之一）。
 * 加载失败时回退为占位方块，而不是让整页崩溃。
 */
@Composable
private fun SafeDrawableImage(
    resId: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val painter = remember(resId) {
        runCatching {
            ContextCompat.getDrawable(context, resId)
                ?.toBitmap()
                ?.asImageBitmap()
                ?.let { BitmapPainter(it) }
        }.getOrNull()
    }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Color(0xFFD0D0D0).copy(alpha = 0.25f)))
    }
}

// ==================== 提示脚注 ====================

@Composable
fun Footer(text: String) {
    Text(
        text = text,
        fontSize = MiuiX.MICRO.sp,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
    )
}

// ==================== 子页顶栏（返回 + 标题） ====================

/**
 * 子页顶栏。`showBack` 控制是否显示左侧返回按钮：
 * - 二级设置页默认显示（[SubSettingsActivity] 需要返回上一级）；
 * - 关于页传 `false`，只保留标题，与主页顶栏（无返回键）保持一致。
 */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit, showBack: Boolean = true) {
    val colors = MiuixTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
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
            }
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
                text = "点击后滑动颜色条选择颜色及透明度",
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
                    stored = pickerColor
                    open = false
                }) { Text("确定", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    activity.writeRemoteInt(key, -1)
                    stored = -1
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
    // 检查更新 / 下载状态（问题1：MiuiX 风格弹窗；问题3：下载进度）。
    // 用 Material3 AlertDialog（在 MiuixTheme 下即 MiuiX 风格）+ Miuix 进度条，替代原生 AlertDialog / ProgressDialog。
    var updateInfo by remember { mutableStateOf<UpdateCheckResult.Available?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    // 0f..1f 表示确定进度；-1f 表示未知总长（服务器未回 Content-Length），退化为 indeterminate。
    var downloadProgress by remember { mutableStateOf(0f) }
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        SubTopBar(title = "关于", showBack = false, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
                // 与主页一致：在滚动内容里为悬浮底栏预留高度，避免底部出现空白栏、末项被遮。
                .padding(bottom = FloatingTabBarDefaults.Height + Settings.floatingBarBottomMarginDp().dp),
        ) {
            // ── 应用卡片（居中大图标 + 标题 + 版本，无背景卡片色，参考 miuix/HyperModifier 关于页风格）──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { openLink(activity, MiuiX.REPO_URL) }
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SafeDrawableImage(
                    resId = R.mipmap.ic_launcher,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = "Mi Market Purify",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.onSurface,
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 14.sp,
                    color = colors.onSurfaceSecondary,
                )
                Text(
                    text = "小米应用商店净化与增强",
                    fontSize = 12.sp,
                    color = colors.onSurfaceSecondary,
                )
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openLink(activity, "https://weibo.com/u/3963594403") }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SafeDrawableImage(
                        resId = R.drawable.avatar_mars,
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
                            .clip(RoundedCornerShape(12.dp))
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

            SectionHeader("其他", "")

            GroupCard {
                NavRow(
                    title = "检查更新",
                    summary = "对比 GitHub 最新 Release 版本",
                    value = "",
                    enabled = true,
                ) {
                    Thread {
                        val result = UpdateChecker.check()
                        activity.runOnUiThread {
                            when (result) {
                                is UpdateCheckResult.Available -> {
                                    updateInfo = result
                                    showUpdate = true
                                }
                                is UpdateCheckResult.Latest ->
                                    Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show()
                                is UpdateCheckResult.Unavailable ->
                                    Toast.makeText(activity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }
                Spacer(Modifier.height(MiuiX.ROW_GAP.dp))
                NavRow(
                    title = "隐私政策",
                    summary = "查看本模块隐私政策与数据说明",
                    value = "",
                    enabled = true,
                ) {
                    activity.startActivity(Intent(activity, PrivacyPolicyActivity::class.java))
                }
            }

            Footer("不乱拉屎的应用商店才是好的应用商店@Mars")
        }

        // ── 检查更新结果弹窗（MiuiX 风格；问题1）──
        if (showUpdate && updateInfo != null) {
            val info = updateInfo!!
            AlertDialog(
                onDismissRequest = { showUpdate = false },
                title = { Text(text = "模块更新", color = colors.onSurface) },
                text = {
                    val sizeText = if (info.sizeBytes > 0) {
                        "大小：%.1f MB".format(info.sizeBytes / 1048576.0)
                    } else ""
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 说明可能很长（含多张截图）：限高 + 纵向滚动，避免图片把弹窗撑出屏幕、
                            // 底部「下载并安装 / 去发布页」按钮被顶掉。
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = buildString {
                                append("发现新版本 v${info.versionName}")
                                if (sizeText.isNotEmpty()) append("\n$sizeText")
                            },
                            fontSize = 14.sp,
                            color = colors.onSurfaceSecondary,
                            lineHeight = (14 * 1.4).sp,
                        )
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            // GitHub Release 说明常内联图片（`<img src="..."/>`），旧实现把整段 body
                            // 当纯文本，导致图片标签原样显示成「代码」；这里解析为「文本 / 图片」两类块，
                            // 文本块做轻量 Markdown 清理，图片块用内置下载器直接显示（点击可查看原图）。
                            ReleaseNotesView(
                                notes = info.notes,
                                onImageClick = { openLink(activity, it) },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdate = false
                        downloading = true
                        downloadProgress = 0f
                        startDownload(
                            activity = activity,
                            info = info,
                            onProgress = { d, t ->
                                activity.runOnUiThread {
                                    // 未知总长时退化为 indeterminate（-1f），否则按字节比计算百分比。
                                    downloadProgress = if (t > 0) (d.toFloat() / t).coerceIn(0f, 1f) else -1f
                                }
                            },
                            onDone = { file ->
                                activity.runOnUiThread {
                                    downloading = false
                                    installApk(activity, file)
                                }
                            },
                            onError = { e ->
                                activity.runOnUiThread {
                                    downloading = false
                                    Toast.makeText(activity, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }) { Text(text = "下载并安装", color = colors.primary) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUpdate = false
                        openLink(activity, info.releaseUrl)
                    }) { Text(text = "去发布页", color = colors.onSurfaceSecondary) }
                },
            )
        }

        // ── 下载进度弹窗（MiuiX 风格；问题3）──
        if (downloading) {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "正在下载更新包…",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(18.dp))
                        if (downloadProgress >= 0f) {
                            CircularProgressIndicator(progress = downloadProgress)
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                fontSize = 14.sp,
                                color = colors.onSurfaceSecondary,
                            )
                        } else {
                            CircularProgressIndicator(progress = null)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "正在获取下载信息…",
                                fontSize = 14.sp,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * 检查更新的发起逻辑已内联到 [AboutContent]：点击「检查更新」于子线程调用 [UpdateChecker.check]，
 * 结果写入 Compose 状态并以 MiuiX 风格弹窗呈现（不再用原生 AlertDialog）。
 */

private data class GroupCardState(val title: String, val subtitle: String)

private fun openLink(activity: ComponentActivity, url: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * 后台下载 APK：进度经 [onProgress](字节) 回调，完成经 [onDone]、失败经 [onError] 回主线程，
 * 由调用方（[AboutContent]）驱动 MiuiX 进度弹窗与安装调起。
 */
private fun startDownload(
    activity: ComponentActivity,
    info: UpdateCheckResult.Available,
    onProgress: (downloaded: Long, total: Long) -> Unit,
    onDone: (File) -> Unit,
    onError: (Exception) -> Unit,
) {
    Thread {
        try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
            dir.mkdirs()
            val file = File(dir, info.apkName.ifBlank { "MiMarketPurify_update.apk" })
            downloadFile(info.apkUrl, file, onProgress)
            onDone(file)
        } catch (e: Exception) {
            onError(e)
        }
    }.start()
}

/** 后台把 APK 下到本地；下载进度经 [onProgress](downloaded, total，单位字节) 回调。 */
private fun downloadFile(
    url: String,
    dest: File,
    onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
        // contentLengthLong 为 -1 表示服务器未回 Content-Length（分块传输），此时无法计算百分比，
        // 由上层按 total<=0 退化为 indeterminate 进度条。
        val total = conn.contentLengthLong.takeIf { it >= 0 } ?: 0L
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                var downloaded = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    } finally {
        conn.disconnect()
    }
}

/** 用 FileProvider 暴露 APK 并调起安装（Android 7+ 禁止 file://，必须走 content://）。 */
private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.mars.mimarketpurify.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        // 安装权限未授予或被拒：退回发布页，让用户手动获取
        Toast.makeText(context, "无法调起安装，已转去发布页", Toast.LENGTH_LONG).show()
        openLink(context as ComponentActivity, "https://github.com/moonbai/MiMarketPurify/releases/latest")
    }
}
