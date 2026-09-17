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

            val tagField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("tag")
                    .filterByType(String::class.java)
                    .firstOrNull()
            }.getOrNull()

            debugLog("init: tagField=${if (tagField != null) "found" else "null"}")

            tabInfoClz.methodFinder()
                .filterByName("fromJSON")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val raw = proceed()
                    if (!enabled()) return@hooked raw

                    val origList = raw as? List<*>
                        ?: return@hooked raw
                    val list = origList.toMutableList<Any?>()

                    debugLog("fromJSON: origList.size=${origList.size}")

                    val already = list.any { item ->
                        runCatching {
                            tagField?.get(item) as? String
                                ?: (item as? Any?)?.invokeAs("getTag") as? String
                        }.getOrDefault(null) == PURIFY_UPDATE
                    }
                    if (already) {
                        debugLog("fromJSON: 已存在 purify_update，跳过注入")
                        return@hooked list
                    }

                    val tab = tabInfoClz.newInstance()
                    tagField?.set(tab, PURIFY_UPDATE)
                    debugLog("fromJSON: 创建新 tab 实例")

                    // titles
                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("titles").first()
                            .set(tab, mapOf("cn" to "更新", "en" to "Update"))
                        debugLog("fromJSON: titles 设置完成")
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] titles 设置失败: ${it.message}")
                    }

                    // icon — 从模块自身 drawable 读取资源 ID
                    runCatching {
                        val iconField = tabInfoClz.fieldFinder()
                            .filterByName("tab_view_icon").first()
                        val activityThreadClz = Class.forName("android.app.ActivityThread")
                        val appCtx = activityThreadClz
                            .getMethod("currentApplication")
                            .invoke(null) as? android.content.Context
                            ?: return@runCatching
                        val resId = appCtx.resources.getIdentifier(
                            "ic_purify_update", "drawable", appCtx.packageName
                        )
                        if (resId != 0) {
                            iconField.set(tab, resId)
                            debugLog("fromJSON: icon resId=0x${resId.toString(16)}")
                        } else {
                            debugLog("fromJSON: ic_purify_update 资源未找到")
                        }
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] icon 设置失败: ${it.message}")
                    }

                    // intent → UpdateListActivity
                    runCatching {
                        val intentField = tabInfoClz.fieldFinder()
                            .filterByName("intent").first()
                        val intent = android.content.Intent().apply {
                            setClassName(
                                "com.xiaomi.market",
                                "com.xiaomi.market.ui.UpdateListActivity"
                            )
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        intentField.set(tab, intent)
                        debugLog("fromJSON: intent 设置完成 → UpdateListActivity")
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] intent 设置失败: ${it.message}")
                    }

                    list.add(tab)
                    debugLog("fromJSON: 注入完成, total=${list.size}")
                    return@hooked list
                }

            debugLog("hook 已安装")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG,
                "[UpdateTabEntry] hook 失败: ${it.message}", null)
        }
    }
}
