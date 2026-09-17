package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder

object HideFruitEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_FRUIT

    override val name: String
        get() = "屏蔽领水果入口"

    private const val RES_NAME = "entrance_gif"
    private const val RES_TYPE = "id"

    @Volatile
    private var fruitId: Int = -1

    override fun init() {
        // 1) 视图挂接到窗口时立即隐藏
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    ensureId(view)
                    if (fruitId != -1 && view.id == fruitId) {
                        debugLog("onAttachedToWindow: 隐藏领水果入口 view=$view")
                        view.visibility = View.GONE
                    }
                    return@hooked null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 安装失败", it)
        }

        // 2) 目标被重新设为 VISIBLE 时强制拦截为 GONE
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val view = thisObject as? View
                    if (view != null && fruitId != -1 && view.id == fruitId) {
                        val vis = (args[0] as? Int) ?: return@hooked proceed()
                        if (vis == View.VISIBLE) {
                            debugLog("setVisibility: 拦截领水果 VISIBLE → GONE")
                            view.visibility = View.GONE
                            return@hooked null
                        }
                    }
                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 安装失败", it)
        }

        debugLog("hook 已安装, RES_NAME=$RES_NAME")
    }

    private fun ensureId(view: View) {
        if (fruitId != -1) return
        synchronized(this) {
            if (fruitId == -1) {
                fruitId = runCatching {
                    view.resources.getIdentifier(RES_NAME, RES_TYPE, view.context.packageName)
                }.getOrDefault(-1)
                debugLog("ensureId: resId=0x${fruitId.toString(16)}")
            }
        }
    }
}
