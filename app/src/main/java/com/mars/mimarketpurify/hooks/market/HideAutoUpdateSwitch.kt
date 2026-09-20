package com.mars.mimarketpurify.hooks.market

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.ResIdHiderHook

/**
 * 隐藏自动升级开关（资源 id 锚点 + 文案兜底，对抗资源名漂移）。
 * 基础逻辑见模板类 [ResIdHiderHook]。
 */
object HideAutoUpdateSwitch : ResIdHiderHook(resName = "auto_update_item_open") {

    override val prefKey: String = Settings.KEY_HIDE_AUTO_UPDATE_SWITCH

    override val name: String
        get() = "隐藏自动升级开关"

    /** 兜底：ID 没命中但子树含「自动升级」+「WLAN网络下」文案时也隐藏 */
    override fun fallbackHit(view: View): Boolean = containsAutoUpdateText(view)

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
