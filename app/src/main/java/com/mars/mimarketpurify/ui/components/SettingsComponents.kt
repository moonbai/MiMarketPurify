package com.mars.mimarketpurify.ui.components

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
            // 应用卡片
            GroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openLink(activity, MiuiX.REPO_URL) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SafeDrawableImage(
                        resId = R.mipmap.ic_launcher,
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
                            color = colors.onSurfaceSecondary,
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
                ) { checkForUpdatesManual(activity) }
            }

            Footer("不乱拉屎的应用商店才是好的应用商店@Mars")
        }
    }
}

/**
 * 手动检查更新：子线程请求 GitHub Releases，结果回主线程以 Toast 提示；
 * 发现新版本则直接打开发布页。插件主页与关于页共用（原在 [MainActivity]，现统一收口于此）。
 */
private fun checkForUpdatesManual(context: Context) {
    val activity = context as? ComponentActivity ?: return
    Thread {
        val result = UpdateChecker.check()
        activity.runOnUiThread {
            when (result) {
                is UpdateCheckResult.Available -> showUpdateDialog(activity, result)

                is UpdateCheckResult.Latest ->
                    Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()

                is UpdateCheckResult.Unavailable ->
                    Toast.makeText(context, "检查更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}

private data class GroupCardState(val title: String, val subtitle: String)

private fun openLink(activity: ComponentActivity, url: String) {
    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * 发现新版本后的交互：弹窗展示版本与更新说明；
 *  - 若解析到 APK 直链：主按钮「下载并安装」走应用内下载 + FileProvider 调起安装；
 *  - 否则（无直链）：只保留「前往发布页」兜底。
 */
private fun showUpdateDialog(activity: ComponentActivity, result: UpdateCheckResult.Available) {
    val sizeText = if (result.sizeBytes > 0) {
        "大小：%.1f MB".format(result.sizeBytes / 1048576.0)
    } else ""
    val msg = buildString {
        append("发现新版本 v${result.versionName}")
        if (sizeText.isNotEmpty()) append("\n$sizeText")
        if (result.notes.isNotBlank()) append("\n\n${result.notes.take(800)}")
    }
    val canDirect = result.apkUrl.isNotBlank()
    AlertDialog.Builder(activity).apply {
        setTitle("模块更新")
        setMessage(msg)
        if (canDirect) {
            setPositiveButton("下载并安装") { _, _ ->
                downloadAndInstall(activity, result.apkUrl, result.apkName)
            }
        }
        setNegativeButton(if (canDirect) "去发布页" else "前往下载") { _, _ ->
            openLink(activity, result.releaseUrl)
        }
        setCancelable(true)
        show()
    }
}

/** 后台把 APK 下到本地，完成后经 FileProvider 调起系统安装。 */
private fun downloadAndInstall(activity: ComponentActivity, apkUrl: String, apkName: String) {
    val dialog = ProgressDialog(activity).apply {
        setMessage("正在下载更新包…")
        setCancelable(false)
        show()
    }
    Thread {
        try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
            dir.mkdirs()
            val file = File(dir, apkName.ifBlank { "MiMarketPurify_update.apk" })
            downloadFile(apkUrl, file)
            activity.runOnUiThread {
                dialog.dismiss()
                installApk(activity, file)
            }
        } catch (e: Exception) {
            activity.runOnUiThread {
                dialog.dismiss()
                Toast.makeText(activity, "下载失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}

private fun downloadFile(url: String, dest: File) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    try {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) out.write(buf, 0, read)
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
