package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 标签页深层精简：隐藏 Tab 时同时跳过其 Fragment 的数据加载。
 *
 * ⚠️ 注意：本 Hook 在模块初始化时一次性读取 [Settings.getKeptTabs]，
 * 决定 hook 哪些 Fragment。用户在设置页切换 Tab 筛选后，需重启商店才能生效
 * （因为 Tab 的创建/销毁只发生在 MarketTabActivity 启动时）。
 */
object TabDeepClean : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_DEEP_CLEAN
    override val name: String = "标签页深层精简"

    private val tabFragmentMap = mapOf(
        "native_market_agent" to listOf(
            "com.xiaomi.market.business_ui.main.agent.AgentFeedTabFragment"
        ),
        "native_app_assemble" to listOf(
            "com.xiaomi.market.business_ui.main.app_assemble.BaseNativeAppAssembleFragment",
            "com.xiaomi.market.business_ui.main.app_assemble.BaseNativeAppAssemblePagerFragment"
        ),
        "native_market_game" to listOf(
            "com.xiaomi.market.business_ui.main.game.GameFeatureTabFragment",
            "com.xiaomi.market.business_ui.main.game.NativeGamePageFragment"
        )
    )

    override fun init() {
        val keptTabs = Settings.getKeptTabs()

        tabFragmentMap.forEach { (tabKey, fragmentClasses) ->
            if (tabKey in keptTabs) return@forEach

            fragmentClasses.forEach { className ->
                runCatching {
                    ClassUtil.loadClass(className)
                        .methodFinder()
                        .filterByName("onCreateView")
                        .first()
                        .hooked {
                            debugLog("拦截 $className.onCreateView → Tab '$tabKey' 已隐藏，跳过数据加载")
                            // 返回空 View，让 Fragment 不加载任何数据
                            // 从 LayoutInflater 参数取 context（args[0] = LayoutInflater）
                            val inflater = args[0] as? android.view.LayoutInflater
                            val ctx = inflater?.context
                            if (ctx != null) {
                                android.widget.FrameLayout(ctx)
                            } else {
                                proceed()
                            }
                        }
                }.onFailure {
                    HookEnv.base.log(Log.VERBOSE, TAG,
                        "$name: $className.onCreateView 未找到（Tab=$tabKey）", null)
                }
            }
        }
    }
}
