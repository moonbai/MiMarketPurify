package com.mars.mimarketpurify.hooks.market

import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * Miux4 底栏「更新」入口点击兜底（应用商店 4.128.11+）。
 *
 * 原理：4.128.11 起 Miux4 底栏 tab 选中的必经链路是
 * `BottomTabLayoutMiux4.addFragmentTab$lambda$2(index) → getFragment(I)`，
 * 且 lambda 内 getFragment 的返回值被丢弃（纯副作用触发），与点击是否走
 * `View.performClick` 无关。因此当 TabFilter 的 performClick 层拦截
 * （NativeTabBar.tagOf 链路）在 Miux4 上不生效时，这里作为**确定性兜底**：
 * 反射读该下标 TabInfo 的 tag，命中 purify_update 即拉起应用更新页。
 *
 * 仅 4.128.11+ 存在 BottomTabLayoutMiux4；旧版本类找不到 → init 直接返回，旧逻辑不受影响。
 * 开关与「底栏更新入口」共用 [Settings.KEY_UPDATE_TAB]，由 BaseHook.enabled() 每次调用时检查。
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
        val method = clz.declaredMethods.firstOrNull {
            it.name == "getFragment" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (method == null) {
            debugLog("BottomTabLayoutMiux4.getFragment(I) 未找到，跳过")
            return
        }

        method.hooked {
            val index = args[0] as? Int ?: return@hooked proceed()
            val tag = runCatching {
                val infos = thisObject?.invokeAs<List<*>>("getTabInfoList")
                infos?.getOrNull(index)?.invokeAs<String>("getTag")
            }.getOrNull()
            debugLog("Miux4 getFragment($index) tag=$tag")
            if (tag == PURIFY_UPDATE) {
                val now = System.currentTimeMillis()
                if (now - lastLaunchTs >= LAUNCH_DEBOUNCE_MS) {
                    lastLaunchTs = now
                    val ctx = (thisObject as? android.view.View)?.context
                    if (ctx != null) {
                        debugLog("命中 purify_update，拉起更新页")
                        MarketUpdateLauncher.launch(ctx)
                    }
                }
            }
            proceed()
        }
        debugLog("hooked BottomTabLayoutMiux4.getFragment(I)")
    }
}
