// ==================== 高亮开关组件 ====================

@Composable
private fun HighlightSwitch(
    activity: SubSettingsActivity,
    key: String,
    title: String,
    summary: String,
    enabled: Boolean,
    highlightKey: String,
    default: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    val isHighlighted = highlightKey.isNotEmpty() && highlightKey == key
    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0x331976D2) else Color.Transparent,
        label = "highlight_bg"
    )
    Box(
        modifier = if (isHighlighted) Modifier.background(bgColor) else Modifier,
    ) {
        PrefSwitch(
            activity = activity,
            key = key,
            title = title,
            summary = summary,
            checked = activity.readLocal(key, default),
            enabled = enabled,
            default = default,
            onCheckedChange = onCheckedChange,
        )
    }
}
