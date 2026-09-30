package com.mars.mimarketpurify.hooks.market

import android.os.Bundle
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
    override val name: String get() = "筛选底部TAB标签"

    private const val PURIFY_UPDATE = "purify_update"

    private var tabField: Field? = null
    private var tabsField: Field? = null // 缓存 PageConfig.tabs 字段引用，避免每次触发都 getDeclaredField
    private var cachedPageConfig: Any? = null
    private var purifyTabInfo: Any? = null

    private fun findMethod(clazz: Class<*>, name: String, paramCount: Int): Method? {
        return clazz.declaredMethods.firstOrNull { m ->
            m.name == name && m.parameterTypes.size == paramCount
        }
    }

    override fun init() {
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] init() 开始")
        hookTabInfoParse()
        runCatching { hookPageConfig() }
        runCatching { hookInitTabs() }
        runCatching { hookGetFragmentInfo() }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] init() 完成")
    }

    private fun tabsFieldOf(clazz: Class<*>): Field? {
        tabsField?.let { return it }
        synchronized(this) {
            tabsField?.let { return it }
            tabsField = runCatching {
                clazz.getDeclaredField("tabs").apply { isAccessible = true }
            }.getOrNull()
            return tabsField
        }
    }

    // = = = = hook initTabs = = = =

    private fun hookInitTabs() {
        val pageConfigClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.PageConfig") }.getOrNull() ?: return
        val initTabsMethod = findMethod(pageConfigClz, "initTabs", 1)
        if (initTabsMethod == null) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] initTabs 方法未找到")
            return
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] initTabs 方法: ${initTabsMethod.name} private=${Modifier.isPrivate(initTabsMethod.modifiers)}")

        initTabsMethod.hooked {
            debugLog("initTabs lambda 被触发, thisObject=${thisObject?.javaClass?.simpleName}")
            val result = proceed()
            val tabsField = tabsFieldOf(pageConfigClz)
            if (tabsField != null) {
                val tabs = tabsField.get(thisObject) as? MutableList<Any?> ?: return@hooked result
                debugLog("tabs.size=${tabs.size}")
                val alreadyHas = tabs.any { item ->
                    item != null && runCatching { tabField?.get(item) as? String }.getOrNull() == PURIFY_UPDATE
                }
                debugLog("alreadyHas=$alreadyHas")
                if (!alreadyHas) {
                    val tabInfo = ensurePurifyTab()
                    if (tabInfo != null) {
                        tabs.add(tabInfo)
                        debugLog("注入 purify_update! tabs.size=${tabs.size}")
                    }
                }
            }
            return@hooked result
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig.initTabs()")
    }

    // = = = = hook getFragmentInfo = = = =

    private fun hookGetFragmentInfo() {
        val pageConfigClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.PageConfig") }.getOrNull() ?: return
        val method = pageConfigClz.declaredMethods.firstOrNull {
            it.name == "getFragmentInfo" && it.parameterTypes.size == 4
        } ?: return

        val fragmentInfoClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.ui.ITabActivity\$FragmentInfo") }.getOrNull()
        val fragmentInfoCtor = fragmentInfoClz?.declaredConstructors?.firstOrNull {
            it.parameterTypes.size == 3 && it.parameterTypes[0] == Class::class.java
        }
        val dummyFragmentClz = runCatching { Class.forName("android.app.Fragment") }.getOrNull()
            ?: runCatching { Class.forName("androidx.fragment.app.Fragment") }.getOrNull()

        if (fragmentInfoCtor == null || dummyFragmentClz == null) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] 无法构造 FragmentInfo")
            return
        }

        method.hooked {
            val result = proceed()
            val index = args[0] as? Int ?: return@hooked result
            if (result != null) return@hooked result

            val pc = cachedPageConfig ?: return@hooked result
            val tabs = runCatching { pc.getFieldValue("tabs") as? List<*> }.getOrNull() ?: return@hooked result
            if (index < 0 || index >= tabs.size) return@hooked result

            val tab = tabs[index] ?: return@hooked result
            val tag = runCatching { tabField?.get(tab) as? String ?: tab.invokeAs<String>("getTag") }.getOrNull()
            if (tag != PURIFY_UPDATE) return@hooked result

            val args = Bundle()
            args.putString("url", "market://update")
            args.putString("tab_tag", PURIFY_UPDATE)
            val fragInfo = fragmentInfoCtor.newInstance(dummyFragmentClz, args, false)
            debugLog("getFragmentInfo($index): dummy for purify_update")
            return@hooked fragInfo
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig.getFragmentInfo()")
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
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] purify TabInfo 已创建")
        return tab
    }

    private fun getTabsSize(): Int {
        val pc = cachedPageConfig ?: return -1
        val tabs = runCatching { pc.getFieldValue("tabs") as? List<*> }.getOrNull() ?: return -1
        return tabs.size
    }

    private fun hookPageConfig() {
        val clazz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.PageConfig") }.getOrNull() ?: return
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] PageConfig 已加载")

        findMethod(clazz, "get", 0)?.let { m ->
            if (Modifier.isStatic(m.modifiers)) {
                m.hooked {
                    val result = proceed()
                    if (result != null && cachedPageConfig == null) {
                        cachedPageConfig = result
                        debugLog("PageConfig 单例已缓存")
                    }
                    return@hooked result
                }
                HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig.get()")
            }
        }

        findMethod(clazz, "getTabInfo", 1)?.let { m ->
            m.hooked {
                val result = proceed()
                val index = args[0] as? Int ?: return@hooked result
                val resultTag = if (result != null) runCatching {
                    tabField?.get(result) as? String ?: result.invokeAs<String>("getTag")
                }.getOrNull() else null
                if (resultTag == PURIFY_UPDATE) return@hooked result
                val tabsSize = getTabsSize()
                if (tabsSize > 0 && index == tabsSize) {
                    debugLog("getTabInfo($index): purify_update (tabs.size=$tabsSize)")
                    return@hooked ensurePurifyTab()
                }
                return@hooked result
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig.getTabInfo()")
        }

        runCatching { findMethod(clazz, "getTabIndexFromTag", 1)?.hooked {
            val tag = args[0] as? String
            if (tag == PURIFY_UPDATE) return@hooked getTabsSize()
            return@hooked proceed()
        } }

        runCatching { findMethod(clazz, "isTabValid", 1)?.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            if (getTabsSize() > 0 && index == getTabsSize()) return@hooked true
            return@hooked proceed()
        } }

        runCatching { findMethod(clazz, "toValidTabIndex", 1)?.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            if (getTabsSize() > 0 && index == getTabsSize()) return@hooked index
            return@hooked proceed()
        } }
    }

    // = = = = TabInfo.fromJSON = = = =

    private fun tagOf(tab: Any): String? {
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching { tab.invokeAs<String>("getTag") }.getOrNull()
    }

    private fun hookTabInfoParse() {
        try {
            val clazz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
            tabField = runCatching {
                clazz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull()
            }.getOrNull()

            clazz.methodFinder().filterByName("fromJSON").filterByParamCount(1).first().hooked {
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
                return@hooked list
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked TabInfo.fromJSON")
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook TabInfo 失败: ${e.message}")
        }
    }
}
