package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * Miux4 底栏「更新」入口兜底 v3.1 = v3(渲染+提交双验证日志) + 点击拉起。
 *
 * v3.1 新增：hook `commitNavigationItems()` 打 pendingItems.size / tabInfoList.size ——
 * 与 addFragmentTab 日志联合可一次判定断点：
 *   A) [Miux4渲染] addFragmentTab tag=purify_update 出现 + commit pendingItems=3
 *      → 渲染提交全执行，断点在 miuix navigator.H()/U(i) UI 呈现层
 *   B) addFragmentTab tag=purify_update 出现 + commit pendingItems=2
 *      → addFragmentTab 内 createNavigationItem(purify) 抛异常（pendingItems 未加进第3项）
 *   C) addFragmentTab 只有 tag=native_market_home/mine 两次
 *      → 循环未把 purify 送进 addFragmentTab（与既有 getFragmentInfo(2) 日志矛盾，需另查）
 *
 * 所有验证日志用无条件 base.log，不吃调试开关(KEY_RANK_DEBUG)与配置还原时序的亏。
 * 替换项目内 hooks/market/Miux4UpdateEntry.kt 后构建，Market.kt 注册行无需改动。
 */
object Miux4UpdateEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "Miux4更新入口兜底"

    private var lastLaunchTs = 0L
    private const val LAUNCH_DEBOUNCE_MS = 800L
    private const val PURIFY_UPDATE = "purify_update"

    override fun init() {
        val clz = runCatching {
            ClassUtil.loadClass("com.xiaomi.market.widget.BottomTabLayoutMiux4")
        }.getOrNull()
        if (clz == null) {
            debugLog("BottomTabLayoutMiux4 不存在（旧版商店），跳过")
            return
        }

        val pendingField = runCatching {
            clz.getDeclaredField("pendingItems").apply { isAccessible = true }
        }.getOrNull()
        val tabInfoListField = runCatching {
            clz.getDeclaredField("tabInfoList").apply { isAccessible = true }
        }.getOrNull()

        // ① 渲染入口验证（每次实际进入原生 addFragmentTab 的 tag）
        val addTab = clz.declaredMethods.firstOrNull {
            it.name == "addFragmentTab" && it.parameterTypes.size == 2
        }
        if (addTab != null) {
            addTab.hooked {
                val tag = runCatching {
                    (args[0] as? Any)?.invokeAs<String>("getTag")
                }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] addFragmentTab tag=$tag")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked addFragmentTab")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] addFragmentTab 未找到！")
        }

        // ② 提交层数量验证（pendingItems=真正交给 navigator 的项数）
        val commit = clz.declaredMethods.firstOrNull {
            it.name == "commitNavigationItems" && it.parameterTypes.isEmpty()
        }
        if (commit != null) {
            commit.hooked {
                val pending = runCatching {
                    (pendingField?.get(thisObject) as? List<*>)?.size
                }.getOrNull()
                val tabs = runCatching {
                    (tabInfoListField?.get(thisObject) as? List<*>)?.size
                }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] commit pendingItems=$pending tabInfoList=$tabs")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked commitNavigationItems")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] commitNavigationItems 未找到！")
        }

        // ③ 点击拉起更新页（v2 保留）
        val onSelect = clz.declaredMethods.firstOrNull {
            it.name == "onTabSelected" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (onSelect == null) {
            debugLog("BottomTabLayoutMiux4.onTabSelected(I) 未找到，跳过")
            return
        }
        onSelect.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            val tag = runCatching {
                val infos = thisObject?.invokeAs<List<*>>("getTabInfoList")
                infos?.getOrNull(index)?.invokeAs<String>("getTag")
            }.getOrNull()
            debugLog("Miux4 onTabSelected($index) tag=$tag")
            if (tag == PURIFY_UPDATE) {
                val now = System.currentTimeMillis()
                if (now - lastLaunchTs >= LAUNCH_DEBOUNCE_MS) {
                    lastLaunchTs = now
                    val ctx = (thisObject as? View)?.context
                    if (ctx != null) {
                        HookEnv.base.log(Log.DEBUG, TAG, "[Miux4更新入口] 命中 purify_update，拉起更新页")
                        MarketUpdateLauncher.launch(ctx)
                    }
                }
            }
            proceed()
        }
        debugLog("hooked BottomTabLayoutMiux4.onTabSelected(I)")
    }
}
