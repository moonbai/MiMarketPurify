package com.mars.mimarketpurify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView

class SubSettingsActivity : SettingsBaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_ADS = "ads"
        const val PAGE_MINE = "mine"
        const val PAGE_TABS = "tabs"
        const val PAGE_MISC = "misc"
        const val PAGE_EXTRA = "extra"
        const val PAGE_MODULE = "module"

        fun intent(context: Context, page: String): Intent =
            Intent(context, SubSettingsActivity::class.java).putExtra(EXTRA_PAGE, page)
    }

    private var page: String = PAGE_MINE
    private val tabChecks = mutableListOf<CheckBox>()
    private var tabSelectBlock: View? = null
    private var hideIconSwitch: CompoundButton? = null

    private val adFeatures = listOf(
        Feature(Settings.KEY_SPLASH, "开屏广告", "屏蔽应用商店启动时的开屏广告"),
        Feature(Settings.KEY_MAIN_TAB, "前台广告/推荐", "屏蔽主页切换时的推荐与广告弹窗"),
        Feature(Settings.KEY_HOME_FEED, "信息流广告", "隐藏主页底部视频/应用推荐与热词栏"),
        Feature(Settings.KEY_SEARCH, "搜索推荐", "搜索建议、搜索页、搜索结果的软件推荐"),
        Feature(Settings.KEY_UPDATE_DL, "升级/下载推荐", "应用升级页与下载页的软件推荐"),
        Feature(Settings.KEY_DETAIL, "详情页广告", "应用详情页的广告、评论与推荐位"),
        Feature(Settings.KEY_RANK, "榜单广告", "榜单界面的广告 / 推广卡片")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = intent?.getStringExtra(EXTRA_PAGE) ?: PAGE_MINE
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG)
        }
        setupRoot(header)
        buildSubTopBar(header, titleOf(page))
        when (page) {
            PAGE_ADS -> buildAds()
            PAGE_MINE -> buildMine()
            PAGE_TABS -> buildTabs()
            PAGE_MISC -> buildMisc()
            PAGE_EXTRA -> buildExtra()
            else -> buildModule()
        }
        refreshAll()
    }

    private fun titleOf(page: String): String = when (page) {
        PAGE_ADS -> "广告净化"
        PAGE_MINE -> "「我的」页精简"
        PAGE_TABS -> "底部标签栏"
        PAGE_MISC -> "其他界面精简"
        PAGE_EXTRA -> "高级净化"
        else -> "模块功能"
    }

    private fun buildAds() {
        addSectionHeader("广告净化", "拦截商店各处的广告与软件推荐")
        val group = groupCard()
        adFeatures.forEach { f ->
            addSwitchRow(group = group, title = f.title, summary = f.summary,
                checked = readLocal(f.key, true), tag = f.key
            ) { on -> writeRemote(f.key, on) }
        }
        addSwitchRow(group = group, title = "领水果入口",
            summary = "隐藏福利活动 gif 动图入口",
            checked = readLocal(Settings.KEY_FRUIT, true), tag = Settings.KEY_FRUIT
        ) { on -> writeRemote(Settings.KEY_FRUIT, on) }
        addSwitchRow(group = group, title = "首页活动入口",
            summary = "隐藏搜索框左侧云控下发的活动小图标 / 动图",
            checked = readLocal(Settings.KEY_ENTRANCE, true), tag = Settings.KEY_ENTRANCE
        ) { on -> writeRemote(Settings.KEY_ENTRANCE, on) }
        addSwitchRow(group = group, title = "详情页广告",
            summary = "详情页拼装推荐、底部多按钮推广栏、浏览器下载弹窗广告",
            checked = readLocal(Settings.KEY_DETAIL_EXTRAS, true), tag = Settings.KEY_DETAIL_EXTRAS
        ) { on -> writeRemote(Settings.KEY_DETAIL_EXTRAS, on) }
        content.addView(group)
        addFooter("屏蔽后若页面空白，多为该页组件被整体过滤，关掉对应开关即可恢复。")
    }

    private fun buildExtra() {
        addSectionHeader("高级净化", "深度运营内容、活动入口、弹窗与角标清理")
        val group = groupCard()
        content.addView(group)
        addFooter("这些开关会同时作用于「移除升级/下载推荐」等既有功能，关掉后对应位置恢复原样。")
    }

    private fun buildMine() {
        addSectionHeader("「我的」页精简", "清理「我的」页中不需要的板块与推荐")
        val group = groupCard()
        addSwitchRow(group = group, title = "应用推荐与推广",
            summary = "隐藏页面顶部推荐卡片与底部推广列表",
            checked = readLocal(Settings.KEY_MINE_RECOMMEND, true), tag = Settings.KEY_MINE_RECOMMEND
        ) { on -> writeRemote(Settings.KEY_MINE_RECOMMEND, on) }
        addSwitchRow(group = group, title = "应用管理入口",
            summary = "隐藏页面中间的官方应用管理功能入口 tab",
            checked = readLocal(Settings.KEY_MINE_OFFICIAL_TAB, true), tag = Settings.KEY_MINE_OFFICIAL_TAB
        ) { on -> writeRemote(Settings.KEY_MINE_OFFICIAL_TAB, on) }
        addSwitchRow(group = group, title = "清理与卸载",
            summary = "隐藏手机清理与应用卸载入口",
            checked = readLocal(Settings.KEY_MINE_CLEANUP, true), tag = Settings.KEY_MINE_CLEANUP
        ) { on -> writeRemote(Settings.KEY_MINE_CLEANUP, on) }
        addSwitchRow(group = group, title = "个人信息区",
            summary = "隐藏头像、昵称、消息、收藏",
            checked = readLocal(Settings.KEY_MINE_SUMMARY, true), tag = Settings.KEY_MINE_SUMMARY
        ) { on -> writeRemote(Settings.KEY_MINE_SUMMARY, on) }
        addSwitchRow(group = group, title = "安全检测",
            summary = "隐藏应用安全检测卡片",
            checked = readLocal(Settings.KEY_MINE_SECURITY, true), tag = Settings.KEY_MINE_SECURITY
        ) { on -> writeRemote(Settings.KEY_MINE_SECURITY, on) }
        addSwitchRow(group = group, title = "更新卡片背景",
            summary = "清除升级卡片的果园背景",
            checked = readLocal(Settings.KEY_ORCHARD_SKIN, true), tag = Settings.KEY_ORCHARD_SKIN
        ) { on -> writeRemote(Settings.KEY_ORCHARD_SKIN, on) }
            
        
        // ========== 锁定：升级卡片展开 ==========
        addSwitchRow(group = group,
            title = "升级卡片展开",
            summary = "升级卡片默认展开显示更多应用更新（该选项已锁定，不可修改）",
            checked = readLocal(Settings.KEY_CARD_EXPAND, false),
            tag = Settings.KEY_CARD_EXPAND
        ) {}.apply {
            isEnabled = false
            isClickable = false
            setOnTouchListener { _, _ -> true }
        }
                    
        addSwitchRow(group = group, title = "底栏角标",
            summary = "去掉底部标签页的数字角标与「新」字红点",
            checked = readLocal(Settings.KEY_TAB_BADGE, true), tag = Settings.KEY_TAB_BADGE
        ) { on -> writeRemote(Settings.KEY_TAB_BADGE, on) }
        content.addView(group)
        addFooter("改动一般在下次进入「我的」页时生效。")
    }

    private fun buildTabs() {
        addSectionHeader("底部标签栏", "分别控制底栏标签与顶栏推广位")
        val group = groupCard()

        addSwitchRow(group = group, title = "筛选底部标签",
            summary = "隐藏不需要的底部标签（首页/我的/榜单等）",
            checked = readLocal(Settings.KEY_TAB_FILTER, true), tag = Settings.KEY_TAB_FILTER
        ) { on -> writeRemote(Settings.KEY_TAB_FILTER, on); updateGateState() }

        // ========== 锁定：底栏「更新」入口 ==========
        addSwitchRow(group = group, title = "底栏「更新」入口",
            summary = "在底栏标签栏添加直达「应用更新」页面的快捷入口（该选项已锁定，不可修改）",
            checked = readLocal(Settings.KEY_UPDATE_TAB, false), tag = Settings.KEY_UPDATE_TAB
        ) {}.apply {
            isEnabled = false
            isClickable = false
            setOnTouchListener { _, _ -> true }
        }
                
        buildTabSelectBlock(group)
        content.addView(group)
        addFooter("隐藏标签后需重启一次应用商店才会重建底栏。")
    }

    private fun buildMisc() {
        addSectionHeader("其他界面精简", "各类零散页面、弹窗的冗余内容清理")
        val group = groupCard()
        addSwitchRow(group = group, title = "详情页「精选」",
            summary = "按文案匹配，仅在应用详情页生效",
            checked = readLocal(Settings.KEY_DETAIL_FEATURED, true), tag = Settings.KEY_DETAIL_FEATURED
        ) { on -> writeRemote(Settings.KEY_DETAIL_FEATURED, on) }
        addSwitchRow(group = group, title = "升级记录页推荐",
            summary = "隐藏「升级记录」底部的精选推荐、热门下载、大家还安装了",
            checked = readLocal(Settings.KEY_UPDATE_HISTORY, true), tag = Settings.KEY_UPDATE_HISTORY
        ) { on -> writeRemote(Settings.KEY_UPDATE_HISTORY, on) }
        addSwitchRow(group = group, title = "搜索页「也在看」",
            summary = "隐藏搜索结果底部的「搜索 xxx 的人也在看」",
            checked = readLocal(Settings.KEY_SEARCH_ALSO_VIEW, true), tag = Settings.KEY_SEARCH_ALSO_VIEW
        ) { on -> writeRemote(Settings.KEY_SEARCH_ALSO_VIEW, on) }

        addSwitchRow(group = group, title = "筛选顶栏推广位",
            summary = "清理首页/榜单等页面顶部的云控推广子标签",
            checked = readLocal(Settings.KEY_SUB_TAB_FILTER, true), tag = Settings.KEY_SUB_TAB_FILTER
        ) { on -> writeRemote(Settings.KEY_SUB_TAB_FILTER, on) }

        content.addView(group)
        addFooter("升级记录与搜索结果按标题文案匹配，改版后可能失效，届时请反馈。")
    }

    private fun buildModule() {
        addSectionHeader("模块功能", "仅影响本模块的显示方式与调试选项")
        val group = groupCard()
        hideIconSwitch = addSwitchRow(group = group, title = "隐藏桌面图标",
            summary = "仅移除桌面抽屉中的图标，仍可从 LSPosed 模块列表进入主页",
            checked = isLauncherIconHidden(), tag = "hide_launcher_icon", gated = false, remote = false
        ) { hide -> applyHideIcon(hide) }
        addSwitchRow(group = group, title = "调试默认",
            summary = "开启后模块将统一日志输出，日常使用建议关闭",
            checked = readLocal(Settings.KEY_RANK_DEBUG, false), tag = Settings.KEY_RANK_DEBUG, gated = false
        ) { on -> writeRemote(Settings.KEY_RANK_DEBUG, on) }
        content.addView(group)
        addFooter("隐藏图标后需从 LSPosed 等框架的模块列表打开主页。")
    }

    private fun addFooter(text: String) {
        content.addView(TextView(this).apply {
            this.text = text; textSize = Ui.MICRO; setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), dp(2), dp(4), dp(16))
        })
    }

    private fun buildTabSelectBlock(group: LinearLayout) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(2); it.bottomMargin = dp(8) }
            setPadding(dp(12), 0, 0, 0)
        }
        block.addView(TextView(this).apply {
            text = "保留哪些标签（取消勾选 = 隐藏该标签）"
            textSize = Ui.ROW_SUMMARY; setTextColor(Ui.TEXT_SECONDARY)
            setPadding(dp(Ui.ROW_PAD_H), dp(4), dp(Ui.ROW_PAD_H), dp(2))
        })
        Settings.TAB_ITEMS.forEach { (tag, label) ->
            val cb = CheckBox(this).apply {
                text = label; textSize = Ui.ROW_TITLE; setTextColor(Ui.TEXT_PRIMARY)
                this.tag = tag; isChecked = readLocalTabs().contains(tag)
                setPadding(dp(Ui.ROW_PAD_H), dp(4), dp(4), dp(4))
                compoundDrawablePadding = dp(10); minimumHeight = dp(Ui.TOUCH_MIN)
                buttonDrawable?.let { buttonDrawable = it.tinted(Ui.ACCENT, Ui.CHECK_OFF) }
                setOnCheckedChangeListener { _, _ -> writeTabSelection() }
            }
            tabChecks.add(cb); block.addView(cb)
        }
        group.addView(block); tabSelectBlock = block
    }

    private fun writeTabSelection() {
        val kept = tabChecks.filter { it.isChecked }.map { it.tag as String }.toSet()
        writeRemoteString(Settings.KEY_TAB_KEEP, kept.joinToString(","))
    }

    override fun onRefresh() {
        hideIconSwitch?.isChecked = isLauncherIconHidden()
        val kept = readLocalTabs()
        tabChecks.forEach { cb -> val t = cb.tag; cb.isChecked = t is String && kept.contains(t) }
    }

    override fun updateGateState() {
        super.updateGateState()
        val master = readLocal(Settings.KEY_MASTER, true)
        val filterOn = readLocal(Settings.KEY_TAB_FILTER, true)
        tabSelectBlock?.visibility = if (filterOn) View.VISIBLE else View.GONE
        tabChecks.forEach { it.isEnabled = master && filterOn }
    }

    data class Feature(val key: String, val title: String, val summary: String)
}
