package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.ResIdHiderHook

object HideAutoUpdateSwitch : ResIdHiderHook(resName = "auto_update_item_open") {

    override val prefKey: String = Settings.KEY_HIDE_AUTO_UPDATE_SWITCH

    override val name: String
        get() = "隐藏自动升级开关"

    private const val TARGET_ACTIVITY = "com.xiaomi.market.ui.UpdateListActivity"

    private fun isTargetActivity(ctx: Context?): Boolean {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c.javaClass.name == TARGET_ACTIVITY
            c = c.baseContext
        }
        return false
    }

    override fun fallbackHit(view: View): Boolean {
        if (!isTargetActivity(view.context)) return false
        return containsAutoUpdateText(view)
    }

    private fun containsAutoUpdateText(view: View): Boolean {
        if (view is TextView) {
            val txt = view.text ?: return false
            return txt.contains("自动升级") && txt.contains("WLAN网络下")
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (containsAutoUpdateText(child)) return true
            }
        }
        return false
    }
}
