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
 *  - NativeSearchResultFragment：接口过滤 + 无硬依赖视图扫描拦截「安装XX的用户还喜欢」
 */
object SearchAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH

    override val name: String
        get() = "移除搜索推荐"

    private val targetKeywords = listOf(
        "的用户还喜欢",
        "大家还喜欢",
        "相关推荐",
        "你可能还喜欢"
    )

    /**
     * 递归扫描所有 TextView，命中关键词则隐藏自身所在父Item块
     */
    private fun findAndHideIfMatch(root: View) {
        fun scan(view: View): Boolean {
            if (view is TextView) {
                val txt = view.text?.toString() ?: return false
                return targetKeywords.any { txt.contains(it) }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (scan(view.getChildAt(i))) return true
                }
            }
            return false
        }

        if (scan(root)) {
            root.visibility = View.GONE
            root.layoutParams?.let { lp ->
                lp.height = 0
                root.layoutParams = lp
            }
            HookEnv.base.log(Log.INFO, TAG, "$name: 拦截关联推荐块")
        }

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findAndHideIfMatch(root.getChildAt(i))
            }
        }
    }

    /**
     * 不硬引用 RecyclerView：通过类名判断 + 反射注册子View附着监听
     */
    @Suppress("UNCHECKED_CAST")
    private fun attachScrollWatcher(viewRoot: ViewGroup) {
        fun traverse(parent: ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val clzName = child.javaClass.name
                // 用类名识别 RecyclerView，不 import
                if (clzName.contains("recyclerview") || clzName.endsWith("RecyclerView")) {
                    try {
                        // 获取 addOnChildAttachStateChangeListener 方法
                        val addMethod = child.javaClass.getDeclaredMethod(
                            "addOnChildAttachStateChangeListener",
                            Class.forName("androidx.recyclerview.widget.RecyclerView\$OnChildAttachStateChangeListener")
                        )
                        // 动态实例化监听器
                        val listenerCls = Class.forName("androidx.recyclerview.widget.RecyclerView\$OnChildAttachStateChangeListener")
                        val listener = java.lang.reflect.Proxy.newProxyInstance(
                            child.javaClass.classLoader,
                            arrayOf(listenerCls),
                            { _, method, args ->
                                when (method.name) {
                                    "onChildViewAttachedToWindow" -> {
                                        val attachedView = args[0] as View
                                        findAndHideIfMatch(attachedView)
                                    }
                                }
                                null
                            }
                        )
                        addMethod.invoke(child, listener)
                    } catch (e: Throwable) {
                        // 找不到类/方法直接静默跳过，不影响整体
                    }
                } else if (child is ViewGroup) {
                    traverse(child)
                }
            }
        }
        traverse(viewRoot)
    }

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
                    return@hooked true
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索引导页拦截失败", it) }

        // 搜索结果：接口过滤 + 视图动态扫描
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

            searchResultFragCls.methodFinder()
                .filterByName("onViewCreated")
                .first()
                .hooked {
                    proceed()
                    val root = args.getOrNull(1) as? ViewGroup ?: return@hooked null

                    // 首次预绘制兜底扫描
                    root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        private var once = false
                        override fun onPreDraw(): Boolean {
                            if (once) return true
                            once = true
                            root.viewTreeObserver.removeOnPreDrawListener(this)
                            findAndHideIfMatch(root)
                            return true
                        }
                    })

                    // 无硬依赖注册滚动条目监听
                    attachScrollWatcher(root)

                    return@hooked null
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果+关联推荐拦截失败", it) }
    }
}
