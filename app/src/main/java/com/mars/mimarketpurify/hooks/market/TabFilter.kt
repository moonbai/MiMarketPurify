package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import com.mars.mimarketpurify.util.setFieldValue
import io.github.kyuubiran.ezxhelper.EzXHelper.appContext
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field
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

    override fun init() {
        hookTabInfoParse()
        runCatching { hookPagerTabsInfo() }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] PagerTabsInfo 跳过: ${it.message}", null)
        }
        // 新增：探测底栏渲染路径
        runCatching { probeBottomBar() }
    }

    // = = = = 探测底栏渲染 = = = =

    private fun probeBottomBar() {
        // 尝试 hook ViewPager2 RecyclerView.Adapter 的 getItemCount
        runCatching {
            val vp2Class = Class.forName("androidx.viewpager2.widget.ViewPager2")
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] ViewPager2 class found, probing adapters...")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] ViewPager2 not found: ${it.message}")
        }

        // 尝试找 BottomNavigationView
        runCatching {
            val bnClass = Class.forName("com.google.android.material.bottomnavigation.BottomNavigationView")
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] BottomNavigationView class found")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] BottomNavigationView not found: ${it.message}")
        }

        // 扫描市场 App 的 adapter 类
        runCatching {
            val marketPkg = "com.xiaomi.market"
            val classLoader = appContext.classLoader
            val adapterCandidates = listOf(
                "com.xiaomi.market.ui.adapter.MainTabAdapter",
                "com.xiaomi.market.ui.adapter.TabAdapter",
                "com.xiaomi.market.ui.adapter.BottomTabAdapter",
                "com.xiaomi.market.ui.MainTabFragmentPagerAdapter",
                "com.xiaomi.market.ui.MainPagerAdapter",
                "com.xiaomi.market.ui.adapter.HomePagerAdapter",
                "com.xiaomi.market.adapter.MainPagerAdapter",
                "com.xiaomi.market.adapter.TabFragmentPagerAdapter"
            )
            adapterCandidates.forEach { name ->
                runCatching {
                    val clz = Class.forName(name, false, classLoader)
                    HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] FOUND adapter: $name")
                    // 打印方法列表
                    clz.declaredMethods.forEach { m ->
                        if (m.name.contains("count", true) || m.name.contains("item", true) ||
                            m.name.contains("tab", true) || m.name.contains("page", true)) {
                            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   method: ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                        }
                    }
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] adapter scan failed: ${it.message}")
        }

        // 扫描包含 "tab" 关键词的市场包内类
        runCatching {
            val classLoader = appContext.classLoader
            val pkg = "com.xiaomi.market"
            // 尝试常见的 tab 相关类名
            val tabClasses = listOf(
                "com.xiaomi.market.ui.MainTabActivity",
                "com.xiaomi.market.ui.MainActivity",
                "com.xiaomi.market.ui.HomeActivity",
                "com.xiaomi.market.activity.MainActivity",
                "com.xiaomi.market.MainTabActivity",
                "com.xiaomi.market.MainActivity"
            )
            tabClasses.forEach { name ->
                runCatching {
                    val clz = Class.forName(name, false, classLoader)
                    HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] FOUND activity: $name")
                    // 找 ViewPager/Adapter 相关字段
                    clz.declaredFields.forEach { f ->
                        val type = f.type.simpleName
                        if (type.contains("Pager") || type.contains("Adapter") || type.contains("Tab") ||
                            type.contains("Navigation") || type.contains("Bottom")) {
                            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   field: ${f.name}: ${f.type.name}")
                        }
                    }
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] activity scan failed: ${it.message}")
        }

        // hook 所有 Activity 的 onCreate，打印 View 层级中的 tab 相关组件
        runCatching {
            val activityClass = Class.forName("android.app.Activity")
            activityClass.methodFinder()
                .filterByName("onCreate")
                .first()
                .hooked {
                    val act = instance ?: return@hooked proceed()
                    val actName = act.javaClass.name
                    if (!actName.contains("market", true)) return@hooked proceed()

                    HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] Activity.onCreate: $actName")

                    // 递归搜索 View 层级中的 ViewPager / BottomNavigationView
                    runCatching {
                        val decorView = act.window?.decorView ?: return@runCatching
                        searchViews(decorView, 0)
                    }

                    return@hooked proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] activity hook failed: ${it.message}")
        }
    }

    private fun searchViews(view: android.view.View, depth: Int) {
        if (depth > 8) return
        val name = view.javaClass.name
        if (name.contains("TabLayout") || name.contains("BottomNav") ||
            name.contains("ViewPager") || name.contains("RecyclerView")) {
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] View[$depth]: $name id=${getResourceId(view)}")
            // 尝试获取 adapter
            runCatching {
                val adapterField = view.javaClass.getField("mAdapter")
                    .orDeclaredField(view.javaClass, "mRecycler")
                    ?.let { null } // RecyclerView
                // 尝试 ViewPager2
                if (name.contains("ViewPager2")) {
                    val getter = view.javaClass.getMethod("getAdapter")
                    val adapter = getter.invoke(view)
                    if (adapter != null) {
                        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   ViewPager2 adapter: ${adapter.javaClass.name}")
                        // 打印 adapter 的 count 方法
                        runCatching {
                            val countMethod = adapter.javaClass.getMethod("getItemCount")
                            val count = countMethod.invoke(adapter)
                            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   adapter.itemCount = $count")
                        }
                    }
                }
                // 尝试 ViewPager (v1)
                if (name.endsWith("ViewPager") && !name.contains("2")) {
                    val getter = view.javaClass.getMethod("getAdapter")
                    val adapter = getter.invoke(view)
                    if (adapter != null) {
                        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   ViewPager adapter: ${adapter.javaClass.name}")
                        runCatching {
                            val countMethod = adapter.javaClass.getMethod("getCount")
                            val count = countMethod.invoke(adapter)
                            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   adapter.count = $count")
                        }
                    }
                }
                // 尝试 BottomNavigationView
                if (name.contains("BottomNav")) {
                    val getter = view.javaClass.getMethod("getMenu")
                    val menu = getter.invoke(view)
                    HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   BottomNav menu: ${menu?.javaClass?.name}")
                    runCatching {
                        val sizeMethod = menu!!.javaClass.getMethod("size")
                        val size = sizeMethod.invoke(menu)
                        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   menu.size = $size")
                    }
                }
            }.onFailure {
                HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter]   adapter introspect failed: ${it.message}")
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                runCatching { searchViews(view.getChildAt(i), depth + 1) }
            }
        }
    }

    private fun getResourceId(view: android.view.View): Int {
        return runCatching {
            val method = android.view.View::class.java.getMethod("getId")
            method.invoke(view) as? Int ?: -1
        }.getOrNull() ?: -1
    }

    private fun Class<*>.orDeclaredField(parent: Class<*>, name: String): Field? {
        return runCatching { parent.getDeclaredField(name) }.getOrNull()
    }

    // = = = = TabInfo.fromJSON (保持不变) = = = =

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

            InjectFields.ensureInit(clazz)

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

                    list.forEachIndexed { i, item ->
                        val tag = if (item != null) runCatching { tagOf(item) }.getOrNull() else null
                        debugLog("fromJSON: tab[$i] tag='${tag}' class=${item?.javaClass?.simpleName}")
                    }

                    list.removeAll { item ->
                        if (item == null) return@removeAll true
                        val tag = runCatching { tagOf(item) }.getOrNull()
                        if (tag == PURIFY_UPDATE) return@removeAll false
                        val removed = tag == null || tag !in kept
                        if (removed) debugLog("fromJSON: removed ${tag ?: "(null)"}")
                        removed
                    }

                    val afterCount = list.size
                    if (beforeCount != afterCount) debugLog("fromJSON: ${beforeCount} → ${afterCount} tabs")

                    list.forEach { runCatching { sanitizeSubTabs(it, 0) } }
                    InjectFields.injectPurifyUpdate(list)

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
            if (hit) {
                HookEnv.base.log(Log.INFO, TAG,
                    "[TabFilter] removed subTab: ${subTags[i]}(${titles?.get("cn")}) under $parentTag", null)
            }
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
                    val parentTag = runCatching { args[0]?.let { tagOf(it) } }.getOrNull()
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
            if (hit) {
                dropped += "$tag(${titleMap?.get("cn") ?: ""})"
            } else {
                keepIdx += i
            }
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

