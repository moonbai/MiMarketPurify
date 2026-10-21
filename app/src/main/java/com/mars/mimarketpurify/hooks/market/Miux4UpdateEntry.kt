package com.mars.mimarketpurify.hooks.market

import android.view.View
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * Miux4 底栏「更新」入口点击兜底 v2（应用商店 4.128.11+）。
 *
 * v2 修正（重要）：hook 点由 getFragment(I) 改为 onTabSelected(I)。
 * 反编译确证 4.128.11 存在两条调用路径：
 *   1) 点击路径：navigator item 选中 → _init_$lambda$1(index) → onTabSelected(index)
 *      → updateFragmentVisibility → getFragment(index)；
 *   2) 预热路径：addFragmentTab → postDelayed(widget.r, 2000ms) → r.run
 *      → BottomTabLayoutMiux4.a() → addFragmentTab$lambda$2 → getFragment(index)
 *      —— 每个 tab 添加 2 秒后都会被调一次，与点击无关。
 * v1 hook 的 getFragment 会被预热路径触发，导致打开商店 2 秒后自动弹更新页；
 * 而 onTabSelected 仅由 navigator 点击回调触发（程序 show()/selectTab 不经过它），
 * 是纯点击信号，且命中后 performClick 层拦截成功时本层不会被触达（performClick
 * 被 TabFilter 拦截跳过原逻辑 → listener 不执行），两层天然互斥、不会双弹。
 *
 * 仅 4.128.11+ 存在 BottomTabLayoutMiux4；旧版本类找不到 → init 直接返回。
 * 开关与「底栏更新入口」共用 [Settings.KEY_UPDATE_TAB]。
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
        // private final onTabSelected(I)V —— 仅 navigator 点击回调 (_init_$lambda$1) 触达
        val method = clz.declaredMethods.firstOrNull {
            it.name == "onTabSelected" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (method == null) {
            debugLog("BottomTabLayoutMiux4.onTabSelected(I) 未找到，跳过")
            return
        }

        method.hooked {
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
                        debugLog("命中 purify_update，拉起更新页")
                        MarketUpdateLauncher.launch(ctx)
                    }
                }
            }
            proceed()
        }
        debugLog("hooked BottomTabLayoutMiux4.onTabSelected(I)")
    }
}
