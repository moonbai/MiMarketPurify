package com.mars.mimarketpurify.hooks.market

import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object HideUpdateAllButton : BaseHook() {

    override val prefKey: String = Settings.KEY_HIDE_UPDATE_ALL // 记得在 Settings 里补这个常量

    override val name: String
        get() = "隐藏更新页「全部更新」按钮"

    override fun init() {
        val targetCls = ClassUtil.loadClass("com.xiaomi.market.ui.UpdateListActivity")

        // hook onCreate，拿到后延迟查找 View（避免还没 inflate）
        methodFinder(targetCls)
            .filterByName("onCreate")
            .first()
            .hooked {
                proceed()
                val activity = thisObject as android.app.Activity
                activity.window?.decorView?.post {
                    // 先藏整个 update_all_panel（包含背景、文字、按钮，整块可点击区）
                    val allPanel = activity.findViewById<View>(com.xiaomi.market.R.id.update_all_panel)
                    allPanel?.visibility = View.GONE

                    // 冗余兜底：单独再把 update_all_button 也置 gone，防止新版布局拆分
                    val allBtn = activity.findViewById<View>(com.xiaomi.market.R.id.update_all_button)
                    allBtn?.visibility = View.GONE
                }
            }
    }
}
