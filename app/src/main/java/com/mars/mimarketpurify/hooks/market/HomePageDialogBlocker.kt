package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 屏蔽首页弹窗推广。
 *
 * 商店首页进入时弹出的 Dialog 推广位（Banner 图 + 跳转链接 + 曝光埋点），
 * 由 [HomeFeatureTabFragment.tryShowNativeHomePageDialog] 触发，
 * 实际展示的是 [HomePageDialog]。
 *
 * 两个 hook 点覆盖不同商店版本：
 *  - 旧版：直接 hook HomePageDialog.show()
 *  - 新版：hook tryShowNativeHomePageDialog()，从源头拦截
 */
object HomePageDialogBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_HOME_PAGE_DIALOG
    override val name: String = "首页弹窗推广"

    override fun init() {
        // hook 1：拦截 HomePageDialog.show()
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.main.home.HomePageDialog")
                .methodFinder()
                .filterByName("show")
                .first()
                .hooked {
                    debugLog("拦截 HomePageDialog.show → 跳过首页弹窗推广")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: HomePageDialog.show 未找到（可能已改名）", null)
        }

        // hook 2：拦截触发入口 tryShowNativeHomePageDialog
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.main.home.HomeFeatureTabFragment")
                .methodFinder()
                .filterByName("tryShowNativeHomePageDialog")
                .first()
                .hooked {
                    debugLog("拦截 tryShowNativeHomePageDialog → 从源头阻止弹窗")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: HomeFeatureTabFragment.tryShowNativeHomePageDialog 未找到", null)
        }
    }
}
