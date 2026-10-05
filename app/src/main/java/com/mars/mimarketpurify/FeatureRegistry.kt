package com.mars.mimarketpurify

import androidx.annotation.StringRes

object FeatureRegistry {

    data class Feature(
        val key: String,
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int,
        val page: String,
        val keywords: List<String>,
        val category: String,
    )

    val allFeatures: List<Feature> = listOf(
        // ═══════════════ PAGE_ADS（广告净化） ═══════════════
        Feature(Settings.KEY_SPLASH, R.string.feat_splash, R.string.feat_splash_summary,
            SubSettingsActivity.PAGE_ADS, listOf("开屏", "启动", "splash", "launch"), "cat_ad"),
        Feature(Settings.KEY_MAIN_TAB, R.string.feat_main_tab, R.string.feat_main_tab_summary,
            SubSettingsActivity.PAGE_ADS, listOf("前台", "切换", "推荐", "弹窗", "foreground", "tab", "recommend"), "cat_ad"),
        Feature(Settings.KEY_HOME_FEED, R.string.feat_home_feed, R.string.feat_home_feed_summary,
            SubSettingsActivity.PAGE_ADS, listOf("信息流", "视频", "热词", "feed", "video"), "cat_ad"),
        Feature(Settings.KEY_SEARCH, R.string.feat_search, R.string.feat_search_summary,
            SubSettingsActivity.PAGE_ADS, listOf("搜索", "建议", "search", "suggest"), "cat_ad"),
        Feature(Settings.KEY_UPDATE_DL, R.string.feat_update_dl, R.string.feat_update_dl_summary,
            SubSettingsActivity.PAGE_ADS, listOf("升级", "下载", "update", "download"), "cat_ad"),
        Feature(Settings.KEY_DETAIL, R.string.feat_detail, R.string.feat_detail_summary,
            SubSettingsActivity.PAGE_ADS, listOf("详情", "评论", "detail", "comment"), "cat_ad"),
        Feature(Settings.KEY_RANK, R.string.feat_rank, R.string.feat_rank_summary,
            SubSettingsActivity.PAGE_ADS, listOf("榜单", "排名", "rank"), "cat_ad"),
        Feature(Settings.KEY_FRUIT, R.string.feat_fruit, R.string.feat_fruit_summary,
            SubSettingsActivity.PAGE_ADS, listOf("水果", "福利", "活动", "fruit", "welfare"), "cat_ad"),
        Feature(Settings.KEY_ENTRANCE, R.string.feat_entrance, R.string.feat_entrance_summary,
            SubSettingsActivity.PAGE_ADS, listOf("首页", "活动", "云控", "entrance", "activity"), "cat_ad"),
        Feature(Settings.KEY_DETAIL_EXTRAS, R.string.feat_detail_extras, R.string.feat_detail_extras_summary,
            SubSettingsActivity.PAGE_ADS, listOf("详情", "附加", "推广栏", "extra", "promo"), "cat_ad"),
        Feature(Settings.KEY_FLOATING_AD, R.string.feat_floating_ad, R.string.feat_floating_ad_summary,
            SubSettingsActivity.PAGE_ADS, listOf("悬浮", "浮动", "广告", "floating", "float"), "cat_ad"),
        Feature(Settings.KEY_AD_BACK_FLOAT, R.string.feat_ad_back_float, R.string.feat_ad_back_float_summary,
            SubSettingsActivity.PAGE_ADS, listOf("返回", "浮窗", "跳转", "back", "float"), "cat_ad"),
        Feature(Settings.KEY_HOME_PAGE_DIALOG, R.string.feat_home_page_dialog, R.string.feat_home_page_dialog_summary,
            SubSettingsActivity.PAGE_ADS, listOf("首页", "弹窗", "dialog", "promotion"), "cat_ad"),
        Feature(Settings.KEY_INSTALL_RECOMMEND, R.string.feat_install_recommend, R.string.feat_install_recommend_summary,
            SubSettingsActivity.PAGE_ADS, listOf("安装后", "用户还喜欢", "推荐弹窗", "install", "recommend"), "cat_ad"),
        // ═══════════════ PAGE_MINE（我的页精简） ═══════════════
        Feature(Settings.KEY_MINE_RECOMMEND, R.string.feat_mine_recommend, R.string.feat_mine_recommend_summary,
            SubSettingsActivity.PAGE_MINE, listOf("推荐", "推广", "我的", "mine", "recommend"), "cat_mine"),
        Feature(Settings.KEY_MINE_OFFICIAL_TAB, R.string.feat_mine_official_tab, R.string.feat_mine_official_tab_summary,
            SubSettingsActivity.PAGE_MINE, listOf("官方", "入口", "tab", "official"), "cat_mine"),
        Feature(Settings.KEY_MINE_CLEANUP, R.string.feat_mine_cleanup, R.string.feat_mine_cleanup_summary,
            SubSettingsActivity.PAGE_MINE, listOf("清理", "卸载", "cleanup", "uninstall"), "cat_mine"),
        Feature(Settings.KEY_MINE_SUMMARY, R.string.feat_mine_summary, R.string.feat_mine_summary_summary,
            SubSettingsActivity.PAGE_MINE, listOf("头像", "昵称", "消息", "收藏", "summary", "profile"), "cat_mine"),
        Feature(Settings.KEY_MINE_SECURITY, R.string.feat_mine_security, R.string.feat_mine_security_summary,
            SubSettingsActivity.PAGE_MINE, listOf("安全", "检测", "security", "check"), "cat_mine"),
        Feature(Settings.KEY_ORCHARD_SKIN, R.string.feat_orchard_skin, R.string.feat_orchard_skin_summary,
            SubSettingsActivity.PAGE_MINE, listOf("果园", "背景", "皮肤", "orchard", "skin"), "cat_mine"),
        Feature(Settings.KEY_CARD_EXPAND, R.string.feat_card_expand, R.string.feat_card_expand_summary,
            SubSettingsActivity.PAGE_MINE, listOf("展开", "升级", "卡片", "expand", "card"), "cat_mine"),
        Feature(Settings.KEY_TAB_BADGE, R.string.feat_tab_badge, R.string.feat_tab_badge_summary,
            SubSettingsActivity.PAGE_MINE, listOf("角标", "红点", "badge", "dot"), "cat_mine"),
        // ═══════════════ PAGE_TABS（标签栏 / 悬浮底栏） ═══════════════
        Feature(Settings.KEY_TAB_FILTER, R.string.feat_tab_filter, R.string.feat_tab_filter_summary,
            SubSettingsActivity.PAGE_TABS, listOf("标签", "筛选", "底部", "tab", "filter"), "cat_tabs"),
        Feature(Settings.KEY_UPDATE_TAB, R.string.feat_update_tab, R.string.feat_update_tab_summary,
            SubSettingsActivity.PAGE_TABS, listOf("更新", "入口", "底栏", "update", "entry"), "cat_tabs"),
        Feature(Settings.KEY_TAB_DEEP_CLEAN, R.string.feat_tab_deep_clean, R.string.feat_tab_deep_clean_summary,
            SubSettingsActivity.PAGE_TABS, listOf("顶栏", "深度", "清理", "deep", "clean"), "cat_tabs"),
        Feature(Settings.KEY_FLOATING_BAR, R.string.feat_floating_bar, R.string.feat_floating_bar_summary,
            SubSettingsActivity.PAGE_TABS, listOf("悬浮", "底栏", "胶囊", "导航栏", "floating", "bar"), "cat_floating"),
        Feature(Settings.KEY_FLOATING_BAR_LABEL, R.string.feat_floating_bar_label, R.string.feat_floating_bar_label_summary,
            SubSettingsActivity.PAGE_TABS, listOf("标签", "文字", "图标", "label", "text"), "cat_floating"),
        Feature(Settings.KEY_FLOATING_BAR_LIQUID, R.string.feat_floating_bar_liquid, R.string.feat_floating_bar_liquid_summary,
            SubSettingsActivity.PAGE_TABS, listOf("液态", "动画", "高亮", "liquid", "animation"), "cat_floating"),
        Feature(Settings.KEY_FLOATING_BAR_LIQUID_3D, R.string.feat_floating_bar_liquid_3d, R.string.feat_floating_bar_liquid_3d_summary,
            SubSettingsActivity.PAGE_TABS, listOf("3D", "液态", "阴影", "立体", "3d", "shadow"), "cat_floating"),
        Feature(Settings.KEY_FLOATING_BAR_MONOCHROME, R.string.feat_floating_bar_monochrome, R.string.feat_floating_bar_monochrome_summary,
            SubSettingsActivity.PAGE_TABS, listOf("单色", "图标", "描边", "主题", "monochrome", "icon"), "cat_floating"),
        // ═══════════════ PAGE_MISC（界面精简 / 功能增强） ═══════════════
        Feature(Settings.KEY_DETAIL_FEATURED, R.string.feat_detail_featured, R.string.feat_detail_featured_summary,
            SubSettingsActivity.PAGE_MISC, listOf("详情", "精选", "featured", "pick"), "cat_misc"),
        Feature(Settings.KEY_UPDATE_HISTORY, R.string.feat_update_history, R.string.feat_update_history_summary,
            SubSettingsActivity.PAGE_MISC, listOf("升级记录", "推荐", "精选", "history", "record"), "cat_misc"),
        Feature(Settings.KEY_SEARCH_ALSO_VIEW, R.string.feat_search_also_view, R.string.feat_search_also_view_summary,
            SubSettingsActivity.PAGE_MISC, listOf("搜索", "也在看", "推荐", "also", "viewing"), "cat_misc"),
        Feature(Settings.KEY_SUB_TAB_FILTER, R.string.feat_sub_tab_filter, R.string.feat_sub_tab_filter_summary,
            SubSettingsActivity.PAGE_MISC, listOf("顶栏", "推广", "子标签", "sub", "tab", "promo"), "cat_misc"),
        Feature(Settings.KEY_HIDE_UPDATE_ALL, R.string.feat_hide_update_all, R.string.feat_hide_update_all_summary,
            SubSettingsActivity.PAGE_MISC, listOf("全部升级", "按钮", "update", "all"), "cat_misc"),
        Feature(Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, R.string.feat_hide_auto_update_switch, R.string.feat_hide_auto_update_switch_summary,
            SubSettingsActivity.PAGE_MISC, listOf("自动升级", "开关", "auto", "update"), "cat_misc"),
        Feature(Settings.KEY_PUSH_FLOAT, R.string.feat_push_float, R.string.feat_push_float_summary,
            SubSettingsActivity.PAGE_MISC, listOf("推送", "通知", "悬浮", "push", "notify", "float"), "cat_misc"),
        Feature(Settings.KEY_UPDATE_FLOAT_CARD, R.string.feat_update_float_card, R.string.feat_update_float_card_summary,
            SubSettingsActivity.PAGE_MISC, listOf("升级", "浮窗", "卡片", "立即安装", "update", "float", "card"), "cat_misc"),
        Feature(Settings.KEY_BLOCK_BG_DOWNLOAD, R.string.feat_block_bg_download, R.string.feat_block_bg_download_summary,
            SubSettingsActivity.PAGE_MISC, listOf("后台", "静默", "下载", "流量", "background", "download"), "cat_misc"),
        Feature(Settings.KEY_LONG_PRESS_JUMP, R.string.feat_long_press_jump, R.string.feat_long_press_jump_summary,
            SubSettingsActivity.PAGE_MISC, listOf("长按", "跳转", "插件", "主页", "long", "press", "jump"), "cat_misc"),
        Feature(Settings.KEY_ISLAND, R.string.feat_island, R.string.feat_island_summary,
            SubSettingsActivity.PAGE_MISC, listOf("超级岛", "下载", "island", "download"), "cat_enhance"),
        Feature(Settings.KEY_MISC, R.string.feat_misc, R.string.feat_misc_summary,
            SubSettingsActivity.PAGE_MISC, listOf("细节", "修正", "misc", "fix"), "cat_enhance"),
        Feature(Settings.KEY_UPDATE_DIALOG, R.string.feat_update_dialog, R.string.feat_update_dialog_summary,
            SubSettingsActivity.PAGE_MISC, listOf("升级", "弹窗", "提醒", "dialog", "reminder"), "cat_enhance"),
        Feature(Settings.KEY_RANK_DEBUG, R.string.feat_rank_debug, R.string.feat_rank_debug_summary,
            SubSettingsActivity.PAGE_MISC, listOf("调试", "日志", "debug", "log"), "cat_enhance"),
        // ═══════════════ PAGE_THEME（主题与外观） ═══════════════
        Feature(Settings.KEY_FLOAT_BAR_ENABLE, R.string.feat_float_bar_enable, R.string.feat_float_bar_enable_summary,
            SubSettingsActivity.PAGE_THEME, listOf("插件", "悬浮", "底栏", "plugin", "floating"), "cat_floating"),
        Feature(Settings.KEY_PREDICTIVE_BACK, R.string.feat_predictive_back, R.string.feat_predictive_back_summary,
            SubSettingsActivity.PAGE_THEME, listOf("返回", "手势", "predictive", "back", "gesture"), "cat_theme"),
    )

