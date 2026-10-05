package com.mars.mimarketpurify

import android.util.Log
import com.mars.mimarketpurify.TAG
import org.xmlpull.v1.XmlPullParser
import java.io.File

object Settings {

    const val PREFS_GROUP = "settings"
    private const val TARGET_PKG = "com.xiaomi.market"

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
    const val KEY_SUB_TAB_FILTER = "sub_tab_filter"
    const val KEY_TAB_KEEP = "tab_keep"
    const val KEY_FLOATING_AD = "floating_ad"

    // ═══════════════ 悬浮底栏（纯原生自绘） ═══════════════
    const val KEY_FLOATING_BAR = "floating_bar"
    const val KEY_FLOATING_BAR_LABEL = "floating_bar_label"
    const val KEY_FLOATING_BAR_LIQUID = "floating_bar_liquid"
    const val KEY_FLOATING_BAR_LIQUID_3D = "float_bar_liquid_3d"
    const val KEY_FLOATING_BAR_ALPHA = "floating_bar_alpha"
    const val KEY_FLOATING_BAR_RADIUS = "floating_bar_radius"
    const val KEY_FLOATING_BAR_BOTTOM_MARGIN = "floating_bar_bottom_margin"
    const val KEY_FLOATING_BAR_MONOCHROME = "floating_bar_monochrome"

    // ===== 悬浮胶囊底栏 外观&手势配置 =====
    const val KEY_FLOAT_BAR_ENABLE = "float_bar_enable"
    const val KEY_FLOAT_BAR_GESTURE_SLIDE = "float_bar_gesture_slide"
    const val KEY_FLOAT_BAR_FOLLOW_DARK = "float_bar_follow_dark"
    const val KEY_FLOAT_BG_COLOR = "float_bar_bg_color"
    const val KEY_FLOAT_SELECT_BG_COLOR = "float_bar_select_bg_color"
    const val KEY_FLOAT_TEXT_NORMAL_COLOR = "float_bar_text_normal_color"
    const val KEY_FLOAT_TEXT_SELECT_COLOR = "float_bar_text_select_color"

    // ===== 商店悬浮底栏（Hook）专属配色：与「插件本体底栏」配色相互独立、互不影响 =====
    const val KEY_STORE_FLOAT_BG_COLOR = "store_float_bar_bg_color"
    const val KEY_STORE_FLOAT_SELECT_BG_COLOR = "store_float_bar_select_bg_color"
    const val KEY_STORE_FLOAT_TEXT_NORMAL_COLOR = "store_float_bar_text_normal_color"
    const val KEY_STORE_FLOAT_TEXT_SELECT_COLOR = "store_float_bar_text_select_color"
    const val KEY_FLOAT_BAR_ALPHA = "float_bar_bg_alpha"
    const val KEY_FLOAT_SELECT_ALPHA = "float_bar_select_alpha"
    const val KEY_FLOAT_BAR_HEIGHT = "float_bar_height"
    const val KEY_FLOAT_CORNER_RADIUS = "float_bar_corner_radius"
    const val KEY_FLOAT_ICON_SIZE = "float_bar_icon_size"
    const val KEY_FLOAT_ANIM_SLIDER = "float_bar_anim_slider"
    const val KEY_FLOAT_ANIM_PRESS = "float_bar_anim_press"

    // ===== 商店悬浮底栏颜色默认值（浅色） =====
    const val STORE_FLOAT_BG_COLOR_LIGHT = 0xFFF2F2F2.toInt()
    const val STORE_FLOAT_SELECT_BG_COLOR_LIGHT = 0xFFDADADA.toInt()
    const val STORE_FLOAT_TEXT_NORMAL_COLOR_LIGHT = 0xFF000000.toInt()
    const val STORE_FLOAT_TEXT_SELECT_COLOR_LIGHT = 0xFF000000.toInt()
    const val FLOAT_BG_COLOR_LIGHT = 0xFFF2F2F2.toInt()
    const val FLOAT_SELECT_BG_COLOR_LIGHT = 0xFFDADADA.toInt()
    const val FLOAT_TEXT_NORMAL_COLOR_LIGHT = 0xFF000000.toInt()
    const val FLOAT_TEXT_SELECT_COLOR_LIGHT = 0xFF000000.toInt()

