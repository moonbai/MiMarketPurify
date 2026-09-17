package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook

/**
 * 已合并到 TabFilter.hookTabInfoParse() 中执行。
 * 此文件保留空壳，避免 LSPosed 加载时找不到类报错。
 */
object UpdateTabEntry : BaseHook() {
    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "底栏更新入口(已合并)"
    override fun init() {
        debugLog("init: 已合并到 TabFilter，跳过")
    }
}
