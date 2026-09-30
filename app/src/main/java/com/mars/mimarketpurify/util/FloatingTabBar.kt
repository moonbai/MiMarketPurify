package com.mars.mimarketpurify.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.minOf
import kotlin.math.roundToInt

/**
 * 悬浮底栏（MiuiX 风格，对标 AritxOnly/HyperModifier 的 MiuixFloatingTabBar 视觉）。
 *
 * 形态：胶囊形容器（[Capsule]，来自 HyperModifier 实际依赖 io.github.kyant0:shapes）+ 滑动胶囊
 * 指示器（选中项高亮用中性半透明胶囊，暗色白 / 亮色黑，不含主色）+ 图标与文字（默认竖排 Stacked）。
 * 内容色（图标 + 文字）在暗色背景下为白、亮色背景下为黑，与 HyperModifier 一致；选中态仅靠滑动
 * 胶囊表达，文字字重 SemiBold ↔ Medium 区分，颜色不随选中变化（除非在「悬浮底栏配置」中手动指定）。
 *
 * 为避免直接复制 GPL-3.0 的 HyperModifier 源码，本文件在本仓库自有包内重新实现其视觉，
 * 但复用其 [Capsule] 胶囊形状依赖。毛玻璃：商店底栏由调用方已有的 ViewBackdropLayer 采样器提供，
 * 程序主页使用 MiuiX 原生半透明胶囊（与 miuix FloatingNavigationBar 一致，零运行时着色器风险）。
 */
data class FloatingTabItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val badge: Boolean = false,
)

enum class FloatingTabLayout {
    Horizontal,
    Stacked,
}

object FloatingTabBarDefaults {
    val Height = 54.dp
    val HorizontalContentPadding = 6.dp
    val VerticalContentPadding = 3.dp
    val IndicatorHorizontalOverflow = 3.dp
    val MaximumWidth = 380.dp
    val MaximumItemWidth = 88.dp
    val TabIconSize = 26.dp
    val ContainerShape: Shape = Capsule()
    val IndicatorShape: Shape = Capsule()
}

@Composable
fun FloatingTabBar(
    items: List<FloatingTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: FloatingTabLayout = FloatingTabLayout.Stacked,
    showLabel: Boolean = true,
    radius: androidx.compose.ui.unit.Dp = 28.dp,
    barColor: Color? = null,
    indicatorColor: Color? = null,
    contentSelectedColor: Color? = null,
    contentNormalColor: Color? = null,
) {
    if (items.isEmpty()) return
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.onSurfaceContainer.luminance() > 0.5f
    val defaultContent = if (isDark) Color.White else Color.Black
    val resolvedIndicator = indicatorColor
        ?: if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val resolvedBar = barColor ?: scheme.surfaceContainer.copy(alpha = 0.9f)
    val resolvedSelected = contentSelectedColor ?: defaultContent
    val resolvedNormal = contentNormalColor ?: defaultContent

    // 完全圆角（半径 ≥ 半高）用 Capsule；否则用圆角矩形，遵循「悬浮底栏配置」的圆角设置。
    val containerShape: Shape = if (radius >= FloatingTabBarDefaults.Height / 2) {
        Capsule()
    } else {
        RoundedCornerShape(radius)
    }

    val motion = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (items.indices.contains(selectedIndex)) {
            motion.animateTo(
                selectedIndex.toFloat(),
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val visualPosition = motion.value

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .widthIn(
                max = minOf(
                    FloatingTabBarDefaults.MaximumWidth,
                    FloatingTabBarDefaults.MaximumItemWidth * items.size,
                ),
            )
            .fillMaxWidth()
            .height(FloatingTabBarDefaults.Height)
            .background(resolvedBar, containerShape)
            .clip(containerShape),
    ) {
        val itemWidth = maxWidth / items.size
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val overflowPx = with(density) {
            FloatingTabBarDefaults.IndicatorHorizontalOverflow.toPx()
        }

        // 滑动胶囊指示器：跟随选中项以弹簧动画平移，选中态仅靠它表达（不含主色）。
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (visualPosition * itemWidthPx - overflowPx).roundToInt(),
                        0,
                    )
                }
                .width(itemWidth + FloatingTabBarDefaults.IndicatorHorizontalOverflow * 2)
                .fillMaxHeight()
                .clip(Capsule())
                .background(resolvedIndicator),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                FloatingTabItemContent(
                    item = item,
                    selected = index == selectedIndex,
                    selectedColor = resolvedSelected,
                    normalColor = resolvedNormal,
                    layout = layout,
                    showLabel = showLabel,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun FloatingTabItemContent(
    item: FloatingTabItem,
    selected: Boolean,
    selectedColor: Color,
    normalColor: Color,
    layout: FloatingTabLayout,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (item.enabled) {
        if (selected) selectedColor else normalColor
    } else {
        (if (selected) selectedColor else normalColor).copy(alpha = 0.3f)
    }
    val color by animateColorAsState(
        targetValue = baseColor,
        label = "floating_tab_content_color",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(Capsule())
            .selectable(
                selected = selected,
                enabled = item.enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (layout) {
            FloatingTabLayout.Horizontal -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconWithBadge(item, color)
                if (showLabel && item.label.isNotEmpty()) {
                    Spacer(Modifier.width(5.dp))
                    Label(item.label, color, selected)
                }
            }

            FloatingTabLayout.Stacked -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconWithBadge(item, color)
                if (showLabel && item.label.isNotEmpty()) {
                    Label(item.label, color, selected)
                }
            }
        }
    }
}

@Composable
private fun IconWithBadge(item: FloatingTabItem, color: Color) {
    Box(contentAlignment = Alignment.TopEnd) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(FloatingTabBarDefaults.TabIconSize),
            )
        }
        if (item.badge) {
            val scheme = MiuixTheme.colorScheme
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(Capsule())
                    .background(scheme.error),
            )
        }
    }
}

@Composable
private fun Label(text: String, color: Color, selected: Boolean) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
