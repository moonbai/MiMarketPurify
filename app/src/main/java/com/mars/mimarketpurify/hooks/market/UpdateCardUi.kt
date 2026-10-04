package com.mars.mimarketpurify.hooks.market

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.app.Activity
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

object UpdateCardUi : BaseHook() {
    override val prefKey: String? = null
    override val name: String = "更新卡片UI调整"

    private const val UPDATE_BTN_COLOR = 0xFF0DAE73.toInt()
    private const val UPDATE_BTN_COLOR_NIGHT = 0xFF0F9B68.toInt()
    private const val TITLE_WHITE = 0xFFFFFFFF.toInt()

    private val originalBtnTint = java.util.WeakHashMap<View, ColorStateList?>()
    private val originalTextColor = java.util.WeakHashMap<TextView, Int>()

    private var liveUpdateView = WeakReference<View?>(null)

    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.KEY_ORCHARD_SKIN) {
            liveUpdateView.get()?.post {
                val rootView = liveUpdateView.get() ?: return@post
                applyBtnColorOnly(MinePageClean.findViewByResName(rootView, "update_button_layout"), true)
                applyBtnColorOnly(MinePageClean.findViewByResName(rootView, "update_button_parent_layout"), false)
                refreshAllUpdateButton(rootView)
            }
        }
    }

    override fun init() {
        hookCardExpand()
        hookViewAttachObserver()
        hookActivityConfigChange()
        registerPrefsListener()
    }

    private fun hookActivityConfigChange() {
        runCatching {
            val activityCls = ClassUtil.loadClass("android.app.Activity")
            activityCls?.methodFinder()
                ?.filterByName("onConfigurationChanged")
                ?.first()
                ?.hooked {
                    val newConfig = args[0] as Configuration
                    val oldConfig = thisObject as Activity
                    proceed()
                    val oldNight = oldConfig.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val newNight = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (oldNight != newNight) {
                        oldConfig.window?.decorView?.postDelayed({
                            refreshAllUpdateButton(oldConfig.window?.decorView)
                        }, 100)
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: hookActivityConfigChange失败", it)
        }
    }

    private fun registerPrefsListener() {
        runCatching {
            val prefs = (HookEnv.base as XposedModule).getRemotePreferences(Settings.PREFS_GROUP)
            prefs.registerOnSharedPreferenceChangeListener(settingsListener)
            HookEnv.base.log(Log.INFO, TAG, "$name: 已注册 KEY_ORCHARD_SKIN 变化监听")
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: 注册偏好监听失败", it)
        }
    }

    private fun refreshAllUpdateButton(rootView: View?) {
        rootView ?: return
        val orchardOn = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
        val resName = MinePageClean.getResourceName(rootView)

        if (resName == "update_button_layout") {
            applyBtnColorOnly(rootView, true)
        }
        if (resName == "update_button_parent_layout") {
            applyBtnColorOnly(rootView, false)
        }

        if (rootView is TextView && resName in setOf(
                "mine_app_update_title", "update_empty_text", "mine_update_arrow"
            )
        ) {
            if (!orchardOn) {
                originalTextColor[rootView]?.let { rootView.setTextColor(it); originalTextColor.remove(rootView) }
            } else {
                if (originalTextColor[rootView] == null) originalTextColor[rootView] = rootView.currentTextColor
                val isNight = rootView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                if (isNight) rootView.setTextColor(TITLE_WHITE)
            }
        }

        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                refreshAllUpdateButton(rootView.getChildAt(i))
            }
        }
    }

    private fun hookViewAttachObserver() {
        runCatching {
            val viewCls = ClassUtil.loadClass("android.view.View")
            viewCls?.methodFinder()
                ?.filterByName("onAttachedToWindow")
                ?.first()
                ?.hooked {
                    val result = proceed()
                    val v = thisObject as? View ?: return@hooked result
                    val resName = MinePageClean.getResourceName(v)

                    if (resName == "update_button_layout") {
                        applyBtnColorOnly(v, true)
                    }
                    if (resName == "update_button_parent_layout") {
                        applyBtnColorOnly(v, false)
                    }

                    if (v is TextView && resName in setOf(
                            "mine_app_update_title", "update_empty_text", "mine_update_arrow"
                        )
                    ) {
                        val orchardOn = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
                        if (!orchardOn) {
                            originalTextColor[v]?.let { v.setTextColor(it); originalTextColor.remove(v) }
                        } else {
                            if (originalTextColor[v] == null) originalTextColor[v] = v.currentTextColor
                            val isNight = v.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                            if (isNight) v.setTextColor(TITLE_WHITE)
                        }
                    }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 挂钩失败", it)
        }
    }

    /**
     * 升级卡片横向展开。
     *
     * 展开功能仅依赖 [Settings.KEY_CARD_EXPAND] 开关本身，独立于「清理与卸载」与「更新卡片背景」。
     * 是否显示并允许开启该开关，由 UI 层（SubSettingsActivity）按双前置条件 gate；
     * 这里只校验展开开关，不再重复校验前置，避免前置状态变化（如仅关闭其一）时误判导致展开失效。
     */
    private fun hookCardExpand() {
        runCatching {
            val cls = ClassUtil.loadClass("com.xiaomi.market.business_ui.main.mine.view.MineUpdateView")
            cls?.methodFinder()
                ?.filterByName("onFinishInflate")
                ?.forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? View)?.let { view ->
                            liveUpdateView = WeakReference(view)
                            view.post {
                                runCatching {
                                    val expandOn = Settings.isEnabled(Settings.KEY_CARD_EXPAND, false)
                                    if (!expandOn) return@runCatching
                                    val header = MinePageClean.findViewByResName(view, "expand_collapse_header")
                                    val arrow = MinePageClean.findViewByResName(view, "expand_arrow")
                                    (arrow ?: header)?.performClick()
                                    view.postDelayed({ flattenUpdateIcons(view) }, 120)
                                }
                            }
                            result
                        }
                    }
                }
        }.onFailure {
            HookEnv.base.log(Log.VERBOSE, TAG, "$name: 无 MineUpdateView，跳过升级卡片展开", null)
        }
    }

    private fun flattenUpdateIcons(root: View) {
        val icons = MinePageClean.findViewByResName(root, "update_icon_layout") as? ViewGroup ?: return
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

        val btnParent = MinePageClean.findViewByResName(root, "update_button_parent_layout")
        val btnLayout = MinePageClean.findViewByResName(root, "update_button_layout")

        applyBtnColorOnly(btnLayout, true)
        applyBtnColorOnly(btnParent, false)
        root.postDelayed({ applyBtnColorOnly(btnLayout, true); applyBtnColorOnly(btnParent, false) }, 150)
        root.postDelayed({ applyBtnColorOnly(btnLayout, true); applyBtnColorOnly(btnParent, false) }, 400)
        root.postDelayed({ applyBtnColorOnly(btnLayout, true); applyBtnColorOnly(btnParent, false) }, 800)

        refreshAllUpdateButton(root)
    }

    fun applyBtnColorOnly(view: View?, modifyPadding: Boolean) {
        view ?: return
        val orchardOn = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
        if (!orchardOn) {
            view.background?.setTintList(originalBtnTint[view])
            originalBtnTint.remove(view)
            return
        }
        if (!originalBtnTint.containsKey(view)) {
            originalBtnTint[view] = readTintList(view.background)
        }

        val isNight = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val color = if (isNight) UPDATE_BTN_COLOR_NIGHT else UPDATE_BTN_COLOR
        val csl = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(shade(color, 0.85f), color)
        )
        view.background?.setTintList(csl)
    }

    private fun readTintList(drawable: android.graphics.drawable.Drawable?): ColorStateList? {
        return runCatching {
            drawable?.javaClass?.getMethod("getTintList")?.invoke(drawable) as? ColorStateList
        }.getOrNull()
    }

    private fun shade(color: Int, factor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = (((color shr 16) and 0xFF) * factor).toInt()
        val g = (((color shr 8) and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
