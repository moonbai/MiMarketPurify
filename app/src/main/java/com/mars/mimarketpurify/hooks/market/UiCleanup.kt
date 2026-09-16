package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.Collections
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object UiCleanup : BaseHook() {

    override val prefKey: String? = null
    override val name: String = "界面元素屏蔽"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")

    private val mineCleanupIds = listOf(
        "phone_clear_forbid_layout",
        "mine_uninstall_app_layout"
    )

    private val mineSummaryIds = listOf(
        "mine_avatar",
        "mine_nickname",
        "mine_message",
        "mine_message_layout",
        "mine_favorites",
        "mine_favorites_count",
        "mine_favorites_layout",
        "mine_favorites_arrow",
        "mine_summary_root"
    )

    private val featuredTexts = setOf("精选")

    private var cleanupIdCache: Set<Int>? = null

    private fun getCleanupIdSet(v: View): Set<Int> {
        cleanupIdCache?.let { return it }
        val set = mineCleanupIds.mapNotNull { n ->
            val id = runCatching {
                v.resources.getIdentifier(n, "id", "com.xiaomi.market")
            }.getOrNull()
            if (id != null && id > 0) id else null
        }.toSet()
        cleanupIdCache = set
        HookEnv.base.log(Log.WARN, TAG, "$name: cleanupIdSet 缓存 ${set.size}/${mineCleanupIds.size}")
        return set
    }

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    private val orchardMethods = listOf(
        "applyUpdateViewOrchardStyle",
        "applyViewOrchardState",
        "applyEmptyViewOrchardState"
    )
    private val updateViewClasses = listOf(
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView",
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateLayout"
    )

    override fun init() {
        // Hook View.setVisibility，拦截 cleanup 目标被商店重新显示
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("setVisibility")
                .first()
                .hooked {
                    val view = thisObject as? View ?: return@hooked proceed()
                    val arg = args[0] as? Int ?: return@hooked proceed()
                    if (arg == View.VISIBLE && Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
                        val id = view.id
                        if (id > 0 && id in getCleanupIdSet(view)) {
                            return@hooked proceed()
                        }
                    }
                    proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 挂钩失败", it)
        }

        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // 仅在用户启用「果园皮肤修正」时才 hook，否则不动升级卡片
        if (Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
            hookOrchardSkin()
        }
    }

    private fun hookOrchardSkin() {
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls.methodFinder()
                        .filterByName(method)
                        .forEach { m ->
                            m.hooked {
                                val result = proceed()
                                (thisObject as? View)?.let { view ->
                                    view.background = null
                                    view.setPadding(0, 0, 0, 0)
                                }
                                result
                            }
                        }
                }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过果园皮肤处理", null)
            }
        }
    }

    private fun hookActivityRescan(className: String) {
        runCatching {
            ClassUtil.loadClass(className)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        val decor = (thisObject as? Activity)?.window?.decorView ?: return@hooked result

                        // 第一轮：立即扫描
                        scanTree(decor, 0)

                        // 第二轮：延迟 300ms 重扫
                        mainHandler.postDelayed({
                            runCatching { scanTree(decor, 0) }
                        }, 300L)

                        // 第三轮：延迟 800ms 兜底
                        mainHandler.postDelayed({
                            runCatching {
                                scanTree(decor, 0)
                                if(Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)){
                                    deepFindAndHide(decor, idSet(decor,"summary",mineSummaryIds))
                                }
                            }
                        }, 800L)

                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }

    private fun scanTree(v: View, depth: Int) {
        if (depth > 8) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtLeast(0).coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    private fun deepFindAndHide(root: View, targetIds: Set<Int>, depth: Int = 0) {
        if (depth > 12) return
        if (root.id in targetIds) {
            hide(root)
        }
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            deepFindAndHide(root.getChildAt(i) ?: continue, targetIds, depth + 1)
        }
    }

    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) hide(v)
        }
    }

    /**
     * 判断是否为「我的」页需要隐藏的目标。
     *
     * 每个开关只管自己的 ID 列表，互不影响：
     * - 推荐广告卡片 → KEY_MINE_RECOMMEND
     * - 官方标签栏   → KEY_MINE_OFFICIAL_TAB
     * - 清理与卸载   → KEY_MINE_CLEANUP
     * - 顶部个人信息 → KEY_MINE_SUMMARY
     */
    private fun isMineTarget(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false

        val recommendOn = Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)
        val tabOn = Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)
        val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
        val summaryOn = Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)

        if (recommendOn && id in idSet(v, "recommend", mineRecommendIds)) return true
        if (tabOn && id in idSet(v, "tab", mineTabIds)) return true
        if (cleanupOn && id in getCleanupIdSet(v)) return true
        if (summaryOn && id in idSet(v, "summary", mineSummaryIds)) return true

        return false
    }

    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    private fun idSet(v: View, key: String, names: List<String>): Set<Int> {
        resolved[key]?.let { return it }
        val set = resolve(v, names)
        resolved[key] = set
        return set
    }

    private fun resolve(v: View, names: List<String>): Set<Int> =
        names.mapNotNull { n ->
            val id = runCatching {
                v.resources.getIdentifier(n, "id", "com.xiaomi.market")
            }.getOrNull()
            if (id != null && id > 0) id else null
        }.toSet()

    private fun hide(v: View) {
        runCatching {
            v.visibility = View.GONE
        }
    }
}
