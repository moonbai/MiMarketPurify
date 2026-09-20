package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import io.github.kyuubiran.ezxhelper.core.hook.AfterHook

/**
 * 移除搜索相关的软件推荐。
 *
 * 覆盖 README 中：
 *  - 移除搜索建议的软件推荐（NativeSearchSugFragment）
 *  - 移除搜索页面的软件推荐（NativeSearchGuideFragment）
 *  - 移除搜索结果的软件推荐（NativeSearchResultFragment）
 *  - 新增：搜索结果页「安装xx的用户还喜欢」关联推荐（native_app_item_view / recycler_view 视图兜底）
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

        // ========== 原有：搜索结果接口层过滤 Apps‑相关组件 ==========
        runCatching {
            val searchResultFragCls = ClassUtil.loadClass(
                "com.xiaomi.market.business_ui.search.NativeSearchResultFragment"
            )

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

            // ✨ 新增：Fragment onViewCreated 钩子 → 监听 R.id.recycler_view，拦截「用户还喜欢」native_app_item_view
            searchResultFragCls.methodFinder()
                .filter { it.name == "onViewCreated" }
                .first()
                .hook(AfterHook) { param ->
                    val view = param.args[1] as? ViewGroup ?: return@hook
                    val rv = view.findViewById<RecyclerView>(
                        view.resources.getIdentifier("recycler_view", "id", "com.xiaomi.market")
                    ) ?: return@hook

                    // 给 RecyclerView 加子View attach 监听：命中 native_app_item_view + 包含关联推荐关键词时隐藏
                    rv.setOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                        override fun onChildViewAttachedToWindow(child: View) {
                            // 匹配目标 item id: native_app_item_view
                            val itemView = child.findViewById<View>(
                                child.resources.getIdentifier("native_app_item_view", "id", "com.xiaomi.market")
                            ) ?: return

                            // 文本特征匹配："安装.*的用户还喜欢" / "大家还在搜" / "相关推荐" 这类；命中直接隐藏
                            val hasRelatedTip = runCatching {
                                val txtSb = StringBuilder()
                                fun traverse(v: View) {
                                    if (v is android.widget.TextView) txtSb.append(v.text ?: "")
                                    if (v is ViewGroup) for (i in 0 until v.childCount) traverse(v.getChildAt(i))
                                }
                                traverse(child)
                                val text = txtSb.toString()
                                text.contains("安装") && text.contains("的用户还喜欢")
                                        || text.contains("大家还喜欢")
                                        || text.contains("相关推荐")
                            }.getOrDefault(false)

                            if (hasRelatedTip) {
                                child.visibility = View.GONE
                                (child.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                                    width = 0; height = 0
                                }
                                HookEnv.base.log(Log.INFO, TAG, "$name: 已屏蔽「安装xx的用户还喜欢」关联条目")
                            }
                        }

                        override fun onChildViewDetachedFromWindow(view: View) {}
                    })
                }

        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果+关联推荐拦截失败", it) }
    }
}
