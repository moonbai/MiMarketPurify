package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.ResIdHiderHook

/** 屏蔽「领水果」活动入口（资源 id 锚点，逻辑见模板类 [ResIdHiderHook]） */
object HideFruitEntry : ResIdHiderHook(resName = "entrance_gif") {

    override val prefKey: String = Settings.KEY_FRUIT

    override val name: String
        get() = "屏蔽领水果入口"
}
