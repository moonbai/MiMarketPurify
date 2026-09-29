package com.mars.mimarketpurify.hooks.market

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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

    private const val CARD_RADIUS_DP = 16f
    private const val UPDATE_BTN_COLOR = 0xFF0DAE73.toInt()       // 日间按钮色
    private const val UPDATE_BTN_COLOR_NIGHT = 0xFF0F9B68.toInt() // 夜间适配（略暗）
    private const val BTN_RADIUS_DP = 24f
    private const val UPDATE_BTN_MARGIN_HORIZONTAL_DP = 12f
    private const val TITLE_WHITE = 0xFFFFFFFF.toInt() // 白色

    /** 「更新卡片背景」开关关闭时，用于还原按钮/文字的原始样式 */
    private val originalBtnBg = java.util.WeakHashMap<View, Drawable?>()
    private val originalTextColor = java.util.WeakHashMap<TextView, Int>()
    /** 缓存按钮原始 padding（仅内层 update_button_layout 会改小高度），关闭开关时一并还原 */
    private val originalBtnPadding = java.util.WeakHashMap<View, IntArray>()

    /** 当前更新卡片（MineUpdateView）实例，用于开关变化时实时重绘 */
    private var liveUpdateView = WeakReference<View?>(null)

    /** 监听「更新卡片背景」开关变化：关闭后即时把按钮/文字还原为官方样式，无需重启/重进页面 */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.KEY_ORCHARD_SKIN) {
            liveUpdateView.get()?.post { refreshAllUpdateButton(liveUpdateView.get()) }
        }
    }

    override fun init() {
        hookCardExpand()
        hookViewAttachObserver()
        hookActivityConfigChange() // 监听深色模式切换
        registerPrefsListener()    // 监听「更新卡片背景」开关变化，实时还原按钮/文字
    }

    // 监听Activity配置变更(深色/浅色切换)，触发重绘按钮+标题文字
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

    /** 注册「更新卡片背景」开关的偏好变化监听；LSPosed 远程偏好变更会跨进程通知已注册的监听。 */
    private fun registerPrefsListener() {
        runCatching {
            val prefs = (HookEnv.base as XposedModule).getRemotePreferences(Settings.PREFS_GROUP)
            prefs.registerOnSharedPreferenceChangeListener(settingsListener)
            HookEnv.base.log(Log.INFO, TAG, "$name: 已注册 KEY_ORCHARD_SKIN 变化监听")
        }.onFailure {
            // 监听不可用时不致命：用户开关后重进「我的」页（触发 onAttachedToWindow）仍可正确还原
            HookEnv.base.log(Log.WARN, TAG, "$name: 注册偏好监听失败，开关变化需重进我的页生效", it)
        }
    }

    // 递归遍历View树：同时处理按钮背景、标题、empty文字、箭头
    private fun refreshAllUpdateButton(rootView: View?) {
        rootView ?: return
        val resName = MinePageClean.getResourceName(rootView)

        // 1. 一键升级按钮
        if (resName == "update_button_layout") {
            applyBtnColorOnly(rootView, true) // 内层按钮：设置背景 + 还原官方高度
        }
        if (resName == "update_button_parent_layout") {
            applyBtnColorOnly(rootView, false) // 外层容器：仅上色，不修改padding
        }

        // 2~4. 标题 / empty 文字 / 箭头文字：受「更新卡片背景」开关控制
        if (rootView is TextView && resName in setOf(
                "mine_app_update_title", "update_empty_text", "mine_update_arrow"
            )
        ) {
            val on = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
            if (!on) {
                // 开关关闭：还原原始文字颜色
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

                    // 按钮
                    if(resName == "update_button_layout"){
                        applyBtnColorOnly(v, true)
                    }
                    if(resName == "update_button_parent_layout"){
                        applyBtnColorOnly(v, false)
                    }

                    // 文本控件：受「更新卡片背景」开关控制
                    if (v is TextView && resName in setOf(
                            "mine_app_update_title", "update_empty_text", "mine_update_arrow"
                        )
                    ) {
                        val on = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
                        if (!on) {
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

    private fun hookCardExpand() {
        // 注意：不在此处做安装期开关判断（过去在 init 阶段就 return，导致用户在设置页
        // 打开「升级卡片横向展开」后已运行的会话无法重新挂钩、开关形同虚设）。
        // 改为每次 onFinishInflate 时实时读取开关（与 BaseHook.hooked 的实时 enabled() 原则一致），
        // 打开/关闭开关后无需重启应用商店即可生效。
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
                                    val cleanupOn = Settings.isEnabled(Settings.KEY_MINE_CLEANUP, true)
                                    val orchardOn = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
                                    if (!expandOn || !cleanupOn || !orchardOn) return@runCatching

                                    val header = MinePageClean.findViewByResName(view, "expand_collapse_header")
                                    val arrow = MinePageClean.findViewByResName(view, "expand_arrow")
                                    (arrow ?: header)?.performClick()
                                    view.postDelayed({ flattenUpdateIcons(view) }, 120)
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

        // 同时刷新所有文本颜色
        refreshAllUpdateButton(root)

        HookEnv.base.log(Log.INFO, TAG, "btnParent=$btnParent, btnLayout=$btnLayout")
    }

    /**
     * 一键升级按钮样式：绿色胶囊背景，保留官方原始高度。
     * 受「更新卡片背景」开关（[Settings.KEY_ORCHARD_SKIN]）控制：
     *  - 关闭时还原原始背景与 padding；
     *  - 开启时按日间/夜间取色，并用 StateListDrawable 保留按下反馈。
     *
     * @param modifyPadding true=参与 padding 还原（仅内层 update_button_layout）；false=外层容器不变
     */
    fun applyBtnColorOnly(view: View?, modifyPadding: Boolean) {
        view ?: return
        val on = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
        val original = originalBtnBg[view]
        if (!on) {
            // 开关关闭：还原原始背景与 padding（若曾改过），呈现官方效果
            if (original != null) {
                view.background = original
                originalBtnBg.remove(view)
            }
            originalBtnPadding[view]?.let { p ->
                view.setPadding(p[0], p[1], p[2], p[3])
                originalBtnPadding.remove(view)
            }
            return
        }
        // 首次见到该 View 时缓存商店原始背景与 padding，便于关闭开关后还原
        if (original == null) {
            originalBtnBg[view] = view.background
            if (modifyPadding) {
                originalBtnPadding[view] = intArrayOf(
                    view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom
                )
            }
        }

        val density = view.resources.displayMetrics.density
        val isNight = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val color = if (isNight) UPDATE_BTN_COLOR_NIGHT else UPDATE_BTN_COLOR

        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = BTN_RADIUS_DP * density
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(shade(color, 0.85f))
            cornerRadius = BTN_RADIUS_DP * density
        }
        val bg = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        view.background = bg

        // 保留按钮原始高度：不再把垂直 padding 压到 6dp，否则「更新卡片背景」开启后
        // 一键升级按钮会被压扁、与上方的应用图标贴在一起。内层按钮（modifyPadding=true）
        // 还原官方 padding；外层容器（modifyPadding=false）本就不改高度，保持原样。
        if (modifyPadding) {
            originalBtnPadding[view]?.let { p ->
                view.setPadding(p[0], p[1], p[2], p[3])
            }
        }

        runCatching {
            val setTint = view::class.java.getDeclaredMethod(
                "setBackgroundTintList",
                android.content.res.ColorStateList::class.java
            )
            setTint.isAccessible = true
            setTint.invoke(view, null)
        }
    }

    /** 颜色按比例变暗，用于按下态 */
    private fun shade(color: Int, factor: Float): Int {
        val a = (color shr 24) and 0xFF
        val r = (((color shr 16) and 0xFF) * factor).toInt()
        val g = (((color shr 8) and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
