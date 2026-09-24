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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * iOS 风格的**液态选中高亮胶囊**，纯原生 Canvas 实现。
 *
 * 液态感来自：
 * 1. 非对称插值（前导边快、尾随边慢，飞行中拉丝）；
 * 2. 落点过冲 + 果冻二次弹跳；
 * 3. 拖动时按速度形变（前导边拉长、尾随边压缩）；
 * 4. 玻璃质感：竖向渐变 + 顶部高光 + 方向光晕尾巴；
 * 5. [liquid3D] 开关：更夸张的形变与光晕。
 */
class LiquidSelectionView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val sheenRect = RectF()

    private var fillGradient: LinearGradient? = null
    private var lastGradientColor = 0

    private var slots: List<FloatArray> = emptyList()

    private var color: Int = DEFAULT_COLOR
    private var radiusPx: Float = 22f * density
    private var padY: Float = 7f * density

    private var left = 0f
    private var right = 0f
    private var hasGeometry = false

    private var animator: ValueAnimator? = null
    private var squashAnimator: ValueAnimator? = null

    // ── 拖动形变状态 ──
    private var dragVelocity = 0f
    private var lastDragTime = 0L
    /** 果冻弹跳时的垂直挤压系数（1=正常，<1 压扁） */
    private var squashY = 1f

    /** 3D 液态模式：更夸张的形变与光晕，由宿主从设置写入 */
    var liquid3D: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        haloPaint.style = Paint.Style.FILL
        sheenPaint.style = Paint.Style.STROKE
        sheenPaint.strokeWidth = max(0.6f, 0.4f * density)
    }

    fun configure(color: Int, radiusPx: Float) {
        this.color = color
        this.radiusPx = radiusPx
        invalidate()
    }

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

    fun select(index: Int, animated: Boolean = true) {
        val target = slots.getOrNull(index) ?: return
        val tl = target[0] - target[1]
        val tr = target[0] + target[1]
        if (!hasGeometry) {
            left = tl; right = tr; hasGeometry = true
            invalidate()
            return
        }
        animator?.cancel()
        squashAnimator?.cancel()
        dragVelocity = 0f
        if (!animated) {
            left = tl; right = tr; hasGeometry = true; squashY = 1f; invalidate(); return
        }
        val fl = left
        val fr = right
        val movingRight = tr >= fr
        val distance = abs(tr - fr) + abs(tl - fl)
        val totalMs = (BASE_DURATION_MS + distance * MS_PER_PX)
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS).toLong()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = totalMs
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
                if (right - left < MIN_WIDTH_PX) {
                    val c = (left + right) / 2f
                    left = c - MIN_WIDTH_PX / 2f
                    right = c + MIN_WIDTH_PX / 2f
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    startSquash()
                }
            })
            start()
        }
    }

    /** 到位后垂直挤压回弹，模拟果冻落地 */
    private fun startSquash() {
        if (!liquid3D) return
        squashAnimator?.cancel()
        squashAnimator = ValueAnimator.ofFloat(1f, 0.90f, 1f).apply {
            duration = SQUASH_DURATION
            interpolator = OvershootInterpolator(SQUASH_TENSION)
            addUpdateListener { av ->
                squashY = av.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        dragVelocity = 0f
    }

    /**
     * 拖动中：胶囊水平平移 deltaX，并按速度形变（前导边拉长、尾随边压缩）。
     */
    fun dragBy(deltaX: Float) {
        if (!hasGeometry) return
        left += deltaX
        right += deltaX
        dragVelocity = deltaX
        lastDragTime = System.currentTimeMillis()

        if (liquid3D && abs(deltaX) > 0.5f) {
            val stretch = abs(deltaX) * STRETCH_FACTOR
            val midX = (left + right) / 2f
            val halfW = (right - left) / 2f
            if (deltaX > 0) {
                left = midX - halfW + stretch * 0.3f
                right = midX + halfW + stretch
            } else {
                left = midX - halfW - stretch
                right = midX + halfW - stretch * 0.3f
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasGeometry || slots.isEmpty()) return
        val h = height.toFloat()
        if (h <= 0f) return

        // 果冻弹跳：垂直挤压（上下边距动态调整）
        val effectivePadY = padY / squashY
        val top = effectivePadY
        val bottom = h - effectivePadY
        if (bottom - top <= 0f) return

        rect.set(left, top, right, bottom)
        val r = min(radiusPx, min(rect.width() / 2f, rect.height() / 2f))

        // 1) 外圈光晕：拖动时按速度方向偏移，形成液体尾巴
        haloPaint.shader = null
        haloPaint.color = withAlpha(color, HALO_ALPHA)
        val tailBoost = if (liquid3D) 2.2f else 1.0f
        val tailX = dragVelocity * TAIL_FACTOR * tailBoost
        canvas.drawRoundRect(
            rect.left - GLOW_INSET_PX + tailX * 0.3f,
            rect.top - GLOW_INSET_PX,
            rect.right + GLOW_INSET_PX + tailX,
            rect.bottom + GLOW_INSET_PX,
            r + GLOW_INSET_PX, r + GLOW_INSET_PX, haloPaint
        )

        // 2) 主体：上深下浅竖向渐变
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

        // 3) 顶部高光泽线
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
        squashAnimator?.cancel()
        animator = null
        squashAnimator = null
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

        // 更Q弹的过冲与拉丝
        private const val OVERSHOOT_TENSION = 1.8f
        private const val TRAIL_DECELERATE = 2.2f

        private const val HALO_ALPHA = 22
        private const val FILL_BOTTOM_ALPHA = 230
        private const val SHEEN_ALPHA = 46
        private const val MIN_WIDTH_PX = 8f
        private const val GLOW_INSET_PX = 2f

        // 拖动形变
        private const val STRETCH_FACTOR = 0.6f
        // 光晕尾巴
        private const val TAIL_FACTOR = 1.5f
        // 果冻弹跳
        private const val SQUASH_DURATION = 220L
        private const val SQUASH_TENSION = 1.6f
    }
}
