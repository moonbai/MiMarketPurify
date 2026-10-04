package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections
import java.util.WeakHashMap

/**
 * 按**板块标题文案**整块隐藏推荐位。
 *
 * 目标页面的共同点：推荐位是「一个标题 + 一排应用」组成的卡片，
 * 标题文案稳定（精选推荐 / 搜索 xxx 的人也在看 / 猜你喜欢），但容器 id / 类名随版本乱变，
 * 按 id 匹配基本抓不住。所以这里反过来——先认标题，再往上找到整块卡片的根，
 * 把根整个隐藏，应用列表自然一起消失。
 *
 * 各页面用独立开关控制（[Settings.KEY_UPDATE_HISTORY] / [Settings.KEY_SEARCH_ALSO_VIEW] /
 * [Settings.KEY_DETAIL_RECOMMEND]，因此本 hook 不设单一 prefKey。
 */
object RecommendSections : BaseHook() {

    override val prefKey: String? = null

    override val name: String
        get() = "推荐板块屏蔽"

    /** 「升级记录」页底部三类推荐的标题 */
    private val historyTitles = setOf(
        "精选推荐", "热门下载", "大家还安装了", "大家还装了", "大家还在装"
    )

    /** 「升级记录」页 */
    private const val HISTORY_HOST = "UpdateHistoryActivity"

    /** 搜索结果页「搜索 xxx 的人也在看」——文案带搜索词，只能按片段匹配 */
    private val alsoViewTokens = listOf("也在看", "还在看", "还看了")

    /** 搜索结果页 */
    private const val SEARCH_HOST = "SearchActivityPhone"

    /** 详情页（AppDetailActivityInner）推荐区块的常见标题——按片段匹配，覆盖版本文案微调 */
    private val detailTokens = listOf(
        "猜你喜欢", "相关推荐", "热门推荐", "为你推荐", "人气推荐",
        "你可能喜欢", "大家也在用", "大家也在看", "大家都在用", "大家都在看",
        "的用户还喜欢", "大家还喜欢", "精选推荐", "大家都在下载"
    )

    /** 详情页 */
    private const val DETAIL_HOST = "AppDetailActivityInner"

    /**
     * 「安装后插入的关联推荐」卡片标题——全局生效（不限页面）：
     * 这些文案只出现在推荐卡标题上，命中即隐藏整卡，不依赖页面类名 / 容器 id，
     * 覆盖搜索结果页点击安装后插入的卡片、以及其他任何页面动态插入的同款卡片。
     */
    private val globalTokens = listOf(
        "的用户还喜欢", "大家还喜欢", "你可能还喜欢",
        // ===== 新增：下载/安装后弹出的推荐弹窗文案 =====
        "安装后", "可能还会喜欢", "热门推荐", "相似应用", "同类型",
        "大家都在用", "大家都在装", "你可能喜欢", "安装了", "也喜欢",
    )

    /**
     * 更新/升级板块标题白名单——命中后**不**当作推荐位隐藏。
     *
     * 例如「升级[招商银行]的用户也安装了这些应用」是「我的→升级记录」页里的升级关联板块，
     * 标题含「升级」二字，会被 [globalTokens] 里的「安装了」通配符误伤。
     * 升级/更新类板块与推荐位的语义不同，必须保留可见，故在此显式排除。
     * 仅用内容语义关键词，不绑定系统/App 版本号。
     */
    private val updateSectionTokens = listOf("升级", "更新")

    /** 已隐藏过的板块标题，避免日志刷屏 */
    private val reported = Collections.synchronizedSet(mutableSetOf<String>())

    /** 已挂常驻防恢复监听的 decorView（WeakHashMap：页面销毁后自动释放，防泄漏） */
    private val watchedDecors = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 防恢复重扫节流：布局变化后 500ms 内只扫一次 */
    private const val GLOBAL_RESCAN_INTERVAL_MS = 500L

