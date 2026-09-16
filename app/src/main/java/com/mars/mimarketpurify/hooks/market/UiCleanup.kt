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

    // ═══════════════ 五组开关 ID ═══════════════
    private val mineRecommendIds = listOf("mine_ad_container")
    private val mineTabIds = listOf("mine_middle_menu_container")

    // 手机清理和应用卸载卡片容器
    private val mineCleanupIds = listOf(
        "phone_clear_layout",
        "mine_uninstall_app_layout"
    )

    private val mineSummaryIds = listOf(
        "mine_avatar", "mine_nickname", "mine_message", "mine_message_layout",
        "mine_favorites", "mine_favorites_count", "mine_favorites_layout",
        "mine_favorites_arrow", "mine_message_arrow", "mine_summary_root"
    )
    private val mineSecurityIds = listOf("mineSecurityView")
    private val featuredTexts = setOf("精选")

    private val resolved = Collections.synchronizedMap(mutableMapOf<String, Set<Int>>())
    private fun getCleanupIdSet(v: View): Set<Int> = resolve(v, mineCleanupIds)

    // ═══════════════ init ═══════════════
    override fun init() {
        // ── setVisibility 拦截：挡住商店恢复 VISIBLE ──
        // args 是 UnmodifiableList 无法直接修改；
        // 用 post{} 在当前帧结束后重新 hide，确保 View 不会被恢复。
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
            runCatching {
                ClassUtil.loadClass("android.view.View")
                    .methodFinder()
                    .filterByName("setVisibility")
                    .first()
                    .hooked {
                        val view = thisObject as? View ?: return@hooked proceed()
                        val result = proceed()
                        // proceed 后检查：如果 View 又变回 VISIBLE，下一帧重新 hide
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

        // ── 果园皮肤：仅在清理与卸载关闭时才修正更新卡片 ──
        if (Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)
            && !Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)) {
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
                                // 只清除背景，保留 layout params（margin/padding）
                                view.background = null
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

                        // 扫描：只隐藏目标 View 本身，不向上追溯父容器
                        scanTree(decor, 0)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 300L)
                        mainHandler.postDelayed({ runCatching { scanTree(decor, 0) } }, 800L)

                        // 精确定位
                        mainHandler.postDelayed({
                            runCatching {
                                getCleanupIdSet(decor).forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    if (v.visibility == View.VISIBLE) {
                                        HookEnv.base.log(Log.WARN, TAG,
                                            "$name: ★ 精确 hide ${getResourceName(v)}")
                                        hide(v)
                                    }
                                }
                            }
                        }, 1500L)

                        // 验证
                        mainHandler.postDelayed({
                            runCatching {
                                getCleanupIdSet(decor).forEach { targetId ->
                                    val v = findViewById(decor, targetId) ?: return@forEach
                                    if (v.visibility == View.VISIBLE) {
                                        HookEnv.base.log(Log.WARN, TAG,
                                            "$name: ⚠ 恢复了! 重新 hide ${getResourceName(v)}")
                                        hide(v)
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
        if (Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
            && id in getCleanupIdSet(v)) return true
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
