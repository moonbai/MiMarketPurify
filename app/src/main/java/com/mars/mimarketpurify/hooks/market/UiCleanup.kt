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

    // ═══════════════ ID分组（新增 phone_clear_title 用于特征兜底） ═══════════════
    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")
    private val mineCleanupIds = listOf("phone_clear_layout")
    private val mineCleanupTitleIds = listOf("phone_clear_title") // 标题特征兜底
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
    private fun getRecommendIdSet(v: View): Set<Int> = idSet(v, "recommend", mineRecommendIds)
    private fun getTabIdSet(v: View): Set<Int> = idSet(v, "tab", mineTabIds)
    private fun getSummaryIdSet(v: View): Set<Int> = idSet(v, "summary", mineSummaryIds)
    private fun getSecurityIdSet(v: View): Set<Int> = idSet(v, "security", mineSecurityIds)

    // ═══════════════ init ═══════════════
    override fun init() {
        // ── setVisibility：无条件挂载，内部实时读全部开关，前置拦截（不再只绑 KEY_MINE_CLEANUP） ──
        runCatching {
            ClassUtil.loadClass("android.view.View")
                .methodFinder()
                .filterByName("setVisibility")
                .first()
                .hooked {
                    val view = thisObject as? View ?: return@hooked proceed()
                    val targetVis = args[0] as? Int ?: return@hooked proceed()

                    val shouldHide = runCatching { isMineTarget(view) || isFeaturedTarget(view) }.getOrDefault(false)
                    if (shouldHide && targetVis == View.VISIBLE) {
                        args[0] = View.GONE
                    }
                    val result = proceed()
                    // 兜底防商店强制刷回
                    if (shouldHide && view.visibility == View.VISIBLE) {
                        view.post { hide(view) }
                    }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility 挂钩失败", it)
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

        // ── 升级卡片展开 ──
        if (Settings.isEnabled(Settings.KEY_CARD_EXPAND, false)) {
            hookCardExpand()
        }

        // ── onResume 补扫 ──
        hookActivityRescan("com.xiaomi.market.business_ui.main.MarketTabActivity")
        hookActivityRescan("com.xiaomi.market.ui.detail.AppDetailActivityInner")

        // ✅【修复果园互斥】不再这里提前判断开关！无条件挂方法Hook，内部实时读取
        hookOrchardSkin()
    }

    // ═══════════════ 升级卡片展开 ═══════════════
    private fun hookCardExpand() {
        runCatching {
            val cls = ClassUtil.loadClass("com.xiaomi.market.business_ui.main.mine.view.MineUpdateView")
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
                                    // 简单防重复点击：已经展开就跳过（用tag标记）
                                    if (view.getTag(R.id.auto_tag_mark) == null) {
                                        (arrow ?: header)?.performClick()
                                        view.setTag(R.id.auto_tag_mark, true)
                                    }
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

    // 临时TagId（内联避免新增资源，用一个安全高位值）
    private val R.auto_tag_mark: Int get() = 0x7fffffff

    private fun findViewByResName(root: View, resName: String): View? {
        val targetId = runCatching {
            root.resources.getIdentifier(resName, "id", "com.xiaomi.market")
        }.getOrNull() ?: return null
        if (targetId <= 0) return null
        return findViewById(root, targetId)
    }

    // ═══════════════ ✅【修复果园皮肤】移除静态互斥，运行时实时判断开关 ──
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
                            // 👉 这里实时读：不再和 KEY_MINE_CLEANUP 硬绑定
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
                        val decor = (thisObject as? Activity)?.window?.decorView ?: return@hooked result

                        val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
                        val recommendOn = Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true)
                        val tabOn = Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true)
                        val summaryOn = Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false)
                        val securityOn = Settings.isEnabled(Settings.KEY_MINE_SECURITY, true)

                        HookEnv.base.log(Log.INFO, TAG,
                            "$name: onResume: cleanup=$cleanupOn recommend=$recommendOn " +
                            "tab=$tabOn summary=$summaryOn security=$securityOn")

                        if (!cleanupOn && !recommendOn && !tabOn && !summaryOn && !securityOn) {
                            return@hooked result
                        }

                        scanTree(decor, 0)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 300L)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 800L)

                        if (cleanupOn) {
                            mainHandler.postDelayed({
                                runCatching {
                                    getCleanupIdSet(decor).forEach { targetId ->
                                        val v = findViewById(decor, targetId) ?: return@forEach
                                        if (v.visibility == View.VISIBLE) {
                                            HookEnv.base.log(Log.WARN, TAG, "$name: ★ 精确 hide ${getResourceName(v)}")
                                            hide(v)
                                        }
                                    }
                                }
                            }, 1500L)
                            mainHandler.postDelayed({
                                runCatching {
                                    getCleanupIdSet(decor).forEach { targetId ->
                                        val v = findViewById(decor, targetId) ?: return@forEach
                                        if (v.visibility == View.VISIBLE) {
                                            HookEnv.base.log(Log.WARN, TAG, "$name: ⚠ 恢复了! 重新 hide ${getResourceName(v)}")
                                            hide(v)
                                        }
                                    }
                                }
                            }, 3000L)
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

    // ═══════════════ 遍历 ═══════════════
    private fun scanTree(v: View, depth: Int) {
        if (depth > 10) return // 适度放宽深度应对新版嵌套
        inspect(v)
        if (v !is ViewGroup) return
        val count = v.childCount.coerceAtLeast(0).coerceAtMost(40)
        for (i in 0 until count) {
            scanTree(v.getChildAt(i) ?: continue, depth + 1)
        }
    }

    // ═══════════════ 判断 & 隐藏（✅ 新增：phone_clear_title 向上回溯兼容逻辑） ═══════════════
    private fun inspect(v: View) {
        if (v.visibility != View.VISIBLE) return
        runCatching {
            if (isMineTarget(v) || isFeaturedTarget(v)) {
                hide(v)
            }
        }
    }

    private fun isMineTarget(v: View): Boolean {
        val id = v.id
        if (id == View.NO_ID || id <= 0) return false

        if (Settings.isEnabled(Settings.KEY_MINE_RECOMMEND, true) && id in getRecommendIdSet(v)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_OFFICIAL_TAB, true) && id in getTabIdSet(v)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
            // 命中主布局ID → 直接隐藏
            if (id in getCleanupIdSet(v)) return true
            // ✅ 兼容方案：命中 phone_clear_title → 向上最多3层找父容器并标记为目标
            if (id in getCleanupTitleIdSet(v)) {
                val rootCandidate = findParentByStep(v, 3)
                rootCandidate?.let { hide(it) }
                return true
            }
        }
        if (Settings.isEnabled(Settings.KEY_MINE_SUMMARY, false) && id in getSummaryIdSet(v)) return true
        if (Settings.isEnabled(Settings.KEY_MINE_SECURITY, true) && id in getSecurityIdSet(v)) return true

        return false
    }

    // 向上回溯N层父View，用于标题残留兜底
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
