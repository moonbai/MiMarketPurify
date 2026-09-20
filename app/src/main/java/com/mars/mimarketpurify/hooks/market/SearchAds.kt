package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * 移除搜索相关的软件推荐。
 *
 * 覆盖 README 中：
 *  - 移除搜索建议的软件推荐（NativeSearchSugFragment）
 *  - 移除搜索页面的软件推荐（NativeSearchGuideFragment）
 *  - 移除搜索结果的软件推荐（NativeSearchResultFragment）
 *  - 新增：搜索结果页「安装xx的用户还喜欢」关联推荐（视图层兜底，无RecyclerView硬依赖）
 *
 * 注意：搜索结果页的过滤会保留“应用列表”组件、剔除其它组件，这可能使个别应用
 * （如“小米商城”）在搜索中不可见。该行为沿用原实现，若影响使用可在主页关闭本开关。
 */
object SearchAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH

    override val name: String
        get() = "移除搜索推荐"

    override fun init() {
        // ========== 原有：搜索建议 adFlag 关闭 ==========
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchSugFragment"
            ).methodFinder()
                .filterByName("getRequestParams")
                .first()
                .hooked {
                    val result = proceed()
                    @Suppress("UNCHECKED_CAST")
                    return@hooked (result as Map<String, Any>).toMutableMap().apply {
                        this["adFlag"] = 0
                    }
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索建议拦截失败", it) }

        // ========== 原有：搜索引导页只保留历史 ==========
        runCatching {
            ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchGuideFragment"
            ).apply {
                methodFinder()
                    .filterByName("parseResponseData")
                    .first()
                    .hooked {
                        val result = proceed()
                        @Suppress("UNCHECKED_CAST")
                        return@hooked (result as List<Any>).filter { component ->
                            component.javaClass.name.contains("SearchHistoryComponent")
                        }
                    }

                methodFinder()
                    .filterByName("isLoadMoreEndGone")
                    .first()
                    .hooked { true }
            }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索页面拦截失败", it) }

        // ========== 原有：搜索结果接口层过滤 Apps‑相关组件 + 新增视图兜底 ==========
        runCatching {
            val searchResultFragCls = ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchResultFragment"
            )

            // 原有 parseResponseData 保持原样
            searchResultFragCls.methodFinder()
                .filterByName("parseResponseData")
                .first()
                .hooked {
                    val result = proceed()
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? List<Any> ?: return@hooked result
                    val kept = list.filter { component ->
                        val n = component.javaClass.name
                        n.contains("AppsComponent") || n.contains("ListAppComponent")
                    }
                    return@hooked if (kept.isEmpty()) result else kept
                }

            // ✨ 新增：onViewCreated 后遍历，绑定子ViewAttach监听（纯反射、不import RecyclerView）
            searchResultFragCls.methodFinder()
                .filterByName("onViewCreated")
                .first()
                .hooked { param ->
                    proceed()
                    val view = param.args[1] as? ViewGroup ?: return@hooked

                    // 获取资源ID
                    val res = view.resources
                    val rvId = res.getIdentifier("recycler_view", "id", "com.xiaomi.market")
                    val itemId = res.getIdentifier("native_app_item_view", "id", "com.xiaomi.market")
                    if (rvId == 0 || itemId == 0) return@hooked

                    val rv = view.findViewById<View>(rvId) ?: return@hooked

                    // 反射拿到 setOnChildAttachStateChangeListener 方法，不硬引用类
                    runCatching {
                        val attachMethod = rv.javaClass.getDeclaredMethod(
                            "setOnChildAttachStateChangeListener",
                            Class.forName("androidx.recyclerview.widget.RecyclerView\$OnChildAttachStateChangeListener")
                        )
                        val listenerCls = attachMethod.parameterTypes[0]

                        // 动态实例化监听器
                        val listener = java.lang.reflect.Proxy.newProxyInstance(
                            listenerCls.classLoader,
                            arrayOf(listenerCls)
                        ) { _, method, argsProxy ->
                            if (method.name == "onChildViewAttachedToWindow") {
                                val child = argsProxy[0] as? View ?: return@newProxyInstance null
                                val targetItem = child.findViewById<View>(itemId) ?: return@newProxyInstance null

                                // 递归取全部文本匹配关键词
                                val sb = StringBuilder()
                                fun scan(v: View) {
                                    if (v is TextView) sb.append(v.text ?: "")
                                    if (v is ViewGroup) {
                                        for (i in 0 until v.childCount) scan(v.getChildAt(i))
                                    }
                                }
                                scan(child)
                                val text = sb.toString()
                                if ((text.contains("安装") && text.contains("的用户还喜欢"))
                                    || text.contains("大家还喜欢")
                                    || text.contains("相关推荐")
                                ) {
                                    child.visibility = View.GONE
                                    (child.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                                        width = 0
                                        height = 0
                                    }
                                    HookEnv.base.log(Log.INFO, TAG, "$name: 已屏蔽「安装xx的用户还喜欢」条目")
                                }
                            }
                            null
                        }
                        attachMethod.invoke(rv, listener)
                    }
                }

        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果+关联推荐拦截失败", it) }
    }
}