    override fun init() {
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("onAttachedToWindow")
                .first()
                .hooked {
                    val result = proceed()
                    (thisObject as? View)?.let { scanTree(it, 0, 2) }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: View.onAttachedToWindow 挂钩失败", it)
        }

        hookRescan("com.xiaomi.market.ui.UpdateHistoryActivity")
        hookRescan("com.xiaomi.market.ui.SearchActivityPhone")
        hookRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        runCatching {
            ClassUtil.loadClass("android.app.Activity")
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        val decor = (thisObject as? Activity)?.window?.decorView
                        if (decor != null && watchedDecors.put(decor, true) == null) {
                            decor.viewTreeObserver.addOnGlobalLayoutListener(
                                object : ViewTreeObserver.OnGlobalLayoutListener {
                                    private var lastScan = 0L
                                    override fun onGlobalLayout() {
                                        val now = SystemClock.uptimeMillis()
                                        if (now - lastScan < GLOBAL_RESCAN_INTERVAL_MS) return
                                        lastScan = now
                                        runCatching { scanTree(decor, 0) }
                                    }
                                }
                            )
                        }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: Activity.onResume 防恢复监听挂钩失败", it)
        }
    }

    private fun hookRescan(className: String) {
        runCatching {
            ClassUtil.loadClass(className)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? Activity)?.window?.decorView?.let { scanTree(it, 0) }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }

    private fun scanTree(v: View, depth: Int, maxDepth: Int = 10) {
        if (depth > maxDepth) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1, maxDepth)
        }
    }

    private fun inspect(v: View) {
        if (v !is TextView) return
        if (v.visibility != View.VISIBLE) return
        val text = v.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        // 升级/更新板块标题不视为推荐位，避免误隐藏「我的→升级记录」相关板块
        if (updateSectionTokens.any { text.contains(it) }) return
        val host = v.context?.javaClass?.name.orEmpty()
        if (host.isEmpty()) return
        val hit = when {
            // 更新列表页（UpdateListActivity）属于功能性页面，其关联推荐容器（如 R.id.info_view）
            // 文案常含「安装了」等 globalTokens 片段，若在此命中会把整段更新列表一起隐藏。
            // 该页没有独立的推荐屏蔽开关，故将其排除在全局匹配之外，避免误伤更新列表。
            Settings.isEnabled(Settings.KEY_SEARCH, true) &&
                !host.contains("UpdateListActivity") &&
                globalTokens.any { text.contains(it) } -> true

            host.contains(HISTORY_HOST) &&
                Settings.isEnabled(Settings.KEY_UPDATE_HISTORY, true) &&
                text in historyTitles -> true

            host.contains(SEARCH_HOST) &&
                Settings.isEnabled(Settings.KEY_SEARCH_ALSO_VIEW, true) &&
                alsoViewTokens.any { text.contains(it) } -> true

            host.contains(DETAIL_HOST) &&
                Settings.isEnabled(Settings.KEY_DETAIL_RECOMMEND, true) &&
                detailTokens.any { text.contains(it) } -> true

            else -> false
        }
        if (hit) hideSection(v, text)
    }

    private fun hideSection(title: View, text: String) {
        runCatching {
            val dm = title.resources.displayMetrics
            val fullW = (dm.widthPixels * 0.7f).toInt()
            val maxH = (dm.heightPixels * 0.7f).toInt()
            var cur = title
            var best: View? = null
            repeat(7) {
                val parent = cur.parent as? View ?: return@repeat
                if (isRecycler(parent)) {
                    best = cur
                    return@repeat
                }
                cur = parent
                if (cur.width >= fullW && cur.height > title.height && cur.height <= maxH) {
                    best = cur
                }
            }
            val target = best ?: return
            if (target.visibility == View.GONE) return
            target.visibility = View.GONE
            target.layoutParams?.let { lp ->
                lp.height = 0
                target.layoutParams = lp
            }
            if (reported.add(text)) {
                HookEnv.base.log(Log.WARN, TAG, "$name: 已隐藏推荐板块「$text」", null)
                mainHandler.postDelayed({
                    runCatching {
                        if (target.visibility != View.GONE) {
                            debugLog("⚠「$text」隐藏后 1s 被商店恢复，已重新隐藏")
                            target.visibility = View.GONE
                            target.layoutParams?.let { lp ->
                                lp.height = 0
                                target.layoutParams = lp
                            }
                        }
                    }
                }, 1000L)
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏「$text」失败 ${it.message}", null)
        }
    }

    private fun isRecycler(v: View): Boolean =
        v is ViewGroup && v::class.java.name.contains("RecyclerView")
}
