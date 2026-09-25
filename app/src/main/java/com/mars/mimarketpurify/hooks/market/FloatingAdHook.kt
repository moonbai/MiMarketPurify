package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 主页悬浮广告屏蔽。
 * 资源锚点：floating_icon（悬浮广告图标）、floating_close_btn（关闭按钮）。
 * 命中后隐藏整个悬浮窗容器（parent），并拦截setVisibility防止复原。
 */
object FloatingAdHook : BaseHook() {

    override val prefKey: String = Settings.KEY_FLOATING_AD
    override val name: String = "主页悬浮广告"

    private val anchorNames = listOf("floating_icon", "floating_close_btn")
    private var anchorIds: Set<Int> = emptySet()

    override fun init() {
        // 1. Hook onAttachedToWindow：捕获广告View挂载事件
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    if (!Settings.isEnabled(Settings.KEY_FLOATING_AD, true)) return@hooked null

                    // 每次attach都尝试解析ID，修复首次解析失败永久失效问题
                    resolveIds(view)
                    if (view.id in anchorIds) {
                        debugLog("命中悬浮广告 anchor=${view.id}，隐藏悬浮根容器")
                        val rootContainer = findFloatingRootContainer(view)
                        rootContainer?.visibility = View.GONE
                    }
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow hook 失败", it)
        }

        // 2. 新增：Hook View.setVisibility，拦截Market恢复广告可见性（解决概率复现）
        runCatching {
            View::class.java.methodFinder()
                .filterByName("setVisibility")
                .filterByParamCount(1)
                .first()
                .hooked {
                    val targetView = thisObject as? View ?: return@hooked proceed()
                    if (!Settings.isEnabled(Settings.KEY_FLOATING_AD, true)) return@hooked proceed()
                    resolveIds(targetView)

                    // 如果当前View是广告锚点，或者属于悬浮广告容器，禁止设置为VISIBLE
                    if (targetView.id in anchorIds || isFloatingAdContainer(targetView)) {
                        val targetVis = args[0] as Int
                        if (targetVis == View.VISIBLE) {
                            debugLog("拦截悬浮广告 setVisibility(VISIBLE)")
                            return@hooked null
                        }
                    }
                    proceed()
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: setVisibility hook 失败", it)
        }
    // 在 init() 里面追加
    runCatching {
        ViewGroup::class.java.methodFinder()
            .filterByName("addView")
            .filterByParamCount(1)
            .first()
            .hooked {
                if (!Settings.isEnabled(Settings.KEY_FLOATING_AD, true)) return@hooked proceed()
                val childView = args[0] as? View ?: return@hooked proceed()
                resolveIds(childView)
                if(childView.id in anchorIds || isFloatingAdContainer(childView)){
                    debugLog("拦截addView，禁止加载悬浮广告")
                    return@hooked null
                }
                proceed()
            }
    }.onFailure {
        HookEnv.base.log(Log.ERROR, TAG, "$name: addView hook失败", it)
    }
    
    }

    /**
     * 向上递归查找悬浮广告根容器（最多向上查找5层，防止死循环）
     */
    private fun findFloatingRootContainer(startView: View): ViewGroup? {
        var curr: View? = startView
        var depth = 0
        while (curr != null && depth < 5) {
            val parent = curr.parent as? ViewGroup ?: return curr as? ViewGroup
            curr = parent
            depth++
        }
        return curr as? ViewGroup
    }

    /**
     * 判断当前View是否属于悬浮广告容器：子view包含floating_icon/floating_close_btn
     */
    private fun isFloatingAdContainer(view: View): Boolean {
        if (view !is ViewGroup) return false
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child.id in anchorIds) return true
        }
        return false
    }

    /**
     * 每次调用都解析ID，更新anchorIds，不再一次性缓存
     */
    private fun resolveIds(view: View) {
        val newIds = anchorNames.mapNotNull { name ->
            runCatching {
                view.resources.getIdentifier(name, "id", "com.xiaomi.market")
            }.getOrNull().takeIf { it != null && it > 0 }
        }.toSet()
        if (newIds.isNotEmpty()) {
            anchorIds = newIds
        }
    }
}
