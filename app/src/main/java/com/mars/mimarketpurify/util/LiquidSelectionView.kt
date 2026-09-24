package com.mars.mimarketpurify.util

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 风格的**液态选中高亮胶囊**，纯原生 Canvas 实现（不依赖 Compose / 第三方动画库）。
 *
 * 观感来自三件事的叠加，缺一就像普通色块切换：
 * 1. **非对称插值**：移动时「前导边快、尾随边慢」，胶囊在飞行途中被拉长，
 *    到位后再收回来 —— 这是液态感的主体；
 * 2. **落点过冲**：前导边用过冲插值，到位瞬间轻微越界再回弹，像果冻落地；
 * 3. **玻璃质感**：竖向渐变（上深下浅）+ 顶部一道高光泽 + 外圈柔光晕，
 *    模拟薄玻璃的受光面。
 *
 * 关于「真·背景折射」：iOS 液态玻璃会把胶囊背后的内容采样并扭曲，
 * 在 Android 上等价的 backdrop blur 只有 Compose 的 GraphicsLayer 或
 * 反射 uikit 私有 API 才拿得到，纯原生 View 无法采样兄弟视图之后的内容。
 * 因此这里用渐变 + 高光泽 + 光晕逼近观感，而不是谎称做到了折射。
 *
 * 用法：宿主在布局完成后调用 [setSlots] 传入各项的中心 x 与半宽，
 * 选中变化时调用 [select]。
 */