    /** 按 KEY 索引；主页 / 子界面 / 关于页统一从此处取名称与描述资源 ID，避免多份定义漂移 */
    private val byKey: Map<String, Feature> = allFeatures.associateBy { it.key }
    fun feature(key: String): Feature? = byKey[key]
    @StringRes fun titleRes(key: String): Int = byKey[key]?.titleRes ?: 0
    @StringRes fun summaryRes(key: String): Int = byKey[key]?.summaryRes ?: 0

    /** 各子界面（PAGE）标题资源 ID，主页与子界面共用，消除「底栏自定义 / 底部标签栏」这类不一致 */
    val pageTitleRes: Map<String, Int> = mapOf(
        SubSettingsActivity.PAGE_ADS to R.string.page_ads,
        SubSettingsActivity.PAGE_MINE to R.string.page_mine,
        SubSettingsActivity.PAGE_TABS to R.string.page_tabs,
        SubSettingsActivity.PAGE_MISC to R.string.page_misc,
        SubSettingsActivity.PAGE_THEME to R.string.page_theme,
    )

    /** 分类标题资源 ID（category 为稳定 key，见 allFeatures 中的 category 字段） */
    val categoryTitles: Map<String, Int> = mapOf(
        "ad" to R.string.cat_ad,
        "mine" to R.string.cat_mine,
        "tabs" to R.string.cat_tabs,
        "misc" to R.string.cat_misc,
        "floating" to R.string.cat_floating,
        "enhance" to R.string.cat_enhance,
        "theme" to R.string.cat_theme,
    )

