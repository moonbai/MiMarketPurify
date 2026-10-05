package com.mars.mimarketpurify.ui.components

import com.mars.mimarketpurify.FeatureRegistry
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
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
import com.mars.mimarketpurify.useDarkTheme
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.mars.mimarketpurify.ui.LocalSnackbarHost
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
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

// ==================== 关于页：安全图片加载（被本页复用） ====================
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

    BackHandler(onBack = onBack)

    val doCheckUpdate: () -> Unit = {
        Thread {
            val result = UpdateChecker.check()
            activity.runOnUiThread {
                when (result) {
                    is UpdateCheckResult.Available -> { updateInfo = result; showUpdate = true }
                    is UpdateCheckResult.Latest ->
                        Toast.makeText(activity, activity.getString(R.string.toast_latest), Toast.LENGTH_SHORT).show()
                    is UpdateCheckResult.Unavailable ->
                        Toast.makeText(activity, activity.getString(R.string.toast_check_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    val backdrop = rememberLayerBackdrop()
    val scrollProgress by remember { derivedStateOf { (scrollState.value / 320f).coerceIn(0f, 1f) } }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        AboutFloatingBackground(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
            alpha = 1f - scrollProgress,
        )

        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val heroTopGap = screenHeight * 0.20f
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
            AboutHeroHeader(activity = activity)
            Spacer(Modifier.height(heroBottomGap))
            AboutUpdateBar(
                activity = activity,
                onClick = doCheckUpdate,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(heroBottomGap))

            SettingsSection(topLabel = stringResource(R.string.sec_features)) {
                val byCategory = FeatureRegistry.allFeatures.groupBy { it.category }
                val sep = stringResource(R.string.list_sep)
                for ((category, feats) in byCategory) {
                    val catTitle = stringResource(FeatureRegistry.categoryTitles[category] ?: R.string.sec_features)
                    val sb = StringBuilder()
                    for (f in feats) {
                        if (sb.isNotEmpty()) sb.append(sep)
                        sb.append(stringResource(f.titleRes))
                    }
                    SettingItem(
                        headlineText = catTitle,
                        supportingText = sb.toString(),
                    )
                }
            }

            SettingsSection(topLabel = stringResource(R.string.sec_author)) {
                SettingItem(
                    headlineText = stringResource(R.string.about_author_name),
                    supportingText = stringResource(R.string.about_author_summary),
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

            SettingsSection(topLabel = stringResource(R.string.sec_references)) {
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

            SettingsSection(topLabel = stringResource(R.string.sec_other)) {
                SettingItem(
                    headlineText = stringResource(R.string.about_privacy_title),
                    supportingText = stringResource(R.string.about_privacy_summary),
                    onClick = { activity.startActivity(Intent(activity, PrivacyPolicyActivity::class.java)) },
                )
            }

            Footer(stringResource(R.string.about_footer))
        }

        if (showUpdate && updateInfo != null) {
            val info = updateInfo!!
            AlertDialog(
                onDismissRequest = { showUpdate = false },
                title = { Text(text = stringResource(R.string.about_update_title), color = colors.onSurface) },
                text = {
                    val sizeText = if (info.sizeBytes > 0) {
                        stringResource(R.string.about_update_size, info.sizeBytes / 1048576.0)
                    } else ""
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        val updateFound = stringResource(R.string.about_update_found, info.versionName)
                        Text(
                            text = buildString {
                                append(updateFound)
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
                                    Toast.makeText(activity, activity.getString(R.string.toast_download_fail, e.message ?: ""), Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }) { Text(text = stringResource(R.string.about_download_install), color = colors.primary) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUpdate = false
                        openLink(activity, info.releaseUrl)
                    }) { Text(text = stringResource(R.string.about_go_release), color = colors.onSurfaceVariantSummary) }
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
                            text = stringResource(R.string.about_downloading),
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
                                text = stringResource(R.string.about_download_progress),
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
            text = stringResource(R.string.ui_app_subtitle),
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

// ==================== 关于页：动态光晕背景 ====================

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

// ==================== 关于页：检查更新按钮 ====================

@Composable
private fun AboutUpdateBar(
    activity: ComponentActivity,
    onClick: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f
    val barShape = RoundedCornerShape(20.dp)
    val glassTint = if (isDark) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)
    } else {
        Color.White.copy(alpha = 0.58f)
    }
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
            text = stringResource(R.string.about_check_update),
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
        Toast.makeText(context, context.getString(R.string.toast_install_fail), Toast.LENGTH_LONG).show()
        openLink(context as ComponentActivity, "https://github.com/moonbai/MiMarketPurify/releases/latest")
    }
}
