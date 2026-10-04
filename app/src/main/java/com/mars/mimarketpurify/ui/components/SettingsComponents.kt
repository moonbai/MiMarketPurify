package com.mars.mimarketpurify.ui.components

import android.content.Context
import android.content.Intent
import kotlin.math.roundToInt
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
import com.mars.mimarketpurify.util.MarketRestarter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Dp
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import com.mars.mimarketpurify.util.floatingGlassSurface
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

// ==================== SettingItem（深色模式修复：显式设 onSurface 色） ====================

@Composable
fun SettingItem(
    headlineText: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val itemModifier = if (enabled && onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = itemModifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = headlineText,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,  // ← 修复：显式设色，深色模式下为白色
            )
            if (supportingText.isNotEmpty()) {
                Text(
                    text = supportingText,
                    color = colors.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        trailingContent?.invoke()
    }
}

// ==================== Section 容器 ====================

@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    topLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sectionShape = MaterialTheme.shapes.large.copy(CornerSize(24.dp))
    val container = MiuixTheme.colorScheme.surfaceContainer
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        topLabel?.let {
            Text(
                text = it,
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
                // 左内边距与卡片内行内容(20dp)一致，使分区标题与各功能标题左边缘对齐
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Surface(
            shape = sectionShape,
            color = container,
        ) { Column(modifier = Modifier.fillMaxWidth(), content = content) }
    }
}

// ==================== 兼容旧接口：卡片容器 ====================

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
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 16.dp),
    )
}

// ==================== 子页顶栏 ====================


@Composable
fun SubTopBar(title: String, onBack: () -> Unit, showBack: Boolean = true) {
    val context = LocalContext.current
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
                style = MiuixTheme.textStyles.title3,
                color = colors.onSurface,
            )
            Spacer(Modifier.weight(1f))
            // 重启按钮：浅色底胶囊，点击直接重启（取消二次确认）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable { MarketRestarter.restart(context) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "重启商店",
                    fontSize = 13.sp,
                    color = colors.primary,
                )
            }
        }
        HorizontalDivider(color = colors.dividerLine, thickness = 1.dp)
    }
}



// ==================== 开关行（拇指色恢复白色，通用性最佳） ====================

@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var isChecked by remember(checked) { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { isChecked = !isChecked; onCheckedChange(isChecked) }
            .padding(horizontal = 20.dp, vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = if (enabled) colors.onSurfaceVariantSummary else colors.outline,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it; onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,       // 白色拇指：浅色轨道/深色轨道上都清晰
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(MiuiX.SWITCH_TRACK_OFF),
            ),
        )
    }
}

// ==================== 勾选行 ====================

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
                style = MiuixTheme.textStyles.body1,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (enabled) colors.onSurfaceVariantSummary else colors.outline,
                    modifier = Modifier.padding(top = 3.dp),
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
            .padding(horizontal = 20.dp, vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = if (enabled) colors.onSurfaceVariantSummary else colors.outline,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(text = value, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary)
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
                    style = MiuixTheme.textStyles.body1,
                    color = if (enabled) colors.onSurface else colors.outline,
                )
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (enabled) colors.onSurfaceVariantSummary else colors.outline,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = format(value),
                style = MiuixTheme.textStyles.body2,
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

// ==================== 偏好绑定：颜色选择 ====================

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
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
            )
            Text(
                text = "点击后滑动颜色条选择颜色及透明度",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp),
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
                }) { Text("恢复默认", color = colors.onSurfaceVariantSummary) }
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

// ==================== 关于页内容 ====================

private data class RefProject(val repoName: String, val url: String, val label: String)

