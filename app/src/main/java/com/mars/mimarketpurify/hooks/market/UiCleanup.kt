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
        "mine_favorites_arrow", "mine_summary_root"
    )
    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    private fun getCleanupIdSet(v: View): Set<Int> = resolve(v, mineCleanupIds)

    // ═══════════════ 诊断：定向搜索目标 View 的祖先链 ═══════════════
    private var diagDone = false

    /**
     * 从 View 树中找到包含指定文本或 ID 的 View，
     * 打印它从根到自身的完整祖先链（含每个节点的 ID 名和类名）。
     * 这样就能看到卡片容器的真实 ID。
     */
    private fun traceAncestors(
        root: View,
        target: View,
        path: MutableList<String> = mutableListOf()
    ): Boolean {
        path.add("${root.javaClass.simpleName}#" + runCatching {
            root.resources.getResourceEntryName(root.id)
        }.getOrNull()?.let { "id=$it(${root.id})" } ?: "id=${root.id}")

        if (root === target) {
            HookEnv.base.log(Log.INFO, TAG,
                "$name: ═══ 祖先链 ═══\n  ${path.reversed().joinToString("\n  ")}")
            return true
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i) ?: continue
                if (traceAncestors(child, target, path)) return true
            }
        }
        path.removeAt(path.lastIndex)
        return false
    }

    /**
     * 在 View 树中搜索包含"清理"或"卸载"文本的 TextView，
     * 打印其父容器链，帮助定位卡片容器 ID。
     */
    private fun searchForText(root: View, keyword: String, depth: Int = 0) {
        if (depth > 15) return
        if (root is TextView) {
            val txt = root.text?.toString() ?: ""
            if (txt.contains(keyword)) {
                // 打印这个 View 及其所有祖先
                HookEnv.base.log(Log.WARN, TAG,
                    "$name: ★ 找到文本=\"$txt\" (${root.javaClass.simpleName}#${runCatching {
                        root.resources.getResourceEntryName(root.id)
                    }.getOrNull() ?: "?"} id=${root.id})")
                traceAncestors(
                    (root.parent as? View) ?: root.rootView,
                    root
                )
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                searchForText(root.getChildAt(i) ?: continue, keyword, depth + 1)
            }
        }
    }

    /**
     * 打印指定 View 的直接父容器信息（5 层以内）。
     */
    private fun printParentChain(v: View, levels: Int = 5) {
        val sb = StringBuilder()
        var cur: View? = v
        repeat(levels) {
            if (cur == null) return@repeat
            val idName = runCatching {
                cur!!.resources.getResourceEntryName(cur!!.id)
            }.getOrNull() ?: "?"
            sb.appendLine("  → ${cur!!.javaClass.simpleName}#${idName}(vis=${cur!!.visibility})")
            cur = cur!!.parent as? View
        }
        HookEnv.base.log(Log.INFO, TAG, "$name: parent chain of #${runCatching {
            v.resources.getResourceEntryName(v.id)
        }.getOrNull() ?: "?"}:\n$sb")
    }

    /**
     * 完整 dump 指定 subtree，深度可达 20 层。
     */
    private fun dumpSubtree(v: View, depth: Int = 0, maxDepth: Int = 20, sb: StringBuilder = StringBuilder()) {
        if (depth > maxDepth) return
        val indent = "  ".repeat(depth)
        val idName = if (rootId(v) > 0) runCatching {
            v.resources.getResourceEntryName(rootId(v))
        }.getOrNull() ?: "${rootId(v)}" else "NO_ID"
        val vis = when (v.visibility) { View.VISIBLE -> "V"; View.INVISIBLE -> "I"; View.GONE -> "G"; else -> "?" }
        sb.appendLine("$indent[$vis] ${v.javaClass.simpleName}  id=$idName(${rootId(v)})")
        if (v is TextView && v.text != null) {
            val txt = v.text.toString().trim()
            if (txt.isNotEmpty() && txt.length < 40) sb.appendLine("$indent  └─ text=\"$txt\"")
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                dumpSubtree(v.getChildAt(i) ?: continue, depth + 1, maxDepth, sb)
            }
        }
        if (depth == 0) {
            HookEnv.base.log(Log.INFO, TAG, "$name: ═══ Subtree ═══\n$sb")
        }
    }

    private fun rootId(v: View): Int = v.id

    override fun init() {
        // onAttachedToWindow：View attach 时立即检查
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

        // onResume 补扫
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // 果园皮肤
        if (Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
            hookOrchardSkin()
        }
    }

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

                        HookEnv.base.log(Log.WARN, TAG,
                            "$name: onResume, cleanupIds=${getCleanupIdSet(decor)}")

                        // 诊断：首次搜索"清理"/"卸载"相关文本，找到卡片容器
                        if (!diagDone) {
                            diagDone = true
                            // 延迟 1 秒等 View 全部加载完再搜索
                            mainHandler.postDelayed({
                                runCatching {
                                    HookEnv.base.log(Log.WARN, TAG, "$name: === 搜索'清理'相关 View ===")
                                    searchForText(decor, "清理")
                                    HookEnv.base.log(Log.WARN, TAG, "$name: === 搜索'卸载'相关 View ===")
                                    searchForText(decor, "卸载")
                                    // 也搜一下手机清理卡片内部的特征文本
                                    HookEnv.base.log(Log.WARN, TAG, "$name: === 搜索'禁用'相关 View ===")
                                    searchForText(decor, "禁用")

                                    // dump 已知 cleanup view 的父链
                                    val cleanupIds = getCleanupIdSet(decor)
                                    cleanupIds.forEach { targetId ->
                                        findViewById(decor, targetId)?.let { v ->
                                            HookEnv.base.log(Log.WARN, TAG,
                                                "$name: === $targetId 的父容器链 ===")
                                            printParentChain(v, 7)
                                        }
                                    }
                                }
                            }, 1000L)
                        }

                        scanTree(decor, 0)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 300L)
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

    private fun findViewById(root: View, targetId: Int, depth: Int = 0): View? {
        if (depth > 12) return null
        if (root.id == targetId) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewById(root.getChildAt(i) ?: continue, targetId, depth + 1)
                if (found != null) return found
            }
        }
        return null
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
        if (root.id in targetIds) hide(root)
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            deepFindAndHide(root.getChildAt(i) ?: continue, targetIds, depth + 1)
        }
    }

    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) {
                val idName = runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull() ?: v.id.toString()
                HookEnv.base.log(Log.WARN, TAG, "$name: ★ hiding $idName (${v.javaClass.simpleName})")
                hide(v)
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
