package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.finder.FieldFinder.`-Static`.fieldFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object UpdateTabEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "底栏更新入口"

    private const val PURIFY_UPDATE = "purify_update"

    override fun init() {
        runCatching {
            val tabInfoClz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
            debugLog("init: TabInfo loaded")

            val tagField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("tag")
                    .filterByType(String::class.java)
                    .firstOrNull()
            }.getOrNull()
            debugLog("init: tagField=${if (tagField != null) "found" else "null"}")

            val urlField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("url")
                    .firstOrNull()
            }.getOrNull()
            debugLog("init: urlField=${if (urlField != null) "found" else "null"}")

            val titlesField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("titles")
                    .firstOrNull()
            }.getOrNull()

            // 安装 fromJSON hook
            val hookedMethod = tabInfoClz.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .firstOrNull()

            if (hookedMethod == null) {
                debugLog("init: fromJSON 未找到！")
                return@runCatching
            }

            debugLog("init: fromJSON method found: ${hookedMethod.name}(${hookedMethod.parameterTypes.first().simpleName})")

            hookedMethod.hooked {
                debugLog("fromJSON: >>> 回调触发")
                val raw = proceed()
                if (!enabled()) return@hooked raw

                val origList = raw as? List<*>
                    ?: run { debugLog("fromJSON: proceed() 返回非 List"); return@hooked raw }

                val list = origList.toMutableList<Any?>()
                debugLog("fromJSON: origList.size=${list.size}")

                val already = list.any { item ->
                    item != null && runCatching {
                        tagField?.get(item) as? String
                            ?: (item as? Any?)?.invokeAs("getTag") as? String
                    }.getOrDefault(null) == PURIFY_UPDATE
                }
                if (already) {
                    debugLog("fromJSON: 已存在，跳过")
                    return@hooked list
                }

                val tab = runCatching { tabInfoClz.newInstance() }.getOrNull()
                if (tab == null) {
                    debugLog("fromJSON: newInstance() 返回 null")
                    return@hooked list
                }

                // tag
                runCatching { tagField?.set(tab, PURIFY_UPDATE) }
                debugLog("fromJSON: tag=purify_update")

                // titles
                runCatching { titlesField?.set(tab, mapOf("cn" to "更新")) }

                // url
                runCatching { urlField?.set(tab, "market://update") }
                debugLog("fromJSON: url=market://update")

                list.add(tab)
                debugLog("fromJSON: 注入完成, total=${list.size}")
                return@hooked list
            }

            debugLog("hook 已安装")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG,
                "[UpdateTabEntry] init 失败: ${it.message}", it)
        }
    }
}
