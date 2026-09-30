package com.mars.mimarketpurify.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘导航图标（[ImageVector]）。
 *
 * 用于替换原先打包进模块的商店栅格 WebP（tab_index_* / tab_mine_* 等）。
 * 全部以矢量路径在代码中绘制，单色、随主题着色（tint）、随暗色模式自动反色，
 * 不含任何位图资源——既满足「自绘」诉求，又彻底规避商店 AndResGuard 资源混淆、零维护。
 *
 * 之前的方案是把商店官方 Tab 图标拷进 res/drawable-nodpi，但运行时按名引用会被混淆
 * （tab_icon_index_n → res/7Lo.webp 等）破坏；现在一律改为本文件内手绘，不再依赖任何
 * 外部位图，也不依赖对商店原生图标的实时快照。
 */
object NavIcons {
    /** 主页（程序底栏 / 悬浮底栏「首页」）。 */
    val Home: ImageVector by lazy {
        vector("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")
    }

    /** 我的 / 关于（人形）。 */
    val Person: ImageVector by lazy {
        vector(
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
        )
    }

    /** 关于（信息）。 */
    val Info: ImageVector by lazy {
        vector(
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
        )
    }

    /** 游戏。 */
    val Game: ImageVector by lazy {
        vector(
            "M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zM11 13H8v3H6v-3H3v-2h3V8h2v3h3v2zm4.5 2c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4-3c-.83 0-1.5-.67-1.5-1.5S18.67 9 19.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z",
        )
    }

    /** 榜单 / 排行。 */
    val Rank: ImageVector by lazy {
        vector("M5 9.2h3V19H5zM10.6 5h3v14h-3zm5.6 8H19v6h-2.8z")
    }

    /** 软件（九宫格）。 */
    val Apps: ImageVector by lazy {
        vector(
            "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
        )
    }

    /** 分类（列表）。 */
    val List: ImageVector by lazy {
        vector("M3 5h2v2H3zm0 4h2v2H3zm0 4h2v2H3zm4-8h14v2H7zm0 4h14v2H7zm0 4h14v2H7z")
    }

    /** 更新（环形箭头），供「移花接木」注入的原生更新 tab / 悬浮底栏独立更新按钮使用。 */
    val Update: ImageVector by lazy {
        vector(
            "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8" +
                "c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6" +
                "s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
        )
    }

    private fun vector(path: String): ImageVector {
        val parser = PathParser()
        parser.parsePathString(path)
        return ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = parser.toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }
}
