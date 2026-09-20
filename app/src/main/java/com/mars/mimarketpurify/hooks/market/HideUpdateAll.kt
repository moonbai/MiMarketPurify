package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.ResIdHiderHook

/** 隐藏更新页「全部更新」按钮（资源 id 锚点，逻辑见模板类 [ResIdHiderHook]） */
object HideUpdateAll : ResIdHiderHook(resName = "update_all_visibility_frame") {

    override val prefKey: String = Settings.KEY_HIDE_UPDATE_ALL

    override val name: String
        get() = "隐藏更新页「全部更新」按钮"
}
