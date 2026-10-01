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
    /** 主开关：把原生贴边底栏换成悬浮胶囊底栏 */
    const val KEY_FLOATING_BAR = "floating_bar"
    /** 子选项：悬浮底栏是否显示标签文字（关闭则纯图标） */
    const val KEY_FLOATING_BAR_LABEL = "floating_bar_label"
    /** 子选项：选中项是否使用 iOS 风格液态高亮胶囊 */
    const val KEY_FLOATING_BAR_LIQUID = "floating_bar_liquid"
    /** 子选项：选中项是否使用 3D 风格液态高亮胶囊 */
    const val KEY_FLOATING_BAR_LIQUID_3D = "float_bar_liquid_3d"

    /** 自定义：底栏背景不透明度（百分比整数，避免远程偏好跨版本 float 兼容问题） */
    const val KEY_FLOATING_BAR_ALPHA = "floating_bar_alpha"
    /** 自定义：底栏圆角半径（dp 整数） */
    const val KEY_FLOATING_BAR_RADIUS = "floating_bar_radius"
    /** 自定义：悬浮底栏距屏幕底部的外边距（dp 整数），即「悬空高度」，参考 HyperModifier 的 hiddenNavigationLift */
    const val KEY_FLOATING_BAR_BOTTOM_MARGIN = "floating_bar_bottom_margin"

    //===== 悬浮胶囊底栏 外观&手势配置 =====
    const val KEY_FLOAT_BAR_ENABLE = "float_bar_enable"
    const val KEY_FLOAT_BAR_GESTURE_SLIDE = "float_bar_gesture_slide" // 手势左右滑动切换Tab
    const val KEY_FLOAT_BAR_FOLLOW_DARK = "float_bar_follow_dark"
    
    // 色彩
    const val KEY_FLOAT_BG_COLOR = "float_bar_bg_color"
    const val KEY_FLOAT_SELECT_BG_COLOR = "float_bar_select_bg_color"
    const val KEY_FLOAT_TEXT_NORMAL_COLOR = "float_bar_text_normal_color"
    const val KEY_FLOAT_TEXT_SELECT_COLOR = "float_bar_text_select_color"
    
    // 透明度
    const val KEY_FLOAT_BAR_ALPHA = "float_bar_bg_alpha"         // 底栏整体背景透明度 0~100
    const val KEY_FLOAT_SELECT_ALPHA = "float_bar_select_alpha"  // 选中胶囊背景透明度 0~100
    
    // 尺寸形状
    const val KEY_FLOAT_BAR_HEIGHT = "float_bar_height"          // dp
    const val KEY_FLOAT_CORNER_RADIUS = "float_bar_corner_radius"// 胶囊圆角 dp
    const val KEY_FLOAT_ICON_SIZE = "float_bar_icon_size"        // 图标尺寸 dp
    
    // 动效开关
    const val KEY_FLOAT_ANIM_SLIDER = "float_bar_anim_slider"    // 滑块跟随滑动动画
    const val KEY_FLOAT_ANIM_PRESS = "float_bar_anim_press"      // 点击按压缩放动效
    
    /** 透明度可调区间（%）：低于 35 时胶囊几乎看不见，再低无意义 */
    const val FLOATING_ALPHA_MIN = 35
    const val FLOATING_ALPHA_MAX = 100
    const val FLOATING_ALPHA_DEFAULT = 75
    
    //===== 原生Tab栏样式自定义 =====
    const val KEY_TAB_SELECT_COLOR = "tab_select_color"
    const val KEY_TAB_INDICATOR_VISIBLE = "tab_indicator_visible"
    const val KEY_TAB_INDICATOR_COLOR = "tab_indicator_color"
    
    /** 圆角可调区间（dp）：0=直角，上限取胶囊高度一半 */
    const val FLOATING_RADIUS_MIN = 0
    const val FLOATING_RADIUS_MAX = 29
    const val FLOATING_RADIUS_DEFAULT = 29
    /** 悬浮底栏到底部外边距可调区间（dp）：0=贴边，上限让「悬空高度」足以避开手势区 */
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

    /**
     * hook 侧注入的远程偏好获取器（由 [HookEnv.setBase] 设置）。
     * 仅在被 LSPosed 注入的进程内非空；模块自身 App 侧恒为 null，
     * 因此 App 侧完全不触碰 XposedModule，避免 standalone 模式下类解析失败崩溃。
     * 类型用函数而非 XposedModule，是为剥离对 io.github.libxposed.api 的硬依赖
     * （该依赖为 compileOnly，不进 APK）。
     */
    internal var remotePrefsProvider: ((String) -> android.content.SharedPreferences?)? = null

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
            Log.w(TAG, "读取目标 app SP 失败: ${it.message}")
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
                        "boolean", "int", "long", "float" ->
                            result[name] = parser.getAttributeValue(null, "value")
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
            // 模块自身 App：走 XposedService（随 libxposed.service 打包，始终可用）
            App.mService?.getRemotePreferences(PREFS_GROUP)
                // 被 LSPosed 注入的目标 App：走 hook 侧注入的 provider（内部访问 XposedModule，
                // 仅注入进程可用；App 侧此 provider 为 null，不会触发 XposedModule 加载）
                ?: remotePrefsProvider?.invoke(PREFS_GROUP)
        }.onFailure { e ->
            Log.w(TAG, "远程偏好不可用: ${e.message}")
        }.getOrNull()
        if (fresh != null) {
            remotePrefsCache = fresh
            remotePrefsCachedAt = now
        }
        return fresh
    }

    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    /**
     * 远程偏好当前是否可读（service 已连接 / 注入进程 provider 可用）。
     * 用于悬浮底栏等「实时读开关」的场景：偏好暂不可读时不应据此判定开关关闭，
     * 否则 service 抖动会误卸载悬浮底栏且无法自愈。
     */
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
        // 远程偏好与目标 SP 都不可用时，回退到调用方传入的默认值 def，
        // 而不是硬编码 false——否则总开关 master 也会被判 false，导致模块整体功能全部失效。
        return def
    }

    /**
     * 读取整型配置（悬浮底栏的透明度、圆角等）。
     * 与 [isEnabled] 同一套优先级：远程偏好 → 目标 app SP → 默认值。
     */
     /**
 * 读取颜色整型，用于Tab颜色自定义
 */
    fun getInt(key: String, defColor: Int): Int {
        val remote = getRemotePrefs()
        if (remote != null) {
            return remote.getInt(key, defColor)
        }
        val raw = readFromTargetSp(key)
        raw?.toIntOrNull()?.let { return it }
        return defColor
    }
    
    /** 透明度百分比 → 0..255 alpha 通道值。 */
    fun floatingBarAlphaPercent(): Int =
        getInt(KEY_FLOATING_BAR_ALPHA, FLOATING_ALPHA_DEFAULT)
            .coerceIn(FLOATING_ALPHA_MIN, FLOATING_ALPHA_MAX)

    /** 圆角 dp 值（越界收敛）。 */
    fun floatingBarRadiusDp(): Int =
        getInt(KEY_FLOATING_BAR_RADIUS, FLOATING_RADIUS_DEFAULT)
            .coerceIn(FLOATING_RADIUS_MIN, FLOATING_RADIUS_MAX)

    /** 悬浮底栏到底部外边距 dp 值（越界收敛）。 */
    fun floatingBarBottomMarginDp(): Int =
        getInt(KEY_FLOATING_BAR_BOTTOM_MARGIN, FLOATING_BOTTOM_MARGIN_DEFAULT)
            .coerceIn(FLOATING_BOTTOM_MARGIN_MIN, FLOATING_BOTTOM_MARGIN_MAX)

    fun getKeptTabs(): Set<String> {
        val raw = getRemotePrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP)
            ?: readFromTargetSp(KEY_TAB_KEEP)
            ?: DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
}
