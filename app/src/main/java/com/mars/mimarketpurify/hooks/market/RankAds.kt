package com.mars.mimarketpurify.hooks.market

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import dalvik.system.DexFile
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK

    override val name: String
        get() = "移除榜单广告"

    private val adTokens = setOf(
        "ad", "ads", "advert", "advertise", "advertisement", "advertorial",
        "banner", "promo", "promote", "promotion", "sponsor", "sponsored",
        "recommend", "recommendation", "splash"
    )

    private val typeGetters = listOf(
        "getComponentType", "getItemComponentType", "getItemType", "getType",
        "getViewType", "getItemViewType", "getTemplateType", "getCardType",
        "getBizType", "getStyleType", "getStyle", "getComponentName"
    )

    private val adFlags = listOf(
        "isAd", "getIsAd", "isAdvertise", "isAdvertisement", "isAdItem",
        "isPromote", "isPromotion", "isPromotionItem", "isSponsor", "isSponsored",
        "isRecommendAd", "isRankAd", "isMarketAd"
    )

    private val candidates = listOf(
        "com.xiaomi.market.business_ui.rank.RankFragment",
        "com.xiaomi.market.business_ui.rank.RankActivity",
        "com.xiaomi.market.business_ui.rank.RankListFragment",
        "com.xiaomi.market.business_ui.rank.RankListAdapter",
        "com.xiaomi.market.business_ui.rank.RankSubFragment",
        "com.xiaomi.market.business_ui.rank.RankChildFragment",
        "com.xiaomi.market.business_ui.rank.RankTabFragment",
        "com.xiaomi.market.business_ui.rank.GameRankFragment",
        "com.xiaomi.market.business_ui.rank.adapter.RankListAdapter",
        "com.xiaomi.market.common.view.ListAppsView"
    )

    private val parseMethods = listOf(
        "parseResponseData", "parseResponse", "parseData", "parseResult",
        "handleResponseData", "buildComponents", "getComponents"
    )

    private val dataOwnerClasses = listOf(
        "com.xiaomi.market.business_ui.rank.RankFragment",
        "com.xiaomi.market.business_ui.rank.RankListFragment",
        "com.xiaomi.market.business_ui.rank.RankSubFragment",
        "com.xiaomi.market.business_ui.rank.RankChildFragment",
        "com.xiaomi.market.business_ui.rank.RankTabFragment"
    )

    /** ★ 扩充：增加游戏榜常见推广标签 */
    private val cnLabels = listOf(
        "广告", "推广", "赞助", "热推",
        "游戏推荐", "热门游戏", "精品推荐", "编辑推荐", "精选", "推荐安装"
    )
    private val enLabels = setOf("ad", "ads", "sponsor", "sponsored", "promoted")

    private const val RANK_BADGE = "iv_app_ranking"
    private const val RANK_NUMBER = "tv_app_ranking"

    private val knownNormalTypes = setOf("nativeranklistapps", "ranklistapps")

    private const val BADGE_RATIO = 0.5f

    private val reported = Collections.synchronizedSet(mutableSetOf<String>())
    private val resolvedIds = Collections.synchronizedMap(mutableMapOf<String, Int>())

    @Volatile private var lastScanAt = 0L
    @Volatile private var lastWidthScanAt = 0L
    @Volatile private var lastToastAt = 0L

    override fun init() {
        hookDataLayer()

        var bound = 0

        val discovered = discoverRankClasses()
        HookEnv.base.log(
            Log.WARN, TAG, "$name: dex 扫描额外发现 ${discovered.size} 个 rank 类", null
        )

        (candidates + discovered).distinct().forEach { className ->
            runCatching {
                ClassUtil.loadClass(className)
                    .methodFinder()
                    .filterByName("onBindData")
                    .forEach { m ->
                        m.hooked {
                            val view = thisObject as? View
                            if (view != null) {
                                val bean = args.firstOrNull { isCandidateBean(it) }
                                when {
                                    bean != null && isAdBean(bean) -> {
                                        hide(view)
                                        return@hooked null
                                    }
                                    else -> {
                                        logUnknownShape(view, bean)
                                        hideAdViews(view)
                                        hideHeaderBanner(view)
                                        hideLabelledAds(view)
                                        hideByBadgeWidth(view)
                                        view.post { runCatching { dumpTree(view) } }
                                    }
                                }
                            }
                            return@hooked proceed()
                        }
                        bound++
                    }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 候选类不存在，跳过 $className", null)
            }
        }
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "$name: 已挂载 $bound 个绑定点（候选 ${candidates.size} 个 + 扫描 ${discovered.size} 个）",
            null
        )
    }

    private fun hookDataLayer() {
        parseMethods.forEach { method ->
            dataOwnerClasses.forEach { owner ->
                runCatching {
                    ClassUtil.loadClass(owner)
                        .methodFinder()
                        .filterByName(method)
                        .forEach { m ->
                            m.hooked {
                                val original = proceed()
                                dropAdComponents(original) ?: original
                            }
                        }
                }
            }
        }
    }

    private fun dropAdComponents(result: Any?): Any? {
        val list = when (result) {
            is List<*> -> result
            else -> return null
        }
        if (list.isEmpty()) return null
        val kept = list.filterNot { isAdComponent(it) }
        if (kept.size == list.size) return null
        HookEnv.base.log(
            Log.WARN, TAG, "$name: 数据层剔除 ${list.size - kept.size}/${list.size} 个广告组件", null
        )
        return kept
    }

    private fun isAdComponent(component: Any?): Boolean {
        val c = component ?: return false
        if (tokens(c.javaClass.simpleName).any { it in adTokens }) return true
        typeGetters.forEach { getter ->
            val text = readType(c, getter) ?: return@forEach
            if (tokens(text).any { it in adTokens }) return true
        }
        return false
    }

    private fun discoverRankClasses(): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val pathList = getClassLoader().getFieldValue("pathList")
            val elements = pathList?.getFieldValue("dexElements") as? Array<*> ?: return found
            elements.forEach { element ->
                val dex = element?.getFieldValue("dexFile") as? DexFile
                    ?: (element?.getFieldValue("path") as? String)
                        ?.let { p -> runCatching { DexFile(p) }.getOrNull() }
                    ?: return@forEach
                val entries = dex.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith("com.xiaomi.market") && name.contains("rank", true)) {
                        found += name
                    }
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描不可用：${it.message}", null)
        }
        return found
    }

    private fun isCandidateBean(arg: Any?): Boolean {
        if (arg == null) return false
        if (arg is View || arg is Number || arg is Boolean || arg is CharSequence) return false
        val n = arg.javaClass.name
        return !n.contains("Fragment") &&
            !n.contains("Activity") &&
            !n.contains("Context")
    }

    private fun isAdBean(bean: Any): Boolean {
        typeGetters.forEach { getter ->
            val text = readType(bean, getter) ?: return@forEach
            if (tokens(text).any { it in adTokens }) return true
        }
        adFlags.forEach { flag ->
            if (runCatching { bean.invokeAs<Boolean?>(flag) }.getOrNull() == true) return true
        }
        if (tokens(bean::class.java.simpleName).any { it in adTokens }) return true
        return false
    }

    private fun normalizeType(raw: String): String = raw.lowercase().replace("_", "")

    private fun readType(bean: Any, getter: String): String? =
        runCatching { bean.invokeAs<Any?>(getter)?.toString() }.getOrNull()

    private fun tokens(raw: String): List<String> =
        raw.replace(Regex("[^a-zA-Z0-9]+"), " ")
            .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
            .lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }

    private fun hideAdViews(view: View, depth: Int = 0) {
        if (depth > 5) return
        if (depth > 0 && isAdView(view)) {
            hide(view)
            return
        }
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            hideAdViews(child, depth + 1)
        }
    }

    private fun isAdView(v: View): Boolean =
        isAdResourceName(v) || tokens(v::class.java.simpleName).any { it in adTokens }

    private fun hideHeaderBanner(view: View) {
        if (!view::class.java.simpleName.contains("header", true)) return
        val dm = view.resources.displayMetrics
        val minW = (dm.widthPixels * 0.7f).toInt()
        val minH = (56 * dm.density).toInt()
        runCatching { scanBanners(view, 0, minW, minH) }
    }

    private fun scanBanners(view: View, depth: Int, minW: Int, minH: Int) {
        if (depth > 6) return
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        if (depth > 0 && w >= minW && h >= minH && h * 3 <= w) {
            hide(view)
            return
        }
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            scanBanners(child, depth + 1, minW, minH)
        }
    }

    private fun dumpTree(view: View) {
        if (!Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) return
        if (!reported.add("tree:" + view::class.java.simpleName)) return
        logTree(view, 0)
    }

    private fun logTree(view: View, depth: Int) {
        if (depth > 6) return
        val w = if (view.width > 0) view.width else view.measuredWidth
        val h = if (view.height > 0) view.height else view.measuredHeight
        val id = nameOf(view)
        val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
        val shown = if (text.isEmpty()) "" else " \"${text.take(24)}\""
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "[rank-tree] ${"· ".repeat(depth)}${view::class.java.simpleName}" +
                (if (id != null) "#$id" else "") + " ${w}x$h$shown",
            null
        )
        if (view !is ViewGroup) return
        val count = view.childCount.coerceAtMost(20)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            logTree(child, depth + 1)
        }
    }

    private fun nameOf(v: View): String? {
        if (v.id == View.NO_ID || v.id <= 0) return null
        return runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
    }

    private fun isAdResourceName(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false
        val n = runCatching { v.resources.getResourceEntryName(id) }
            .getOrNull()?.lowercase() ?: return false
        return n == "ad" ||
            n.startsWith("ad_") ||
            n.endsWith("_ad") ||
            n.contains("banner") ||
            n.contains("advert") ||
            n.contains("promot")
    }

    private fun hide(view: View) {
        runCatching {
            view.visibility = View.GONE
            view.layoutParams?.let { lp ->
                lp.height = 0
                view.layoutParams = lp
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 隐藏失败 ${it.message}", null)
        }
    }

    private fun hideLabelledAds(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAt < 1500) return
        lastScanAt = now

        view.post {
            runCatching {
                val list = findListContainer(view) ?: return@runCatching
                val count = list.childCount.coerceAtMost(24)
                for (i in 0 until count) {
                    val item = list.getChildAt(i) ?: continue
                    if (item.visibility != View.VISIBLE) continue
                    if (hasAdLabel(item, 0)) hide(item)
                }
            }
        }
    }

    private fun hideByBadgeWidth(view: View) {
        val now = SystemClock.uptimeMillis()
        if (now - lastWidthScanAt < 1000) return
        lastWidthScanAt = now

        view.post {
            runCatching {
                val list = findListContainer(view)
                    ?: (view.parent as? ViewGroup)
                    ?: return@runCatching
                list.post { runCatching { scanBadgeWidths(list) } }
            }
        }
    }

    private fun scanBadgeWidths(list: ViewGroup) {
        val count = list.childCount.coerceAtMost(32)
        val normal = ArrayList<Pair<View, Int>>()
        val suspects = ArrayList<View>()
        for (i in 0 until count) {
            val item = list.getChildAt(i) ?: continue
            if (item.visibility != View.VISIBLE) continue

            // ★ 新增：序号为"-"的 item 直接判为广告，不走后续启发式
            val number = findByIdName(item, RANK_NUMBER)
            val numberText = number?.text?.toString()?.trim() ?: ""
            if (numberText == "-" || numberText == "–" || numberText == "—") {
                suspects += item
                HookEnv.base.log(Log.WARN, TAG, "$name: 序号为'-'，标记为广告", null)
                continue
            }

            val numbered = number != null &&
                number.visibility == View.VISIBLE &&
                (number.width > 0 || number.measuredWidth > 0)
            if (!numbered) {
                suspects += item
                continue
            }
            val badge = findByIdName(item, RANK_BADGE)
            val w = if (badge == null) 0
            else if (badge.width > 0) badge.width else badge.measuredWidth
            if (w > 0) {
                normal += item to w
            } else {
                suspects += item
            }
        }

        val total = normal.size + suspects.size
        val widths = if (normal.isEmpty()) {
            "<未测到>"
        } else {
            normal.take(12).joinToString(",") { it.second.toString() }
        }
        val shape = "[rank] 名次宽度 $widths px / 无名次 ${suspects.size} 项 / 共 $total 项"
        reportWidths(list, shape, 0)

        // 安全阀：可疑项超过 1/4 就不动手
        if (total < 4 || suspects.isEmpty() || suspects.size * 4 > total) return

        suspects.forEach { hide(it) }
        HookEnv.base.log(
            Log.WARN,
            TAG,
            "$name: 无可见名次，隐藏 ${suspects.size} 条（$shape）",
            null
        )

        if (normal.size >= 4) {
            val mid = normal.map { it.second }.sorted()[normal.size / 2]
            val threshold = (mid * BADGE_RATIO).toInt().coerceAtLeast(1)
            val narrow = normal.filter { it.second <= threshold }
            if (narrow.isNotEmpty() && narrow.size * 4 <= normal.size) {
                narrow.forEach { hide(it.first) }
                HookEnv.base.log(
                    Log.WARN, TAG, "$name: 名次图标偏窄，隐藏 ${narrow.size} 条（中位数 $mid px）", null
                )
            }
        }
    }

    private fun reportWidths(list: View, shape: String, hits: Int) {
        if (!reported.add(shape)) return
        HookEnv.base.log(Log.WARN, TAG, shape, null)
        if (!Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastToastAt < 3000) return
        lastToastAt = now
        list.post {
            runCatching {
                val tip = if (hits > 0) "$shape / 已隐藏 $hits 条" else shape
                Toast.makeText(list.context, tip, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findByIdName(view: View, name: String, depth: Int = 0): View? {
        val id = resolveId(view, name)
        if (id == 0) return null
        if (view.id == id) return view
        if (depth >= 6 || view !is ViewGroup) return null
        val count = view.childCount.coerceAtMost(24)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            findByIdName(child, name, depth + 1)?.let { return it }
        }
        return null
    }

    private fun resolveId(view: View, name: String): Int {
        resolvedIds[name]?.let { if (it != 0) return it }
        val id = runCatching {
            view.resources.getIdentifier(name, "id", "com.xiaomi.market")
        }.getOrNull() ?: 0
        if (id != 0) resolvedIds[name] = id
        return id
    }

    private fun findListContainer(view: View): ViewGroup? {
        var p = view.parent
        while (p is View) {
            if (p is ViewGroup && p::class.java.name.contains("RecyclerView")) return p
            p = p.parent
        }
        return null
    }

    private fun hasAdLabel(view: View, depth: Int): Boolean {
        if (depth > 5) return false
        if (view is TextView) {
            val raw = view.text?.toString() ?: ""
            if (raw.isEmpty()) return false
            val trim = raw.trim()
            // ★ 中文标签：先精确匹配，再 contains（"广告 · 下载" 这类带后缀的文案）
            if (cnLabels.any { trim == it }) return true
            if (cnLabels.any { trim.contains(it) }) return true
            // ★ 去空白后匹配（防止 "广 告" 等 unicode 空格变体）
            val noSpace = trim.replace(Regex("\\s+"), "")
            if (cnLabels.any { noSpace.contains(it) }) return true
            // ★ 英文标签：词匹配（避免 Adobe 误杀）
            if (tokens(noSpace.lowercase()).any { it in enLabels }) return true
        }
        if (view !is ViewGroup) return false
        val count = view.childCount.coerceAtMost(16)
        for (i in 0 until count) {
            val child = view.getChildAt(i) ?: continue
            if (hasAdLabel(child, depth + 1)) return true
        }
        return false
    }

    private fun logUnknownShape(view: View, bean: Any?) {
        val type = if (bean == null) null else {
            typeGetters.firstNotNullOfOrNull { getter -> readType(bean, getter) }
        }
        if (type != null && normalizeType(type) in knownNormalTypes) return
        val shape = buildString {
            append("[rank] ")
            append(view::class.java.simpleName)
            append(" <- ")
            append(bean?.javaClass?.simpleName ?: "<no bean>")
            append(" type=")
            append(type ?: "<none>")
        }
        if (reported.add(shape)) {
            HookEnv.base.log(Log.WARN, TAG, shape, null)
            if (Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) {
                view.post {
                    runCatching { Toast.makeText(view.context, shape, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}