@Composable
fun AboutContent(activity: ComponentActivity, onBack: () -> Unit, floatingBarInset: Dp = 0.dp) {
    val colors = MiuixTheme.colorScheme
    var updateInfo by remember { mutableStateOf<UpdateCheckResult.Available?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val scrollState = rememberScrollState()

    // 系统返回键：关于页内统一返回（独立 Activity 关闭自身；主页内回到首页标签）
    BackHandler(onBack = onBack)

    val doCheckUpdate: () -> Unit = {
        Thread {
            val result = UpdateChecker.check()
            activity.runOnUiThread {
                when (result) {
                    is UpdateCheckResult.Available -> { updateInfo = result; showUpdate = true }
                    is UpdateCheckResult.Latest ->
                        Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show()
                    is UpdateCheckResult.Unavailable ->
                        Toast.makeText(activity, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // LayerBackdrop 捕获「动态光晕背景」；内联「检查更新」按钮消费它，实现真实的高斯模糊。
    val backdrop = rememberLayerBackdrop()

    // 「向上滑动」进度 0→1（滚动 320px 封顶）：动态光晕背景随之上滑淡出。
    val scrollProgress by remember { derivedStateOf { (scrollState.value / 320f).coerceIn(0f, 1f) } }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        // MiuiX 风格「高斯模糊动态背景」：主题色柔和光晕，缓慢漂移；上滑时淡出。
        // 该层被 layerBackdrop 捕获，供内联「检查更新」毛玻璃按钮做真实高斯模糊采样。
        AboutFloatingBackground(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            alpha = 1f - scrollProgress,
        )

        // 首屏留白说明：
        //  - heroTopGap：图标距页面顶部的留白（首屏大留白、近似垂直居中）
        //  - heroBottomGap：检查更新按钮距下方「功能」区块的留白（随屏幕高度缩放）
        //  - 三者配合让 Hero 居上、检查更新按钮内联其下方，滚动后整体自然上移露出后续区块
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val heroTopGap = screenHeight * 0.28f
        val heroBottomGap = screenHeight * 0.10f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = MiuiX.PAGE_H.dp, vertical = 8.dp)
                .padding(bottom = FloatingTabBarDefaults.Height + floatingBarInset + 16.dp),
        ) {
            Spacer(Modifier.height(heroTopGap))
            // 顶部 Hero：图标/版本/描述（无背景卡片、无背景模糊、无文字阴影）
            AboutHeroHeader(activity = activity)
            // Hero 与「检查更新」按钮之间的间距；本轮在上一版(20dp)基础上再下移一些
            Spacer(Modifier.height(40.dp))
            // 检查更新：长条圆角矩形毛玻璃按钮，内联在 Hero 正下方
            AboutUpdateBar(
                activity = activity,
                onClick = doCheckUpdate,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            )
            // 按钮与下方「功能」区块之间的间距（随屏幕高度缩放）
            Spacer(Modifier.height(heroBottomGap))

            SettingsSection(topLabel = "功能") {
                val features = listOf(
                    "广告净化" to "开屏、首页信息流、搜索、升级/下载页、详情页、榜单广告、领水果入口、活动入口",
                    "界面精简" to "「我的」页推荐/清理/安全检测/个人信息、详情页精选、底栏角标、升级记录、搜索也在看",
                    "功能增强" to "下载超级岛、非正版APP显示、被隐藏更新显示、升级弹窗拦截、悬浮底栏",
                )
                features.forEach { (title, desc) ->
                    SettingItem(headlineText = title, supportingText = desc)
                }
            }

            SettingsSection(topLabel = "作者") {
                SettingItem(
                    headlineText = "Mars",
                    supportingText = "点此访问作者主页，点点关注",
                    onClick = { openLink(activity, "https://weibo.com/u/3963594403") },
                    trailingContent = {
                        SafeDrawableImage(
                            resId = R.drawable.avatar_mars,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
            }

            SettingsSection(topLabel = "参考项目") {
                val references = listOf(
                    RefProject("callng/NewFuckMarketAds", "https://github.com/callng/NewFuckMarketAds", "GPL-3.0"),
                    RefProject("lisrain/NewFuckMarketAds_Fork", "https://github.com/lisrain/NewFuckMarketAds_Fork", "GPL-3.0"),
                    RefProject("HowieHChen/XiaomiHelper", "https://github.com/HowieHChen/XiaomiHelper", "GPL-3.0"),
                    RefProject("AritxOnly/HyperModifier", "https://github.com/AritxOnly/HyperModifier", "GPL-3.0"),
                )
                references.forEach { item ->
                    SettingItem(
                        headlineText = item.repoName,
                        supportingText = item.label,
                        onClick = { openLink(activity, item.url) },
                    )
                }
            }

            SettingsSection(topLabel = "其他") {
                SettingItem(
                    headlineText = "隐私政策",
                    supportingText = "查看本模块隐私政策与数据说明",
                    onClick = { activity.startActivity(Intent(activity, PrivacyPolicyActivity::class.java)) },
                )
            }

            Footer("不乱拉屎的应用商店才是好的应用商店@Mars")
        }

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
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = buildString {
                                append("发现新版本 v${info.versionName}")
                                if (sizeText.isNotEmpty()) append("\n$sizeText")
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceVariantSummary,
                            lineHeight = (14 * 1.4).sp,
                        )
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
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
                    }) { Text(text = "去发布页", color = colors.onSurfaceVariantSummary) }
                },
            )
        }

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
                            style = MiuixTheme.textStyles.body1,
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
                                style = MiuixTheme.textStyles.body2,
                                color = colors.onSurfaceVariantSummary,
                            )
                        } else {
                            CircularProgressIndicator(progress = null)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "正在获取下载信息…",
                                style = MiuixTheme.textStyles.body2,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                    }
                },
            )
        }
    }
}

