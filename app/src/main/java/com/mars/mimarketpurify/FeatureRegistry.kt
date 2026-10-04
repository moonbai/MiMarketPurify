package com.mars.mimarketpurify

/**
 * 功能注册表：所有可搜索/可推荐的功能元数据。
 * 主页搜索栏和「发现好用」推荐卡片共用这份数据。
 *
 * [page] 对应 [SubSettingsActivity.PAGE_*] 常量，点击搜索结果时跳转到对应设置页。
 * [keywords] 用于模糊匹配，支持中文/英文/拼音缩写。
 */
object FeatureRegistry {

    data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val page: String,
        val keywords: List<String>,
    )

    /** 所有功能定义，按页面分组 */
    val allFeatures: List<Feature> = listOf(
        // ═══════════ 广告净化 ═══════════
        Feature(Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告",
            SubSettingsActivity.PAGE_ADS, listOf("开屏", "启动", "splash", "广告")),
        Feature(Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗",
            SubSettingsActivity.PAGE_ADS, listOf("前台", "切换", "推荐", "弹窗")),
        Feature(Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏",
            SubSettingsActivity.PAGE_ADS, listOf("信息流", "视频", "热词", "feed")),
        Feature(Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐",
            SubSettingsActivity.PAGE_ADS, listOf("搜索", "建议", "search")),
        Feature(Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐",
            SubSettingsActivity.PAGE_ADS, listOf("升级", "下载", "推荐", "update", "download")),
        Feature(Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位",
            SubSettingsActivity.PAGE_ADS, listOf("详情", "评论", "广告", "detail")),
        Feature(Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片",
            SubSettingsActivity.PAGE_ADS, listOf("榜单", "排名", "rank")),
        Feature(Settings.KEY_FRUIT, "领水果入口", "隐藏福利活动 gif 动图入口",
            SubSettingsActivity.PAGE_ADS, listOf("水果", "福利", "活动", "fruit")),
        Feature(Settings.KEY_ENTRANCE, "首页活动入口", "隐藏搜索框左侧云控下发的活动小图标",
            SubSettingsActivity.PAGE_ADS, listOf("首页", "活动", "云控", "entrance")),
        Feature(Settings.KEY_DETAIL_EXTRAS, "详情页附加推荐", "详情页拼装推荐、底部多按钮推广栏",
            SubSettingsActivity.PAGE_ADS, listOf("详情", "附加", "推广栏")),
        Feature(Settings.KEY_FLOATING_AD, "主页悬浮广告", "屏蔽主页底部/侧边弹出的悬浮广告",
            SubSettingsActivity.PAGE_ADS, listOf("悬浮", "浮动", "广告", "floating")),
        Feature(Settings.KEY_AD_BACK_FLOAT, "返回浮窗广告", "屏蔽返回时弹出的「返回今日头条」等浮窗",
            SubSettingsActivity.PAGE_ADS, listOf("返回", "浮窗", "跳转", "adback")),
        Feature(Settings.KEY_HOME_PAGE_DIALOG, "首页弹窗推广", "屏蔽进入首页时弹出的 Dialog 推广位",
            SubSettingsActivity.PAGE_ADS, listOf("首页", "弹窗", "dialog", "推广")),

        // ═══════════ 我的页 ═══════════
        Feature(Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "隐藏页面顶部推荐卡片与底部推广列表",
            SubSettingsActivity.PAGE_MINE, listOf("推荐", "推广", "我的", "mine")),
        Feature(Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "隐藏页面中间的官方应用管理功能入口 tab",
            SubSettingsActivity.PAGE_MINE, listOf("官方", "入口", "tab")),
        Feature(Settings.KEY_MINE_CLEANUP, "清理与卸载", "隐藏手机清理与应用卸载入口",
            SubSettingsActivity.PAGE_MINE, listOf("清理", "卸载", "cleanup")),
        Feature(Settings.KEY_MINE_SUMMARY, "个人信息区", "隐藏头像、昵称、消息、收藏",
            SubSettingsActivity.PAGE_MINE, listOf("个人信息", "头像", "昵称", "消息", "收藏", "summary")),
        Feature(Settings.KEY_MINE_SECURITY, "安全检测", "隐藏应用安全检测卡片",
            SubSettingsActivity.PAGE_MINE, listOf("安全", "检测", "security")),
        Feature(Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "清除升级卡片的果园背景",
            SubSettingsActivity.PAGE_MINE, listOf("果园", "背景", "皮肤", "orchard")),
        Feature(Settings.KEY_CARD_EXPAND, "升级卡片横向展开", "升级卡片展开显示更多应用更新",
            SubSettingsActivity.PAGE_MINE, listOf("展开", "升级", "卡片", "expand")),
        Feature(Settings.KEY_TAB_BADGE, "底栏角标", "去掉底部标签页的数字角标与红点",
            SubSettingsActivity.PAGE_MINE, listOf("角标", "红点", "badge", "数字")),

        // ═══════════ 底部标签栏 ═══════════
        Feature(Settings.KEY_TAB_FILTER, "筛选底部标签", "选择需要展示的底栏标签",
            SubSettingsActivity.PAGE_TABS, listOf("标签", "筛选", "底部", "tab", "filter")),
        Feature(Settings.KEY_UPDATE_TAB, "底栏更新入口", "在商店原生底栏注入「更新」入口",
            SubSettingsActivity.PAGE_TABS, listOf("更新", "入口", "底栏")),

        // ═══════════ 其他界面精简 ═══════════
        Feature(Settings.KEY_DETAIL_FEATURED, "详情页「精选」", "按文案匹配，仅在应用详情页生效",
            SubSettingsActivity.PAGE_MISC, listOf("详情", "精选", "featured")),
        Feature(Settings.KEY_UPDATE_HISTORY, "升级记录推荐", "隐藏升级记录底部的精选推荐",
            SubSettingsActivity.PAGE_MISC, listOf("升级记录", "推荐", "精选", "history")),
        Feature(Settings.KEY_SEARCH_ALSO_VIEW, "搜索页「也在看」", "隐藏搜索结果底部的推荐",
            SubSettingsActivity.PAGE_MISC, listOf("搜索", "也在看", "推荐", "also_view")),
        Feature(Settings.KEY_SUB_TAB_FILTER, "顶栏推广位", "清理首页/榜单等页面顶部的推广子标签",
            SubSettingsActivity.PAGE_MISC, listOf("顶栏", "推广", "子标签", "sub_tab")),
        Feature(Settings.KEY_HIDE_UPDATE_ALL, "全部升级按钮", "隐藏更新界面全部升级按钮",
            SubSettingsActivity.PAGE_MISC, listOf("全部升级", "按钮", "update_all")),
        Feature(Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, "自动升级开关", "隐藏更新界面自动升级开关",
            SubSettingsActivity.PAGE_MISC, listOf("自动升级", "开关", "auto_update")),
        Feature(Settings.KEY_PUSH_FLOAT, "Push悬浮通知", "屏蔽 MiPush 推送的悬浮通知",
            SubSettingsActivity.PAGE_MISC, listOf("推送", "通知", "悬浮", "push", "浮窗", "游戏")),
        Feature(Settings.KEY_UPDATE_FLOAT_CARD, "升级浮窗卡片", "屏蔽检测到新版本时弹出的浮窗升级提示",
            SubSettingsActivity.PAGE_MISC, listOf("升级", "浮窗", "卡片", "立即安装")),
        Feature(Settings.KEY_BLOCK_BG_DOWNLOAD, "屏蔽后台静默下载", "禁止商店在后台自动下载应用更新",
            SubSettingsActivity.PAGE_MISC, listOf("后台", "静默", "下载", "流量", "电量", "background")),

        // ═══════════ 悬浮底栏 ═══════════
        Feature(Settings.KEY_FLOATING_BAR, "启用悬浮底栏", "在商店底部渲染胶囊风格Tab导航栏",
            SubSettingsActivity.PAGE_TAB_BAR, listOf("悬浮", "底栏", "胶囊", "导航栏", "floating_bar")),
        Feature(Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "关闭后悬浮底栏只保留图标",
            SubSettingsActivity.PAGE_TAB_BAR, listOf("标签", "文字", "图标", "label")),
        Feature(Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "选中项显示跟随移动的液态胶囊",
            SubSettingsActivity.PAGE_TAB_BAR, listOf("液态", "动画", "高亮", "liquid")),
        Feature(Settings.KEY_FLOATING_BAR_MONOCHROME, "单色图标", "图标抽成单色描边、随主题着色",
            SubSettingsActivity.PAGE_TAB_BAR, listOf("单色", "图标", "描边", "主题", "monochrome")),
    )

    /**
     * 搜索功能：支持中文/英文关键词，大小写不敏感。
     * 返回按匹配度排序的结果列表。
     */
    fun search(query: String): List<Feature> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return allFeatures.filter { f ->
            f.title.lowercase().contains(q) ||
            f.summary.lowercase().contains(q) ||
            f.keywords.any { it.lowercase().contains(q) }
        }
    }

    /**
     * 推荐功能：每次随机取 3 个不同页面的功能，确保分类多样性。
     * 传入 [seed] 可控制随机结果（默认每次调用不同）。
     */
    fun recommend(count: Int = 3, seed: Long = System.currentTimeMillis()): List<Feature> {
        val rng = kotlin.random.Random(seed)
        // 按页面分组，每组随机取一个
        val byPage = allFeatures.groupBy { it.page }
        val selectedPages = byPage.keys.shuffled(rng).take(count.coerceAtMost(byPage.size))
        return selectedPages.mapNotNull { page ->
            byPage[page]?.random(rng)
        }
    }
}
