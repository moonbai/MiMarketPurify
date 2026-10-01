package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.NativeTabBar
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * 崩溃兜底（「移花接木」更新 TabView 的无障碍降级）。
 *
 * 背景：被注入的原生「更新」TabView（tag=`purify_update`）在部分商店版本下，其无障碍节点
 * `createAccessibilityNodeInfo` 会尝试解析一个已被 AndResGuard 剥离、并不存在的字符串资源，
 * 抛出 `android.content.res.Resources$NotFoundException`，并在无障碍服务预取节点时直接闪退
 * （见 AppErrorsTracking 上报，设备 Android 17 / 商店 4.126.10）。
 *
 * 该崩溃与「悬浮底栏」开关**无关**——即便悬浮底栏处于默认的关闭状态，原生底栏完整暴露、注入的
 * 更新 tab 仍在，依旧会触发。而既有的同款降级逻辑写在 [com.mars.mimarketpurify.util.ComposeFloatingBarHost]
 * 内，只有悬浮底栏挂载时（开关开启）才会执行，覆盖不到默认场景。
 *
 * 修复：每当商店主页面进入（onResume）时，定位注入的更新 TabView，将其
 * `importantForAccessibility` 降级为 `NO`。`NO` 会把这个 View 自身（连同子树）移出无障碍树，
 * 于是无障碍框架不再对它调用 `createAccessibilityNodeInfo`，从而跳过那个会崩溃的资源解析。
 * 本 guard 只要「更新入口」开关开启（默认开启、注入 tab 存在）就**无条件**执行，不依赖
 * 悬浮底栏是否挂载。
 *
 * 定位与重试：底栏可能在 onResume 时尚未 inflate，故先在下一帧同步试一次，失败再按帧重试
 * 一小会儿（带帧数 / 时长上限），活动销毁时移除监听，避免泄漏与堆积。
 */
object UpdateTabA11yGuard : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB

    override val name: String get() = "更新Tab无障碍兜底"

    /** 注入的更新 tab 的 tag，须与 TabFilter.PURIFY_UPDATE 保持一致。 */
    private const val UPDATE_TAB_TAG = "purify_update"

    private const val MAIN_ACTIVITY =
        "com.xiaomi.market.business_ui.main.MarketTabActivity"

    /** 每个 Activity 的按帧重试监听，onDestroy 时移除，避免泄漏与堆积。 */
    private val pendingRetry = WeakHashMap<Activity, ViewTreeObserver.OnPreDrawListener>()

    override fun init() {
        runCatching {
            ClassUtil.loadClass(MAIN_ACTIVITY)
                .methodFinder()
                .filter { name in setOf("onCreate", "onResume", "onDestroy") }
                .forEach { installHook(it) }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 MarketTabActivity，跳过", null)
        }
    }

    private fun installHook(method: Method) {
        method.hooked {
            when (method.name) {
                "onDestroy" -> {
                    (thisObject as? Activity)?.let { cancelRetry(it) }
                    proceed()
                }
                // onCreate / onResume：先放行原生逻辑，再对注入 tab 做无障碍降级
                else -> {
                    val result = proceed()
                    (thisObject as? Activity)?.let { guard(it) }
                    result
                }
            }
        }
    }

    /**
     * 进入页面时把注入的更新 TabView 的无障碍重要度降级。
     * 底栏可能尚未 inflate，先 post 到下一帧同步尝试一次，失败再按帧重试一小会儿。
     */
    private fun guard(activity: Activity) {
        if (!enabled() || activity.isFinishing || activity.isDestroyed) return
        activity.window?.decorView?.post {
            if (!tryApply(activity)) scheduleRetry(activity)
        }
    }

    /**
     * 找到注入的更新 TabView 并把它降级为 `IMPORTANT_FOR_ACCESSIBILITY_NO`。
     * 找不到（底栏未 inflate 或注入 tab 不存在）时返回 false，由调用方决定是否重试。
     */
    private fun tryApply(activity: Activity): Boolean {
        if (!enabled() || activity.isFinishing || activity.isDestroyed) return false
        val tab = runCatching {
            NativeTabBar.tabViews(activity)
                .firstOrNull { NativeTabBar.tagOf(it) == UPDATE_TAB_TAG }
        }.getOrNull() ?: return false
        if (tab.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO) {
            tab.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            debugLog("已将注入的「更新」TabView 无障碍重要度降级，规避 Resources\$NotFoundException")
        }
        return true
    }

    /** 按帧重试定位注入 tab，带上限；期间顺手压制原生底栏，避免它先闪一帧再被替换。 */
    private fun scheduleRetry(activity: Activity) {
        if (pendingRetry.containsKey(activity)) return
        val decor = activity.window?.decorView ?: return
        val observer = decor.viewTreeObserver.takeIf { it.isAlive } ?: return

        val startedAt = android.os.SystemClock.uptimeMillis()
        var frames = 0
        val listener = ViewTreeObserver.OnPreDrawListener {
            frames++
            runCatching {
                if (!enabled() || activity.isFinishing || activity.isDestroyed) {
                    cancelRetry(activity)
                    return@OnPreDrawListener true
                }
                if (tryApply(activity)) {
                    cancelRetry(activity)
                } else if (frames >= RETRY_MAX_FRAMES ||
                    android.os.SystemClock.uptimeMillis() - startedAt >= RETRY_TIMEOUT_MS
                ) {
                    debugLog("更新Tab兜底重试超限（$frames 帧），放弃本次")
                    cancelRetry(activity)
                }
            }
            true
        }
        observer.addOnPreDrawListener(listener)
        pendingRetry[activity] = listener
    }

    private fun cancelRetry(activity: Activity) {
        pendingRetry.remove(activity)?.let { listener ->
            val vto = activity.window?.decorView?.viewTreeObserver
            if (vto != null && vto.isAlive) runCatching { vto.removeOnPreDrawListener(listener) }
        }
    }

    private const val RETRY_MAX_FRAMES = 60
    private const val RETRY_TIMEOUT_MS = 4_000L
}
