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

    private fun getCleanupIdSet(v: View): Set<Int> {
        return resolve(v, mineCleanupIds)
    }

    // ═══════════════ 诊断：dump View 树 ═══════════════
    private var diagDumped = false

    private fun dumpViewTree(root: View, depth: Int = 0, sb: StringBuilder = StringBuilder()) {
        if (depth > 12) return
        val indent = "  ".repeat(depth)
        val idName = if (root.id > 0) {
            runCatching {
                root.resources.getResourceEntryName(root.id)
            }.getOrNull() ?: "unknown(${root.id})"
        } else {
            "NO_ID"
        }
        val cls = root.javaClass.simpleName
        val vis = when (root.visibility) {
            View.VISIBLE -> "V"
            View.INVISIBLE -> "I"
            View.GONE -> "G"
            else -> "?"
        }
        sb.appendLine("$indent[$vis] $cls  id=$idName (${root.id})")

        // 打印与 cleanup 相关的文本信息（帮助定位目标 View）
        if (root is TextView && root.text != null) {
            val txt = root.text.toString().trim()
            if (txt.isNotEmpty() && txt.length < 30) {
                sb.appendLine("$indent  └─ text=\"$txt\"")
            }
        }

        if (root is ViewGroup) {
            val count = root.childCount.coerceAtMost(50)
            for (i in 0 until count) {
                val child = root.getChildAt(i) ?: continue
                dumpViewTree(child, depth + 1, sb)
            }
        }

        // 根节点输出
        if (depth == 0) {
            HookEnv.base.log(Log.INFO, TAG, "$name: ═══ View Dump ═══\n${sb}")
        }
    }

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
                            "$name: onResume 触发, cleanupIds=${getCleanupIdSet(decor)}, settings=" +
                            "recommend=${Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)}, " +
                            "tab=${Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)}, " +
                            "cleanup=${Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)}, " +
                            "summary=${Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)}")

                        // 诊断：首次 dump 完整 View 树
                        if (!diagDumped) {
                            diagDumped = true
                            dumpViewTree(decor)
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
