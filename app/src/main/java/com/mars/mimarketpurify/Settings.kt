package com.mars.mimarketpurify

import android.util.Log
import com.mars.mimarketpurify.TAG
import io.github.libxposed.api.XposedModule
import kotlin.concurrent.Volatile

/**
 * 功能开关的集中管理。
 *
 * 读取：在 hooked 进程内通过 libxposed 的 [XposedModule.getRemotePreferences] 读取，
 *       该接口由框架在进程间同步，对 hooked app 只读。
 * 写入：在模块自身的 App 进程内通过 [io.github.libxposed.service.XposedService]
 *       写入同一 group，二者使用相同的 [PREFS_GROUP] 即保持同步。
 *
 * 关键修复点：读取被包在 runCatching 中，当框架不支持 remote preferences（或尚未就绪）时，
 * 回落为默认值（开启），保证模块不会因为"读不到开关"而整体失效。
 */
object Settings {

    /** remote preferences 的分组名，读写两端必须一致 */
    const val PREFS_GROUP = "settings"

    // ===== 开关 key =====
    /** 总开关：关闭后所有功能都不生效 */
    const val KEY_MASTER = "master"

    /** 开屏广告 */
    const val KEY_SPLASH = "splash_ads"

    /** 禁止前台/主页广告与推荐（MarketTabActivity 相关） */
    const val KEY_MAIN_TAB = "main_tab_ads"

    /** 隐藏主页信息流广告 / 热词 */
    const val KEY_HOME_FEED = "home_feed_ads"

    /** 搜索相关推荐（建议 / 搜索页 / 搜索结果） */
    const val KEY_SEARCH = "search_ads"

    /** 升级页 / 下载页软件推荐 */
    const val KEY_UPDATE_DL = "update_download_ads"

    /** 应用详情页广告、评论与推荐 */
    const val KEY_DETAIL = "detail_ads"

    /** 隐藏应用安全检测视图 */
    const val KEY_SECURITY = "hide_security"

    /** 精简底部标签栏（总开关：是否启用标签筛选） */
    const val KEY_TAB_FILTER = "tab_filter"

    /** 底部标签栏"保留哪些标签"的选择集合（逗号分隔存储） */
    const val KEY_TAB_KEEP = "tab_keep"

    /** [KEY_TAB_KEEP] 的默认值 */
    const val DEFAULT_TAB_KEEP = "native_market_home,native_market_mine"

    /** 已知底部标签（tag -> 显示名） */
    val TAB_ITEMS: LinkedHashMap<String, String> = linkedMapOf(
        "native_market_home" to "首页",
        "native_market_mine" to "我的",
        "native_market_video" to "视频号",
        "native_market_agent" to "智能体",
        "native_app_assemble" to "应用号",
        "native_market_game" to "游戏",
        "native_market_rank" to "榜单",
    )

    /** 细节修正 */
    const val KEY_MISC = "misc_apply"

    /** 屏蔽「领水果」活动入口 */
    const val KEY_FRUIT = "hide_fruit_entry"

    /** 移除「榜单」界面广告 / 推广卡片 */
    const val KEY_RANK = "rank_ads"

    // ═══════════════ 我的页净化 ═══════════════

    /** 顶部个人信息区（头像/昵称/消息/收藏） */
    const val KEY_MINE_SUMMARY = "mine_summary"

    /** 「我的」页 · 应用推荐位（mine_ad_container） */
    const val KEY_MINE_RECOMMEND = "mine_recommend"

    /** 「我的」页 · 官方入口 tab（mine_middle_menu_container） */
    const val KEY_MINE_OFFICIAL_TAB = "mine_official_tab"

    /**
     * 「我的」页 · 手机清理与应用卸载。
     * 单独成一个开关是因为屏蔽后会把「应用升级」卡片拉宽，
     * 这是有副作用的改动，值得让用户能单独关掉。
     */
    const val KEY_MINE_CLEANUP = "mine_cleanup"

    /** 「我的」页 · 安全检测卡片（mineSecurityView） */
    const val KEY_MINE_SECURITY = "mine_security"

    // ═══════════════ 升级卡片增强 ═══════════════

    const val KEY_ORCHARD_SKIN = "orchard_skin"
    const val KEY_CARD_EXPAND = "card_expand"

    // ═══════════════ 其他 ═══════════════

    /** 隐藏应用详情页的「精选」入口 */
    const val KEY_DETAIL_FEATURED = "hide_detail_featured"

    /** 「升级记录」页底部推荐 */
    const val KEY_UPDATE_HISTORY = "hide_update_history"

    /** 搜索结果页底部「搜索 xxx 的人也在看」 */
    const val KEY_SEARCH_ALSO_VIEW = "hide_search_also_view"

    /** 底部标签页的数字角标与「新」字红点 */
    const val KEY_TAB_BADGE = "hide_tab_badge"

    /** 首页搜索框左侧的云控活动入口 */
    const val KEY_ENTRANCE = "hide_entrance"

    /** 「我的」页底部推广应用列表 */
    const val KEY_MINE_AD_GROUP = "hide_mine_ad_group"

    /** 详情页附加净化 */
    const val KEY_DETAIL_EXTRAS = "hide_detail_extras"

    /** 阻止商店弹出的「升级提醒」对话框 */
    const val KEY_UPDATE_DIALOG = "block_update_dialog"

    /** 榜单调试 */
    const val KEY_RANK_DEBUG = "rank_debug"

    /** 强制启用下载进度小米超级岛 */
    const val KEY_ISLAND = "super_island"

    // ═══════════════ 读取 ═══════════════

    private fun getPrefs(): android.content.SharedPreferences? {
        return runCatching {
            (HookEnv.base as XposedModule).getRemotePreferences(PREFS_GROUP)
        }.onFailure { e ->
            HookEnv.base.log(Log.WARN, TAG, "无法读取远程偏好（开关将使用默认值）: ${e.message}", null)
        }.getOrNull()
    }

    fun isMasterEnabled(): Boolean = isEnabled(KEY_MASTER, true)

    fun isEnabled(key: String, def: Boolean = true): Boolean {
        return getPrefs()?.getBoolean(key, def) ?: def
    }

    fun getKeptTabs(): Set<String> {
        val raw = getPrefs()?.getString(KEY_TAB_KEEP, DEFAULT_TAB_KEEP) ?: DEFAULT_TAB_KEEP
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
