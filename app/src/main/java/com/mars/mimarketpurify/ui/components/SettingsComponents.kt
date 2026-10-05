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
import androidx.compose.ui.res.stringResource

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
                    text = stringResource(R.string.btn_restart),
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
    val onLabel = stringResource(R.string.snack_on)
    val offLabel = stringResource(R.string.snack_off)
    val toggleFmt = stringResource(R.string.snack_toggle)
    var isChecked by remember(checked) { mutableStateOf(checked) }

    fun toggle(next: Boolean) {
        isChecked = next
        onCheckedChange(next)
        if (snackbarHost != null) {
            scope.launch {
                val hintOn = context.getSharedPreferences(Settings.PREFS_GROUP, Context.MODE_PRIVATE)
                    .getBoolean(Settings.KEY_SWITCH_HINT, true)
                if (!hintOn) return@launch
                val msg = toggleFmt.format(title, if (next) onLabel else offLabel)
                // affectsStore=true 时始终显示「重启商店」按钮
                val needsRestart = affectsStore
                val result = snackbarHost.showSnackbar(
                    message = msg,
                    actionLabel = if (needsRestart) context.getString(R.string.btn_restart) else null,
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
    val themeLabels = listOf(stringResource(R.string.theme_follow), stringResource(R.string.theme_light), stringResource(R.string.theme_dark))
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
    SettingsSection(topLabel = stringResource(R.string.sec_theme_mode)) {
        DropdownPreference(
            title = stringResource(R.string.theme_mode_title),
            summary = stringResource(R.string.theme_mode_summary),
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
        !enabled -> stringResource(R.string.tab_filter_off)
        keptLabels.isEmpty() -> stringResource(R.string.tab_filter_none)
        keptLabels.size <= 3 -> stringResource(R.string.tab_filter_selected_few, keptLabels.joinToString(stringResource(R.string.list_sep)))
        else -> stringResource(R.string.tab_filter_selected_many, keptLabels.size, keptLabels.take(3).joinToString(stringResource(R.string.list_sep)))
    }

    // 复用 MiuiX 原生 WindowDropdownPopup 承载多选弹窗（collapseOnSelection=false 保持展开逐项勾选），
    // 条目标题统一使用 body1，与仓库其它条目（PrefSwitch 等）保持一致，不再使用字号偏大的内置标题样式。
    DropdownPreference(
        title = stringResource(R.string.tab_filter_title),
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
                text = stringResource(R.string.color_hint),
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
                }) { Text(stringResource(R.string.color_pick_confirm), color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    activity.writeRemoteInt(key, -1)
                    stored = -1
                    open = false
                }) { Text(stringResource(R.string.color_pick_reset), color = colors.onSurfaceVariantSummary) }
            },
            title = { Text(stringResource(R.string.color_pick_title)) },
            text = {
                ColorPicker(
                    color = Color(pickerColor),
                    onColorChanged = { newColor -> pickerColor = newColor.toArgb() },
                )
            },
        )
    }
}

