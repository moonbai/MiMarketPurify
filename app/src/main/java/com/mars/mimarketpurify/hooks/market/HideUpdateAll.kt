package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

object HideUpdateAll : BaseHook() {

    override val prefKey: String = Settings.KEY_HIDE_UPDATE_ALL
    override val name: String = "隐藏更新页「全部更新」按钮"

    private const val RES_NAME = "update_all_visibility_frame"
    private const val RES_TYPE = "id"

    @Volatile
    private var targetFrameId: Int = -1

    override fun init() {
        // 视图挂载时识别并隐藏（和 HideFruitEntry 逻辑对齐）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    resolveResId(view)
                    if (targetFrameId != -1 && view.id == targetFrameId) {
                        view.visibility = View.GONE
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow Hook 失败", it)
        }

        // 拦截 setVisibility，商店强行设 VISIBLE 时压回 GONE
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View
                    if (view != null && targetFrameId != -1 && view.id == targetFrameId) {
                        val vis = (args[0] as? Int) ?: return@hooked proceed()
                        if (vis == View.VISIBLE) {
                            view.visibility = View.GONE
                            return@hooked null
                        }
                    }
                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility Hook 失败", it)
        }
    }

    private fun resolveResId(view: View) {
        if (targetFrameId != -1) return
        synchronized(this) {
            if (targetFrameId == -1) {
                targetFrameId = runCatching {
                    view.resources.getIdentifier(RES_NAME, RES_TYPE, view.context.packageName)
                }.getOrDefault(-1)
            }
        }
    }
}
