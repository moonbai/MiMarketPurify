package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.ComposeFloatingBarHost
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * **悬浮底栏**（纯原生自绘）：把商店原本贴边的底栏，替换成一枚悬浮在页面底部、
 * 圆角胶囊形的浮层导航栏，并让页面内容真正延伸到底栏之下。
 *
 * 设计参考 `AritxOnly/HyperModifier` 中针对应用商店底栏的实现，
 * 但**只移植其 Hook 机制，不引入 Compose / Miuix KMP**——浮层全部用
 * android.view 原生控件手写，图标直接拷贝商店自己的 Drawable，
 * 点击转发给原生 TabView，导航与埋点语义完全不变。
 *
 * 生效方式（无需重启应用商店）：
 * - Hook 主 Activity 的 `onCreate` / `onResume` / `onDestroy`；
 * - **不使用** [BaseHook.hooked] 的开关门控（那样关掉开关时连「卸载浮层」的逻辑
 *   也会被跳过，用户关掉开关后浮层会一直挂在屏幕上）；改为在拦截体内**实时读开关**：
 *   开启 → 挂载宿主；关闭 → 释放宿主并还原原生底栏；
 * - 首次进入时原生底栏可能还没 inflate，用 postDelayed 按真实时间（8s）重试，
 *   带上限自动放弃，避免无限空转。
 */
object FloatingBottomBar : BaseHook() {

    override val prefKey: String = Settings.KEY_FLOATING_BAR

    override val name: String get() = "悬浮底栏"

    /** 属于视觉增强，默认关闭，由用户在主页主动开启 */
    override val defaultEnabled: Boolean get() = false

    private const val MAIN_ACTIVITY =
        "com.xiaomi.market.business_ui.main.MarketTabActivity"

    /** 等待底栏 inflate 的重试任务，Activity 销毁时移除。 */
    private val pendingRetry = WeakHashMap<Activity, Runnable>()

    /** 已经装过生命周期 hook 的方法，避免重复安装。 */
    private val installed = HashSet<String>()

    private val lifecycleMethods = setOf("onCreate", "onResume", "onDestroy")

    override fun init() {
        runCatching {
            val clazz = ClassUtil.loadClass(MAIN_ACTIVITY)
            clazz.methodFinder()
                .filter { name in lifecycleMethods }
                .forEach { installLifecycleHook(it) }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 MarketTabActivity，跳过", it)
        }
    }

    private fun installLifecycleHook(method: Method) {
        val key = "${method.declaringClass.name}#${method.name}/${method.parameterTypes.size}"
        if (!installed.add(key)) return

        HookEnv.base.hook(method).intercept { param ->
            // 先放行原生逻辑，保证生命周期状态正常
            val result = param.proceed()
            val activity = param.thisObject as? Activity

            if (activity != null) {
                when (method.name) {
                    "onDestroy" -> {
                        cancelRetry(activity)
                        ComposeFloatingBarHost.release(activity)
                    }
                    // onCreate / onResume：按当前开关状态决定挂载或卸载
                    else -> applyTo(activity)
                }
            }
            result
        }
    }

    /**
     * 依据**实时**开关状态同步悬浮底栏：
     * - 关闭：若已挂载则释放（还原原生底栏全部改动）；
     * - 开启：尝试挂载；拿不到原生底栏 View 时按帧重试一小会儿。
     */
    private fun applyTo(activity: Activity) {
        val on = enabled()
        val existing = ComposeFloatingBarHost.get(activity)

        if (!on) {
            if (existing != null) {
                cancelRetry(activity)
                ComposeFloatingBarHost.release(activity)
                debugLog("开关关闭：已卸载悬浮底栏并还原原生底栏")
            }
            cancelRetry(activity)
            return
        }
        if (existing != null) return

        // onCreate 时 View 往往尚未 inflate，post 到下一帧再试
        activity.window?.decorView?.post { tryAttach(activity) }
        if (ComposeFloatingBarHost.get(activity) == null) scheduleRetry(activity)
    }

    private fun tryAttach(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed || !enabled()) return false
        // 先记录是否已存在，避免「复用已有宿主」也被记成一次新挂载（日志刷屏）
        val existed = ComposeFloatingBarHost.get(activity) != null
        val host = runCatching { ComposeFloatingBarHost.attach(activity) }.getOrNull()
        if (host == null) {
            debugLog("attach 失败：未找到原生底栏 View，继续按帧重试")
            return false
        }
        if (!existed) debugLog("悬浮底栏已挂载")
        return true
    }

    /**
     * 按**真实时间**重试挂载（对齐 HyperModifier 的 postDelayed 重试）：底栏可能晚于 onCreate 才
     * inflate，用 postDelayed 固定间隔轮询、以 wall-clock 8s 为上限——不受帧率影响。
     * 早期用 OnPreDrawListener + 帧数上限的写法，在商店高速渲染时会 0.3s 内跑满 240 帧提前放弃，
     * 导致底栏还没出来就停止重试、永远挂不上。
     */
    private fun scheduleRetry(activity: Activity) {
        if (pendingRetry.containsKey(activity)) return
        val decor = activity.window?.decorView ?: return
        val startedAt = android.os.SystemClock.uptimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed || !enabled()) {
                    cancelRetry(activity)
                    return
                }
                if (tryAttach(activity)) {
                    cancelRetry(activity)
                    return
                }
                if (android.os.SystemClock.uptimeMillis() - startedAt >= RETRY_TIMEOUT_MS) {
                    debugLog("重试超时（${RETRY_TIMEOUT_MS}ms），放弃本次悬浮底栏挂载")
                    cancelRetry(activity)
                    return
                }
                decor.postDelayed(this, RETRY_INTERVAL_MS)
            }
        }
        pendingRetry[activity] = runnable
        decor.postDelayed(runnable, RETRY_INTERVAL_MS)
    }

    private fun cancelRetry(activity: Activity) {
        pendingRetry.remove(activity)?.let { runnable ->
            activity.window?.decorView?.removeCallbacks(runnable)
        }
    }

    // 与 HyperModifier 对齐：底栏可能晚于 onCreate 才 inflate，给足 8s 重试窗口。
    private const val RETRY_INTERVAL_MS = 32L
    private const val RETRY_TIMEOUT_MS = 8_000L
}
