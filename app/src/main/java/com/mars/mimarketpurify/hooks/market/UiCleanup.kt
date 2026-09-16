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
    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")
    private val mineCleanupIds = listOf(
        "phone_clear_forbid_layout",
        "mine_uninstall_app_layout"
    )
    private val mineSummaryIds = listOf(
        "mine_avatar", "mine_nickname", "mine_message", "mine_message_layout",
        "mine_favorites", "mine_favorites_count", "mine_favorites_layout",
        "mine_favorites_arrow", "mine_message_arrow", "mine_summary_root"
    )
    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    private fun getCleanupIdSet(v: View): Set<Int> = resolve(v, mineCleanupIds)

    // ═══════════════ init ═══════════════
    override fun init() {
        // ── setVisibility 拦截：挡住商店恢复 VISIBLE ──
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
            runCatching {
                ClassUtil.loadClass("android.view.View")
                    .methodFinder()
                    .filterByName("setVisibility")
                    .first()
                    .hooked {
                        val view = thisObject as? View ?: return@hooked proceed()
                        val arg = args[0] as? Int ?: return@hooked proceed()
                        if (arg == View.VISIBLE) {
                            val id = view.id
                            if (id > 0 && id in getCleanupIdSet(view)) {
                                args[0] = View.GONE
                            }
                        }
                        proceed()
                    }
            }.onFailure {
                HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 挂钩失败", it)
            }
        }

        // ── onAttachedToWindow：View 进 window 时立即检查 ──
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("onAttachedToWindow")
                .first()
                .hooked {
                    val result = proceed()
                    (thisObject as? View)?.let { v -> runCatching { inspect(v) } }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 挂钩失败", it)
        }

        // ── onResume 补扫 ──
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // ── 果园皮肤 ──
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
                    cls.methodFinder().filterByName(method).forEach { m ->
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

                        scanTree(decor, 0)

                        // 延迟补扫
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 300L)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 800L)

                        // ── 精确定位 phone_clear_forbid_layout ──
                        // 扫描找不到它，用 findViewById 直接按 ID 找
                        mainHandler.postDelayed({
                            runCatching {
                                val cleanupIds = getCleanupIdSet(decor)
                                cleanupIds.forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    val name = getResourceName(v)
                                    HookEnv.base.log(Log.WARN, TAG,
                                        "$name: 精确定位 $name vis=${visStr(v)} parent=${getResourceName(v.parent as? View)}")

                                    if (v.visibility == View.VISIBLE) {
                                        HookEnv.base.log(Log.WARN, TAG,
                                            "$name: ★ 精确 hide $name")
                                        hide(v)
                                        // 向上 hide 父容器
                                        hideAncestors(v, maxLevels = 5)
                                    }
                                }
                            }
                        }, 1500L)

                        // ── 验证：确认 View 没被恢复 ──
                        mainHandler.postDelayed({
                            runCatching {
                                val cleanupIds = getCleanupIdSet(decor)
                                cleanupIds.forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    if (v.visibility == View.VISIBLE) {
                                        HookEnv.base.log(Log.WARN, TAG,
                                            "$name: ⚠ 恢复了! 重新 hide ${getResourceName(v)}")
                                        hide(v)
                                        hideAncestors(v, 5)
                                    }
                                }
                            }
                        }, 3000L)

                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }

    /**
     * 精确按 ID 在 View 树中查找，不依赖 scanTree。
     */
    private fun findViewById(root: View, targetId: Int, depth: Int = 0): View? {
        if (depth > 20) return null
        if (root.id == targetId) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewById(root.getChildAt(i) ?: continue, targetId, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 向上隐藏祖先容器，遇到 RecyclerView/ScrollView 就停。
     */
    private fun hideAncestors(v: View, maxLevels: Int) {
        var cur: View? = v.parent as? View
        repeat(maxLevels) {
            if (cur == null) return
            val cls = cur!!.javaClass.name
            if (cls.contains("RecyclerView") || cls.contains("ScrollView") ||
                cls.contains("NestedScroll") || cls.contains("CoordinatorLayout")) {
                return
            }
            if (cur!!.visibility == View.VISIBLE && cur!!.id > 0) {
                HookEnv.base.log(Log.WARN, TAG,
                    "$name:   → hide ancestor ${getResourceName(cur!!)} (${cur!!.javaClass.simpleName})")
                hide(cur!!)
            }
            cur = cur!!.parent as? View
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

    // ═══════════════ 判断 & 隐藏 ═══════════════
    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) {
                hide(v)
                if (v.id > 0 && v.id in getCleanupIdSet(v)) {
                    hideAncestors(v, 5)
                }
            }
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
                v.layoutParams = lp
            }
        }
    }

    private fun getResourceName(v: View?): String = if (v == null) "null" else runCatching {
        v.resources.getResourceEntryName(v.id)
    }.getOrNull() ?: "id=${v.id}"

    private fun visStr(v: View): String = when (v.visibility) {
        View.VISIBLE -> "VISIBLE"
        View.INVISIBLE -> "INVISIBLE"
        View.GONE -> "GONE"
        else -> "?"
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
}
