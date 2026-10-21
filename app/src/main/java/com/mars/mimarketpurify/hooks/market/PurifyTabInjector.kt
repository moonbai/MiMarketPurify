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
 * 移花接木注入点 v2：hook `PageConfig.updateTabs(Z)`。
 *
 * 根因（4.128.11 实测反编译确认）：
 * 1. `initTabs(json)` 内 `tabs` 字段经 `updateTabs()` 由策略类重建（从 originTabs 过滤复制）；
 * 2. **`MarketTabActivity.onCreate` 会再次调用 `updateTabs(Z)`**，设置页切换模式也会调用
 *    —— 每次调用都用「策略过滤(originTabs)」的**新 list 整体替换 tabs 字段**；
 * 3. 原有注入点 `initTabs` hook（proceed 后 add）只覆盖第 1 步，onCreate 的重建会把注入冲掉；
 *    `fromJSON` hook 注入 originTabs 依赖 kept 非空且方法匹配，任一条件不满足即失效。
 * 结果：官方底栏渲染读 tabs 时已无 purify_update → 无入口；悬浮底栏不依赖 tabs → 有入口。
 *
 * 修复：updateTabs 是 tabs 字段的唯一写入口，proceed 后幂等补注入，
 * 覆盖 initTabs 内 / onCreate / 设置页全部 3 个调用点，渲染前最后一次写入必经此处。
 * 与 TabFilter 既有注入（fromJSON/initTabs）通过 tag 判重幂等共存，不会重复注入。
 *
 * 开关与「底栏更新入口」共用 [Settings.KEY_UPDATE_TAB]。
 */
object PurifyTabInjector : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "移花接木注入(updateTabs)"

    private const val PURIFY_UPDATE = "purify_update"

    private var tabField: Field? = null
    private var tabsField: Field? = null
    private var purifyTabInfo: Any? = null

    override fun init() {
        val pcClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.PageConfig") }.getOrNull() ?: return
        tabsField = runCatching {
            pcClz.getDeclaredField("tabs").apply { isAccessible = true }
        }.getOrNull()

        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.model.TabInfo").fieldFinder()
                .filterByName("tag").filterByType(String::class.java).firstOrNull()
        }.onSuccess { tabField = it }

        val updateTabs = pcClz.declaredMethods.firstOrNull {
            it.name == "updateTabs" && it.parameterTypes.size == 1
        }
        if (updateTabs == null) {
            HookEnv.base.log(Log.WARN, TAG, "[PurifyTabInjector] updateTabs 未找到，跳过")
            return
        }

        updateTabs.hooked {
            val result = proceed()
            runCatching {
                if (!Settings.isEnabled(Settings.KEY_UPDATE_TAB, true)) return@runCatching
                val tabs = tabsField?.get(thisObject) as? MutableList<Any?> ?: return@runCatching
                val hasPurify = tabs.any { tagOf(it) == PURIFY_UPDATE }
                if (hasPurify) return@runCatching
                val purify = ensurePurifyTab(tabs) ?: return@runCatching
                tabs.add(purify)
                HookEnv.base.log(Log.DEBUG, TAG, "[PurifyTabInjector] 注入 purify_update, size=${tabs.size}")
            }.onFailure {
                HookEnv.base.log(Log.WARN, TAG, "[PurifyTabInjector] 注入失败: ${it.message}")
            }
            return@hooked result
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[PurifyTabInjector] hooked PageConfig.updateTabs(Z)")
    }

    private fun tagOf(tab: Any?): String? {
        if (tab == null) return null
        tabField?.let { f -> runCatching { return f.get(tab) as? String } }
        return runCatching {
            tab.javaClass.getMethod("getTag").invoke(tab) as? String
        }.getOrNull()
    }

    /**
     * 克隆真实原生 TabInfo 构造「更新」注入项（与 TabFilter.ensurePurifyTab 同源逻辑）。
     * 模板取不到时返回 null 不缓存 —— 空对象字段不全会被商店跳过，宁可下次 updateTabs 重试。
     */
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
        HookEnv.base.log(Log.DEBUG, TAG, "[PurifyTabInjector] purify TabInfo 已创建")
        return tab
    }
}
