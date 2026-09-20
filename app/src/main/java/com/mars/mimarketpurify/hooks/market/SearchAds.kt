package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 *  - NativeSearchSugFragment：搜索建议 adFlag = 0
 *  - NativeSearchGuideFragment：搜索引导页仅保留搜索历史
 *  - NativeSearchResultFragment：搜索结果过滤非应用组件 + 视图层兜底屏蔽「安装xx的用户还喜欢」类关联推荐
 */
object SearchAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH

    override val name: String
        get() = "移除搜索推荐"

    override fun init() {
        // 搜索建议 adFlag = 0
        runCatching {
            ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchSugFragment")
                .methodFinder()
                .filterByName("getRequestParams")
                .first()
                .hooked {
                    val result = proceed()
                    @Suppress("UNCHECKED_CAST")
                    val map = (result as? Map<String, Any>)?.toMutableMap() ?: return@hooked result
                    map["adFlag"] = 0
                    return@hooked map
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索建议拦截失败", it) }

        // 搜索引导页只保留历史
        runCatching {
            val cls = ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchGuideFragment")

            cls.methodFinder()
                .filterByName("parseResponseData")
                .first()
                .hooked {
                    val result = proceed()
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? List<Any> ?: return@hooked result
                    return@hooked list.filter {
                        it.javaClass.name.contains("SearchHistoryComponent")
                    }
                }

            cls.methodFinder()
                .filterByName("isLoadMoreEndGone")
                .first()
                .hooked {
                    return@hooked proceed(true)
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索引导页拦截失败", it) }

        // 搜索结果：接口过滤 + onViewCreated 挂载视图监听
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
                    val kept = list.filter {
                        val n = it.javaClass.name
                        n.contains("AppsComponent") || n.contains("ListAppComponent")
                    }
                    return@hooked if (kept.isNotEmpty()) kept else result
                }

            // onViewCreated(Bundle, View) — 和仓库保持一致的无‑chain 写法，用 args[1] 取 View
            searchResultFragCls.methodFinder()
                .filterByName("onViewCreated")
                .first()
                .hooked {
                    proceed()
                    val root = args.getOrNull(1) as? ViewGroup ?: return@hooked null

                    root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        private var once = false
                        override fun onPreDraw(): Boolean {
                            if (once) return true
                            once = true
                            root.viewTreeObserver.removeOnPreDrawListener(this)

                            fun collectAllText(v: View): String {
                                val sb = StringBuilder()
                                fun scan(node: View) {
                                    if (node is TextView) sb.append(node.text ?: "")
                                    if (node is ViewGroup) {
                                        for (i in 0 until node.childCount) scan(node.getChildAt(i))
                                    }
                                }
                                scan(v)
                                return sb.toString()
                            }

                            fun traverse(parent: ViewGroup) {
                                for (i in 0 until parent.childCount) {
                                    val child = parent.getChildAt(i)
                                    val text = collectAllText(child)
                                    if ((text.contains("安装") && text.contains("的用户还喜欢"))
                                        || text.contains("大家还喜欢")
                                        || text.contains("相关推荐")
                                    ) {
                                        child.visibility = View.GONE
                                        child.layoutParams?.let { lp -> lp.height = 0; child.layoutParams = lp }
                                        HookEnv.base.log(Log.INFO, TAG, "$name: 已屏蔽关联推荐条目")
                                    }
                                    if (child is ViewGroup) traverse(child)
                                }
                            }
                            traverse(root)
                            return true
                        }
                    })
                    return@hooked null
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果+关联推荐拦截失败", it) }
    }
}
