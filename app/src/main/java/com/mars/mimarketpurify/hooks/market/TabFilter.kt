package com.mars.mimarketpurify.hooks.market

import android.os.Bundle
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.NativeTabBar
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

    /** 移花接木（底栏更新入口）总开关，与 [Settings.KEY_UPDATE_TAB] / 悬浮底栏配置页开关一致。 */
    private fun updateEntryEnabled(): Boolean = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)

    private var tabField: Field? = null
    private var tabsField: Field? = null // 缓存 PageConfig.tabs 字段引用，避免每次触发都 getDeclaredField
    private var cachedPageConfig: Any? = null
    private var purifyTabInfo: Any? = null

    /** 原生「更新」tab 点击跳转的去抖时间戳（毫秒），避免连续触发。 */
    private var lastUpdateLaunchTs = 0L
    private const val UPDATE_LAUNCH_DEBOUNCE_MS = 800L

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
        runCatching { hookNativeUpdateClick() }
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
                    if (!updateEntryEnabled()) {
                        debugLog("移花接木更新入口已关闭，跳过注入")
                    } else {
                        val tabInfo = ensurePurifyTab()
                        if (tabInfo != null) {
                            tabs.add(tabInfo)
                            debugLog("注入 purify_update! tabs.size=${tabs.size}")
                        }
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
            if (!updateEntryEnabled()) return@hooked result

            val args = Bundle()
            args.putString("url", "market://update")
            args.putString("tab_tag", PURIFY_UPDATE)
            val fragInfo = fragmentInfoCtor.newInstance(dummyFragmentClz, args, false)
            debugLog("getFragmentInfo($index): dummy for purify_update")
            return@hooked fragInfo
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked PageConfig.getFragmentInfo()")
    }

    // = = = = 原生「更新」tab 点击拦截 = = = =

    /**
     * 拦截原生「更新」tab 的点击：直达应用商店更新页。
     *
     * 该 tab 由移花接木注入（tag=purify_update），但商店自身的路由是
     * `market://update`（系统并未注册此 scheme），点击只会走到 dummy 碎片、毫无反应。
     * 这里在 `View.performClick` 层面拦截，无论悬浮底栏是否开启都能跳转
     * （悬浮底栏开启时「更新」入口走其自身的 [com.mars.mimarketpurify.util.ComposeFloatingBarHost]
     * 点击逻辑，走不到这里的原生 tab；本拦截只在悬浮底栏关闭、用户直接点原生「更新」tab 时生效）。
     */
    private fun hookNativeUpdateClick() {
        runCatching {
            val performClick = android.view.View::class.java.getDeclaredMethod("performClick")
            HookEnv.base.hook(performClick).intercept { param ->
                if (!updateEntryEnabled()) return@intercept param.proceed()
                val v = param.thisObject as? android.view.View ?: return@intercept param.proceed()
                val tag = runCatching { NativeTabBar.tagOf(v) }.getOrNull()
                if (tag != PURIFY_UPDATE) return@intercept param.proceed()
                val now = System.currentTimeMillis()
                if (now - lastUpdateLaunchTs < UPDATE_LAUNCH_DEBOUNCE_MS) return@intercept param.proceed()
                lastUpdateLaunchTs = now
                MarketUpdateLauncher.launch(v.context)
                true
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook 原生更新 tab 点击失败: ${it.message}")
        }
    }

    // = = = = PageConfig hook = = = =

    /**
     * 构造「更新」注入项。优先从 [template]（一个真实原生 TabInfo）克隆全部非静态字段，
     * 再覆盖 tag / titles / url，使注入项与原生 tab 结构完全一致，
     * 从而能被原生底栏正常渲染（否则字段不全常被商店跳过，导致关闭悬浮底栏后「更新」入口消失）。
     * [template] 取不到时回落到从已缓存的 PageConfig.tabs 里找一个同类型实例。
     */
    private fun ensurePurifyTab(template: Any? = null): Any? {
        purifyTabInfo?.let { return it }
        val tabInfoClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.TabInfo") }.getOrNull() ?: return null
        val tab = runCatching { tabInfoClz.newInstance() }.getOrNull() ?: return null
        val src = template ?: runCatching {
            (cachedPageConfig?.getFieldValue("tabs") as? List<*>)
                ?.firstOrNull { it != null && it.javaClass == tabInfoClz }
        }.getOrNull()
        src?.let { from ->
            runCatching {
                tabInfoClz.declaredFields.forEach { f ->
                    if (Modifier.isStatic(f.modifiers)) return@forEach
                    f.isAccessible = true
                    runCatching { f.set(tab, f.get(from)) }
                }
            }
        }
        val tagF = runCatching { tabInfoClz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull() }.getOrNull()
        val titlesF = runCatching { tabInfoClz.fieldFinder().filterByName("titles").firstOrNull() }.getOrNull()
        val urlF = runCatching { tabInfoClz.fieldFinder().filterByName("url").firstOrNull() }.getOrNull()
        runCatching { tagF?.set(tab, PURIFY_UPDATE) }
        runCatching { titlesF?.set(tab, mapOf("cn" to "更新", "en" to "Update")) }
        runCatching { urlF?.set(tab, "market://update") }
        purifyTabInfo = tab
        HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] purify TabInfo 已创建（克隆模板=${src != null}）")
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
                if (!updateEntryEnabled()) return@hooked result
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
            if (tag == PURIFY_UPDATE && updateEntryEnabled()) return@hooked getTabsSize()
            return@hooked proceed()
        } }

        runCatching { findMethod(clazz, "isTabValid", 1)?.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            if (getTabsSize() > 0 && index == getTabsSize() && updateEntryEnabled()) return@hooked true
            return@hooked proceed()
        } }

        runCatching { findMethod(clazz, "toValidTabIndex", 1)?.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            if (getTabsSize() > 0 && index == getTabsSize() && updateEntryEnabled()) return@hooked index
            return@hooked proceed()
        } }
    }

    // = = = = TabInfo.fromJSON = = = =

    private fun tagOf(tab: Any?): String? {
        if (tab == null) return null
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
                // 移花接木：把「更新」入口真正写进原生底栏的 tab 列表（与商店其它原生 tab 同源），
                // 商店据此渲染出一个真实的「更新」TabView，底栏因此始终保持「首页 / 更新」两项，
                // 不会在「屏蔽到只剩首页」时被商店当成单 tab 而整体隐藏；也无需依赖悬浮胶囊的合成兜底，
                // 真正融入原底栏架构。原生 TabView 的点击仍走商店自身 tab 路由（market://update）。
                if (updateEntryEnabled()) {
                    val alreadyHas = list.any { runCatching { tagOf(it ?: return@any false) }.getOrNull() == PURIFY_UPDATE }
                    if (!alreadyHas) {
                        val tabInfo = ensurePurifyTab((result as? List<*>)?.firstOrNull())
                        if (tabInfo != null) {
                            list.add(tabInfo)
                            debugLog("fromJSON: 注入 purify_update（移花接木）")
                        }
                    }
                }
                return@hooked list
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[TabFilter] hooked TabInfo.fromJSON")
        } catch (e: Exception) {
            HookEnv.base.log(Log.WARN, TAG, "[TabFilter] hook TabInfo 失败: ${e.message}")
        }
    }
}
