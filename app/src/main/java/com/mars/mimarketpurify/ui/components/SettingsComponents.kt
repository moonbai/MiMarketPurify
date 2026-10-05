package com.mars.mimarketpurify.ui.components

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

// ==================== SettingItem ====================

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
                color = colors.onSurface,
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



// ==================== 开关行 ====================

@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    /** affectsStore=true 时弹出带「重启商店」按钮的提示 */
    affectsStore: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var isChecked by remember(checked) { mutableStateOf(checked) }

    fun toggle(next: Boolean) {
        isChecked = next
        onCheckedChange(next)
        if (snackbarHost != null) {
            scope.launch {
                val hintOn = context.getSharedPreferences(Settings.PREFS_GROUP, Context.MODE_PRIVATE)
                    .getBoolean(Settings.KEY_SWITCH_HINT, true)
                if (!hintOn) return@launch
                val msg = "$title：${if (next) "已开启" else "已关闭"}"
                // affectsStore=true 时始终显示「重启商店」按钮
                val needsRestart = affectsStore
                val result = snackbarHost.showSnackbar(
                    message = msg,
                    actionLabel = if (needsRestart) "重启商店" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) MarketRestarter.restart(context)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { toggle(!isChecked) }
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
            onCheckedChange = { toggle(it) },
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

// ==================== 下拉偏好行 ====================
// 标题统一使用 MiuixTheme.textStyles.body1，与仓库其它条目（PrefSwitch / PrefSlider 等）保持一致；
// 下拉弹窗复用 MiuiX 原生 WindowDropdownPopup，避免引入额外依赖并保证深色模式适配。

@Composable
fun DropdownPreference(
    title: String,
    summary: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    collapseOnSelection: Boolean = true,
    entries: List<DropdownEntry>,
) {
    val colors = MiuixTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { expanded = true }
            .padding(horizontal = 20.dp, vertical = MiuiX.ROW_PAD_V.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = if (enabled) colors.onSurface else colors.outline,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (enabled) colors.onSurfaceVariantSummary else colors.outline,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (!valueText.isNullOrEmpty()) {
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantActions,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(text = "›", fontSize = 20.sp, color = colors.outline, modifier = Modifier.size(20.dp, 20.dp))
    }
    if (entries.isNotEmpty()) {
        WindowDropdownPopup(
            entries = entries,
            show = expanded,
            onDismiss = { expanded = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            collapseOnSelection = collapseOnSelection,
        )
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
    affectsStore: Boolean = true,
    onChanged: ((Boolean) -> Unit)? = null,
) {
    val tick by activity.refreshSignal
    var checked by remember(tick) { mutableStateOf(activity.readLocal(key, default)) }
    SwitchRow(title, summary, checked, enabled, affectsStore = affectsStore) { on ->
        checked = on
        activity.writeRemote(key, on)
        onChanged?.invoke(on)
    }
}

// ==================== 偏好绑定：主题模式 ====================

@Composable
fun PrefThemeMode(activity: SettingsBaseActivity, onApplied: () -> Unit = {}) {
    val tick by activity.refreshSignal
    var mode by remember(tick) {
        mutableStateOf(
            activity.readLocalIntDirect(
                com.mars.mimarketpurify.Settings.KEY_THEME_MODE,
                com.mars.mimarketpurify.THEME_FOLLOW,
            )
        )
    }
    val themeLabels = listOf("跟随系统", "浅色", "深色")
    val items = themeLabels.mapIndexed { index, label ->
        DropdownItem(
            text = label,
            selected = index == mode,
            onClick = {
                mode = index
                activity.writeLocalInt(com.mars.mimarketpurify.Settings.KEY_THEME_MODE, index)
                onApplied()
            },
        )
    }
    SettingsSection(topLabel = "显示样式") {
        DropdownPreference(
            title = "显示样式",
            summary = "选择浅色、深色或跟随系统自动切换",
            valueText = themeLabels[mode],
            entries = listOf(DropdownEntry(items = items)),
        )
    }
}

// ==================== 偏好绑定：底部标签筛选 ====================

@Composable
fun PrefTabFilterSpinner(
    activity: SettingsBaseActivity,
    enabled: Boolean,
) {
    val tick by activity.refreshSignal
    var kept by remember(tick) { mutableStateOf(Settings.getKeptTabs()) }
    val keys = remember { Settings.TAB_ITEMS.keys.toList() }
    val items = keys.map { key ->
        val label = Settings.TAB_ITEMS[key] ?: key
        DropdownItem(
            text = label,
            selected = key in kept,
            onClick = {
                kept = if (key in kept) kept - key else kept + key
                activity.writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
            },
        )
    }

    val keptLabels = kept.mapNotNull { key -> Settings.TAB_ITEMS[key] }
    val dynamicSummary = when {
        !enabled -> "已关闭筛选，恢复全部标签"
        keptLabels.isEmpty() -> "未选择任何标签"
        keptLabels.size <= 3 -> "已选：${keptLabels.joinToString("、")}"
        else -> "已选 ${keptLabels.size} 项：${keptLabels.take(3).joinToString("、")}…"
    }

    // 复用 MiuiX 原生 WindowDropdownPopup 承载多选弹窗（collapseOnSelection=false 保持展开逐项勾选），
    // 条目标题统一使用 body1，与仓库其它条目（PrefSwitch 等）保持一致，不再使用字号偏大的内置标题样式。
    DropdownPreference(
        title = "标签显示",
        summary = dynamicSummary,
        enabled = enabled,
        collapseOnSelection = false,
        entries = listOf(DropdownEntry(items = items)),
    )
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
    onChanged: ((Int) -> Unit)? = null,
) {
    val tick by activity.refreshSignal
    var value by remember(tick) { mutableStateOf(activity.readLocalInt(key, default)) }
    var textValue by remember(tick) { mutableStateOf(format(value)) }
    val colors = MiuixTheme.colorScheme

    fun commit() {
        activity.writeRemoteInt(key, value)
        onChanged?.invoke(value)
    }

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
            BasicTextField(
                value = textValue,
                onValueChange = { new ->
                    textValue = new
                    new.filter { it.isDigit() }.toIntOrNull()?.let { v ->
                        val clamped = v.coerceIn(min, max)
                        if (clamped != value) { value = clamped; textValue = format(value) }
                    }
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                textStyle = TextStyle(
                    color = if (enabled) colors.primary else colors.outline,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                ),
                modifier = Modifier.widthIn(min = 40.dp, max = 72.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                value = v.roundToInt()
                textValue = format(value)
            },
            onValueChangeFinished = { commit() },
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

// ==================== 偏好绑定：颜色选择（深色模式适配 + 双默认色） ====================

@Composable
fun PrefColorRow(
    activity: SettingsBaseActivity,
    title: String,
    key: String,
    defaultColorLight: Int,
    defaultColorDark: Int,
) {
    val tick by activity.refreshSignal
    val dark = activity.useDarkTheme()
    val defaultColor = if (dark) defaultColorDark else defaultColorLight
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
            containerColor = colors.surfaceContainer,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
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
            title = { Text("选择颜色") },
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

            SettingsSection(topLabel = "功能") {
                val features = listOf(
                    "广告净化" to "开屏、首页信息流、搜索、升级/下载页、详情页、榜单广告、领水果入口、活动入口",
                    "界面精简" to "「我的」页推荐/清理/安全检测/个人信息、详情页精选、底栏角标、升级记录、搜索也在看",
                    "功能增强" to "下载超级岛、非正版APP显示、被隐藏更新显示、升级弹窗拦截",
                    "底栏配置" to "自定义底栏显示，添加更新入口，悬浮底栏设置",
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
            text = "小米应用商店净化与增强",
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
