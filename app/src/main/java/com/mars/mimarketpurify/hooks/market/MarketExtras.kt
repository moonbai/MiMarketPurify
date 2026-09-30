package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.util.Log
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.EarlyBadgeSuppressor
import com.mars.mimarketpurify.util.NativeTabBar
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.WeakHashMap

/**
 * 从 XiaomiHelper（HowieHChen/XiaomiHelper）的应用商店规则里补移植过来的几项。
 *
 * 分成五个独立对象而不是一个大对象，是因为它们各自挂在**不同的开关**上：
 * [BaseHook.hooked] 每次调用都会重新读取 [BaseHook.prefKey]，
 * 这样用户在主页切换其中任何一项都不用重启应用商店。
 * 合成一个对象的话 prefKey 只能有一个，就没法分别控制了。
 *
 * 所有 hook 都包在 runCatching 里：商店换个版本类名就没了，
 * 缺一个类不该影响其它功能（这条原则与项目里其它 hook 一致）。
 */

/** 底部 Tab 的数字角标与「新」字红点 */
object TabBadge : BaseHook() {

    override val prefKey: String = Settings.KEY_TAB_BADGE

    override val name: String
        get() = "隐藏底栏角标"

    private const val MAIN_ACTIVITY =
        "com.xiaomi.market.business_ui.main.MarketTabActivity"

    /**
     * 每个 Activity 当前的「早压制守卫」：onResume 复用、onDestroy 释放，
     * 用 WeakHashMap 避免持有已销毁 Activity 造成泄漏，也避免 OnPreDrawListener 堆积。
     */
    private val suppressors = WeakHashMap<Activity, EarlyBadgeSuppressor>()

    /**
     * 双层净化，参考 HyperModifier 针对应用商店底栏的原生 Hook 思路：
     *
     * 1) **方法层**（原逻辑，保留并扩充）：拦截 TabView 的角标设置方法，
     *    让商店之后再也设不上数字 / 红点；
     * 2) **原生 View 层**（新增）：方法层拦不住「已经 inflate 出来的角标」，
     *    也挡不住布局刷新时的单帧闪现。这里 Hook 主 Activity 生命周期，
     *    直接按资源 id 定位底栏 TabView，主动清掉已显示的角标，并用
     *    [EarlyBadgeSuppressor]（OnPreDrawListener）在首帧前后按帧补刀防复现。
     *
     * 全程 runCatching 兜底：商店换版本类名/资源名漂移时静默降级，不影响其它功能。
     */
    override fun init() {
        // ── 方法层：拦截角标的「新设置」──
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.widget.TabView")
                .methodFinder()
                .filter {
                    name in setOf(
                        "setNumber", "showNewMessageTag",
                        "setRedPoint", "showRedPoint", "setRedDot", "showRedDot"
                    )
                }
                .forEach { it.hooked { null } }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 TabView，跳过方法层拦截", null)
        }

        // ── 原生 View 层：定位底栏 TabView，主动清除已显示角标 + 防复现 ──
        runCatching {
            ClassUtil.loadClass(MAIN_ACTIVITY)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? Activity)?.let { act -> enforceBadgesHidden(act) }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 MarketTabActivity，跳过原生角标清理", null)
        }

        // ── 生命周期清理：Activity 销毁时移除监听，防止泄漏 ──
        runCatching {
            ClassUtil.loadClass(MAIN_ACTIVITY)
                .methodFinder()
                .filterByName("onDestroy")
                .forEach { m ->
                    m.hooked {
                        (thisObject as? Activity)?.let { act -> suppressors.remove(act)?.stop() }
                        proceed()
                    }
                }
        }.onFailure { /* 清理失败不影响主功能 */ }
    }

    /**
     * 原生清除底栏角标：先同步清一次，再挂早压制守卫在首帧前后按帧补刀，
     * 覆盖「商店陆续把角标设回」的时序，消除单帧闪现。
     * 调用点已在 [BaseHook.hooked] 内，受 [prefKey] 开关实时门控；
     * post 回调里再判一次 [enabled]，保证用户在过程中关掉开关能即时收手。
     */
    private fun enforceBadgesHidden(activity: Activity) {
        activity.window?.decorView?.post {
            runCatching {
                if (!enabled()) return@runCatching
                if (activity.isFinishing || activity.isDestroyed) return@runCatching
                val hidden = NativeTabBar.hideAllBadges(activity)
                if (hidden > 0) debugLog("onResume: 原生清除 $hidden 个底栏角标")
                // 同一 Activity 复用守卫：先停旧的再起新的，避免监听堆积
                suppressors.remove(activity)?.stop()
                suppressors[activity] = EarlyBadgeSuppressor(activity).also { it.start() }
            }
        }
    }
}

