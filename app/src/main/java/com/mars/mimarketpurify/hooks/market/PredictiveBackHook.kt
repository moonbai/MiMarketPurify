package com.mars.mimarketpurify.hooks.market

import android.app.Application
import android.os.Build
import android.util.Log
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

/**
 * 预测性返回增强 Hook
 *
 * 针对 Android 15+ (API 35+) 行为变更：
 * - targetSdk >= 35 的应用被系统强制启用预测性返回（不可关闭）
 * - targetSdk < 35 的应用仍为 opt-in，需要应用自己设置
 *
 * 本 Hook 尝试：
 * 1. 反射设置目标 App 的 ApplicationInfo.enableOnBackInvokedCallback = true
 * 2. 如果系统已强制开启（API 35+），则无需此反射（但不影响）
 * 3. Hook Application.attachBaseContext / onCreate，确保在 Application 初始化阶段注入
 */
internal object PredictiveBackHook : BaseHook() {

    override val name: String = "预测性返回"

    override fun init() {
        if (!Settings.isEnabled(Settings.KEY_PREDICTIVE_BACK, true)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        try {
            hookApplicationAttach()
            Log.i(TAG, "PredictiveBackHook: 已初始化")
        } catch (e: Throwable) {
            Log.w(TAG, "PredictiveBackHook: 初始化失败", e)
        }
    }

    /**
     * Hook Application.attachBaseContext 与 onCreate，在最早时机注入预测性返回开关。
     *
     * 原理：
     * - attachBaseContext 是 Application 的最早生命周期回调，此时 ApplicationInfo 已可读写
     * - 修改 ApplicationInfo.enableOnBackInvokedCallback 后，系统后续初始化时会读取此值
     * - onCreate 再次设置，防止 attachBaseContext 中的修改被覆盖
     *
     * 限制：
     * - Android 15+ (API 35+) 的 enforceEnableOnBackInvokedCallback()
     *   会绕过 ApplicationInfo 的 flag，强制按 targetSdk 判断
     * - 因此此方案仅对 targetSdk < 35 的目标 App 有效
     * - 对 targetSdk >= 35 的应用，系统已强制开启，无需此 Hook
     */
    private fun hookApplicationAttach() {
        // 方案 1：attachBaseContext，最早时机（先 proceed 放行原生逻辑，再注入开关）
        Application::class.java.methodFinder()
            .filterByName("attachBaseContext")
            .filterByParamCount(1)
            .first()
            .hooked {
                val result = proceed()
                (thisObject as? Application)?.let { applyPredictiveBack(it) }
                result
            }

        // 方案 2：onCreate 后再次确认，防止被覆盖
        Application::class.java.methodFinder()
            .filterByName("onCreate")
            .filterByParamCount(0)
            .first()
            .hooked {
                val result = proceed()
                (thisObject as? Application)?.let { applyPredictiveBack(it) }
                result
            }
    }

    private fun applyPredictiveBack(app: Application) {
        val appInfo = app.applicationInfo
        val pkgName = app.packageName ?: return

        try {
            val clazz = appInfo::class.java
            val field = clazz.getField("enableOnBackInvokedCallback")
            val current = field.getBoolean(appInfo)

            if (!current) {
                field.setBoolean(appInfo, true)
                Log.i(TAG, "PredictiveBackHook: 已设置 enableOnBackInvokedCallback = true (包=$pkgName)")
            } else {
                Log.d(TAG, "PredictiveBackHook: enableOnBackInvokedCallback 已为 true")
            }
        } catch (e: NoSuchFieldException) {
            Log.d(TAG, "PredictiveBackHook: enableOnBackInvokedCallback 字段不存在 (API < 33)")
        } catch (e: Throwable) {
            Log.w(TAG, "PredictiveBackHook: 反射失败", e)
        }
    }
}
