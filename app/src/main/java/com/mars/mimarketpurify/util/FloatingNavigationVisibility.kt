package com.mars.mimarketpurify.util

import android.view.View
import android.view.WindowManager

/**
 * 控制注入浮层的可见性：140ms alpha 淡入淡出 + 无障碍属性切换。
 * 注意：本模块浮层是挂在宿主 decorView 下的子 View（非独立 Window），因此不操作
 * WindowManager flags（[windowManager] 传 null 即可）。
 * 移植自 AritxOnly/HyperModifier 的 FloatingNavigationVisibility。
 */
internal class FloatingNavigationVisibility(
    private val view: View,
    private val windowManager: WindowManager? = null,
) {
    private var originalAccessibility: Int? = null
    private var targetVisible: Boolean? = null
    private var disposed = false

    fun setVisible(visible: Boolean) {
        if (disposed) return
        if (targetVisible == visible) {
            updateWindowTouchable(visible)
            return
        }
        val accessibility = originalAccessibility ?: view.importantForAccessibility.also {
            originalAccessibility = it
        }
        val previous = targetVisible
        targetVisible = visible
        view.animate().cancel()
        view.importantForAccessibility = if (visible) accessibility else
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val touchReady = updateWindowTouchable(visible)

        if (previous == null || !view.isAttachedToWindow || (!visible && !touchReady)) {
            view.alpha = if (visible) 1f else 0f
            view.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }
        if (visible) {
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(FADE_DURATION_MS).start()
        } else {
            view.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction {
                if (!disposed && targetVisible == false) view.visibility = View.GONE
            }.start()
        }
    }

    /** Called after a separate application-panel window has been attached. */
    fun onAttached() {
        if (!disposed) updateWindowTouchable(targetVisible != false)
    }

    fun dispose() {
        disposed = true
        view.animate().cancel()
        originalAccessibility?.let { view.importantForAccessibility = it }
    }

    private fun updateWindowTouchable(touchable: Boolean): Boolean {
        val manager = windowManager ?: return true
        if (!view.isAttachedToWindow) return true
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return true
        val flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (params.flags == flags) return true
        val previousFlags = params.flags
        params.flags = flags
        return runCatching { manager.updateViewLayout(view, params) }.onFailure {
            params.flags = previousFlags
        }.isSuccess
    }

    private companion object {
        const val FADE_DURATION_MS = 140L
    }
}
