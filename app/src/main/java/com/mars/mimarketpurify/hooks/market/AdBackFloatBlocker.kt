package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 屏蔽返回浮窗广告（AdBack）。
 *
 * 商店返回时弹出的悬浮浮窗「返回今日头条」「返回xxx」，
 * 引导用户跳转第三方 App。完整链路：
 *   AdBackManager.parseIntent(intent, ref)
 *     → parseAndShow(uri, ref)
 *       → AdBackFloatView.Builder.build()
 *         → show()
 *
 * Hook 点选在 [AdBackManager.parseIntent] 入口处，直接拦截最高效，
 * 无需等 FloatView 构建出来再 hide。
 */
object AdBackFloatBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_AD_BACK_FLOAT
    override val name: String = "返回浮窗广告"

    override fun init() {
        // 主 hook：拦截 AdBackManager.parseIntent(Intent, String)
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.ad.floatback.AdBackManager")
                .methodFinder()
                .filterByName("parseIntent")
                .filterByParamCount(2)
                .first()
                .hooked {
                    debugLog("拦截 AdBackManager.parseIntent → 跳过返回浮窗广告")
                    // 直接返回，不调 proceed → 整个 parseAndShow 链路被跳过
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: AdBackManager.parseIntent 挂钩失败", it)
        }

        // 备用 hook：即使 parseIntent 绕过了，拦截 parseAndShow 作为兜底
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.ad.floatback.AdBackManager")
                .methodFinder()
                .filterByName("parseAndShow")
                .filterByParamCount(2)
                .first()
                .hooked {
                    debugLog("拦截 AdBackManager.parseAndShow → 兜底拦截")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: parseAndShow 兜底 hook（可选）", null)
        }
    }
}
