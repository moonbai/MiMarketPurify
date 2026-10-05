package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

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
 * 3. Hook Activity.attachBaseContext，确保在 Application 初始化阶段注入
 */
internal object PredictiveBackHook : BaseHook() {

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
     * Hook Application.attachBaseContext，在最早时机注入预测性返回开关。
     *
     * 原理：
     * - attachBaseContext 是 Application 的最早生命周期回调
     * - 此时 ApplicationInfo 已可读写，但 Activity 尚未创建
     * - 修改 ApplicationInfo.enableOnBackInvokedCallback 后，
     *   系统在后续 Activity 初始化时会读取此值
     *
     * 限制：
     * - Android 15+ (API 35+) 的 enforceEnableOnBackInvokedCallback()
     *   会绕过 ApplicationInfo 的 flag，强制按 targetSdk 判断
     * - 因此此方案仅对 targetSdk < 35 的目标 App 有效
     * - 对 targetSdk >= 35 的应用，系统已强制开启，无需此 Hook
     */
    private fun hookApplicationAttach() {
        val context = HookEnv.hostContext ?: return
        val pkgName = context.packageName ?: return

        // 方案 1：直接反射修改（最可靠）
        try {
            val appInfo = context.applicationInfo
            val clazz = appInfo.javaClass

            // ApplicationInfo.enableOnBackInvokedCallback
            // API 33+ 才有此字段
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

        // 方案 2：Hook Application.onCreate 后再次确认
        try {
            val appClass = context.javaClass
            appClass.methodFinder()
                .filterByName("onCreate")
                .filterByParamCount(0)
                .first()
                .hooked {
                    // onCreate 后再次设置，防止 attachBaseContext 中的修改被覆盖
                    val appInfo = context.applicationInfo
                    val field = appInfo.javaClass.getField("enableOnBackInvokedCallback")
                    if (!field.getBoolean(appInfo)) {
                        field.setBoolean(appInfo, true)
                        Log.i(TAG, "PredictiveBackHook: onCreate 后重新设置 enableOnBackInvokedCallback")
                    }
                }
        } catch (_: Throwable) {
            // 静默失败
        }
    }
}
