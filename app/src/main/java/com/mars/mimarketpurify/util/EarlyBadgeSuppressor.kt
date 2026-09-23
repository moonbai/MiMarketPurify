package com.mars.mimarketpurify.util

import android.app.Activity
import android.os.SystemClock
import android.view.ViewTreeObserver

/**
 * 底栏角标「早压制」守卫——机制移植自 HyperModifier 的 `EarlyBottomBarSuppressor`，
 * 但目标从「压制整条底栏等待 Compose 覆盖层」改为「按帧压制底栏角标防闪现/复现」，
 * 且**纯原生实现，不引入任何 UI 框架**。
 *
 * 为什么需要它：商店在 Activity 首帧前后会陆续把红点 / 数字角标设回 TabView，
 * 仅 Hook `TabView.setNumber` 这类方法只能拦「新设置」，拦不住已经 inflate 出来的
 * 角标，也可能在布局刷新时先显示一帧再被隐藏（肉眼可见的闪烁）。
 *
 * 这里用 [ViewTreeObserver.OnPreDrawListener] 在**每一帧绘制前**把角标压回 GONE，
 * 直到达到帧数上限或时间上限后自动移除监听，避免像旧实现那样监听永不移除、无限空转。
 *
 * 典型用法（在主线程）：
 * ```
 * EarlyBadgeSuppressor(activity).start()
 * ```
 */
class EarlyBadgeSuppressor(
    private val activity: Activity,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : ViewTreeObserver.OnPreDrawListener {

    private val decor = activity.window?.decorView
    private val startedAt = SystemClock.uptimeMillis()
    private var frames = 0
    private var observing = false

    /** 开始监听并立即压制一次。重复调用安全。 */
    fun start() {
        val vto = decor?.viewTreeObserver ?: return
        if (!observing && vto.isAlive) {
            vto.addOnPreDrawListener(this)
            observing = true
        }
        suppressNow()
    }

    override fun onPreDraw(): Boolean {
        frames++
        suppressNow()
        if (frames >= maxFrames || SystemClock.uptimeMillis() - startedAt >= timeoutMs) {
            stop()
        }
        // 返回 true：不取消本帧绘制，仅顺带修正角标可见性
        return true
    }

    private fun suppressNow() {
        runCatching {
            if (!activity.isFinishing && !activity.isDestroyed) {
                NativeTabBar.hideAllBadges(activity)
            }
        }
    }

    /** 立即停止并移除监听。Activity 销毁或达到上限时调用。 */
    fun stop() {
        val vto = decor?.viewTreeObserver
        if (observing && vto != null && vto.isAlive) {
            vto.removeOnPreDrawListener(this)
        }
        observing = false
    }

    companion object {
        /** 约 40 帧（60Hz 下 <1s）足以覆盖首帧 inflate + 首轮数据回填。 */
        private const val DEFAULT_MAX_FRAMES = 40

        /** 时间上限兜底：低刷屏或掉帧时按墙钟时间收手。 */
        private const val DEFAULT_TIMEOUT_MS = 3_000L
    }
}
