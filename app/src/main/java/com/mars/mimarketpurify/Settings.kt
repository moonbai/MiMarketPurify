package com.mars.mimarketpurify

import android.util.Log
import com.mars.mimarketpurify.TAG
import io.github.libxposed.api.XposedModule

object Settings {

    const val PREFS_GROUP = "settings"

    // ═══════════════ 通用 ═══════════════
    const val KEY_MASTER = "master"
    const val KEY_SPLASH = "splash_ads"
    const val KEY_MAIN_TAB = "main_tab_ads"
    const val KEY_HOME_FEED = "home_feed_ads"
    const val KEY_SEARCH = "search_ads"
    const val KEY_UPDATE_DL = "update_download_ads"
    const val KEY_DETAIL = "detail_ads"
    const val KEY_SECURITY = "hide_security"
    const val KEY_TAB_FILTER = "tab_filter"
    const val KEY_TAB_KEEP = "tab_keep"
    const val DEFAULT_TAB_KEEP = "native_market_home,native_market_mine"
    val TAB_ITEMS: LinkedHashMap<String, String> = linkedMapOf(
        "native_market_home" to "首页",
        "native_market_mine" to "我的",
        "native_market_video" to "视频号",
        "native_market_agent" to "智能体",
        "native_app_assemble" to "应用号",
        "native_market_game" to "游戏",
        "native_market_rank" to "榜单",
    )
    const val KEY_MISC = "misc_apply"
    const val KEY_FRUIT = "hide_fruit_entry"
    const val KEY_RANK = "rank_ads"

    // ═══════════════ 我的页净化 ═══════════════
    const val KEY_MINE_SUMMARY = "mine_summary"
    const val KEY_MINE_RECOMMEND = "mine_recommend"
    const val KEY_MINE_OFFICIAL_TAB = "mine_official_tab"
    const val KEY_MINE_CLEANUP = "mine_cleanup"
    const val KEY_MINE_SECURITY = "mine_security"

    // ═══════════════ 升级卡片增强 ═══════════════
    const val KEY_ORCHARD_SKIN = "orchard_skin"
    const val KEY_CARD_EXPAND = "card_expand"

    // ═══════════════ 其他 ═══════════════
    const val KEY_DETAIL_FEATURED = "hide_detail_featured"
    const val KEY_UPDATE_HISTORY = "hide_update_history"
    const val KEY_SEARCH_ALSO_VIEW = "hide_search_also_view"
    const val KEY_TAB_BADGE = "hide_tab_badge"
    const val KEY_ENTRANCE = "hide_entrance"
    const val KEY_MINE_AD_GROUP = "hide_mine_ad_group"
    const val KEY_DETAIL_EXTRAS = "hide_detail_extras"
    const val KEY_UPDATE_DIALOG = "block_update_dialog"
    const val KEY_RANK_DEBUG = "rank_debug"
    const val KEY_ISLAND = "super_island"

    // ═══════════════ 读取 ═══════════════

    private fun getPrefs(): android.content.SharedPreferences? {
        return runCatching {
            (HookEnv.base as XposedModule).getRemotePreferences(PREFS_GROUP)
        }.onFailure { e ->
            HookEnv.base.log(Log.WARN, TAG, "无法读取远程偏好: ${e.message}", null)
        }.getOrNull()
    }

    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    fun isEnabled(key: String, def: Boolean = true): Boolean {
        val prefs = getPrefs()
        if (prefs == null) {
            HookEnv.base.log(Log.WARN, TAG,
                "Settings.isEnabled($key): prefs 为 null，使用默认值 $def")
            return def
        }
        val value = prefs.getBoolean(key, def)
        // 对 mine 页面相关开关输出日志，方便排查
        if (key.startsWith("mine_")) {
            HookEnv.base.log(Log.INFO, TAG,
                "Settings.isEnabled($key) = $value (default=$def)")
        }
        return value
    }

    fun getKeptTabs(): Set<String> {
        val raw = getPrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP) ?: DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