/** 首页搜索框左侧的动态活动入口（云控下发的小图标 / 动图） */
object EntranceAds : BaseHook() {

    override val prefKey: String = Settings.KEY_ENTRANCE

    override val name: String
        get() = "屏蔽首页活动入口"

    override fun init() {
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.util.EntranceManager")
                .methodFinder()
                .filterByName("getEntranceConfig")
                .forEach { it.hooked { null } }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 EntranceManager，跳过", null) }
    }
}

/** 「我的」页底部推广应用列表（数据层直接返回 0 组） */
object MineAdGroup : BaseHook() {

    override val prefKey: String = Settings.KEY_MINE_RECOMMEND  // 原来是 KEY_MINE_AD_GROUP

    override val name: String
        get() = "屏蔽「我的」页推广组"

    override fun init() {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.analytics.onetrack.ExperimentManager\$Companion"
            ).methodFinder()
                .filterByName("getMineAdRecommendGroup")
                .forEach { it.hooked { 0 } }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 ExperimentManager，跳过", null) }
    }
}

/**
 * 详情页与「浏览器下载」弹窗里剩下的广告位：
 * 拼装推荐、多按钮底栏（多出来的那个通常是推广）、下载弹窗内的广告列表。
 */
object DetailExtras : BaseHook() {

    override val prefKey: String = Settings.KEY_DETAIL_EXTRAS

    override val name: String
        get() = "详情页附加净化"

    override fun init() {
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.common.network.retrofit.response.bean.AppDetailV3"
            ).apply {
                methodFinder()
                    .filter { name in setOf("showAssemble", "getShowAssemble") }
                    .forEach { it.hooked { false } }

                // 底部多按钮那一栏多出来的按钮通常是推广位，压回单按钮
                methodFinder()
                    .filterByName("getLayoutType")
                    .forEach { m ->
                        m.hooked {
                            val original = proceed()
                            if (original == "bottomMultiButton") "bottomSingleButton" else original
                        }
                    }
            }
        }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 AppDetailV3，跳过", null) }

        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.directmail.BottomMiniSourceFileFragment"
            ).apply {
                methodFinder()
                    .filterByName("needShowAdList")
                    .forEach { it.hooked { false } }
                methodFinder()
                    .filterByName("ensureRiskLayout")
                    .forEach { it.hooked { null } }
            }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 BottomMiniSourceFileFragment，跳过", null)
        }
    }
}

/** 阻止商店弹出的「升级提醒」对话框 */
object UpdateDialogBlock : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_DIALOG

    override val name: String
        get() = "阻止升级提醒弹窗"

    override fun init() {
        listOf(
            "com.xiaomi.market.ui.UpdateListFragment",
            "com.xiaomi.market.ui.update.UpdatePushDialogManager"
        ).forEach { owner ->
            runCatching {
                ClassUtil.loadClass(owner)
                    .methodFinder()
                    .filterByName("tryShowDialog")
                    .forEach { it.hooked { null } }
            }.onFailure { HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过", null) }
        }
    }
}
