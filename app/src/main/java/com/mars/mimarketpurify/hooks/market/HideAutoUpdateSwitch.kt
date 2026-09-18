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
    override val name: String = "隐藏更新界面自动升级开关"

    private const val RES_NAME = "auto_item"
    private const val RES_TYPE = "id"

    @Volatile
    private var targetItemId: Int = -1

    override fun init() {
        // 视图挂载识别并隐藏
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    resolveResId(view)
                    if (targetItemId != -1 && view.id == targetItemId) {
                        view.visibility = View.GONE
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow Hook 失败", it)
        }

        // 拦截商店重新置为 VISIBLE（对抗刷新/滑动复现）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View
                    if (view != null && targetItemId != -1 && view.id == targetItemId) {
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
        if (targetItemId != -1) return
        synchronized(this) {
            if (targetItemId == -1) {
                targetItemId = runCatching {
                    view.resources.getIdentifier(RES_NAME, RES_TYPE, view.context.packageName)
                }.getOrDefault(-1)
            }
        }
    }
}
