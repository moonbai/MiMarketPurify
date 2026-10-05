package com.mars.mimarketpurify

object FeatureRegistry {

    data class Feature(
        val key: String,
        val title: String,
        val summary: String,
        val page: String,
        val keywords: List<String>,
        val category: String,
    )

    val allFeatures: List<Feature> = listOf(
        // ═══════════════ 广告净化（14项） ═══════════════
        Feature(Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告",
            SubSettingsActivity.PAGE_ADS, listOf("开屏", "启动", "splash"), "广告净化"),
        Feature(Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗",
            SubSettingsActivity.PAGE_ADS, listOf("前台", "切换", "推荐", "弹窗"), "广告净化"),
        Feature(Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏",
            SubSettingsActivity.PAGE_ADS, listOf("信息流", "视频", "热词", "feed"), "广告净化"),
        Feature(Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐",
            SubSettingsActivity.PAGE_ADS, listOf("搜索", "建议", "search"), "广告净化"),
        Feature(Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐",
            SubSettingsActivity.PAGE_ADS, listOf("升级", "下载", "update"), "广告净化"),
        Feature(Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位",
            SubSettingsActivity.PAGE_ADS, listOf("详情", "评论", "detail"), "广告净化"),
        Feature(Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片",
            SubSettingsActivity.PAGE_ADS, listOf("榜单", "排名", "rank"), "广告净化"),
        Feature(Settings.KEY_FRUIT, "领水果入口", "隐藏福利活动 gif 动图入口",
            SubSettingsActivity.PAGE_ADS, listOf("水果", "福利", "活动", "fruit"), "广告净化"),
        Feature(Settings.KEY_ENTRANCE, "首页活动入口", "隐藏搜索框左侧云控下发的活动小图标",
            SubSettingsActivity.PAGE_ADS, listOf("首页", "活动", "云控", "entrance"), "广告净化"),
        Feature(Settings.KEY_DETAIL_EXTRAS, "详情页附加推荐", "详情页拼装推荐、底部多按钮推广栏",
            SubSettingsActivity.PAGE_ADS, listOf("详情", "附加", "推广栏"), "广告净化"),
        Feature(Settings.KEY_FLOATING_AD, "主页悬浮广告", "屏蔽主页底部/侧边弹出的悬浮广告",
            SubSettingsActivity.PAGE_ADS, listOf("悬浮", "浮动", "广告", "floating"), "广告净化"),
        Feature(Settings.KEY_AD_BACK_FLOAT, "返回浮窗广告", "屏蔽返回时弹出的「返回今日头条」等浮窗",
            SubSettingsActivity.PAGE_ADS, listOf("返回", "浮窗", "跳转", "adback"), "广告净化"),
        Feature(Settings.KEY_HOME_PAGE_DIALOG, "首页弹窗推广", "屏蔽进入首页时弹出的 Dialog 推广位",
            SubSettingsActivity.PAGE_ADS, listOf("首页", "弹窗", "dialog", "推广"), "广告净化"),
        Feature(Settings.KEY_INSTALL_RECOMMEND, "安装后推荐", "拦截点击安装后弹出的「用户还喜欢」推荐弹窗",
            SubSettingsActivity.PAGE_ADS, listOf("安装后", "用户还喜欢", "推荐弹窗", "install"), "广告净化"),

        // ═══════════════ 我的页精简（8项） ═══════════════
        Feature(Settings.KEY_MINE_RECOMMEND, "应用推荐与推广", "隐藏页面顶部推荐卡片与底部推广列表",
            SubSettingsActivity.PAGE_MINE, listOf("推荐", "推广", "我的", "mine"), "我的页精简"),
        Feature(Settings.KEY_MINE_OFFICIAL_TAB, "应用管理入口", "隐藏页面中间的官方应用管理功能入口",
            SubSettingsActivity.PAGE_MINE, listOf("官方", "入口", "tab"), "我的页精简"),
        Feature(Settings.KEY_MINE_CLEANUP, "清理与卸载", "隐藏手机清理与应用卸载入口",
            SubSettingsActivity.PAGE_MINE, listOf("清理", "卸载", "cleanup"), "我的页精简"),
        Feature(Settings.KEY_MINE_SUMMARY, "个人信息区", "隐藏头像、昵称、消息、收藏",
            SubSettingsActivity.PAGE_MINE, listOf("头像", "昵称", "消息", "收藏", "summary"), "我的页精简"),
        Feature(Settings.KEY_MINE_SECURITY, "安全检测", "隐藏应用安全检测卡片",
            SubSettingsActivity.PAGE_MINE, listOf("安全", "检测", "security"), "我的页精简"),
        Feature(Settings.KEY_ORCHARD_SKIN, "更新卡片背景", "清除升级卡片的果园背景",
            SubSettingsActivity.PAGE_MINE, listOf("果园", "背景", "皮肤", "orchard"), "我的页精简"),
        Feature(Settings.KEY_CARD_EXPAND, "升级卡片横向展开", "升级卡片展开显示更多应用更新",
            SubSettingsActivity.PAGE_MINE, listOf("展开", "升级", "卡片", "expand"), "我的页精简"),
        Feature(Settings.KEY_TAB_BADGE, "底栏角标", "去掉底部标签页的数字角标与红点",
            SubSettingsActivity.PAGE_MINE, listOf("角标", "红点", "badge", "数字"), "我的页精简"),

        // ═══════════════ 标签栏（2项） ═══════════════
        Feature(Settings.KEY_TAB_FILTER, "筛选底部标签", "选择需要展示的底栏标签",
            SubSettingsActivity.PAGE_TABS, listOf("标签", "筛选", "底部", "tab", "filter"), "标签栏"),
        Feature(Settings.KEY_UPDATE_TAB, "底栏更新入口", "在商店原生底栏注入「更新」入口",
            SubSettingsActivity.PAGE_TABS, listOf("更新", "入口", "底栏"), "标签栏"),

        // ═══════════════ 界面精简（10项） ═══════════════
        Feature(Settings.KEY_DETAIL_FEATURED, "详情页「精选」", "按文案匹配，仅在应用详情页生效",
            SubSettingsActivity.PAGE_MISC, listOf("详情", "精选", "featured"), "界面精简"),
        Feature(Settings.KEY_UPDATE_HISTORY, "升级记录推荐", "隐藏升级记录底部的精选推荐",
            SubSettingsActivity.PAGE_MISC, listOf("升级记录", "推荐", "精选", "history"), "界面精简"),
        Feature(Settings.KEY_SEARCH_ALSO_VIEW, "搜索页「也在看」", "隐藏搜索结果底部的推荐",
            SubSettingsActivity.PAGE_MISC, listOf("搜索", "也在看", "推荐", "also_view"), "界面精简"),
        Feature(Settings.KEY_SUB_TAB_FILTER, "顶栏推广位", "清理首页/榜单等页面顶部的推广子标签",
            SubSettingsActivity.PAGE_MISC, listOf("顶栏", "推广", "子标签", "sub_tab"), "界面精简"),
        Feature(Settings.KEY_HIDE_UPDATE_ALL, "全部升级按钮", "隐藏更新界面全部升级按钮",
            SubSettingsActivity.PAGE_MISC, listOf("全部升级", "按钮", "update_all"), "界面精简"),
        Feature(Settings.KEY_HIDE_AUTO_UPDATE_SWITCH, "自动升级开关", "隐藏更新界面自动升级开关",
            SubSettingsActivity.PAGE_MISC, listOf("自动升级", "开关", "auto_update"), "界面精简"),
        Feature(Settings.KEY_PUSH_FLOAT, "Push悬浮通知", "屏蔽 MiPush 推送的悬浮通知",
            SubSettingsActivity.PAGE_MISC, listOf("推送", "通知", "悬浮", "push", "浮窗"), "界面精简"),
        Feature(Settings.KEY_UPDATE_FLOAT_CARD, "升级浮窗卡片", "屏蔽检测到新版本时弹出的浮窗升级提示",
            SubSettingsActivity.PAGE_MISC, listOf("升级", "浮窗", "卡片", "立即安装"), "界面精简"),
        Feature(Settings.KEY_BLOCK_BG_DOWNLOAD, "屏蔽后台静默下载", "禁止商店在后台自动下载应用更新",
            SubSettingsActivity.PAGE_MISC, listOf("后台", "静默", "下载", "流量", "background"), "界面精简"),
        Feature(Settings.KEY_LONG_PRESS_JUMP, "长按跳转插件", "长按下载按钮跳转到插件主页",
            SubSettingsActivity.PAGE_MISC, listOf("长按", "跳转", "插件", "主页", "longpress"), "界面精简"),

        // ═══════════════ 悬浮底栏（已合并到底栏自定义 PAGE_TABS） ═══════════════
        Feature(Settings.KEY_FLOATING_BAR, "启用悬浮底栏", "替换商店原生贴底栏为居中胶囊导航",
            SubSettingsActivity.PAGE_TABS, listOf("悬浮", "底栏", "胶囊", "导航栏", "floating_bar"), "悬浮底栏"),
        Feature(Settings.KEY_FLOATING_BAR_LABEL, "显示标签文字", "关闭后悬浮底栏只保留图标",
            SubSettingsActivity.PAGE_TABS, listOf("标签", "文字", "图标", "label"), "悬浮底栏"),
        Feature(Settings.KEY_FLOATING_BAR_LIQUID, "液态选中高亮动画", "选中项显示跟随移动的液态胶囊",
            SubSettingsActivity.PAGE_TABS, listOf("液态", "动画", "高亮", "liquid"), "悬浮底栏"),
        Feature(Settings.KEY_FLOATING_BAR_LIQUID_3D, "3D 液态高亮", "在液态基础上叠加阴影，增强立体感",
            SubSettingsActivity.PAGE_TABS, listOf("3D", "液态", "阴影", "立体"), "悬浮底栏"),
        Feature(Settings.KEY_FLOATING_BAR_MONOCHROME, "单色图标", "图标抽成单色描边、随主题着色",
            SubSettingsActivity.PAGE_TABS, listOf("单色", "图标", "描边", "主题", "monochrome"), "悬浮底栏"),

        // ═══════════════ 补全为单源（主页高级/模块功能、子界面专属开关） ═══════════════
        Feature(Settings.KEY_TAB_DEEP_CLEAN, "顶栏标签深度清理", "清理首页/榜单等页面顶部的推广子标签",
            SubSettingsActivity.PAGE_TABS, listOf("顶栏", "深度", "清理", "deep"), "标签栏"),
        Feature(Settings.KEY_FLOAT_BAR_ENABLE, "启用插件悬浮底栏", "在主页 / 关于页底部显示居中胶囊导航",
            SubSettingsActivity.PAGE_THEME, listOf("插件", "悬浮", "底栏"), "悬浮底栏"),
        Feature(Settings.KEY_PREDICTIVE_BACK, "预测性返回", "启用系统返回手势的预测动画（Android 13+）",
            SubSettingsActivity.PAGE_THEME, listOf("返回", "手势", "predictive"), "主题与外观"),
        Feature(Settings.KEY_ISLAND, "下载超级岛", "强制让下载进度进入小米超级岛",
            SubSettingsActivity.PAGE_MISC, listOf("超级岛", "下载", "island"), "功能增强"),
        Feature(Settings.KEY_MISC, "细节修正", "显示非正版 APP、被隐藏更新等细节处理",
            SubSettingsActivity.PAGE_MISC, listOf("细节", "修正", "misc"), "功能增强"),
        Feature(Settings.KEY_UPDATE_DIALOG, "升级提醒弹窗", "不再弹出应用商店的升级提醒对话框",
            SubSettingsActivity.PAGE_MISC, listOf("升级", "弹窗", "提醒"), "功能增强"),
        Feature(Settings.KEY_RANK_DEBUG, "调试模式", "开启后将统一日志输出",
            SubSettingsActivity.PAGE_MISC, listOf("调试", "日志", "debug"), "功能增强"),
    )

    /** 按 KEY 索引；主页 / 子界面 / 关于页统一从此处取名称与描述，避免多份定义漂移 */
    private val byKey: Map<String, Feature> = allFeatures.associateBy { it.key }
    fun feature(key: String): Feature? = byKey[key]
    fun titleOf(key: String): String = byKey[key]?.title ?: key
    fun summaryOf(key: String): String = byKey[key]?.summary ?: ""

    /** 各子界面（PAGE）标题，主页与子界面共用，消除「底栏自定义 / 底部标签栏」这类不一致 */
    val pageTitles: Map<String, String> = mapOf(
        SubSettingsActivity.PAGE_ADS to "广告净化",
        SubSettingsActivity.PAGE_MINE to "「我的」页精简",
        SubSettingsActivity.PAGE_TABS to "底栏自定义",
        SubSettingsActivity.PAGE_MISC to "其他界面精简",
        SubSettingsActivity.PAGE_THEME to "主题与外观",
    )

    fun search(query: String): List<Feature> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return allFeatures.filter { f ->
            f.title.lowercase().contains(q) ||
            f.summary.lowercase().contains(q) ||
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
