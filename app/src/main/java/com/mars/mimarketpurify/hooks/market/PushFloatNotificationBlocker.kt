package com.mars.mimarketpurify.hooks.market

import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 屏蔽 Push 悬浮通知。
 *
 * 商店通过 MiPush 推送的浮窗通知会直接覆盖在当前界面上方，
 * 包含游戏推广、应用推荐等内容。涉及的 Processor：
 *
 *  - [PushFloatingNotificationProcessor]  — 通用悬浮通知处理器
 *  - [PushGameFloatingNotificationProcessor] — 游戏推广悬浮通知
 *  - [PushActiveNotificationProcessor] — 主动推送通知
 *  - [PushPreInstallNotificationProcessor] — 预装通知
 *
 * 选择在各 Processor 的 [processMessage] 入口拦截，
 * 等同于让 push 消息被"静默忽略"，不会走到构建浮窗/通知的流程。
 *
 * 同时 hook [MarketFloatNotification.Builder.build] 作为兜底，
 * 即使 Processor 被热修复绕过，build 返回 null 也能阻止浮窗显示。
 */
object PushFloatNotificationBlocker : BaseHook() {

    override val prefKey: String = Settings.KEY_PUSH_FLOAT
    override val name: String = "Push悬浮通知"

    /** 需要拦截的 Processor 类名列表 */
    private val processorClasses = listOf(
        "com.xiaomi.market.business_core.push.mi_push.PushFloatingNotificationProcessor",
        "com.xiaomi.market.business_core.push.market_float_notification.PushGameFloatingNotificationProcessor",
        "com.xiaomi.market.business_core.push.mi_push.PushActiveNotificationProcessor",
        "com.xiaomi.market.business_core.push.mi_push.PushPreInstallNotificationProcessor"
    )

    override fun init() {
        // 拦截所有 Processor 的 processMessage
        processorClasses.forEach { className ->
            runCatching {
                ClassUtil.loadClass(className)
                    .methodFinder()
                    .filterByName("processMessage")
                    .first()
                    .hooked {
                        debugLog("拦截 ${className.substringAfterLast('.')} → 跳过Push悬浮通知")
                        null
                    }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: $className.processMessage 未找到", null)
            }
        }

        // 兜底：拦截 MarketFloatNotification.Builder.build()
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.push.market_float_notification.MarketFloatNotification\$Builder")
                .methodFinder()
                .filterByName("build")
                .first()
                .hooked {
                    debugLog("拦截 MarketFloatNotification.Builder.build → 兜底返回null")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: MarketFloatNotification.Builder.build 未找到", null)
        }

        // 兜底：拦截 FloatHintController 的显示入口
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_core.push.market_float_notification.FloatHintController")
                .methodFinder()
                .filterByName("show")
                .first()
                .hooked {
                    debugLog("拦截 FloatHintController.show → 兜底阻止浮窗控制显示")
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: FloatHintController.show 未找到", null)
        }
    }
}
