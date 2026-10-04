package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 屏蔽升级浮窗卡片。
 *
 * 商店检测到新版本时弹出的浮窗升级提示卡片（继承自 FloatMiniCardLayoutV2），
 * 包含"立即安装"强推按钮、倒计时动画等。与 [UpdateDialogBlock] 屏蔽的
 * SelfUpdateDialogFragment 不同——这里是浮窗形态的升级卡片，走独立的显示路径。
 *
 * 主要 hook 点：
 *  - [AppUpdateFloatCardLayout.showInstallImmediately] — 立即安装强推入口
 *  - [AppUpdateFloatCardLayout.showUpgradeCard] — 升级卡片展示入口（若存在）
 *
 * 兜底：
 *  - [FloatMiniCardLayoutV2.show] — 基类 show()，如果子类方法找不到则拦截基类
 */
object UpdateFloatCardBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_FLOAT_CARD
    override val name: String = "升级浮窗卡片"

    override fun init() {
        // hook 1：拦截 AppUpdateFloatCardLayout.showInstallImmediately
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.update.external.AppUpdateFloatCardLayout")
                .methodFinder()
                .filterByName("showInstallImmediately")
                .first()
                .hooked {
                    debugLog("拦截 AppUpdateFloatCardLayout.showInstallImmediately → 跳过立即安装浮窗")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: showInstallImmediately 未找到", null)
        }

        // hook 2：拦截升级卡片展示入口 showUpgradeCard（部分版本存在）
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.update.external.AppUpdateFloatCardLayout")
                .methodFinder()
                .filterByName("showUpgradeCard")
                .first()
                .hooked {
                    debugLog("拦截 AppUpdateFloatCardLayout.showUpgradeCard → 跳过升级浮窗")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: showUpgradeCard 未找到", null)
        }

        // 兜底 hook 3：拦截基类 FloatMiniCardLayoutV2.show()
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.ui.floatminicard.FloatMiniCardLayoutV2")
                .methodFinder()
                .filterByName("show")
                .first()
                .hooked {
                    val caller = thisObject?.javaClass?.name ?: "unknown"
                    // 只拦截来自 update 包的子类，不影响其他使用 FloatMiniCard 的功能
                    if (caller.contains("update")) {
                        debugLog("拦截 FloatMiniCardLayoutV2.show (来自 $caller) → 兜底")
                        null
                    } else {
                        proceed()
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: FloatMiniCardLayoutV2.show 未找到", null)
        }
    }
}