// ==================== 关于页：模糊顶栏（Hero Header） ====================

/**
 * 通用毛玻璃容器：从父组件传入 [LayerBackdrop]，对页面内容做高斯模糊采样。
 * 参考 HyperModifier 的 SoftGlassSurface / DeadlinerMiuixScaffold，使用 Miuix KMP 自带的
 * [rememberLayerBackdrop] + [floatingGlassSurface] 替代原 ViewBackdropSampler，避免整窗
 * PixelCopy，同时保证关于页 Hero 与底栏、首页顶栏都有真实模糊。
 */
@Composable
internal fun AboutGlassCard(
    backdrop: LayerBackdrop?,
    shape: Shape,
    modifier: Modifier,
    tint: Color? = null,
    border: Brush? = null,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassTint = tint ?: MiuixTheme.colorScheme.surfaceContainer.copy(alpha = FloatingTabBarDefaults.GlassTintAlpha)
    Box(
        modifier = modifier
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.20f),
                    )
                } else {
                    Modifier
                },
            )
            .floatingGlassSurface(backdrop = backdrop, shape = shape, tint = glassTint)
            .then(if (border != null) Modifier.border(width = 1.dp, brush = border, shape = shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .clip(shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 关于页顶部头图：无背景卡片（图标/版本/描述直接落在页面底色上）、居中排布，
 * 无背景模糊、无文字阴影；点击跳转到仓库。
 */
@Composable
private fun AboutHeroHeader(activity: ComponentActivity) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openLink(activity, MiuiX.REPO_URL) }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SafeDrawableImage(
            resId = R.mipmap.ic_launcher,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = "Mi Market Purify",
            style = MiuixTheme.textStyles.title2,
            textAlign = TextAlign.Center,
            color = colors.onSurface,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary,
        )
        Text(
            text = "小米应用商店净化与增强",
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

// ==================== 关于页：MiuiX 风格动态光晕背景 ====================

/**
 * 「高斯模糊动态背景」：以主题色派生的柔和高光色，绘制 3 个缓慢漂移的径向光晕铺满整页，
 * 作为关于页底图（参考 HyperModifier 的 AboutFloatingBackground / Deadliner 浮动光晕）。
 *
 * 该层被 [layerBackdrop] 捕获，底部「检查更新」毛玻璃按钮据此做真实高斯模糊采样；
 * [alpha] 随滚动进度衰减（上滑淡出），让内容区回归纯净底色。
 */
@Composable
private fun AboutFloatingBackground(modifier: Modifier = Modifier, alpha: Float) {
    val transition = rememberInfiniteTransition(label = "aboutFloatingBackground")
    val horizontalOffset by transition.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(7_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "aboutBgHorizontalOffset",
    )
    val verticalOffset by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = -0.08f,
        animationSpec = infiniteRepeatable(tween(5_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "aboutBgVerticalOffset",
    )
    val accentOffset by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(tween(6_400, easing = LinearEasing), RepeatMode.Reverse),
        label = "aboutBgAccentOffset",
    )
    val scheme = MiuixTheme.colorScheme
    val firstColor = vividGlowColor(scheme.primary, 0f)
    val secondColor = vividGlowColor(scheme.primary, 42f)
    val thirdColor = vividGlowColor(scheme.primary, -48f)
    val surfaceColor = scheme.background

    Canvas(modifier) {
        drawRect(surfaceColor)
        if (alpha <= 0f) return@Canvas
        val radius = maxOf(size.width, size.height) * 0.9f
        fun center(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        val first = center(0.18f + horizontalOffset, 0.22f + verticalOffset)
        val second = center(0.86f - horizontalOffset, 0.66f - verticalOffset)
        val third = center(0.52f + accentOffset, 0.96f - accentOffset)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(firstColor.copy(alpha = 0.22f * alpha), Color.Transparent),
                center = first,
                radius = radius,
            ),
            radius = radius,
            center = first,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondColor.copy(alpha = 0.18f * alpha), Color.Transparent),
                center = second,
                radius = radius * 0.86f,
            ),
            radius = radius * 0.86f,
            center = second,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(thirdColor.copy(alpha = 0.16f * alpha), Color.Transparent),
                center = third,
                radius = radius * 0.7f,
            ),
            radius = radius * 0.72f,
            center = third,
        )
    }
}

