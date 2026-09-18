// HideUpdateAll.kt
package com.mars.mimarketpurify.hooks.market

import android.view.View
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object HideUpdateAll : BaseHook() {
    override val prefKey: String = Settings.KEY_HIDE_UPDATE_ALL
    override val name: String = "隐藏更新页「全部更新」按钮"

    override fun init() {
        val targetCls = ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListActivity") ?: return

        // 和 HideSecurityView 保持完全一致的链式写法
        targetCls.methodFinder()
            .filterByName("onCreate")
            .first()
            .hooked {
                proceed()
                val activity = thisObject as android.app.Activity
                activity.window?.decorView?.post {
                    // 反射获取资源ID，避免直接依赖小米市场R类
                    fun getResId(name: String): Int {
                        return try {
                            val rCls = Class.forName("com.xiaomi.market.R\$id")
                            rCls.getField(name).getInt(null)
                        } catch (_: Throwable) { -1 }
                    }

                    val panelId = getResId("update_all_panel")
                    val btnId = getResId("update_all_button")

                    if (panelId != -1) {
                        activity.findViewById<View>(panelId)?.visibility = View.GONE
                    }
                    if (btnId != -1) {
                        activity.findViewById<View>(btnId)?.visibility = View.GONE
                    }
                }
            }
    }
}