private object InjectFields {
    private var initialized = false
    private var tagField: Field? = null
    private var titlesField: Field? = null
    private var urlField: Field? = null
    private var tabInfoClz: Class<*>? = null

    fun ensureInit(clazz: Class<*>) {
        if (initialized) return
        tabInfoClz = clazz
        tagField = runCatching { clazz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull() }.getOrNull()
        titlesField = runCatching { clazz.fieldFinder().filterByName("titles").firstOrNull() }.getOrNull()
        urlField = runCatching { clazz.fieldFinder().filterByName("url").firstOrNull() }.getOrNull()
        initialized = true
        HookEnv.base.log(Log.DEBUG, TAG,
            "[TabFilter] InjectFields: tag=${tagField != null}, titles=${titlesField != null}, url=${urlField != null}", null)
    }

    fun injectPurifyUpdate(list: MutableList<Any?>) {
        if (!initialized || tabInfoClz == null) return
        val alreadyHas = list.any { item ->
            item != null && runCatching { tagField?.get(item) as? String }.getOrNull() == "purify_update"
        }
        if (alreadyHas) return
        val tab = runCatching { tabInfoClz!!.newInstance() }.getOrNull() ?: return
        runCatching { tagField?.set(tab, "purify_update") }
        runCatching { titlesField?.set(tab, mapOf("cn" to "更新", "en" to "Update")) }
        runCatching { urlField?.set(tab, "market://update") }
        list.add(tab)
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] 注入 purify_update, total=${list.size}", null)
    }
}
