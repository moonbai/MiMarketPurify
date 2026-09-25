package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 主页悬浮广告屏蔽。
 * 资源锚点：floating_icon（悬浮广告图标）、floating_close_btn（关闭按钮）。
 * 命中后隐藏整个悬浮窗容器（parent）。
 */
object FloatingAdHook : BaseHook() {

    override val prefKey: String = Settings.KEY_FLOATING_AD
    override val name: String = "主页悬浮广告"

    private val anchorNames = listOf("floating_icon", "floating_close_btn")
    private var anchorIds: Set<Int>? = null

    override fun init() {
        runCatching {
            View::class.java.methodFinder()
                .filterByName("onAttachedToWindow")
                .filterByParamCount(0)
                .first()
                .hooked {
                    proceed()
                    val view = thisObject as? View ?: return@hooked null
                    if (!Settings.isEnabled(Settings.KEY_FLOATING_AD, true)) return@hooked null
                    resolveIds(view)
                    if (view.id in (anchorIds ?: emptySet())) {
                        debugLog("命中悬浮广告 anchor=${view.id}，隐藏容器")
                        val container = view.parent as? View
                        (container ?: view).visibility = View.GONE
                    }
                    null
                }
        }.onFailure {
            HookEnv.base.log(Log.ERROR, TAG, "$name: onAttachedToWindow hook 失败", it)
        }
    }

    private fun resolveIds(view: View) {
        if (anchorIds != null) return
        anchorIds = anchorNames.mapNotNull { name ->
            runCatching {
                view.resources.getIdentifier(name, "id", "com.xiaomi.market")
            }.getOrNull().takeIf { it != null && it > 0 }
        }.toSet()
    }
}
//（注：内容由AI生成）
