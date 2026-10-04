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
 * 现有 [TabFilter] 只隐藏 Tab 标签本身，但 Fragment 的 onCreateView / onResume
 * 仍会执行数据请求和视图构建，浪费流量和内存。
 *
 * 本 Hook 在 Tab 被隐藏时，拦截对应 Fragment 的 [onCreateView] 返回空 View，
 * 等同于 Fragment 虽被创建但不加载任何内容。
 *
 * ⚠️ 注意：本 Hook 在模块初始化时一次性读取 [Settings.getKeptTabs]，
 * 决定 hook 哪些 Fragment。用户在设置页切换 Tab 筛选后，需重启商店才能生效
 * （因为 Tab 的创建/销毁只发生在 MarketTabActivity 启动时）。
 *
 * 覆盖范围：
 *  - native_market_agent → AgentFeedTabFragment
 *  - native_app_assemble → BaseNativeAppAssembleFragment / BaseNativeAppAssemblePagerFragment
 *  - native_market_game → GameFeatureTabFragment / NativeGamePageFragment
 *
 * 随商店版本迭代，Fragment 类名可能变化，每个 hook 独立 runCatching 隔离。
 */
object TabDeepClean : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_DEEP_CLEAN
    override val name: String = "标签页深层精简"

    /**
     * Tab key → 需要拦截的 Fragment 类名列表。
     * key 与 Settings.TAB_ITEMS 中的 key 一致。
     */
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
            // 只在该 Tab 被隐藏时才 hook，如果用户保留了该 Tab 就不拦截
            if (tabKey in keptTabs) return@forEach

            fragmentClasses.forEach { className ->
                runCatching {
                    ClassUtil.loadClass(className)
                        .methodFinder()
                        .filterByName("onCreateView")
                        .first()
                        .hooked {
                            debugLog("拦截 $className.onCreateView → Tab '$tabKey' 已隐藏，跳过数据加载")
                            // 返回一个空的 View，让 Fragment 不加载任何数据
                            val ctx = (args[0] as? android.view.LayoutInflater)?.context
                                ?: (thisObject as? android.app.Fragment)?.activity
                                ?: (thisObject as? androidx.fragment.app.Fragment)?.requireContext()
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
