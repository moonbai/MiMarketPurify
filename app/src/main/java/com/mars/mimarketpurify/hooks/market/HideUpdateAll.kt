package com.mars.mimarketpurify.hooks.market

import android.view.View
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Method

object HideUpdateAll : BaseHook() {
    override val prefKey: String = Settings.KEY_HIDE_UPDATE_ALL
    override val name: String = "隐藏更新页「全部更新」按钮"

    // 反射拿资源ID
    private fun getResId(name: String): Int {
        return try {
            val rCls = Class.forName("com.xiaomi.market.R\$id")
            rCls.getField(name).getInt(null)
        } catch (_: Throwable) { -1 }
    }

    // 给View强制锁死 GONE，并且拦截后续 setVisibility
    private fun lockViewGone(view: View?) {
        view ?: return
        view.visibility = View.GONE
        // Hook 这个View实例的 setVisibility，不让改回来
        try {
            val setVis: Method = View::class.java.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            setVis.hooked {
                if (it.args[0] != View.GONE) {
                    it.args[0] = View.GONE
                }
                it.proceed()
            }
        } catch (_: Throwable) {}
    }

    override fun init() {
        val targetCls = ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListActivity") ?: return

        targetCls.methodFinder()
            .filterByName("onCreate")
            .first()
            .hooked {
                proceed()
                val activity = thisObject as android.app.Activity
                activity.window?.decorView?.post {
                    val frameId = getResId("update_all_visibility_frame")
                    val panelId = getResId("update_all_panel")
                    val btnId = getResId("update_all_button")

                    lockViewGone(activity.findViewById(frameId))
                    lockViewGone(activity.findViewById(panelId))
                    lockViewGone(activity.findViewById(btnId))
                }
            }

        // 返回/刷新兜底
        targetCls.methodFinder()
            .filterByName("onResume")
            .first()
            .hooked {
                proceed()
                val activity = thisObject as android.app.Activity
                activity.window?.decorView?.post {
                    val frameId = getResId("update_all_visibility_frame")
                    val panelId = getResId("update_all_panel")
                    val btnId = getResId("update_all_button")

                    lockViewGone(activity.findViewById(frameId))
                    lockViewGone(activity.findViewById(panelId))
                    lockViewGone(activity.findViewById(btnId))
                }
            }
    }
}
