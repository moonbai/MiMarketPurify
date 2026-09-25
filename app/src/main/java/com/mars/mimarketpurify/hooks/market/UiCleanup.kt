package com.mars.mimarketpurify.hooks.market

import android.app.Activity
import android.graphics.drawable.GradientDrawable
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

    private val pendingRescanTasks = mutableListOf<Runnable>()

    // ═══════════════ 五组开关 ID ═══════════════
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
    private fun getCleanupIdSet(v: View): Set<Int> = idSet(v, "cleanup", mineCleanupIds)
    private fun getCleanupTitleIdSet(v: View): Set<Int> = idSet(v, "cleanup_title", mineCleanupTitleIds)

    // ================= 常量定义：圆角 & 颜色 =================
    // 更新卡片背景圆角 dp
    private const val CARD_RADIUS_DP = 16f
    // 一键更新按钮颜色 #FF0DAE73
    private const val UPDATE_BTN_COLOR = 0xFF0DAE73.toInt()
    private const val BTN_RADIUS_DP = 12f

    // ═══════════════ init ═══════════════
    override fun init() {
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

        hookCardExpand()

        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        hookOrchardSkin()
    }

    // ═══════════════ 升级卡片展开 ═══════════════
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
                                    val expandOn = Settings.isEnabled(Settings.KEY_CARD_EXPAND, false)
                                    val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
                                    val orchardOn = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
                                    if (!expandOn || !cleanupOn || !orchardOn) return@runCatching

                                    val header = findViewByResName(view, "expand_collapse_header")
                                    val arrow = findViewByResName(view, "expand_arrow")
                                    (arrow ?: header)?.performClick()
                                    view.postDelayed({
                                        runCatching { flattenUpdateIcons(view) }
                                    }, 120)
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

    private fun flattenUpdateIcons(root: View) {
        val icons = findViewByResName(root, "update_icon_layout") as? ViewGroup ?: return
        if (icons is android.widget.GridLayout) {
            icons.columnCount = 4
        }
        for (i in 0 until icons.childCount) {
            val child = icons.getChildAt(i)
            val lp = child.layoutParams ?: continue
            when (lp) {
                is android.widget.LinearLayout.LayoutParams -> {
                    lp.width = 0
                    lp.weight = 1f
                    lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    child.layoutParams = lp
                }
                is android.widget.GridLayout.LayoutParams -> {
                    lp.columnSpec = android.widget.GridLayout.spec(i, 1, 1f)
                    lp.width = 0
                    lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    child.layoutParams = lp
                }
            }
        }
        // ========== 修改一键更新按钮逻辑 ==========
        val btn = findViewByResName(root, "update_button_layout")
        btn?.let { view ->
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                solidColor = UPDATE_BTN_COLOR
                // dp转px
                val density = view.resources.displayMetrics.density
                cornerRadius = BTN_RADIUS_DP * density
            }
            view.background = drawable
        }
    }

    private fun findViewByResName(root: View, resName: String): View? {
        val targetId = runCatching {
            root.resources.getIdentifier(resName, "id", "com.xiaomi.market")
        }.getOrNull() ?: return null
        if (targetId <= 0) return null
        return findViewById(root, targetId)
    }

    // ═══════════════ 果园皮肤 ═══════════════
    private val orchardDrawableNames = setOf(
        "mine_update_orchard_bg",
        "mine_update_orchard_tree_bg",
        "mine_update_dark_bg",
        "mine_update_dark_arrow"
    )
    private val orchardIconNames = setOf(
        "no_update_history",
        "no_update_new"
    )
    private val orchardDrawableIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val orchardIconIds = Collections.synchronizedSet(mutableSetOf<Int>())
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

    private fun isNightResources(res: android.content.res.Resources): Boolean =
        (res.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun getCardBackgroundDrawable(res: android.content.res.Resources, isNight: Boolean): GradientDrawable {
        val bgColor = if (isNight) 0xFF242424.toInt() else 0xFFFFFFFF.toInt()
        val density = res.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            solidColor = bgColor
            cornerRadius = CARD_RADIUS_DP * density
        }
    }

    private fun hookOrchardSkin() {
        // apply* 方法 hook：proceed 即可，背景替换由 getDrawable hook 完成
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls.methodFinder().filterByName(method).forEach { m ->
                        m.hooked { proceed() }
                    }
                }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 $owner，跳过果园皮肤处理", null)
            }
        }

        // hook Resources.getDrawable：命中即替换 drawable，不加载原图
        runCatching {
            ClassUtil.loadClass("android.content.res.Resources")
                .methodFinder()
                .filterByName("getDrawable")
                .forEach { m ->
                    m.hooked {
                        if (!Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
                            return@hooked proceed()
                        }
                        val res = thisObject as? android.content.res.Resources
                            ?: return@hooked proceed()
                        val id = m.parameterTypes
                            .indexOfFirst { it == Int::class.javaPrimitiveType }
                            .takeIf { it >= 0 }
                            ?.let { args[it] as? Int }
                            ?: return@hooked proceed()
                        if (id <= 0) return@hooked proceed()

                        if (!orchardIdsResolved) {
                            orchardDrawableIds.clear()
                            orchardIconIds.clear()
                            orchardDrawableNames.forEach { name ->
                                runCatching {
                                    val rid = res.getIdentifier(name, "drawable", "com.xiaomi.market")
                                    if (rid > 0) orchardDrawableIds.add(rid)
                                }
                            }
                            orchardIconNames.forEach { name ->
                                runCatching {
                                    val rid = res.getIdentifier(name, "drawable", "com.xiaomi.market")
                                    if (rid > 0) orchardIconIds.add(rid)
                                }
                            }
                            orchardIdsResolved = true
                        }

                        if (id in orchardIconIds) {
                            return@hooked android.graphics.drawable.ColorDrawable(0)
                        }
                        if (id in orchardDrawableIds) {
                            // 替换为带圆角的背景，替代原来ColorDrawable
                            return@hooked getCardBackgroundDrawable(res, isNightResources(res))
                        }
                        proceed()
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: hook Resources.getDrawable 失败", it)
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

    private fun scanTree(v: View, depth: Int) {
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
