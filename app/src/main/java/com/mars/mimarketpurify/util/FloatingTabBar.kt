package com.mars.mimarketpurify.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 悬浮底栏（MiuiX 风格，形态对标 AritxOnly/HyperModifier 的 MiuixFloatingTabBar）。
 *
 * 视觉要点：
 * - **居中**：外层 [Box] 撑满可用宽度并 [Alignment.Center] 居中内层胶囊，因此无论几个按钮、
 *   悬浮底栏都水平居中（此前内层没有居中容器，被父布局默认靠左摆放）。
 * - **透明**：默认容器色是半透明的 surfaceContainer（见 [FloatingTabBarDefaults.BarAlpha]）；
 *   传入 [backdrop] 时改用 miuix 的真实背景模糊（[drawBackdrop] + [blur]）绘制毛玻璃，
 *   玻璃上的着色比纯色更淡（[FloatingTabBarDefaults.GlassTintAlpha]），保证「透」。
 * - 滑动胶囊指示器 + 图标文字竖排，选中态仅靠中性半透明胶囊表达（暗色白 / 亮色黑，不含主色）。
 *
 * 毛玻璃实现依据 miuix **0.9.4**：[drawBackdrop] 与 [blur] / colorControls / effect 等在此版本
 * 已变为 `BackdropEffectScope` 的扩展成员（不再是顶层 import），因此本文件只 import 顶层
 * `drawBackdrop` 与成员 `blur`，不引用 rc01 时期的顶层 `colorControls` / `runtimeShaderEffect`。
 */
data class FloatingTabItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    /** 原生 TabView 的图标位图（优先于 [icon] 使用；短剧等无自绘图标时复用商店自带图标）。 */
    val iconBitmap: ImageBitmap? = null,
    /** 原生图标「选中态」位图（与 [iconBitmapUnselected] 成对，用于双态图标；为 null 时退化用 [iconBitmap]）。 */
    val iconBitmapSelected: ImageBitmap? = null,
    /** 原生图标「未选中态」位图。 */
    val iconBitmapUnselected: ImageBitmap? = null,
    /** 原生图标的单色描边位图（monochrome mask），启用单色时优先使用。 */
    val iconMonochrome: ImageBitmap? = null,
    /** 是否保留原生图标原始配色（不随主题单色化）。单色关闭且原生图标可用时为 true。 */
    val preserveOriginalIconColors: Boolean = false,
    val enabled: Boolean = true,
    /** 红点角标（不显示数字）。 */
    val badge: Boolean = false,
    /** 数字角标；>0 时显示数字（覆盖红点）。用于「更新」标签显示待更新数量。 */
    val badgeNumber: Int = 0,
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

    /** 无毛玻璃时的默认容器背景不透明度——刻意偏低，保证「背景透明」。 */
    const val BarAlpha = 0.4f

    /** 毛玻璃（有 backdrop）时叠在模糊层上的着色不透明度。 */
    const val GlassTintAlpha = 0.28f

    /** 默认背景模糊半径（像素）。 */
    const val BlurRadiusPx = 28f
}

/**
 * 悬浮底栏的统一「表面」修饰符：有 [backdrop] 时画真实模糊毛玻璃，否则退回半透明纯色。
 *
 * 对应 HyperModifier 的 SoftGlassSurface：其内部尺寸交给调用方，本函数只负责材质与着色，
 * 因此不会像旧 ViewBackdropLayer 那样把 requiredSize 撑给父布局（那正是底栏被顶到顶部的原因）。
 */
