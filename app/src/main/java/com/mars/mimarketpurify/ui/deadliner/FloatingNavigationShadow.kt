package com.mars.mimarketpurify.ui.deadliner

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 悬浮底栏的投影。平替 AritxOnly/HyperModifier 的 `softGlassShadow`（其依赖 miuix-blur 内部
 * textureBlur 与自定义 SoftGlassShadowTokens）。这里用 Compose 标准 `Modifier.shadow`，
 * 仅做轻投影、不裁剪子节点，由外层容器的圆角 clip 负责形状。
 */
fun Modifier.floatingNavigationShadow(
    shape: Shape,
    isDark: Boolean,
): Modifier {
    return this.shadow(
        elevation = if (isDark) 8.dp else 6.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black,
        spotColor = Color.Black,
    )
}
