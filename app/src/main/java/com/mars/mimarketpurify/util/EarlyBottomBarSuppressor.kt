package com.mars.mimarketpurify.util

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import java.util.IdentityHashMap

/**
 * 在 Compose 悬浮栏注入前持续压制官方底栏（alpha=0 + 无障碍屏蔽），避免「原生底栏先闪一帧
 * 再被替换」的闪烁。浮层就绪后调用 [restore] 还原官方状态。
 * 移植自 AritxOnly/HyperModifier 的 EarlyBottomBarSuppressor（纯原生逻辑，无需 Compose）。
 */
internal class EarlyBottomBarSuppressor(
    activity: Activity,
    private val findBottomBar: () -> View?,
    private val onSuppress: () -> Unit = {},
    private val onRestore: () -> Unit = {},
) : ViewTreeObserver.OnPreDrawListener {
    private val decorView = activity.window.decorView
    private val originalStates = IdentityHashMap<View, OriginalBottomBarState>()
    private var observing = false
    private var currentBottomBar: View? = null

    fun start() {
        if (!observing && decorView.viewTreeObserver.isAlive) {
            decorView.viewTreeObserver.addOnPreDrawListener(this)
            observing = true
        }
        suppressNow()
    }

    override fun onPreDraw(): Boolean {
        suppressNow()
        return true
    }

    /** Restore immediately; callers may create the permanent host in the same main-thread turn. */
    fun restore() {
        stopObserving()
        runCatching(onRestore)
        originalStates.forEach { (view, state) ->
            view.alpha = state.alpha
            view.importantForAccessibility = state.importantForAccessibility
        }
        originalStates.clear()
        currentBottomBar = null
    }

    private fun suppressNow() {
        runCatching(onSuppress)
        val bottomBar = currentBottomBar?.takeIf(View::isAttachedToWindow)
            ?: runCatching(findBottomBar).getOrNull()?.also { currentBottomBar = it }
            ?: return
        originalStates.getOrPut(bottomBar) {
            OriginalBottomBarState(
                alpha = bottomBar.alpha,
                importantForAccessibility = bottomBar.importantForAccessibility,
            )
        }
        if (bottomBar.alpha != 0f) bottomBar.alpha = 0f
        if (bottomBar.importantForAccessibility !=
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        ) {
            bottomBar.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    private fun stopObserving() {
        if (observing && decorView.viewTreeObserver.isAlive) {
            decorView.viewTreeObserver.removeOnPreDrawListener(this)
        }
        observing = false
    }
}

private data class OriginalBottomBarState(
    val alpha: Float,
    val importantForAccessibility: Int,
)