    // ===== 商店悬浮底栏颜色默认值（深色） =====
    const val STORE_FLOAT_BG_COLOR_DARK = 0xFF1A1A1A.toInt()
    const val STORE_FLOAT_SELECT_BG_COLOR_DARK = 0xFF333333.toInt()
    const val STORE_FLOAT_TEXT_NORMAL_COLOR_DARK = 0xFFB0B0B0.toInt()
    const val STORE_FLOAT_TEXT_SELECT_COLOR_DARK = 0xFFFFFFFF.toInt()
    const val FLOAT_BG_COLOR_DARK = 0xFF1A1A1A.toInt()
    const val FLOAT_SELECT_BG_COLOR_DARK = 0xFF333333.toInt()
    const val FLOAT_TEXT_NORMAL_COLOR_DARK = 0xFFB0B0B0.toInt()
    const val FLOAT_TEXT_SELECT_COLOR_DARK = 0xFFFFFFFF.toInt()

    // ===== 商店悬浮底栏（Hook）独立外观参数：与插件本体底栏彻底解耦 =====
    const val KEY_STORE_FLOAT_BOTTOM_MARGIN = "store_float_bar_bottom_margin"

    const val FLOATING_ALPHA_MIN = 35
    const val FLOATING_ALPHA_MAX = 100
    const val FLOATING_ALPHA_DEFAULT = 75

    const val STORE_FLOAT_ALPHA_DEFAULT = 40
    const val STORE_FLOAT_BOTTOM_MARGIN_MIN = 0
    const val STORE_FLOAT_BOTTOM_MARGIN_MAX = 48
    const val STORE_FLOAT_BOTTOM_MARGIN_DEFAULT = 4

    // ===== 原生Tab栏样式自定义 =====
    const val KEY_TAB_SELECT_COLOR = "tab_select_color"
    const val KEY_TAB_INDICATOR_VISIBLE = "tab_indicator_visible"
    const val KEY_TAB_INDICATOR_COLOR = "tab_indicator_color"

    const val FLOATING_RADIUS_MIN = 0
    const val FLOATING_RADIUS_MAX = 29
    const val FLOATING_RADIUS_DEFAULT = 29
    const val FLOATING_BOTTOM_MARGIN_MIN = 0
    const val FLOATING_BOTTOM_MARGIN_MAX = 48
    const val FLOATING_BOTTOM_MARGIN_DEFAULT = 4
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
    const val KEY_AD_BACK_FLOAT = "ad_back_float"
    const val KEY_HOME_PAGE_DIALOG = "home_page_dialog"
    const val KEY_PUSH_FLOAT = "push_float"
    const val KEY_UPDATE_FLOAT_CARD = "update_float_card"
    const val KEY_BLOCK_BG_DOWNLOAD = "block_bg_download"
    const val KEY_TAB_DEEP_CLEAN = "tab_deep_clean"

    const val KEY_RANK_DEBUG = "rank_debug"
    const val KEY_ISLAND = "super_island"
    const val KEY_HIDE_UPDATE_ALL = "hide_update_all"
    const val KEY_HIDE_AUTO_UPDATE_SWITCH = "hide_auto_update_switch"
    const val KEY_DETAIL_RECOMMEND = "hide_detail_recommend"
    const val KEY_RECOMMENDATIONS_ENABLED = "recommendations_enabled"

    // ═══════════════ 移花接木 ═══════════════
    const val KEY_UPDATE_TAB = "update_tab_entry"

    // ═══════════════ 新增功能 ═══════════════
    const val KEY_LONG_PRESS_JUMP = "long_press_jump"
    const val KEY_INSTALL_RECOMMEND = "install_recommend"

    /** 主题模式：0=跟随系统，1=浅色，2=深色（模块自身 SP，不经目标 App） */
    const val KEY_THEME_MODE = "theme_mode"

    // ═══════════════ 外观（模块自身 UI）══════════════
    const val KEY_PREDICTIVE_BACK = "predictive_back"
    const val KEY_UI_SCALE = "ui_scale"
    const val KEY_SWITCH_HINT = "switch_hint"

    // ═══════════════ 读取缓存 ═══════════════
    private const val CACHE_TTL_MS = 500L

    @Volatile private var remotePrefsCache: android.content.SharedPreferences? = null
    @Volatile private var remotePrefsCachedAt: Long = 0L

    internal var remotePrefsProvider: ((String) -> android.content.SharedPreferences?)? = null

    private class SpFileCache(
        val mtime: Long,
        val values: Map<String, String>?
    )

    @Volatile private var spFileCache: SpFileCache? = null

    // ═══════════════ 读取 ═══════════════

