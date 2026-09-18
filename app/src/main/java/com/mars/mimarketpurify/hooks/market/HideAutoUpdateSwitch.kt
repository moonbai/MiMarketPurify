package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

object HideAutoUpdateSwitch : BaseHook() {
    override val prefKey: String = Settings.KEY_HIDE_AUTO_UPDATE_SWITCH
    override val name: String = "隐藏自动升级开关"

    // ✅ 修正为你抓到的真实根ID资源名
    private const val RES_NAME = "auto_update_item_open"
    private const val RES_TYPE = "id"

    @Volatile
    private var targetItemId: Int = -1

    override fun init() {
        // 解析资源ID（只解析一次）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    resolveResId(view)
                    // 命中根条目直接隐藏；额外加文本兜底防ID改名漂移
                    if (targetItemId != -1 && view.id == targetItemId) {
                        view.visibility = View.GONE
                        view.isClickable = false
                    }
                    // 兜底：如果ID没命中，但子View包含"自动升级"+"WLAN网络下"，也隐藏（对抗未来资源名变更）
                    else if (containsAutoUpdateText(view)) {
                        view.visibility = View.GONE
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow Hook 失败", it)
        }

        // ✅ 强拦截 setVisibility：列表滑动、刷新、局部重绘时强制锁死 GONE，解决残留闪现
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View ?: return@hooked proceed()
                    val targetVis = args[0] as? Int ?: return@hooked proceed()

                    resolveResId(view)
                    val shouldBlock =
                        (targetItemId != -1 && view.id == targetItemId) || containsAutoUpdateText(view)

                    if (shouldBlock && targetVis == View.VISIBLE) {
                        args[0] = View.GONE
                    }
                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility Hook 失败", it)
        }
    }

    private fun resolveResId(view: View) {
        if (targetItemId != -1) return
        synchronized(this) {
            if (targetItemId == -1) {
                targetItemId = runCatching {
                    view.resources.getIdentifier(RES_NAME, RES_TYPE, view.context.packageName)
                }.getOrDefault(-1)
            }
        }
    }

    // 兜底递归文本匹配，专门对付「ID没变但部分子View先被单独处理、留下骨架残留」的场景
    private fun containsAutoUpdateText(view: View): Boolean {
        if (view is android.widget.TextView) {
            val txt = view.text ?: return false
            return txt.contains("自动升级") && txt.contains("WLAN网络下")
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (containsAutoUpdateText(child)) return true
            }
        }
        return false
    }
}
