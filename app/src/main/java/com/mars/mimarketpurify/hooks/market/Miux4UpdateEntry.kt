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
 * Miux4 底栏「更新」入口兜底 v3 = v2(点击拉起) + 渲染验证日志(新增)。
 *
 * v3 新增：hook `BottomTabLayoutMiux4.addFragmentTab(TabInfo, FragmentInfo)`，
 * 用【无条件 base.log】打印每次实际进入原生渲染的 tab tag —— 不走 debugLog，
 * 避免 10-21 16:54 那次「调试开关未开 + 配置未还原」导致日志缺失、无法判定的问题。
 * 该日志直接回答：purify_update 是否真的进入原生渲染循环（i=2 的 addFragmentTab 是否执行）。
 *
 * v2 保留：hook `onTabSelected(I)` 纯点击信号拉起更新页（800ms 防抖）。
 *
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

        // ① 渲染验证（无条件日志，不依赖调试开关）
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

        // ② 点击拉起更新页（v2 保留）
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
