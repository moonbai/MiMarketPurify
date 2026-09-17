package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import com.mars.mimarketpurify.util.setFieldValue
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object TabFilter : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_FILTER

    override val name: String
        get() = "筛选底部TAB标签与云控推广位"

    private const val HOME_TAG = "native_market_home"

    /** 本模块注入的 tag，永远不过滤 */
    private const val PURIFY_UPDATE = "purify_update"

    private val homeSubTabWhitelist by lazy {
        setOf(
            "native_market_feature",
            "native_market_rank_software",
            "must-have",
            "GOLDEN MI AWARD",
            "Classification",
            "software_sub5",
            "minor"
        )
    }

    private val subBlackTags by lazy {
        setOf("xiaomishipin", "native_market_shortplay", "native_market_agent")
    }

    private val subBlackTitles by lazy {
        setOf("看剧", "短剧")
    }

    private var tabField: Field? = null

    // ====== UpdateTabEntry 合并字段 ======
    private var tabInfoClz: Class<*>? = null
    private var titlesField: Field? = null
    private var urlField: Field? = null

    override fun init() {
        // 预加载 UpdateTabEntry 所需字段
        runCatching {
            tabInfoClz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
            titlesField = tabInfoClz?.fieldFinder()
                ?.filterByName("titles")?.firstOrNull()
            urlField = tabInfoClz?.fieldFinder()
                ?.filterByName("url")?.firstOrNull()
            debugLog("UpdateTabEntry fields: titles=${titlesField != null}, url=${urlField != null}")
        }.onFailure {
            debugLog("UpdateTabEntry fields 加载失败: ${it.message}")
        }

        hookTabInfoParse()
        runCatching { hookPagerTabsInfo() }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] PagerTabsInfo 收口不可用，跳过: ${it.message}", null)
        }
    }

    // = = = = 公共判定 = = = =

    private fun tagOf(tab: Any): String? {
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching { tab.invokeAs<String>("getTag") }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun titlesOf(tab: Any): Map<String, String>? =
        runCatching { tab.getFieldValue("titles") as? Map<String, String> }.getOrNull()

    private fun isBlacklisted(tag: String?, titles: Map<String, String>?): Boolean =
        (tag != null && subBlackTags.contains(tag)) ||
            titles?.values?.any { subBlackTitles.contains(it) } == true

    private fun deniedByWhitelist(parentTag: String?, siblings: List<String?>, tag: String?): Boolean {
        if (parentTag != HOME_TAG) return false
        val looksManaged = siblings.any { it != null && homeSubTabWhitelist.contains(it) }
        val whitelisted = tag != null && homeSubTabWhitelist.contains(tag)
        return looksManaged && !whitelisted
    }

    // = = = = ① 数据层：TabInfo.fromJSON = = = =

    private fun hookTabInfoParse() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
            tabField = runCatching {
                clazz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull()
            }.getOrNull()

            clazz.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val kept = Settings.getKeptTabs()
                    debugLog("fromJSON: kept=$kept")
                    if (kept.isEmpty()) {
                        debugLog("fromJSON: kept 为空，跳过过滤")
                        return@hooked proceed()
                    }

                    val result = proceed()
                    val list = (result as List<*>).toMutableList()
                    val beforeCount = list.size

                    // ====== Step 1: 过滤 unwanted tabs ======
                    list.removeAll { item ->
                        if (item == null) return@removeAll true
                        val tag = runCatching { tagOf(item) }.getOrNull()
                        if (tag == PURIFY_UPDATE) return@removeAll false
                        val removed = tag == null || tag !in kept
                        if (removed) {
                            debugLog("fromJSON: removed tab ${tag ?: "(null)"}")
                        }
                        removed
                    }

                    val afterCount = list.size
                    if (beforeCount != afterCount) {
                        debugLog("fromJSON: ${beforeCount} → ${afterCount} tabs")
                    }

                    list.forEach { runCatching { sanitizeSubTabs(it, 0) } }

                    // ====== Step 2: 注入 purify_update ======
                    val alreadyHas = list.any { item ->
                        item != null && runCatching {
                            tagField?.get(item) as? String
                        }.getOrNull() == PURIFY_UPDATE
                    }
                    if (!alreadyHas && tabInfoClz != null) {
                        val tab = runCatching { tabInfoClz!!.newInstance() }.getOrNull()
                        if (tab != null) {
                            runCatching { tabField?.set(tab, PURIFY_UPDATE) }
                            runCatching { titlesField?.set(tab, mapOf("cn" to "更新", "en" to "Update")) }
                            runCatching { urlField?.set(tab, "market://update") }
                            list.add(tab)
                            debugLog("fromJSON: 注入 purify_update, total=${list.size}")
                        } else {
                            debugLog("fromJSON: TabInfo.newInstance() 失败")
                        }
                    } else if (alreadyHas) {
                        debugLog("fromJSON: purify_update 已存在，跳过注入")
                    }

                    return@hooked list
                }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked TabInfo.fromJSON", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook TabInfo 失败: ${e.message}", null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sanitizeSubTabs(tab: Any?, depth: Int) {
        if (tab == null || depth > 2) return
        val parentTag = runCatching { tagOf(tab) }.getOrNull()
        val subs = runCatching { tab.invokeAs<MutableList<Any>>("getSubTabs") }.getOrNull() ?: return
        if (subs.isEmpty()) return
        val subTags = subs.map { runCatching { tagOf(it) }.getOrNull() }
        val removed = subs.filterIndexed { i, _ ->
            val titles = runCatching { titlesOf(subs[i]) }.getOrNull()
            val hit = isBlacklisted(subTags[i], titles) ||
                deniedByWhitelist(parentTag, subTags, subTags[i])
            if (hit) {
                val reason = when {
                    subBlackTags.contains(subTags[i]) -> "blacklisted tag"
                    titles?.values?.any { subBlackTitles.contains(it) } == true -> "blacklisted title"
                    else -> "denied by whitelist"
                }
                debugLog("subTab removed: ${subTags[i]}(${titles?.get("cn")}) under $parentTag reason=$reason")
                HookEnv.base.log(
                    Log.INFO, TAG,
                    "[TabFilter] removed subTab: ${subTags[i]}(${titles?.get("cn")}) under $parentTag",
                    null
                )
            }
            hit
        }
        if (removed.isNotEmpty()) subs.removeAll(removed.toSet())
        subs.forEach { runCatching { sanitizeSubTabs(it, depth + 1) } }
    }

    // = = = = ② 渲染层：PagerTabsInfo.fromTabInfo = = = =

    private fun hookPagerTabsInfo() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.ui.PagerTabsInfo")
            val method = clazz.methodFinder().filterByName("fromTabInfo").firstOrNull()
                ?: clazz.methodFinder().firstOrNull {
                    Modifier.isStatic(modifiers) &&
                        parameterTypes.size == 1 &&
                        parameterTypes[0].name.endsWith("TabInfo") &&
                        returnType.name.endsWith("PagerTabsInfo")
                }
            if (method == null) {
                HookEnv.base.log(Log.VERBOSE, TAG, "[TabFilter] PagerTabsInfo.fromTabInfo 不存在，跳过", null)
                return
            }
            method.hooked {
                val result = proceed()
                if (result != null) {
                    val parentTag = runCatching { args[0]?.let { tagOf(it) } }.getOrNull()
                    debugLog("PagerTabsInfo: parentTag=$parentTag, processing...")
                    runCatching { filterPagerTabsInfo(result, parentTag) }
                        .onFailure {
                            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] filterPagerTabsInfo: ${it.message}", null)
                        }
                }
                return@hooked result
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PagerTabsInfo.fromTabInfo(${method.name})", null)
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook PagerTabsInfo 失败: ${e.message}", null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun filterPagerTabsInfo(info: Any, parentTag: String?) {
        val tags = info.getFieldValue("tags") as? MutableList<String> ?: return
        if (tags.isEmpty()) return
        val urls = info.getFieldValue("urls") as? MutableList<String>
        val titles = info.getFieldValue("titles") as? MutableList<Map<String, String>>
        val abNormals = info.getFieldValue("abNormals") as? MutableList<Boolean>
        val tabInfos = info.getFieldValue("tabInfos") as? MutableMap<String, Any>

        debugLog("PagerTabsInfo filter: ${tags.size} tabs, tags=${tags}")

        val dropped = mutableListOf<String>()
        val keepIdx = mutableListOf<Int>()
        tags.forEachIndexed { i, tag ->
            if (tag == PURIFY_UPDATE) { keepIdx += i; return@forEachIndexed }
            val titleMap = titles?.getOrNull(i)
            val whitelisted = parentTag == HOME_TAG && homeSubTabWhitelist.contains(tag)
            val promoIcon = abNormals?.getOrNull(i) == true && !whitelisted
            val hit = isBlacklisted(tag, titleMap) || promoIcon ||
                deniedByWhitelist(parentTag, tags, tag)
            if (hit) {
                val reason = when {
                    subBlackTags.contains(tag) -> "blacklisted"
                    promoIcon -> "abNormal promo"
                    else -> "not in whitelist"
                }
                dropped += "$tag(${titleMap?.get("cn") ?: ""})"
                debugLog("PagerTabsInfo drop: $tag reason=$reason")
            } else {
                keepIdx += i
            }
        }
        if (dropped.isEmpty() || keepIdx.isEmpty()) return
        debugLog("PagerTabsInfo: dropped ${dropped.size}, kept ${keepIdx.size}")
        dropped.forEach {
            HookEnv.base.log(Log.INFO, TAG, "[TabFilter] dropped pager tab: $it under $parentTag", null)
        }
        replaceWith(tags, keepIdx)
        urls?.let { replaceWith(it, keepIdx) }
        titles?.let { replaceWith(it, keepIdx) }
        abNormals?.let { replaceWith(it, keepIdx) }
        tabInfos?.keys?.retainAll(tags.toSet())
        val def = runCatching { info.getFieldValue("defaultSelectedTag") as? String }.getOrNull()
        if (def != null && def !in tags) {
            info.setFieldValue("defaultSelectedTag", tags.firstOrNull())
        }
    }

    private fun <T> replaceWith(list: MutableList<T>, keepIdx: List<Int>) {
        val copy = keepIdx.map { list[it] }
        list.clear()
        list.addAll(copy)
    }
}