/** 由主题色派生高饱和、明亮度适中的高光色；[hueShift] 生成邻近色相，避免多色光晕发灰。 */
private fun vividGlowColor(color: Color, hueShift: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * 1.55f).coerceIn(0.56f, 0.92f)
    hsl[2] = if (hsl[2] < 0.5f) {
        (hsl[2] + 0.14f).coerceAtMost(0.68f)
    } else {
        (hsl[2] - 0.06f).coerceAtLeast(0.36f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

// ==================== 关于页：检查更新按钮（长条毛玻璃） ====================

/**
 * 关于页「检查更新」长条圆角矩形毛玻璃按钮，内联在 Hero 正下方：
 * 文字主色高亮，点击触发检查更新。毛玻璃不可用时回退半透明纯色。
 */
@Composable
private fun AboutUpdateBar(
    activity: ComponentActivity,
    onClick: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f
    // 长条圆角矩形（半径小于半高，区别于全圆角胶囊）
    val barShape = RoundedCornerShape(20.dp)
    // 毛玻璃着色：亮色近白、暗色用 surfaceContainer，保证在动态光晕上清晰可见
    val glassTint = if (isDark) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
    } else {
        Color.White.copy(alpha = 0.58f)
    }
    // 边缘高光：顶部亮、底部弱，模拟玻璃反光
    val edgeBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.22f else 0.72f),
            Color.White.copy(alpha = if (isDark) 0.05f else 0.14f),
        ),
    )
    AboutGlassCard(
        backdrop = backdrop,
        shape = barShape,
        tint = glassTint,
        border = edgeBrush,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
    ) {
        Text(
            text = "检查更新",
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

private fun openLink(activity: ComponentActivity, url: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

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

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.mars.mimarketpurify.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "无法调起安装，已转去发布页", Toast.LENGTH_LONG).show()
        openLink(context as ComponentActivity, "https://github.com/moonbai/MiMarketPurify/releases/latest")
    }
}
