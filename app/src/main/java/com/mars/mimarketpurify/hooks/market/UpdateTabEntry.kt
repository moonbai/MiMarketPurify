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

/**
 * 移花接木：在底栏注入「更新」标签，点击直达 UpdateListActivity。
 *
 * 只需 Hook 数据层——在 TabInfo.fromJSON() 返回的列表末尾追加一个自定义 TabInfo，
 * 其 intent 指向 UpdateListActivity。市场框架会自动处理点击跳转和 UI 渲染。
 */
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

                    val already = list.any { item ->
                        runCatching {
                            tagField?.get(item) as? String
                                ?: (item as? Any?)?.invokeAs("getTag") as? String
                        }.getOrDefault(null) == PURIFY_UPDATE
                    }
                    if (already) return@hooked list

                    val tab = tabInfoClz.newInstance()
                    tagField?.set(tab, PURIFY_UPDATE)

                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("titles").first()
                            .set(tab, mapOf("cn" to "更新", "en" to "Update"))
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] titles 设置失败: ${it.message}")
                    }

                    runCatching {
                        val iconField = tabInfoClz.fieldFinder()
                            .filterByName("icon").first()
                        val activityThreadClz = Class.forName("android.app.ActivityThread")
                        val appCtx = activityThreadClz
                            .getMethod("currentApplication")
                            .invoke(null) as? android.content.Context
                            ?: return@runCatching
                        val pkgRes = appCtx.packageManager
                            .getResourcesForApplication("com.xiaomi.market")
                        val iconId = pkgRes.resources.getIdentifier(
                            "ongoing_notification_update_icon",
                            "drawable",
                            "com.xiaomi.market"
                        ).let { id: Int -> if (id != 0) id else 0x7f080d1f }
                        iconField.set(tab, iconId)
                        HookEnv.base.log(Log.DEBUG, TAG,
                            "[UpdateTabEntry] icon resId=0x${iconId.toString(16)}")
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] icon 设置失败: ${it.message}")
                    }

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
                        HookEnv.base.log(Log.DEBUG, TAG,
                            "[UpdateTabEntry] intent 设置完成")
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] intent 设置失败: ${it.message}")
                    }

                    list.add(tab)
                    HookEnv.base.log(Log.INFO, TAG,
                        "[UpdateTabEntry] 注入 tab: $PURIFY_UPDATE (共 ${list.size} 个)")
                    return@hooked list
                }

            HookEnv.base.log(Log.DEBUG, TAG, "[UpdateTabEntry] hook 已安装")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG,
                "[UpdateTabEntry] hook 失败: ${it.message}", null)
        }
    }
}
