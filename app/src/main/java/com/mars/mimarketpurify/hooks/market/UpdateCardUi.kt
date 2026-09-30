package com.mars.mimarketpurify.hooks.market

import android.content.SharedPreferences
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

    private const val UPDATE_BTN_COLOR = 0xFF0DAE73.toInt()       // 日间按钮色
    private const val UPDATE_BTN_COLOR_NIGHT = 0xFF0F9B68.toInt() // 夜间适配（略暗）
    private const val TITLE_WHITE = 0xFFFFFFFF.toInt() // 白色

    /** 「更新卡片背景」开关关闭时，用于还原按钮的原始着色 */
    /** 仅缓存官方背景的 tint（着色），按钮的圆角/形状/尺寸/位置/padding 一律保持官方原样，从不替换 background 对象 */
    private val originalBtnTint = java.util.WeakHashMap<View, ColorStateList?>()
    private val originalTextColor = java.util.WeakHashMap<TextView, Int>()

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

        // 1. 一键升级按钮（仅改颜色，圆角/尺寸/位置/padding 保持官方原样）
        if (resName == "update_button_layout") {
            applyBtnColorOnly(rootView, true)
        }
        if (resName == "update_button_parent_layout") {
            applyBtnColorOnly(rootView, false)
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
     * 一键升级按钮样式（仅改颜色，不动形状/圆角/尺寸/位置/padding）：
     *  - 不替换官方 background 对象，仅对其调用 [View.getBackground].setTintList(ColorStateList)，
     *    因此官方背景自带的圆角、形状、宽高、位置、内边距全部保持原样；
     *  - 用 ColorStateList 同时给定「正常态 / 按下态」两档颜色（按下态略深）以保留点击反馈；
     *  - 受「更新卡片背景」开关（[Settings.KEY_ORCHARD_SKIN]）控制，关闭时把 tint 还原为官方。
     *
     * @param modifyPadding 该参数已废弃：本实现不再修改任何 padding，保留仅为兼容既有调用点。
     */
    fun applyBtnColorOnly(view: View?, modifyPadding: Boolean) {
        view ?: return
        val on = Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, true)
        if (!on) {
            // 开关关闭：仅还原我们的着色（缓存值；null 即清除着色回到官方原色），
            // 官方背景的圆角/形状/尺寸/位置/padding 始终不变
            view.background?.setTintList(originalBtnTint[view])
            originalBtnTint.remove(view)
            return
        }
        // 首次见到该 View 时反射缓存商店官方背景着色，便于关闭开关后精确还原
        if (!originalBtnTint.containsKey(view)) {
            originalBtnTint[view] = readTintList(view.background)
        }

        val isNight = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val color = if (isNight) UPDATE_BTN_COLOR_NIGHT else UPDATE_BTN_COLOR
        // 仅着色：保留官方背景的形状与圆角，仅替换颜色；按下态略深以保留点击反馈
        val csl = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(shade(color, 0.85f), color)
        )
        view.background?.setTintList(csl)
    }

    /** 反射读取 Drawable 原始 TintList（getTintList 在某些 compileSdk 下无 Kotlin 合成属性，故用反射保编译兼容） */
    private fun readTintList(drawable: android.graphics.drawable.Drawable?): ColorStateList? {
        return runCatching {
            drawable?.javaClass?.getMethod("getTintList")?.invoke(drawable) as? ColorStateList
        }.getOrNull()
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
