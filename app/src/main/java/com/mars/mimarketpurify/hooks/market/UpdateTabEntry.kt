package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook

object UpdateTabEntry : BaseHook() {
    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "底栏更新入口(已合并)"
    override fun init() {
        debugLog("init: 已合并到 TabFilter，跳过")
    }
}