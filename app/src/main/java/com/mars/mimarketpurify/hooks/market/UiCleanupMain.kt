package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.init.BaseHook

object UiCleanupMain : BaseHook() {
    override val prefKey: String? = null
    override val name: String = "界面清理总入口"

    override fun init() {
        MinePageClean.init()
        UpdateCardUi.init()
        UpdateCardSkin.init()
    }
}
