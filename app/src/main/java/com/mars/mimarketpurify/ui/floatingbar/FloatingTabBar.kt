package com.mars.mimarketpurify.ui.floatingbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt
import com.mars.mimarketpurify.util.ViewBackdropLayer
import com.mars.mimarketpurify.util.ViewBackdropSnapshot
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 仿 Miuix 的悬浮胶囊标签条（自实现，不复刻 HyperModifier 私有 MiuixFloatingTabBar）：
 * 圆角胶囊 + 毛玻璃底层 + 选中指示滑块（spring 过冲动画）+ 图标/标签/角标。
 * 原生 TabView 仍负责导航与埋点，本组件仅做呈现，点击通过 [onItemSelected] 回调给宿主转发。
 */

data class FloatingTabItemData(
    val key: String,
    val label: String,
    val selectedPainter: Painter?,
    val unselectedPainter: Painter?,
    val badge: String? = null,
)

object FloatingTabBarDefaults {
    val Height = 56.dp
}

@Composable
fun FloatingTabBar(
    items: List<FloatingTabItemData>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    snapshot: ViewBackdropSnapshot? = null,
) {
    val count = items.size
    val selectedIndex = remember(selectedKey, count) {
        val idx = items.indexOfFirst { it.key == selectedKey }
        if (idx < 0) 0 else idx
    }
    val density = LocalDensity.current
    var rowWidthPx by remember { mutableStateOf(0) }

    val indicatorCenterTarget = remember(rowWidthPx, selectedIndex, count) {
        if (rowWidthPx <= 0 || count == 0) 0f else (selectedIndex + 0.5f) / count * rowWidthPx
    }
    val indicatorX = remember { Animatable(indicatorCenterTarget) }
    LaunchedEffect(indicatorCenterTarget) {
        if (rowWidthPx > 0) {
            indicatorX.animateTo(
                indicatorCenterTarget,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f),
            )
        }
    }
    val indicatorWidthPx = if (count > 0) rowWidthPx / count.toFloat() else 0f
    val indicatorPadPx = with(density) { FloatingTabBarDefaults.Height.value * density.density * 0.12f }

    val isNight = MiuixTheme.colorScheme.isNight
    val glassFallback = if (isNight) {
        Color.Black.copy(alpha = 0.36f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val indicatorColor = MiuixTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FloatingTabBarDefaults.Height)
            .clip(RoundedCornerShape(percent = 50)),
    ) {
        // 毛玻璃底层：优先用采样快照，否则回退半透明色块
        if (snapshot != null && backdrop != null) {
            ViewBackdropLayer(snapshot = snapshot, backdrop = backdrop)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassFallback, RoundedCornerShape(percent = 50)),
            )
        }

        // 选中指示滑块（弹簧过冲）
        if (indicatorWidthPx > 0) {
            val leftPx = (indicatorX.value - indicatorWidthPx / 2f + indicatorPadPx)
                .coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), with(density) { (FloatingTabBarDefaults.Height * 0.12f).roundToPx() }) }
                    .width(with(density) { (indicatorWidthPx - 2 * indicatorPadPx).coerceAtLeast(1f).toDp() })
                    .fillMaxHeight(0.76f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(indicatorColor),
            )
        }

        // 标签项
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rowWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                FloatingTabItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onItemSelected(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FloatingTabItem(
    item: FloatingTabItemData,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MiuixTheme.colorScheme
    // MiuiX：选中项用 primary 强调色，未选中项用静默的 onSurfaceVariant（而非满对比度的 onSurface）
    val contentColor = if (selected) theme.primary else theme.onSurfaceVariant
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
    )
    val painter = (if (selected) item.selectedPainter else item.unselectedPainter)
        ?: item.selectedPainter
        ?: item.unselectedPainter

        Column(
            modifier = modifier
                .fillMaxHeight()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = item.label,
                        colorFilter = ColorFilter.tint(contentColor),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                }
            if (!item.badge.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-2).dp)
                        .size(if (item.badge.length > 1) 14.dp else 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(theme.error),
                )
            }
        }
        if (item.label.isNotEmpty()) {
            BasicText(
                text = item.label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 10.5.sp,
                ),
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 在 MiuixTheme.Colors 上补充 isNight 判断：miuix-kmp 的 Colors 未直接暴露 night 标志，
 * 这里用背景色亮度粗略判定当前是深色还是浅色主题。
 */
private val Colors.isNight: Boolean
    get() = background.red + background.green + background.blue < 1.5f
