package com.mars.mimarketpurify.hooks.market

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.Ui
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object TabStyleCustom : BaseHook() {
    override val prefKey: String = Settings.KEY_TAB_SELECT_COLOR
    override val name: String get() = "原生Tab栏样式自定义"

    override fun init() {
        HookEnv.base.log(Log.DEBUG, TAG, "[TabStyleCustom] init() 开始")
        hookTabSelectColor()
        hookTabIndicator()
        HookEnv.base.log(Log.DEBUG, TAG, "[TabStyleCustom] init() 完成")
    }

    /** 修改Tab选中图标+文字颜色 */
    private fun hookTabSelectColor() {
        val tabLayoutClz = runCatching {
            ClassUtil.loadClass("com.miui.market.widget.TabLayout")
        }.getOrNull() ?: return

        tabLayoutClz.methodFinder()
            .filterByName("selectTab")
            .filterByParamCount(1)
            .firstOrNull()?.hooked {
                val enableMaster = Settings.isMasterEnabled()
                if (!enableMaster) return@hooked proceed()

                val customSelectColor = Settings.getInt(Settings.KEY_TAB_SELECT_COLOR, Ui.ACCENT)
                val tab = args[0] ?: return@hooked proceed()
                val tabView = runCatching {
                    tab.javaClass.getDeclaredMethod("getCustomView").invoke(tab)
                }.getOrNull() ?: runCatching {
                    tab.javaClass.getDeclaredMethod("getView").invoke(tab)
                }.getOrNull() ?: return@hooked proceed()

                val childCount = tabView.javaClass.getDeclaredMethod("getChildCount").invoke(tabView) as Int
                for (i in 0 until childCount) {
                    val child = tabView.javaClass.getDeclaredMethod("getChildAt", Int::class.java).invoke(tabView, i)
                    when (child) {
                        is ImageView -> {
                            child.colorFilter = PorterDuffColorFilter(customSelectColor, PorterDuff.Mode.SRC_IN)
                        }
                        is TextView -> {
                            child.setTextColor(customSelectColor)
                        }
                    }
                }
                return@hooked proceed()
            }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabStyleCustom] hooked selectTab")
    }

    /** Tab底部指示器（横线）：开关 + 自定义颜色 */
    private fun hookTabIndicator() {
        val tabLayoutClz = runCatching {
            ClassUtil.loadClass("com.miui.market.widget.TabLayout")
        }.getOrNull() ?: return

        // 拦截 setSelectedIndicatorColor，替换颜色 + 控制隐藏
        tabLayoutClz.methodFinder()
            .filterByName("setSelectedIndicatorColor")
            .filterByParamCount(1)
            .firstOrNull()?.hooked {
                val enableMaster = Settings.isMasterEnabled()
                if (!enableMaster) return@hooked proceed()

                val indicatorVisible = Settings.isEnabled(Settings.KEY_TAB_INDICATOR_VISIBLE, true)
                val thiz = thisObject ?: return@hooked proceed()
                val indicatorView = indicatorViewOf(thiz)
                if (!indicatorVisible) {
                    indicatorView?.visibility = android.view.View.GONE
                    return@hooked proceed()
                }
                indicatorView?.visibility = android.view.View.VISIBLE
                val customIndicatorColor = Settings.getInt(Settings.KEY_TAB_INDICATOR_COLOR, 0xFFFFFFFF.toInt())
                args[0] = customIndicatorColor
                return@hooked proceed()
            }

        // onLayout 页面重绘时恢复状态，防止滑动/页面重建覆盖指示器
        tabLayoutClz.methodFinder()
            .filterByName("onLayout")
            .filterByParamCount(5)
            .firstOrNull()?.hooked {
                val enableMaster = Settings.isMasterEnabled()
                if (!enableMaster) return@hooked proceed()

                val indicatorVisible = Settings.isEnabled(Settings.KEY_TAB_INDICATOR_VISIBLE, true)
                val thiz = thisObject ?: return@hooked proceed()
                val indicatorView = indicatorViewOf(thiz) ?: return@hooked proceed()

                if (!indicatorVisible) {
                    indicatorView.visibility = android.view.View.GONE
                } else {
                    indicatorView.visibility = android.view.View.VISIBLE
                    val customIndicatorColor = Settings.getInt(Settings.KEY_TAB_INDICATOR_COLOR, 0xFFFFFFFF.toInt())
                    thisObject.javaClass.getDeclaredMethod("setSelectedIndicatorColor", Int::class.javaPrimitiveType)
                        .invoke(thisObject, customIndicatorColor)
                }
                return@hooked proceed()
            }
        HookEnv.base.log(Log.DEBUG, TAG, "[TabStyleCustom] hooked TabLayout indicator")
    }

    /** 反射读取 TabLayout 的指示器 View，字段被混淆时返回 null（静默降级）。 */
    private fun indicatorViewOf(target: Any): android.view.View? = runCatching {
        val f = target.javaClass.getDeclaredField("mSelectedIndicator")
        f.isAccessible = true
        f.get(target) as? android.view.View
    }.getOrNull()

}
