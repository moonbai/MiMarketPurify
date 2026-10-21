package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * 「更新」注入兜底 v2 —— 三个注入点 + 全路径日志定位。
 *
 * 2026-10-21 日志分析结论：
 * - TabFilter.initTabs hook 触发过（purify TabInfo 已创建日志出现）→ 注入发生过又被重建冲掉；
 * - TabInfo.fromJSON hook 装了但运行时从未触发（无 "fromJSON: kept=" 日志）
 *   → 4.128.11 initTabs 走 Robust 热修 patch，原方法体的 fromJSON 调用被绕过；
 * - v1 的 updateTabs(Z) hook 无任何运行时日志 → updateTabs 未被调用或走进静默分支，无法区分。
 *
 * v2 策略：
 * 1. updateTabs(Z)：hook 入口第一行无条件打日志 → 定位它是否被调用；
 * 2. initTabs(JSONObject)：proceed 后注入（与 TabFilter 幂等共存）；
 * 3. **getTabCount()（DoubleTabProxyActivityWrapper）**：渲染循环 addFragmentTabsMiux4(count)
 *    的 count 来源 = tabs.size，是渲染**必经之路**，与解析/热修/重建路径完全无关。
 *    在此确保 tabs 含 purify 并返回正确 count —— 只要官方底栏要渲染就必然注入成功。
 * 每个分支（enabled/tabs null/hasPurify/注入结果）都打日志，一次构建即可定位。
 *
 * 与 TabFilter 既有注入按 tag 判重幂等共存。开关共用 [Settings.KEY_UPDATE_TAB]。
 */
object PurifyTabInjector : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "移花接木注入v2"

    private const val PURIFY_UPDATE = "purify_update"

    private var tabField: Field? = null
    private var tabsField: Field? = null
    private var purifyTabInfo: Any? = null

    override fun init() {
        val pcClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.PageConfig") }.getOrNull() ?: return
        tabsField = runCatching {
            pcClz.getDeclaredField("tabs").apply { isAccessible = true }
        }.getOrNull()
        debugLog("tabsField=${tabsField?.name ?: "NULL"}")

        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.model.TabInfo").fieldFinder()
                .filterByName("tag").filterByType(String::class.java).firstOrNull()
        }.onSuccess { tabField = it }

        // ① updateTabs(Z)：入口无条件打日志，定位是否被调用
        pcClz.declaredMethods.firstOrNull {
            it.name == "updateTabs" && it.parameterTypes.size == 1
        }?.let { m ->
            m.hooked {
                debugLog("→ updateTabs(${args[0]}) 被调用")
                val result = proceed()
                injectIfNeeded(thisObject, "updateTabs")
                return@hooked result
            }
            debugLog("hooked PageConfig.updateTabs(Z)")
        } ?: debugLog("updateTabs(Z) 未找到！")

        // ② initTabs(JSONObject)：proceed 后注入
        pcClz.declaredMethods.firstOrNull {
            it.name == "initTabs" && it.parameterTypes.size == 1
        }?.let { m ->
            m.hooked {
                val result = proceed()
                injectIfNeeded(thisObject, "initTabs")
                return@hooked result
            }
            debugLog("hooked PageConfig.initTabs(JSONObject)")
        } ?: debugLog("initTabs(JSONObject) 未找到！")

        // ③ 渲染必经：wrapper.getTabCount() → tabs.size
        val wrapperClz = runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.DoubleTabProxyActivityWrapper")
        }.getOrNull()
        val getCount = wrapperClz?.declaredMethods?.firstOrNull {
            it.name == "getTabCount" && it.parameterTypes.isEmpty()
        }
        if (getCount == null) {
            debugLog("wrapper.getTabCount 未找到（旧版？）")
        } else {
            getCount.hooked {
                val size = proceed() as? Int ?: return@hooked proceed()
                val pc = runCatching { pcClz.getMethod("get").invoke(null) }.getOrNull()
                val enabled = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)
                if (pc == null || !enabled) {
                    debugLog("getTabCount size=$size (pc=$pc, enabled=$enabled)")
                    return@hooked size
                }
                val tabs = tabsOf(pc)
                if (tabs == null) {
                    debugLog("getTabCount size=$size tabs=NULL")
                    return@hooked size
                }
                val hasPurify = tabs.any { tagOf(it) == PURIFY_UPDATE }
                if (hasPurify) {
                    debugLog("getTabCount size=$size 已含 purify ✓")
                    return@hooked size
                }
                val purify = ensurePurifyTab(tabs)
                if (purify != null) {
                    tabs.add(purify)
                    debugLog("getTabCount 注入成功! size=$size → ${size + 1} (list=${tabs.size})")
                    return@hooked size + 1
                }
                debugLog("getTabCount 注入失败(模板取不到) size=$size")
                return@hooked size
            }
            debugLog("hooked DoubleTabProxyActivityWrapper.getTabCount()")
        }
    }

    private fun tabsOf(pc: Any): MutableList<Any?>? =
        runCatching { tabsField?.get(pc) as? MutableList<Any?> }.getOrNull()

    private fun injectIfNeeded(pc: Any?, source: String) {
        runCatching {
            val enabled = Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)
            if (!enabled) { debugLog("[$source] enabled=false 跳过"); return }
            if (pc == null) { debugLog("[$source] thisObject=null 跳过"); return }
            val tabs = tabsOf(pc)
            if (tabs == null) { debugLog("[$source] tabs=NULL 跳过"); return }
            val hasPurify = tabs.any { tagOf(it) == PURIFY_UPDATE }
            if (hasPurify) { debugLog("[$source] 已含 purify 跳过(size=${tabs.size})"); return }
            val purify = ensurePurifyTab(tabs)
            if (purify == null) { debugLog("[$source] 模板取不到,注入失败"); return }
            tabs.add(purify)
            debugLog("[$source] 注入成功! size=${tabs.size}")
        }.onFailure { debugLog("[$source] 注入异常: ${it.message}") }
    }

    private fun tagOf(tab: Any?): String? {
        if (tab == null) return null
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching {
            tab.javaClass.getMethod("getTag").invoke(tab) as? String
        }.getOrNull()
    }

    /** 克隆真实原生 TabInfo；模板取不到返回 null 不缓存（空对象字段不全会被商店跳过）。 */
    private fun ensurePurifyTab(tabs: List<*>): Any? {
        purifyTabInfo?.let { return it }
        val tabInfoClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.TabInfo") }.getOrNull() ?: return null
        val src = tabs.firstOrNull { it != null && it.javaClass == tabInfoClz } ?: return null
        val tab = runCatching { tabInfoClz.newInstance() }.getOrNull() ?: return null
        runCatching {
            tabInfoClz.declaredFields.forEach { f ->
                if (Modifier.isStatic(f.modifiers)) return@forEach
                f.isAccessible = true
                runCatching { f.set(tab, f.get(src)) }
            }
        }
        runCatching {
            tabInfoClz.fieldFinder().filterByName("tag").filterByType(String::class.java).firstOrNull()
                ?.set(tab, PURIFY_UPDATE)
        }
        runCatching {
            tabInfoClz.fieldFinder().filterByName("titles").firstOrNull()
                ?.set(tab, mapOf("cn" to "更新", "en" to "Update"))
        }
        runCatching {
            tabInfoClz.fieldFinder().filterByName("url").firstOrNull()
                ?.set(tab, "market://update")
        }
        purifyTabInfo = tab
        debugLog("purify TabInfo 已创建(克隆模板=true)")
        return tab
    }
}
