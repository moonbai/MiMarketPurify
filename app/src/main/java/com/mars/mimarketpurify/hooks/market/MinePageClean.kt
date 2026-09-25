package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

object MinePageClean : BaseHook() {
    override val prefKey: String? = null
    override val name: String = "我的页面元素屏蔽"

    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val pendingRescanTasks = mutableListOf<Runnable>()

    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")
    private val mineCleanupIds = listOf("phone_clear_layout", "mine_uninstall_app_layout")
    private val mineCleanupTitleIds = listOf("phone_clear_title")
    private val mineSummaryIds = listOf(
        "mine_avatar", "mine_nickname", "mine_message", "mine_message_layout",
        "mine_favorites", "mine_favorites_count", "mine_favorites_layout",
        "mine_favorites_arrow", "mine_message_arrow", "mine_summary_root"
    )
    private val mineSecurityIds = listOf("mineSecurityView")
    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())

    private fun idSet(v: View, key: String, names: List<String>): Set<Int> {
        resolved[key]?.let { return it }
        val set = resolveIds(v, names)
        resolved[key] = set
        return set
    }

    private fun resolveIds(v: View, names: List<String>): Set<Int> {
        return names.mapNotNull { n ->
            val id = runCatching {
                v.resources.getIdentifier(n, "id", "com.xiaomi.market")
            }.getOrNull()
            if (id != null && id > 0) id else null
        }.toSet()
    }

    internal fun getCleanupIdSet(v: View): Set<Int> = idSet(v, "cleanup", mineCleanupIds)
    internal fun getCleanupTitleIdSet(v: View): Set<Int> = idSet(v, "cleanup_title", mineCleanupTitleIds)

    internal fun getResourceName(v: View?): String {
        return if (v == null) "null" else runCatching {
            v.resources.getResourceEntryName(v.id)
        }.getOrNull() ?: "id=${v.id}"
    }

    internal fun hide(v: View) {
        runCatching {
            v.visibility = View.GONE
            v.layoutParams?.let { lp ->
                lp.height = 0
                v.layoutParams = lp
            }
        }
    }

    internal fun findViewById(root: View, targetId: Int, depth: Int = 0): View? {
        if (depth > 20) return null
        if (root.id == targetId) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i) ?: continue
                val found = findViewById(child, targetId, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    internal fun findViewByResName(root: View, resName: String): View? {
        val targetId = runCatching {
            root.resources.getIdentifier(resName, "id", "com.xiaomi.market")
        }.getOrNull() ?: return null
        if (targetId <= 0) return null
        return findViewById(root, targetId)
    }

    private fun findParentByStep(view: View, step: Int): View? {
        var curr: View? = view
        repeat(step) {
            curr = (curr?.parent as? View) ?: return@repeat
        }
        return curr
    }

    private fun isMineTarget(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false

        if (Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)
            && id in idSet(v, "recommend", mineRecommendIds)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)
            && id in idSet(v, "tab", mineTabIds)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
            if (id in getCleanupIdSet(v)) return true
            if (id in getCleanupTitleIdSet(v)) {
                findParentByStep(v, 3)?.let { hide(it) }
                return true
            }
        }
        if (Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)
            && id in idSet(v, "summary", mineSummaryIds)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_SECURITY, true)
            && id in idSet(v, "security", mineSecurityIds)) return true
        return false
    }

    private fun isFeaturedTarget(v: View): Boolean {
        if (v !is TextView) return false
        if (!Settings.isEnabled(Settings.KEY_DETAIL_FEATURED, true)) return false
        val text = v.text?.toString()?.trim() ?: return false
        if (text !in featuredTexts) return false
        return v.context?.javaClass?.name?.contains("AppDetailActivity") == true
    }

    internal fun scanTree(v: View, depth: Int) {
        if (depth > 8) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtLeast(0).coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            when {
                isMineTarget(v) || isFeaturedTarget(v) -> hide(v)
            }
        }
    }

    override fun init() {
        hookVisibilityMonitor()
        hookViewAttach() // ✅补回 onAttachedToWindow 钩子！
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")
    }

    private fun hookVisibilityMonitor() {
        if (!Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) return
        runCatching {
            val viewCls = ClassUtil.loadClass("android.view.View")
            viewCls?.methodFinder()
                ?.filterByName("setVisibility")
                ?.first()
                ?.hooked {
                    val view = thisObject as? View ?: return@hooked proceed()
                    val result = proceed()
                    if (view.visibility == View.VISIBLE) {
                        val id = view.id
                        if (id > 0 && id in getCleanupIdSet(view)) {
                            view.post { hide(view) }
                        }
                    }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 挂钩失败", it)
        }
    }

    // ✅【新增补回】onAttachedToWindow，每一个View加载到窗口立刻执行inspect，修复mineSecurityView/mine_middle_menu_container不隐藏
    private fun hookViewAttach() {
        runCatching {
            val viewCls = ClassUtil.loadClass("android.view.View")
            viewCls?.methodFinder()
                ?.filterByName("onAttachedToWindow")
                ?.first()
                ?.hooked {
                    val result = proceed()
                    val v = thisObject as? View
                    v?.let { runCatching { inspect(it) } }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 挂钩失败", it)
        }
    }

    private fun hookActivityRescan(className: String) {
        runCatching {
            val actCls = ClassUtil.loadClass(className)
            actCls?.methodFinder()
                ?.filterByName("onResume")
                ?.forEach { m ->
                    m.hooked {
                        val result = proceed()
                        val decor = (thisObject as? Activity)?.window?.decorView ?: return@hooked result

                        val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
                        val recommendOn = Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)
                        val tabOn = Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)
                        val summaryOn = Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)
                        val securityOn = Settings.isEnabled(Settings.KEY_MINE_SECURITY, true)

                        if (!cleanupOn && !recommendOn && !tabOn && !summaryOn && !securityOn) {
                            return@hooked result
                        }

                        pendingRescanTasks.forEach { mainHandler.removeCallbacks(it) }
                        pendingRescanTasks.clear()

                        fun schedule(delay: Long, block: () -> Unit) {
                            val r = Runnable { runCatching { block() } }
                            pendingRescanTasks += r
                            mainHandler.postDelayed(r, delay)
                        }

                        scanTree(decor, 0)
                        schedule(300L) { scanTree(decor, 0) }
                        schedule(800L) { scanTree(decor, 0) }

                        if (cleanupOn) {
                            schedule(1500L) {
                                getCleanupIdSet(decor).forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    if (v.visibility == View.VISIBLE) {
                                        debugLog("★ 精确 hide ${getResourceName(v)}")
                                        hide(v)
                                    }
                                }
                            }
                            schedule(3000L) {
                                getCleanupIdSet(decor).forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    if (v.visibility == View.VISIBLE) {
                                        debugLog("⚠ 恢复了! 重新 hide ${getResourceName(v)}")
                                        hide(v)
                                    }
                                }
                            }
                        }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $className，跳过补扫", null)
        }
    }
}
