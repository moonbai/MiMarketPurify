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

    /** 缓存找到的字段名（首次 init 后不再重新发现） */
    private var cachedIconField: String? = null
    private var cachedIntentField: String? = null
    private var fieldsDiscovered = false

    override fun init() {
        runCatching {
            val tabInfoClz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")

            val tagField = runCatching {
                tabInfoClz.fieldFinder()
                    .filterByName("tag")
                    .filterByType(String::class.java)
                    .firstOrNull()
            }.getOrNull()

            // 首次：打印所有字段，发现 icon 和 intent
            if (!fieldsDiscovered) {
                val allFields = tabInfoClz.declaredFields
                debugLog("TabInfo declared fields (${allFields.size}):")
                allFields.forEach { f ->
                    debugLog("  ${f.name} → ${f.type.name}")
                }

                // 发现 icon 字段（Int 类型）
                cachedIconField = allFields
                    .firstOrNull { f ->
                        f.type == Int::class.javaPrimitiveType &&
                            f.name.contains("icon", ignoreCase = true)
                    }?.name

                // 发现 intent 字段（Intent 类型）
                cachedIntentField = allFields
                    .firstOrNull { f ->
                        android.content.Intent::class.java.isAssignableFrom(f.type)
                    }?.name

                debugLog("discovered: iconField=$cachedIconField, intentField=$cachedIntentField")
                fieldsDiscovered = true
            }

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
                        debugLog("fromJSON: 已存在 purify_update，跳过")
                        return@hooked list
                    }

                    val tab = tabInfoClz.newInstance()
                    tagField?.set(tab, PURIFY_UPDATE)
                    debugLog("fromJSON: tag 设置完成")

                    // titles
                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("titles").first()
                            .set(tab, mapOf("cn" to "更新", "en" to "Update"))
                        debugLog("fromJSON: titles 设置完成")
                    }.onFailure {
                        debugLog("fromJSON: titles 失败: ${it.message}")
                    }

                    // icon — 用发现的字段名
                    if (cachedIconField != null) {
                        runCatching {
                            val iconField = tabInfoClz.fieldFinder()
                                .filterByName(cachedIconField!!).first()
                            val activityThreadClz = Class.forName("android.app.ActivityThread")
                            val appCtx = activityThreadClz
                                .getMethod("currentApplication")
                                .invoke(null) as? android.content.Context
                            if (appCtx == null) {
                                debugLog("fromJSON: appCtx 为 null")
                                return@runCatching
                            }
                            val resId = appCtx.resources.getIdentifier(
                                "ic_purify_update", "drawable", appCtx.packageName
                            )
                            if (resId != 0) {
                                iconField.set(tab, resId)
                                debugLog("fromJSON: icon[$cachedIconField] = 0x${resId.toString(16)}")
                            } else {
                                debugLog("fromJSON: ic_purify_update 资源未找到")
                            }
                        }.onFailure {
                            debugLog("fromJSON: icon 异常: ${it.message}")
                        }
                    } else {
                        debugLog("fromJSON: 无 icon 字段，跳过")
                    }

                    // intent — 用发现的字段名
                    if (cachedIntentField != null) {
                        runCatching {
                            val intentField = tabInfoClz.fieldFinder()
                                .filterByName(cachedIntentField!!).first()
                            val intent = android.content.Intent().apply {
                                setClassName(
                                    "com.xiaomi.market",
                                    "com.xiaomi.market.ui.UpdateListActivity"
                                )
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            intentField.set(tab, intent)
                            debugLog("fromJSON: intent[$cachedIntentField] 设置完成")
                        }.onFailure {
                            debugLog("fromJSON: intent 异常: ${it.message}")
                        }
                    } else {
                        debugLog("fromJSON: 无 intent 字段，跳过")
                    }

                    list.add(tab)
                    debugLog("fromJSON: 注入完成, total=${list.size}")
                    return@hooked list
                }

            debugLog("hook 已安装")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG,
                "[UpdateTabEntry] hook 失败: ${it.message}", it)
        }
    }
}
