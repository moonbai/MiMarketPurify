package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 禁止切换前台时展示的广告/推荐（主界面 MarketTabActivity 相关）。
 *
 * 对应 README 中的“禁止切换前台展示广告”：拦截 tryShowRecallReCommend、
 * tryShowRecommend、trySplash、fetchSearchHotList 等方法。
 *
 * 优化点：原实现一律返回 null，若被拦方法返回 List（如 fetchSearchHotList），
 * 调用方拿到 null 可能直接 NPE。这里按每个方法的真实返回类型返回安全空值。
 */
object MainTabAds : BaseHook() {

    override val prefKey: String = Settings.KEY_MAIN_TAB

    override val name: String
        get() = "禁止前台广告/推荐"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.main.MarketTabActivity")
                .methodFinder()
                .filter {
                    name in setOf(
                        "tryShowRecallReCommend",
                        "tryShowRecommend",
                        "trySplash",
                        "fetchSearchHotList"
                    )
                }.forEach { method ->
                    val returnType = method.returnType
                    method.hooked {
                        return@hooked when {
                            returnType == java.lang.Boolean.TYPE ||
                                returnType == java.lang.Boolean::class.java -> false
                            List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
                            else -> null
                        }
                    }
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name 拦截失败", it) }
    }
}