    /**
     * 功能搜索：匹配双语关键词（title/summary 为资源，静态方法内不解析字符串，
     * 故仅以 keywords 命中；关键词已包含中英文检索词）。
     */
    fun search(query: String): List<Feature> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return allFeatures.filter { f ->
            f.keywords.any { it.lowercase().contains(q) }
        }
    }

    fun recommend(count: Int = 3, seed: Long = System.currentTimeMillis()): List<Feature> {
        val rng = kotlin.random.Random(seed)
        val byPage = allFeatures.groupBy { it.page }
        val selectedPages = byPage.keys.shuffled(rng).take(count.coerceAtMost(byPage.size))
        return selectedPages.mapNotNull { page -> byPage[page]?.random(rng) }
    }

    /** 按分类返回推荐：轮询各分类，每个分类取一个，不重复，最多 [count] 个 */
    fun recommendByCategory(count: Int = 10, seed: Long = System.currentTimeMillis()): List<Feature> {
        val rng = kotlin.random.Random(seed)
        val byCategory = allFeatures.groupBy { it.category }
        val cats = byCategory.keys.shuffled(rng)
        val result = mutableListOf<Feature>()
        var idx = 0
        while (result.size < count && cats.isNotEmpty()) {
            val cat = cats[idx % cats.size]
            val pool = byCategory[cat] ?: break
            val remaining = pool.filter { it !in result }
            if (remaining.isNotEmpty()) {
                result.add(remaining.random(rng))
            }
            idx++
            if (idx > count * cats.size) break
        }
        return result
    }
}

