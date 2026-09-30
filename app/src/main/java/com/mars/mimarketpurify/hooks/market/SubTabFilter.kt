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
import java.lang.reflect.Modifier

object SubTabFilter : BaseHook() {

    override val prefKey: String = Settings.KEY_SUB_TAB_FILTER
    override val name: String get() = "筛选顶栏推广位与子标签"

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

    override fun init() {
        runCatching { hookPagerTabsInfo() }
    }

    private fun hookPagerTabsInfo() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.ui.PagerTabsInfo")
            val method = clazz.methodFinder().filterByName("fromTabInfo").firstOrNull()
                ?: clazz.methodFinder().firstOrNull {
                    Modifier.isStatic(modifiers) && parameterTypes.size == 1 &&
                        parameterTypes[0].name.endsWith("TabInfo") && returnType.name.endsWith("PagerTabsInfo")
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
            HookEnv.base.log(Log.DEBUG, TAG, "[SubTabFilter] hooked PagerTabsInfo")
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[SubTabFilter] hook 失败: ${e.message}")
        }
    }

    private fun tagOf(tab: Any): String? {
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching { tab.invokeAs<String>("getTag") }.getOrNull()
    }

    private fun isBlacklisted(tag: String?, titles: Map<String, String>?): Boolean =
        (tag != null && subBlackTags.contains(tag)) ||
            titles?.values?.any { subBlackTitles.contains(it) } == true

    private fun deniedByWhitelist(parentTag: String?, siblings: List<String?>, tag: String?): Boolean {
        if (parentTag != HOME_TAG) return false
        val looksManaged = siblings.any { it != null && homeSubTabWhitelist.contains(it) }
        val whitelisted = tag != null && homeSubTabWhitelist.contains(tag)
        return looksManaged && !whitelisted
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
            if (hit) dropped += "$tag(${titleMap?.get("cn") ?: ""})" else keepIdx += i
        }
        if (dropped.isEmpty() || keepIdx.isEmpty()) return
        debugLog("PagerTabsInfo: dropped ${dropped.size}, kept ${keepIdx.size}")
        replaceWith(tags, keepIdx)
        urls?.let { replaceWith(it, keepIdx) }
        titles?.let { replaceWith(it, keepIdx) }
        abNormals?.let { replaceWith(it, keepIdx) }
        tabInfos?.keys?.retainAll(tags.toSet())
    }

    private fun <T> replaceWith(list: MutableList<T>, keepIdx: List<Int>) {
        val copy = keepIdx.map { list[it] }; list.clear(); list.addAll(copy)
    }
}
