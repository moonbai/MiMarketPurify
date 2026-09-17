package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object TabFilter : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_FILTER
    override val name: String get() = "筛选底部TAB标签与云控推广位"

    private const val HOME_TAG = "native_market_home"
    private const val PURIFY_UPDATE = "purify_update"

    private val homeSubTabWhitelist by lazy {
        setOf("native_market_feature", "native_market_rank_software",
            "must-have", "GOLDEN MI AWARD", "Classification",
            "software_sub5", "minor")
    }
    private val subBlackTags by lazy {
        setOf("xiaomishipin", "native_market_shortplay", "native_market_agent")
    }
    private val subBlackTitles by lazy { setOf("看剧", "短剧") }

    private var tabField: Field? = null

    private var cachedPageConfig: Any? = null
    private var purifyTabInfo: Any? = null

    /** Java 反射查找方法，避免 ezxHelper Finder 的 lambda 类型推断问题 */
    private fun findMethod(clazz: Class<*>, name: String, paramCount: Int,
                           predicate: ((Method) -> Boolean)? = null): Method? {
        return clazz.declaredMethods.firstOrNull {
            it.name == name && it.parameterTypes.size == paramCount &&
                (predicate == null || predicate(it))
        }
    }

    override fun init() {
        hookTabInfoParse()
        runCatching { hookPagerTabsInfo() }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] PagerTabsInfo 跳过: ${it.message}", null)
        }
        runCatching { hookPageConfig() }
    }

    // = = = = PageConfig hook = = = =

    private fun ensurePurifyTab(): Any? {
        purifyTabInfo?.let { return it }
        val tabInfoClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.TabInfo") }.getOrNull() ?: return null
        val tagF = runCatching { tabInfoClz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull() }.getOrNull()
        val titlesF = runCatching { tabInfoClz.fieldFinder().filterByName("titles").firstOrNull() }.getOrNull()
        val urlF = runCatching { tabInfoClz.fieldFinder().filterByName("url").firstOrNull() }.getOrNull()
        val tab = runCatching { tabInfoClz.newInstance() }.getOrNull() ?: return null
        runCatching { tagF?.set(tab, PURIFY_UPDATE) }
        runCatching { titlesF?.set(tab, mapOf("cn" to "更新", "en" to "Update")) }
        runCatching { urlF?.set(tab, "market://update") }
        purifyTabInfo = tab
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] purify TabInfo created")
        return tab
    }

    private fun getTabsSize(): Int {
        val pc = cachedPageConfig ?: return -1
        val tabs = runCatching { pc.getFieldValue("tabs") as? List<*> }.getOrNull() ?: return -1
        return tabs.size
    }

    private fun hookPageConfig() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.model.PageConfig")

            // 1. hook PageConfig.get() 存单例
            findMethod(clazz, "get", 0) { Modifier.isStatic(it.modifiers) }?.hooked {
                val result = proceed()
                if (result != null && cachedPageConfig == null) {
                    cachedPageConfig = result
                    HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] PageConfig 单例已缓存")
                    ensurePurifyTab()
                }
                return@hooked result
            }

            // 2. hook getTabInfo(int) → index == tabs.size 时返回 purify_update
            findMethod(clazz, "getTabInfo", 1) {
                it.parameterTypes[0] == Int::class.javaPrimitiveType ||
                    it.parameterTypes[0] == Int::class.java
            }?.hooked {
                val result = proceed()
                val index = args[0] as? Int ?: return@hooked result
                val resultTag = if (result != null) runCatching {
                    tabField?.get(result) as? String ?: result.invokeAs<String>("getTag")
                }.getOrNull() else null
                if (resultTag == PURIFY_UPDATE) return@hooked result
                val tabsSize = getTabsSize()
                if (tabsSize > 0 && index == tabsSize) {
                    debugLog("getTabInfo($index): 返回 purify_update (tabs.size=$tabsSize)")
                    return@hooked ensurePurifyTab()
                }
                return@hooked result
            }

            // 3. hook getTabIndexFromTag(String) → purify_update 返回 tabs.size
            runCatching {
                findMethod(clazz, "getTabIndexFromTag", 1)?.hooked {
                    val tag = args[0] as? String
                    if (tag == PURIFY_UPDATE) {
                        val size = getTabsSize()
                        debugLog("getTabIndexFromTag(purify_update): 返回 $size")
                        return@hooked size
                    }
                    return@hooked proceed()
                }
            }

            // 4. hook isTabValid(int) → purify_update 的 index 返回 true
            runCatching {
                findMethod(clazz, "isTabValid", 1) {
                    it.parameterTypes[0] == Int::class.javaPrimitiveType ||
                        it.parameterTypes[0] == Int::class.java
                }?.hooked {
                    val index = args[0] as? Int ?: return@hooked proceed()
                    val tabsSize = getTabsSize()
                    if (tabsSize > 0 && index == tabsSize) {
                        debugLog("isTabValid($index): purify_update → true")
                        return@hooked true
                    }
                    return@hooked proceed()
                }
            }

            // 5. hook toValidTabIndex(int) → purify_update 的 index 不被 clamp
            runCatching {
                findMethod(clazz, "toValidTabIndex", 1) {
                    it.parameterTypes[0] == Int::class.javaPrimitiveType ||
                        it.parameterTypes[0] == Int::class.java
                }?.hooked {
                    val index = args[0] as? Int ?: return@hooked proceed()
                    val tabsSize = getTabsSize()
                    if (tabsSize > 0 && index == tabsSize) {
                        debugLog("toValidTabIndex($index): purify_update → $index")
                        return@hooked index
                    }
                    return@hooked proceed()
                }
            }

            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig")
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook PageConfig 失败: ${e.message}", null)
        }
    }

    // = = = = TabInfo.fromJSON = = = =

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
                    if (kept.isEmpty()) return@hooked proceed()

                    val result = proceed()
                    val list = (result as List<*>).toMutableList()
                    val beforeCount = list.size

                    list.removeAll { item ->
                        if (item == null) return@removeAll true
                        val tag = runCatching { tagOf(item) }.getOrNull()
                        if (tag == PURIFY_UPDATE) return@removeAll false
                        val removed = tag == null || tag !in kept
                        if (removed) debugLog("fromJSON: removed ${tag ?: "(null)"}")
                        removed
                    }

                    if (beforeCount != list.size) debugLog("fromJSON: ${beforeCount} → ${list.size} tabs")

                    list.forEach { runCatching { sanitizeSubTabs(it, 0) } }

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
            val hit = isBlacklisted(subTags[i], titles) || deniedByWhitelist(parentTag, subTags, subTags[i])
            if (hit) HookEnv.base.log(Log.INFO, TAG,
                "[TabFilter] removed subTab: ${subTags[i]}(${titles?.get("cn")}) under $parentTag", null)
            hit
        }
        if (removed.isNotEmpty()) subs.removeAll(removed.toSet())
        subs.forEach { runCatching { sanitizeSubTabs(it, depth + 1) } }
    }

    // = = = = PagerTabsInfo = = = =

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
            if (method == null) return
            method.hooked {
                val result = proceed()
                if (result != null) {
                    val parentTag = runCatching { args[0]?.let { a -> tagOf(a) } }.getOrNull()
                    debugLog("PagerTabsInfo: parentTag=$parentTag")
                    runCatching { filterPagerTabsInfo(result, parentTag) }
                }
                return@hooked result
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PagerTabsInfo", null)
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

        val dropped = mutableListOf<String>()
        val keepIdx = mutableListOf<Int>()
        tags.forEachIndexed { i, tag ->
            if (tag == PURIFY_UPDATE) { keepIdx += i; return@forEachIndexed }
            val titleMap = titles?.getOrNull(i)
            val whitelisted = parentTag == HOME_TAG && homeSubTabWhitelist.contains(tag)
            val promoIcon = abNormals?.getOrNull(i) == true && !whitelisted
            val hit = isBlacklisted(tag, titleMap) || promoIcon || deniedByWhitelist(parentTag, tags, tag)
            if (hit) dropped += "$tag(${titleMap?.get("cn") ?: ""})"
            else keepIdx += i
        }
        if (dropped.isEmpty() || keepIdx.isEmpty()) return
        dropped.forEach {
            HookEnv.base.log(Log.INFO, TAG, "[TabFilter] dropped pager tab: $it under $parentTag", null)
        }
        replaceWith(tags, keepIdx)
        urls?.let { replaceWith(it, keepIdx) }
        titles?.let { replaceWith(it, keepIdx) }
        abNormals?.let { replaceWith(it, keepIdx) }
        tabInfos?.keys?.retainAll(tags.toSet())
    }

    private fun <T> replaceWith(list: MutableList<T>, keepIdx: List<Int>) {
        val copy = keepIdx.map { list[it] }
        list.clear()
        list.addAll(copy)
    }
}
