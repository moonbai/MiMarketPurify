package com.mars.mimarketpurify

import android.content.Context
import android.content.res.Configuration
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 统一的视觉与交互令牌（design tokens）。
 *
 * 直接引用 MiuiX（`top.yukonga.miuix.kmp`）的 [lightColorScheme] / [darkColorScheme]
 * 取色，使模块自身 UI（主页 / 关于页 / 设置页）与悬浮底栏、与系统 MiuiX 主题同源，
 * 不再维护自定义的硬编码配色（原配色对象的橙色品牌色等非 MiuiX 观感一并移除）。
 *
 * 颜色均为「按日/夜模式动态解析」的函数；尺寸、字号、语义色等框架无关项保留为常量。
 * 新增界面应优先复用这里的常量与辅助函数。
 */
object MiuiX {

    // ═══════════════ 配色：直接取自 MiuiX 主题（日 / 夜）══════════════
    // 注：以下字段名沿用 Material3 标准（miuix 的 Colors 与之对齐）。
    // 若 0.9.4-rc01 个别字段名有出入（例如 background），按实际字段微调即可。
    fun bg(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).background.value.toInt()

    /** 卡片 / 分组容器底色 */
    fun card(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).surface.value.toInt()

    /** 主文本 / 标题 */
    fun onSurface(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).onSurface.value.toInt()

    /** 摘要 / 分组标题 */
    fun onSurfaceVariant(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).onSurfaceVariant.value.toInt()

    /** 最弱一级文本 */
    fun outline(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).outline.value.toInt()

    /** 分割线 / 状态卡（未激活）底色 */
    fun outlineVariant(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).outlineVariant.value.toInt()

    /** 强调色：开关开启态、链接、可点元素（MiuiX 蓝/紫） */
    fun primary(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).primary.value.toInt()

    /** 错误 / 警示 */
    fun error(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).error.value.toInt()

    fun onError(isNight: Boolean): Int =
        (if (isNight) darkColorScheme() else lightColorScheme()).onError.value.toInt()

    // ═══════════════ 语义色（MiuiX 无对应 token，沿用系统语义）══════════════
    /** 已激活 / 成功（系统绿） */
    const val STATE_ACTIVE = 0xFF34C759.toInt()
    const val STATE_ACTIVE_SOFT = 0x1434C759.toInt()

    /** 未激活状态卡的柔底色：用 outlineVariant 低透明度，区别于卡片白底 */
    fun neutralSoft(isNight: Boolean): Int = outlineVariant(isNight).withAlpha(0x40)

    /** primary 的低透明度底（约 12%），用于胶囊 / 状态卡背景 */
    fun primarySoft(isNight: Boolean): Int = primary(isNight).withAlpha(0x1F)

    // ═══════════════ 开关 / 勾选框（框架无关，保留）══════════════
    /**
     * 开关轨道未选中色：明确的灰，避免白滑块与浅灰轨道糊在一起。
     */
    const val SWITCH_TRACK_OFF = 0xFFD1D1D6.toInt()
    const val CHECK_OFF = 0xFFC7C7CC.toInt()

    // ═══════════════ 字号层级（sp）══════════════
    const val HOME_TITLE = 26f
    const val PAGE_TITLE = 20f
    const val SECTION = 13f
    const val ROW_TITLE = 16f
    const val ROW_SUMMARY = 12.5f
    const val CAPTION = 13f
    const val MICRO = 11.5f

    // ═══════════════ 间距与尺寸（dp）══════════════
    const val PAGE_H = 16
    const val CARD_PAD_H = 16
    const val CARD_PAD_V = 14
    const val CARD_GAP = 12
    const val SECTION_TOP = 18
    /** 组内行高：遵循 HyperOS 行高，通透但不松散 */
    const val ROW_MIN_HEIGHT = 52
    const val ROW_PAD_H = 16
    const val ROW_PAD_V = 12
    /** 组内行间距：不画分隔线，改用少量留白区分相邻两行 */
    const val ROW_GAP = 4
    /** 最小触摸目标，遵循 Material 48dp 建议 */
    const val TOUCH_MIN = 48

    const val LINE_SPACING = 1.45f

    const val REPO_URL = "https://github.com/moonbai/MiMarketPurify"
}

/** 系统当前是否处于暗色模式 */
fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/** 给 ARGB Int 设置 alpha（返回含新 alpha 的 ARGB Int，0x00~0xFF） */
fun Int.withAlpha(alpha: Int): Int = (alpha shl 24) or (this and 0x00FFFFFF)

/**
 * 给 drawable 着色：选中态 / 未选中态两套颜色（兼容开关、勾选框）。
 * 原配色对象的扩展函数，迁移至此以保证删除原配色对象后各界面仍能着色。
 */
fun android.graphics.drawable.Drawable.tinted(on: Int, off: Int): android.graphics.drawable.Drawable {
    return mutate().apply {
        setTintList(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(on, off)
        ))
    }
}

/** dp -> px */
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** dp -> px（浮点）：给 cornerRadius 这类需要 float 的场合用 */
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
