package com.mars.mimarketpurify.init

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.TAG
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

/**
 * 按资源 id 隐藏指定 View 的通用 hook 模板。
 *
 * 原来 HideFruitEntry / HideUpdateAll / HideAutoUpdateSwitch 三个文件各自实现了一遍
 * 「惰性解析资源 id + onAttachedToWindow 命中隐藏 + setVisibility 强拦回 GONE」，
 * 结构几乎完全相同。这里抽成模板基类，三个功能改为配置化：
 *  - [resName]：目标控件资源名（id 类型）；
 *  - [fallbackHit]：资源名漂移时的文本兜底判定（可覆写）。
 *
 * 行为与原实现保持一致：
 *  - 视图挂接窗口时命中即 GONE（并禁用点击）；
 *  - 商店代码重新 setVisibility(VISIBLE) 时强拦为 GONE，防止滑动/刷新闪现；
 *  - 资源 id 只解析一次（volatile + synchronized 双重检查）。
 */
abstract class ResIdHiderHook(
    private val resName: String,
    private val resType: String = "id"
) : BaseHook() {

    @Volatile
    private var targetId: Int = -1

    /** 资源名未命中时的兜底判定，默认关闭 */
    protected open fun fallbackHit(view: View): Boolean = false

    final override fun init() {
        // 视图挂接窗口时命中即隐藏
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    resolveId(view)
                    if (isTarget(view)) {
                        debugLog("onAttachedToWindow: 隐藏 $resName view=$view")
                        view.visibility = View.GONE
                        view.isClickable = false
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow Hook 失败", it)
        }

        // 商店强设 VISIBLE 时压回 GONE（列表滑动 / 刷新 / 局部重绘防残留闪现）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View ?: return@hooked proceed()
                    val vis = (args[0] as? Int) ?: return@hooked proceed()
                    resolveId(view)
                    if (isTarget(view) && vis == View.VISIBLE) {
                        debugLog("setVisibility: 拦截 $resName VISIBLE → GONE")
                        view.visibility = View.GONE
                        return@hooked null
                    }
                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility Hook 失败", it)
        }
    }

    protected fun isTarget(view: View): Boolean =
        (targetId != -1 && view.id == targetId) || fallbackHit(view)

    private fun resolveId(view: View) {
        if (targetId != -1) return
        synchronized(this) {
            if (targetId == -1) {
                targetId = runCatching {
                    view.resources.getIdentifier(resName, resType, view.context.packageName)
                }.getOrDefault(-1)
                debugLog("resolveId: $resName → 0x${targetId.toString(16)}")
            }
        }
    }
}
