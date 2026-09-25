package com.mars.mimarketpurify.hooks.market

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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

    // tag标记：防止短时间重复执行居中逻辑
    private val TAG_RECENTER_DONE = View.generateViewId()

    override fun init() {
        hookCardExpand()
        hookViewAttachObserver()
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
                    if(resName == "update_button_layout" || resName == "update_button_parent_layout"){
                        applyBtnStyle(v)
                        // tag防重复执行
                        if(v.getTag(TAG_RECENTER_DONE) != true){
                            v.setTag(TAG_RECENTER_DONE, true)
                            v.postDelayed({
                                reCenterButtonContent(v)
                                v.setTag(TAG_RECENTER_DONE, false)
                            },100)
                        }
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

        applyBtnStyle(btnLayout)
        applyBtnStyle(btnParent)
        root.postDelayed({ applyBtnStyle(btnLayout); applyBtnStyle(btnParent) }, 150)
        root.postDelayed({ applyBtnStyle(btnLayout); applyBtnStyle(btnParent) }, 400)
        root.postDelayed({ applyBtnStyle(btnLayout); applyBtnStyle(btnParent) }, 800)

        HookEnv.base.log(Log.INFO, TAG, "btnParent=$btnParent, btnLayout=$btnLayout")
        btnLayout?.let { bl ->
            if(bl.getTag(TAG_RECENTER_DONE) != true) {
                bl.setTag(TAG_RECENTER_DONE, true)
                bl.postDelayed({
                    reCenterButtonContent(bl)
                    bl.setTag(TAG_RECENTER_DONE, false)
                },100)
            }
        }
    }

    // 【重写！不再removeView，不重构布局树，只做位移微调，避免文字消失】
    private fun reCenterButtonContent(btnLayout: View) {
        val textView = MinePageClean.findViewByResName(btnLayout, "update_button_text") as? TextView
        val badgeView = MinePageClean.findViewByResName(btnLayout, "update_button_red_badge")
        if(textView == null || badgeView == null) {
            HookEnv.base.log(Log.WARN, TAG, "reCenterButtonContent: 找不到 update_button_text / update_button_red_badge，跳过")
            return
        }
        val density = btnLayout.resources.displayMetrics.density
        // 整体向上偏移，解决文字偏下裁切，调整这个数值微调
        val offsetY = (-3f * density)
        textView.translationY = offsetY
        badgeView.translationY = offsetY
    }

    fun applyBtnStyle(view: View?) {
        view ?: return
        val density = view.resources.displayMetrics.density
        val btnDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(UPDATE_BTN_COLOR)
            cornerRadius = BTN_RADIUS_DP * density
        }
        view.background = btnDrawable

        val padHorizontal = (20f * density).toInt()
        val padVertical = (10f * density).toInt()
        view.setPadding(padHorizontal, padVertical, padHorizontal, padVertical)

        view.layoutParams?.let { lp ->
            when (lp) {
                is android.widget.LinearLayout.LayoutParams -> {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.weight = 0f
                    lp.marginStart = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.marginEnd = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.topMargin = (16f * density).toInt()
                    view.layoutParams = lp
                }
                is android.widget.FrameLayout.LayoutParams -> {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
                    lp.marginStart = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.marginEnd = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.topMargin = (16f * density).toInt()
                    view.layoutParams = lp
                }
                is android.widget.RelativeLayout.LayoutParams -> {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.marginStart = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.marginEnd = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                    lp.topMargin = (16f * density).toInt()
                    view.layoutParams = lp
                }
                else -> {
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.marginStart = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                        lp.marginEnd = (UPDATE_BTN_MARGIN_HORIZONTAL_DP * density).toInt()
                        lp.topMargin = (16f * density).toInt()
                        view.layoutParams = lp
                    } else {
                        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        view.layoutParams = lp
                    }
                }
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
}