    private fun readFromTargetSp(key: String): String? {
        return runCatching {
            val spFile = File("/data/data/$TARGET_PKG/shared_prefs/com.xiaomi.market_preferences.xml")
            if (!spFile.exists()) { spFileCache = SpFileCache(0L, null); return@runCatching null }
            val mtime = spFile.lastModified()
            val cache = spFileCache
            if (cache == null || cache.mtime != mtime || cache.values == null) {
                spFileCache = SpFileCache(mtime, parseSpFile(spFile))
            }
            spFileCache?.values?.get(key)
        }.onFailure { Log.w(TAG, "读取目标 app SP 失败: ${it.message}") }.getOrNull()
    }

    private fun parseSpFile(spFile: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(spFile.inputStream(), "UTF-8")
        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG) {
                val name = parser.getAttributeValue(null, "name")
                if (name != null) {
                    when (parser.name) {
                        "boolean", "int", "long", "float" -> result[name] = parser.getAttributeValue(null, "value")
                        "string" -> result[name] = parser.nextText()
                    }
                }
            }
            type = parser.next()
        }
        return result
    }

    private fun getRemotePrefs(): android.content.SharedPreferences? {
        val now = System.currentTimeMillis()
        val cached = remotePrefsCache
        if (cached != null && now - remotePrefsCachedAt < CACHE_TTL_MS) return cached
        val fresh = runCatching {
            App.mService?.getRemotePreferences(PREFS_GROUP) ?: remotePrefsProvider?.invoke(PREFS_GROUP)
        }.onFailure { Log.w(TAG, "远程偏好不可用: ${it.message}") }.getOrNull()
        if (fresh != null) { remotePrefsCache = fresh; remotePrefsCachedAt = now }
        return fresh
    }

    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    fun remotePrefsAvailable(): Boolean = getRemotePrefs() != null

    fun isEnabled(key: String, def: Boolean = true): Boolean {
        val remote = getRemotePrefs()
        if (remote != null) {
            return remote.getBoolean(key, def)
        }
        val raw = readFromTargetSp(key)
        if (raw != null) {
            return raw == "true"
        }
        return def
    }

    fun getInt(key: String, defColor: Int): Int {
        val remote = getRemotePrefs()
        if (remote != null) {
            return remote.getInt(key, defColor)
        }
        val raw = readFromTargetSp(key)
        raw?.toIntOrNull()?.let { return it }
        return defColor
    }

    fun floatingBarAlphaPercent(): Int =
        getInt(KEY_FLOATING_BAR_ALPHA, FLOATING_ALPHA_DEFAULT)
            .coerceIn(FLOATING_ALPHA_MIN, FLOATING_ALPHA_MAX)

    fun floatingBarRadiusDp(): Int =
        getInt(KEY_FLOATING_BAR_RADIUS, FLOATING_RADIUS_DEFAULT)
            .coerceIn(FLOATING_RADIUS_MIN, FLOATING_RADIUS_MAX)

    fun floatingBarBottomMarginDp(): Int =
        getInt(KEY_FLOATING_BAR_BOTTOM_MARGIN, FLOATING_BOTTOM_MARGIN_DEFAULT)
            .coerceIn(FLOATING_BOTTOM_MARGIN_MIN, FLOATING_BOTTOM_MARGIN_MAX)

    fun storeBarRadiusDp(): Int =
        getInt(KEY_FLOAT_CORNER_RADIUS, FLOATING_RADIUS_DEFAULT)
            .coerceIn(FLOATING_RADIUS_MIN, FLOATING_RADIUS_MAX)

    fun storeBarAlphaPercent(): Int =
        getInt(KEY_FLOAT_BAR_ALPHA, STORE_FLOAT_ALPHA_DEFAULT)
            .coerceIn(FLOATING_ALPHA_MIN, FLOATING_ALPHA_MAX)

    fun storeBarBottomMarginDp(): Int =
        getInt(KEY_STORE_FLOAT_BOTTOM_MARGIN, STORE_FLOAT_BOTTOM_MARGIN_DEFAULT)
            .coerceIn(STORE_FLOAT_BOTTOM_MARGIN_MIN, STORE_FLOAT_BOTTOM_MARGIN_MAX)

    fun isPluginBarEnabled(): Boolean = isEnabled(KEY_FLOAT_BAR_ENABLE, true)

    fun getKeptTabs(): Set<String> {
        val raw = getRemotePrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP)
            ?: readFromTargetSp(KEY_TAB_KEEP)
            ?: DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
