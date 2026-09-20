package com.mars.mimarketpurify

import android.util.Log
import com.mars.mimarketpurify.TAG
import io.github.libxposed.api.XposedModule
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
    const val KEY_HIDE_UPDATE_ALL = "hide_update_all"
    const val KEY_HIDE_AUTO_UPDATE_SWITCH = "hide_auto_update_switch"
    const val KEY_DETAIL_RECOMMEND = "hide_detail_recommend"


    // ═══════════════ 移花接木 ═══════════════
    const val KEY_UPDATE_TAB = "update_tab_entry"

    // ═══════════════ 读取缓存 ═══════════════
    //
    // 远程偏好经 binder 跨进程获取、SP 文件需读盘 + XML 解析，而 hook 拦截点是高频路径
    // （如 View.setVisibility / onAttachedToWindow 每次调用都会触发 enabled() 判断），
    // 若每次实时读取会放大开销。这里加短 TTL 缓存：
    //  - 缓存有效期 500ms，用户切换开关后最多延迟半秒生效，对体验几乎无感；
    //  - 远程偏好不可用时降级读目标 app 的 SP 文件，按文件 mtime 判断是否需要重新解析。
    private const val CACHE_TTL_MS = 500L

    @Volatile private var remotePrefsCache: android.content.SharedPreferences? = null
    @Volatile private var remotePrefsCachedAt: Long = 0L

    private class SpFileCache(
        val mtime: Long,
        val values: Map<String, String>?
    )

    @Volatile private var spFileCache: SpFileCache? = null

    // ═══════════════ 读取 ═══════════════

    private fun readFromTargetSp(key: String): String? {
        return runCatching {
            val spFile = File(
                "/data/data/$TARGET_PKG/shared_prefs/com.xiaomi.market_preferences.xml")
            if (!spFile.exists()) {
                // 文件不存在：置空缓存，避免每次 stat 都重复解析
                spFileCache = SpFileCache(0L, null)
                return@runCatching null
            }
            val mtime = spFile.lastModified()
            val cache = spFileCache
            if (cache == null || cache.mtime != mtime || cache.values == null) {
                spFileCache = SpFileCache(mtime, parseSpFile(spFile))
            }
            spFileCache?.values?.get(key)
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "读取目标 app SP 失败: ${it.message}", null)
        }.getOrNull()
    }

    /** 一次性解析 SP XML 为 Map，供缓存复用 */
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
                        "boolean" -> result[name] = parser.getAttributeValue(null, "value")
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
        if (cached != null && now - remotePrefsCachedAt < CACHE_TTL_MS) {
            return cached
        }
        val fresh = runCatching {
            (HookEnv.base as XposedModule).getRemotePreferences(PREFS_GROUP)
        }.onFailure { e ->
            HookEnv.base.log(Log.WARN, TAG, "远程偏好不可用: ${e.message}", null)
        }.getOrNull()
        if (fresh != null) {
            remotePrefsCache = fresh
            remotePrefsCachedAt = now
        }
        return fresh
    }

    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    fun isEnabled(key: String, def: Boolean = true): Boolean {
        val remote = getRemotePrefs()
        if (remote != null) {
            return remote.getBoolean(key, def)
        }
        val raw = readFromTargetSp(key)
        if (raw != null) {
            return raw == "true"
        }
        HookEnv.base.log(Log.WARN, TAG, "Settings.isEnabled($key): 远程+目标SP都不可用，默认 false")
        return false
    }

    fun getKeptTabs(): Set<String> {
        val raw = getRemotePrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP)
            ?: readFromTargetSp(KEY_TAB_KEEP)
            ?: DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
