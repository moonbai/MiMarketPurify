package com.mars.mimarketpurify.hooks.market

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
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

object UpdateCardUi : BaseHook() {
    override val prefKey: String? = null
    override val name: String = "更新卡片UI调整"

    private const val CARD_RADIUS_DP = 16f
    private const val UPDATE_BTN_COLOR = 0xFF0DAE73.toInt()
    private const val BTN_RADIUS_DP = 24f
    private const val UPDATE_BTN_MARGIN_HORIZONTAL_DP = 12f
    private const val TITLE_WHITE = 0xFFFFFFFF.toInt() // 白色

    override fun init() {
        hookCardExpand()
        hookViewAttachObserver()
        hookActivityConfigChange() // 监听深色模式切换
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

    // 递归遍历View树：同时处理按钮背景、标题、empty文字、箭头
    private fun refreshAllUpdateButton(rootView: View?) {
        rootView ?: return
        val resName = MinePageClean.getResourceName(rootView)

        // 1. 一键升级按钮
        if (resName == "update_button_layout") {
            applyBtnColorOnly(rootView, true) // 内层按钮：设置背景 + 修改padding（降低高度）
        }
        if (resName == "update_button_parent_layout") {
            applyBtnColorOnly(rootView, false) // 外层容器：仅上色，不修改padding
        }

        // 2. mine_app_update_title 标题
        if(resName == "mine_app_update_title" && rootView is TextView){
            val isNight = rootView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if(isNight){
                rootView.setTextColor(TITLE_WHITE)
            }
        }

        // 3. update_empty_text
        if(resName == "update_empty_text" && rootView is TextView){
            val isNight = rootView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if(isNight){
                rootView.setTextColor(TITLE_WHITE)
            }
        }

        // 4. mine_update_arrow 箭头文字
        if(resName == "mine_update_arrow" && rootView is TextView){
            val isNight = rootView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if(isNight){
                rootView.setTextColor(TITLE_WHITE)
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

                    // 文本控件
                    if(resName == "mine_app_update_title" && v is TextView){
                        val isNight = v.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        if(isNight) v.setTextColor(TITLE_WHITE)
                    }
                    if(resName == "update_empty_text" && v is TextView){
                        val isNight = v.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        if(isNight) v.setTextColor(TITLE_WHITE)
                    }
                    if(resName == "mine_update_arrow" && v is TextView){
                        val isNight = v.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                        if(isNight) v.setTextColor(TITLE_WHITE)
                    }
                    result
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow 挂钩失败", it)
        }
    }

    private fun hookCardExpand() {
        if (!Settings.isEnabled(Settings.KEY_CARD_EXPAND, false)) return

        runCatching {
            val cls = ClassUtil.loadClass("com.xiaomi.market.business_ui.main.mine.view.MineUpdateView")
            cls?.methodFinder()
                ?.filterByName("onFinishInflate")
                ?.forEach { m ->
                    m.hooked {
                        val result = proceed()
                        (thisObject as? View)?.let { view ->
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
     * @param modifyPadding true=修改垂直padding降低高度（仅内层update_button_layout）
     */
    fun applyBtnColorOnly(view: View?, modifyPadding: Boolean) {
        view ?: return
        val density = view.resources.displayMetrics.density
        val btnDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(UPDATE_BTN_COLOR)
            cornerRadius = BTN_RADIUS_DP * density
        }
        view.background = btnDrawable

        // 仅内层按钮修改上下padding，降低按钮高度
        if(modifyPadding){
            val padVertical = (6f * density).toInt()
            view.setPadding(view.paddingLeft, padVertical, view.paddingRight, padVertical)
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
}