class LiquidSelectionView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    /** 高光泽内缩矩形，复用避免每帧分配。 */
    private val sheenRect = RectF()

    /** 竖向渐变缓存：胶囊垂直范围恒定，仅颜色变化时重建，避免动画每帧 new。 */
    private var fillGradient: LinearGradient? = null
    private var lastGradientColor = 0

    /** 每一项的 [centerX, halfWidth]，由宿主在布局完成后写入。 */
    private var slots: List<FloatArray> = emptyList()

    private var color: Int = DEFAULT_COLOR
    private var radiusPx: Float = 22f * density
    private var padY: Float = 7f * density

    /** 当前绘制中的胶囊左右边界（px）。 */
    private var left = 0f
    private var right = 0f
    private var hasGeometry = false

    private var animator: ValueAnimator? = null

    init {
        // 高亮层本身不吃触摸，点击交给上层各项
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        haloPaint.style = Paint.Style.FILL
        sheenPaint.style = Paint.Style.STROKE
        sheenPaint.strokeWidth = max(0.6f, 0.4f * density)
    }

    /** 配置主色与圆角（dp→px 由宿主换算后传入）。 */
    fun configure(color: Int, radiusPx: Float) {
        this.color = color
        this.radiusPx = radiusPx
        invalidate()
    }

    /**
     * 写入各选中位的几何。
     * @param slots 每项为 floatArrayOf(centerX, halfWidth)
     * @param index 当前应处于的选中位（首帧不做动画）
     */
    fun setSlots(slots: List<FloatArray>, index: Int) {
        this.slots = slots
        val slot = slots.getOrNull(index)
        if (slot != null) {
            left = slot[0] - slot[1]
            right = slot[0] + slot[1]
            hasGeometry = true
        } else {
            hasGeometry = false
        }
        invalidate()
    }

    /**
     * 切换到第 [index] 项。
     * @param animated false 用于首帧、重建或开关变化，直接落位不走动画。
     */
    fun select(index: Int, animated: Boolean = true) {
        val target = slots.getOrNull(index) ?: return
        val tl = target[0] - target[1]
        val tr = target[0] + target[1]
        if (!hasGeometry) {
            left = tl; right = tr; hasGeometry = true
            invalidate()
            return
        }
        // 先终止上一次动画：连点时若让两条动画同时写 left/right 会互相打架
        animator?.cancel()
        if (!animated) {
            left = tl; right = tr; hasGeometry = true; invalidate(); return
        }
        val fl = left
        val fr = right
        val movingRight = tr >= fr
        val distance = kotlin.math.abs(tr - fr) + kotlin.math.abs(tl - fl)
        // 全链路 Float：Kotlin 不做隐式数值转换，Float 结果不能拿去 coerceIn(Long, Long)
        val totalMs = (BASE_DURATION_MS + distance * MS_PER_PX)
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS).toLong()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            // 必须用独立名字 totalMs：在 apply 里写 `this.duration = duration`，
            // 右侧 duration 会解析成 ValueAnimator.duration（隐式接收者成员优先于外层局部变量），
            // 变成自赋值，距离插值就静默失效了
            this.duration = totalMs
            // 前导边带过冲（果冻落位），尾随边缓出（产生飞行中的拉伸）
            val lead = OvershootInterpolator(OVERSHOOT_TENSION)
            val trail = DecelerateInterpolator(TRAIL_DECELERATE)
            addUpdateListener { va ->
                val t = va.animatedFraction
                val eLead = lead.getInterpolation(t)
                val eTrail = trail.getInterpolation(t)
                if (movingRight) {
                    right = fr + (tr - fr) * eLead
                    left = fl + (tl - fl) * eTrail
                } else {
                    left = fl + (tl - fl) * eLead
                    right = fr + (tr - fr) * eTrail
                }
                // 数值扰动下别出现负宽度
                if (right - left < MIN_WIDTH_PX) {
                    val c = (left + right) / 2f
                    left = c - MIN_WIDTH_PX / 2f
                    right = c + MIN_WIDTH_PX / 2f
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasGeometry || slots.isEmpty()) return
        val h = height.toFloat()
        if (h <= 0f) return

        val top = padY
        val bottom = h - padY
        if (bottom - top <= 0f) return

        rect.set(left, top, right, bottom)
        val r = min(radiusPx, min(rect.width() / 2f, rect.height() / 2f))

        // 1) 外圈柔光晕：让胶囊边缘像玻璃那样有溢出的光
        haloPaint.shader = null
        haloPaint.color = withAlpha(color, HALO_ALPHA)
        canvas.drawRoundRect(
            rect.left - GLOW_INSET_PX, rect.top - GLOW_INSET_PX,
            rect.right + GLOW_INSET_PX, rect.bottom + GLOW_INSET_PX,
            r + GLOW_INSET_PX, r + GLOW_INSET_PX, haloPaint
        )

        // 2) 主体：上深下浅竖向渐变（垂直范围恒定，仅颜色变化时重建）
        if (fillGradient == null || lastGradientColor != color) {
            fillGradient = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(withAlpha(color, 255), withAlpha(color, FILL_BOTTOM_ALPHA)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            lastGradientColor = color
        }
        fillPaint.shader = fillGradient
        canvas.drawRoundRect(rect, r, r, fillPaint)
        fillPaint.shader = null

        // 3) 顶部高光泽线：薄玻璃的反光（内缩后尺寸仍需为正，否则跳过）
        sheenPaint.color = withAlpha(0xFFFFFFFF.toInt(), SHEEN_ALPHA)
        val insetX = min(r * 0.5f, rect.width() / 2f - 1f)
        val insetY = min(r * 0.5f, rect.height() / 2f - 1f)
        if (insetX > 0f && insetY > 0f) {
            sheenRect.set(rect)
            sheenRect.inset(insetX, insetY)
            canvas.drawRoundRect(sheenRect, r * 0.5f, r * 0.5f, sheenPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (rgb and 0x00FFFFFF)

    companion object {
        private val DEFAULT_COLOR = 0xFFD7F0FF.toInt()

        private const val BASE_DURATION_MS = 210f
        private const val MS_PER_PX = 0.42f
        private const val MIN_DURATION_MS = 210f
        private const val MAX_DURATION_MS = 430f

        private const val OVERSHOOT_TENSION = 1.45f
        private const val TRAIL_DECELERATE = 1.6f

        private const val HALO_ALPHA = 22        // 外圈光晕进一步降低
        private const val FILL_BOTTOM_ALPHA = 230 // 底部高度透明，玻璃感拉满
        private const val SHEEN_ALPHA = 46
        private const val MIN_WIDTH_PX = 8f
        private const val GLOW_INSET_PX = 2f
    }
}
