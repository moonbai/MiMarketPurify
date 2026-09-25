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

    /**
     * 尚未执行的补扫任务。每次 onResume 会先取消上一批任务再重新调度，
     * 防止快速进出页面时 Handler 上堆积大量 300/800/1500/3000ms 的重复扫描。
     * （同一时刻至多一个前台 Activity，全局一份即可；全部操作都在主线程。）
     */
    private val pendingRescanTasks = mutableListOf<Runnable>()

    // ═══════════════ 五组开关 ID ═══════════════
    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")
    private val mineCleanupIds = listOf("phone_clear_layout", "mine_uninstall_app_layout")
    private val mineCleanupTitleIds = listOf("phone_clear_title") // 新增：标题锚点用于兼容
    private val mineSummaryIds = listOf(
        "mine_avatar", "mine_nickname", "mine_message", "mine_message_layout",
        "mine_favorites", "mine_favorites_count", "mine_favorites_layout",
        "mine_favorites_arrow", "mine_message_arrow", "mine_summary_root"
    )
    private val mineSecurityIds = listOf("mineSecurityView")
    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())
    private fun getCleanupIdSet(v: View): Set<Int> = idSet(v, "cleanup", mineCleanupIds)
    private fun getCleanupTitleIdSet(v: View): Set<Int> = idSet(v, "cleanup_title", mineCleanupTitleIds)

    // ═══════════════ init ═══════════════
    override fun init() {
        // ── setVisibility 拦截 ──
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
            runCatching {
                ClassUtil.loadClass("android.view.View")
                    .methodFinder()
                    .filterByName("setVisibility")
                    .first()
                    .hooked {
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

        // ── onAttachedToWindow ──
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

        // ── 升级卡片展开（完全未改动） ──
        if (Settings.isEnabled(Settings.KEY_CARD_EXPAND, false)) {
            hookCardExpand()
        }

        // ── onResume 补扫（完全未改动） ──
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // ── 果园皮肤：移除 init 层开关互斥，总是挂；逻辑内实时判断 ──
        hookOrchardSkin()
    }

    // ═══════════════ 升级卡片展开（原样保留） ═══════════════
    private fun hookCardExpand() {
        runCatching {
            val cls = ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView")
            cls.methodFinder()
                .filterByName("onFinishInflate")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? View)?.let { view ->
                            view.post {
                                runCatching {
                                    val header = findViewByResName(view, "expand_collapse_header")
                                    val arrow = findViewByResName(view, "expand_arrow")
                                    (arrow ?: header)?.performClick()
                                }
                            }
                        }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 MineUpdateView，跳过升级卡片展开", null)
        }
    }

    private fun findViewByResName(root: View, resName: String): View? {
        val targetId = runCatching {
            root.resources.getIdentifier(resName, "id", "com.xiaomi.market")
        }.getOrNull() ?: return null
        if (targetId <= 0) return null
        return findViewById(root, targetId)
    }

    // ═══════════════ 果园皮肤：hook Resources.getDrawable 从源头返回透明 ═══════════════
    private val orchardDrawableNames = setOf(
        "mine_update_orchard_bg",
        "mine_update_orchard_tree_bg"
    )
    /** 已解析出的目标资源 id（懒解析，避免每次 getDrawable 都查 entryName） */
    private val orchardDrawableIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private var orchardIdsResolved = false

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
        // 1) 保留原有 apply* 方法 hook 作为兜底（清掉已设上的背景）
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls.methodFinder().filterByName(method).forEach { m ->
                        m.hooked {
                            val result = proceed()
                            if (Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
                                (thisObject as? View)?.let { view -> view.background = null }
                            }
                            result
                        }
                    }
                }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过果园皮肤处理", null)
            }
        }

        // 2) hook Resources.getDrawable：请求果园背景图时返回透明
        runCatching {
            ClassUtil.loadClass("android.content.res.Resources")
                .methodFinder()
                .filterByName("getDrawable")
                .forEach { m ->
                    m.hooked {
                        val result = proceed()
                        if (!Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
                            return@hooked result
                        }
                        val res = thisObject as? android.content.res.Resources
                            ?: return@hooked result
                        val id = m.parameterTypes
                            .indexOfFirst { it == Int::class.javaPrimitiveType }
                            .takeIf { it >= 0 }
                            ?.let { args[it] as? Int }
                            ?: return@hooked result
                        if (id <= 0) return@hooked result

                        // 首次调用时解析目标资源 id
                        if (!orchardIdsResolved) {
                            orchardDrawableIds.clear()
                            orchardDrawableNames.forEach { name ->
                                runCatching {
                                    val rid = res.getIdentifier(name, "drawable", "com.xiaomi.market")
                                    if (rid > 0) orchardDrawableIds.add(rid)
                                }
                            }
                            orchardIdsResolved = true
                        }

                        if (id in orchardDrawableIds) {
                            // 返回透明 drawable，不影响其他资源
                            return@hooked android.graphics.drawable.ColorDrawable(0)
                        }
                        result
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: hook Resources.getDrawable 失败", it)
        }
    }


    // ═══════════════ onResume 补扫（任务去重版） ═══════════════
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

                        val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
                        val recommendOn = Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)
                        val tabOn = Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)
                        val summaryOn = Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)
                        val securityOn = Settings.isEnabled(Settings.KEY_MINE_SECURITY, true)

                        if (!cleanupOn && !recommendOn && !tabOn && !summaryOn && !securityOn) {
                            return@hooked result
                        }

                        // 取消该 Activity 尚未执行的旧补扫任务，再重新调度：
                        // 快速进出页面时只保留最新一轮扫描，避免 Handler 任务堆积。
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

    // ═══════════════ 遍历（原样保留） ═══════════════
    private fun scanTree(v: View, depth: Int) {
        if (depth > 8) return
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtLeast(0).coerceAtMost(32)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    // ═══════════════ 判断 & 隐藏：增加 phone_clear_title 向上回溯兼容 ═══════════════
    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            when {
                isMineTarget(v) || isFeaturedTarget(v) -> hide(v)
                // 果园皮肤 View 层兜底：apply* 方法 hook 失效/改名时，
                // 挂载或补扫到升级卡片直接把背景清掉（不隐藏卡片本身）
                isOrchardTarget(v) -> v.background = null
            }
        }
    }

    /** 升级卡片的果园背景：按类名兜底清除，开关实时判断 */
    private fun isOrchardTarget(v: View): Boolean {
        if (!Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) return false
        val n = v.javaClass.name
        return n.contains("MineUpdateView") || n.contains("MineUpdateLayout")
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
            // ✅ 标题锚点兜底：ID变了还能抓到父容器
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

    // 新增：向上找 N 层父布局
    private fun findParentByStep(view: View, step: Int): View? {
        var curr: View? = view
        repeat(step) {
            curr = (curr?.parent as? View) ?: return@repeat
        }
        return curr
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
