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

    // ═══════════════ 三组开关 ID ═══════════════
    // KEY_MINE_RECOMMEND  → 应用推荐
    private val mineRecommendIds = listOf(
        "mine_ad_container"
    )
    // KEY_MINE_OFFICIAL_TAB → 官方入口
    private val mineTabIds = listOf(
        "mine_middle_menu_container"
    )
    // KEY_MINE_CLEANUP → 清理与卸载
    private val mineCleanupIds = listOf(
        "phone_clear_forbid_layout",
        "mine_uninstall_app_layout"
    )
    // KEY_MINE_SUMMARY → 个人信息（头像/昵称/收藏/消息）
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

    // ═══════════════ 缓存 ═══════════════
    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    /**
     * 每次都重新解析，不永久缓存。
     * 第一次调用时 View 树可能还没有 cleanup 目标 View，
     * 永久缓存会导致后续 setVisibility / onAttachedToWindow 拦截失效。
     */
    private fun getCleanupIdSet(v: View): Set<Int> {
        return resolve(v, mineCleanupIds)
    }

    // ═══════════════ init ═══════════════
    override fun init() {
        // 路径 A：View 一 attach 到 window 就立即检查（抢在商店 setVisibility 之前）
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("onAttachedToWindow")
                .first()
                .hooked {
                    val result = proceed()
                    (thisObject as? View)?.let { v ->
                        runCatching { inspect(v) }
                    }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 挂钩失败", it)
        }

        // 路径 B：onResume 补扫（兜底，处理动态加载的 View）
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // 路径 C：果园皮肤修正（仅在开启时）
        if (Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
            hookOrchardSkin()
        }
    }

    // ═══════════════ 果园皮肤 ═══════════════
    private val orchardMethods = listOf(
        "applyUpdateViewOrchardStyle",
        "applyViewOrchardState",
        "applyEmptyViewOrchardState"
    )
    private val updateViewClasses = listOf(
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView",
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateLayout"
    )

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

    // ═══════════════ onResume 补扫 ═══════════════
    private fun hookActivityRescan(className: String) {
        runCatching {
            ClassUtil.loadClass(className)
                .methodFinder()
                .filterByName("onResume")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        val decor = (thisObject as? Activity)
                            ?.window?.decorView ?: return@hooked result

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
                                if (Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)) {
                                    deepFindAndHide(decor, idSet(decor, "summary", mineSummaryIds))
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

    // ═══════════════ 遍历 ═══════════════
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

    // ═══════════════ 判断 & 隐藏 ═══════════════
    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) hide(v)
        }
    }

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

    private fun hide(v: View) {
        runCatching {
            v.visibility = View.GONE
            v.layoutParams?.let { lp ->
                lp.height = 0
                v.layoutParams = lp  // 触发父容器重新测量，清掉占用空间
            }
        }
    }

    // ═══════════════ ID 解析（每次都重新解析，不永久缓存） ═══════════════
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
}