fun Modifier.floatingGlassSurface(
    backdrop: LayerBackdrop?,
    shape: Shape,
    tint: Color,
    blurRadiusPx: Float = FloatingTabBarDefaults.BlurRadiusPx,
): Modifier = if (backdrop != null) {
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(radiusX = blurRadiusPx, radiusY = blurRadiusPx)
        },
        onDrawSurface = {
            drawRect(tint)
        },
    )
} else {
    background(color = tint, shape = shape)
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
    backdrop: LayerBackdrop? = null,
    blurRadiusPx: Float = FloatingTabBarDefaults.BlurRadiusPx,
    expandWidth: Boolean = true,
    /** 液态高亮：选中胶囊改用毛玻璃液态效果（参考 iOS）。 */
    liquid: Boolean = false,
    /** 3D 液态：在液态基础上加阴影与更强的立体感。 */
    liquid3d: Boolean = false,
) {
    if (items.isEmpty()) return
    val scheme = MiuixTheme.colorScheme
    val isDark = scheme.onSurfaceContainer.luminance() > 0.5f
    val defaultContent = if (isDark) Color.White else Color.Black
    val resolvedIndicator = indicatorColor
        ?: if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val resolvedBar = barColor
        ?: scheme.surfaceContainer.copy(alpha = FloatingTabBarDefaults.BarAlpha)
    val glassTint = barColor
        ?: scheme.surfaceContainer.copy(alpha = FloatingTabBarDefaults.GlassTintAlpha)
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
    // 关键：外层撑满可用宽度并居中；内层胶囊宽度 = min(最大宽度, 每项宽度 × 项数)。
    // 这样无论 2 项还是 5 项，底栏都保持水平居中。
    Box(
        modifier = if (expandWidth) modifier.fillMaxWidth() else modifier,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(
                    max = if (FloatingTabBarDefaults.MaximumWidth <
                        FloatingTabBarDefaults.MaximumItemWidth * items.size
                    ) {
                        FloatingTabBarDefaults.MaximumWidth
                    } else {
                        FloatingTabBarDefaults.MaximumItemWidth * items.size
                    },
                )
                .height(FloatingTabBarDefaults.Height)
                .floatingGlassSurface(
                    backdrop = backdrop,
                    shape = containerShape,
                    tint = if (backdrop != null) glassTint else resolvedBar,
                    blurRadiusPx = blurRadiusPx,
                )
                .clip(containerShape),
        ) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = with(density) { itemWidth.toPx() }
            val overflowPx = with(density) {
                FloatingTabBarDefaults.IndicatorHorizontalOverflow.toPx()
            }

            // 滑动胶囊指示器：跟随选中项以弹簧动画平移，选中态仅靠它表达（不含主色）。
            // liquid 开启且 backdrop 可用时，选中胶囊改用毛玻璃液态效果（参考 iOS 液态高亮）；
            // 3D 在此基础上再加阴影，增强立体感。
            val indicatorBase = Modifier
                .offset {
                    IntOffset(
                        (visualPosition * itemWidthPx - overflowPx).roundToInt(),
                        0,
                    )
                }
                .width(itemWidth + FloatingTabBarDefaults.IndicatorHorizontalOverflow * 2)
                .fillMaxHeight()
                .clip(Capsule())
            if (liquid && backdrop != null) {
                Box(
                    modifier = indicatorBase
                        .floatingGlassSurface(
                            backdrop = backdrop,
                            shape = Capsule(),
                            tint = glassTint,
                            blurRadiusPx = blurRadiusPx,
                        )
                        .then(
                            if (liquid3d) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = Capsule(),
                                    clip = false,
                                    ambientColor = resolvedIndicator,
                                    spotColor = resolvedIndicator,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            } else {
                Box(modifier = indicatorBase.background(resolvedIndicator))
            }

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
                    liquid = liquid,
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight(),
                )
                }
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
    liquid: Boolean = false,
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
                IconWithBadge(item, color, selected, liquid)
                if (showLabel && item.label.isNotEmpty()) {
                    Spacer(Modifier.width(5.dp))
                    Label(item.label, color, selected)
                }
            }

            FloatingTabLayout.Stacked -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconWithBadge(item, color, selected, liquid)
                if (showLabel && item.label.isNotEmpty()) {
                    Label(item.label, color, selected)
                }
            }
        }
    }
}

@Composable
private fun IconWithBadge(item: FloatingTabItem, color: Color, selected: Boolean, liquid: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected && liquid) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "floating_tab_icon_scale",
    )
    val scaleMod = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    val scheme = MiuixTheme.colorScheme
    Box(contentAlignment = Alignment.TopEnd) {
        when {
            // 单色优先：把原生图标抽成单色描边、按当前主题色重新着色（对齐 HyperModifier 的 monochrome）。
            item.iconMonochrome != null -> Image(
                bitmap = item.iconMonochrome,
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(FloatingTabBarDefaults.TabIconSize).then(scaleMod),
            )
            // 保留原生彩色：单色关闭且抓到彩色双态时，按选中态直出（不 tint）。
            item.preserveOriginalIconColors &&
                (item.iconBitmapSelected != null || item.iconBitmapUnselected != null) -> {
                // 外层 when 条件已保证 selected/unselected 至少其一非 null，但编译器无法把
                // 「A ?: B」整体收窄为非空，故这里用 if (bmp != null) 让 Image 拿到非空 ImageBitmap。
                val bmp = if (selected) {
                    item.iconBitmapSelected ?: item.iconBitmapUnselected
                } else {
                    item.iconBitmapUnselected ?: item.iconBitmapSelected
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier.size(FloatingTabBarDefaults.TabIconSize).then(scaleMod),
                    )
                }
            }
            item.iconBitmap != null -> Image(
                bitmap = item.iconBitmap,
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(FloatingTabBarDefaults.TabIconSize).then(scaleMod),
            )
            item.icon != null -> Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(FloatingTabBarDefaults.TabIconSize).then(scaleMod),
            )
        }
        when {
            item.badgeNumber > 0 -> {
                val text = if (item.badgeNumber > 99) "99+" else item.badgeNumber.toString()
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = (-4).dp)
                        .background(scheme.error, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            item.badge -> Box(
                modifier = Modifier
                    .offset(x = 5.dp, y = (-4).dp)
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
