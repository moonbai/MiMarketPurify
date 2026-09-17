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
 * 其 intent 指向 UpdateListActivity。市场框架会自动处理点击跳转和 UI 渲染，
 * 无需额外 Hook 点击回调或管理选中态。
 */
object UpdateTabEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "底栏更新入口"

    /** 注入 TabInfo 的标识 tag */
    private const val PURIFY_UPDATE = "purify_update"

    override fun init() {
        runCatching {
            val tabInfoClz = ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")

            // 缓存 tag 字段
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
                    val result = proceed()
                    if (!enabled()) return@hooked result

                    val list = (result as List).toMutableList()

                    // 防重复注入
                    val already = list.any { item ->
                        runCatching {
                            tagField?.get(item) as? String
                                ?: item.invokeAs("getTag") as? String
                        }.getOrDefault(null) == PURIFY_UPDATE
                    }
                    if (already) return@hooked list

                    // 构造 TabInfo 实例
                    val tab = tabInfoClz.newInstance()

                    // tag
                    tagField?.set(tab, PURIFY_UPDATE)

                    // titles（中英文）
                    runCatching {
                        tabInfoClz.fieldFinder()
                            .filterByName("titles").first()
                            .set(tab, mapOf("cn" to "更新", "en" to "Update"))
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] titles 字段设置失败: ${it.message}")
                    }

                    // icon：优先动态查找资源名，兜底硬编码 ID
                    runCatching {
                        val iconField = tabInfoClz.fieldFinder()
                            .filterByName("icon").first()
                        val pkgRes = HookEnv.base.getApplication().packageManager
                            .getResourcesForApplication("com.xiaomi.market")
                        val iconId = pkgRes.resources.getIdentifier(
                            "ongoing_notification_update_icon",
                            "drawable",
                            "com.xiaomi.market"
                        ).takeIf { it != 0 } ?: 0x7f080d1f
                        iconField.set(tab, iconId)
                        HookEnv.base.log(Log.DEBUG, TAG,
                            "[UpdateTabEntry] icon resId=0x${iconId.toString(16)}")
                    }.onFailure {
                        HookEnv.base.log(Log.WARN, TAG,
                            "[UpdateTabEntry] icon 字段设置失败: ${it.message}")
                    }

                    // intent：点击时跳转 UpdateListActivity
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
                            "[UpdateTabEntry] intent 字段设置失败: ${it.message}")
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
