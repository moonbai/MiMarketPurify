package com.mars.mimarketpurify.hooks.market

import android.os.Handler
import android.os.Looper
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
 * 移除搜索相关的软件推荐
 * 精准目标: native_app_suggest_root_view / "安装XX的用户还喜欢"
 */
object SearchAds : BaseHook() {

    override val prefKey: String = Settings.KEY_SEARCH
    override val name: String = "移除搜索推荐"

    private val targetKeywords = listOf(
        "的用户还喜欢",
        "大家还喜欢",
        "相关推荐",
        "你可能还喜欢"
    )

    // 市场固定特征ID（字符串匹配，不需要R常量）
    private const val SUGGEST_ROOT_ID_NAME = "native_app_suggest_root_view"
    private const val SUGGEST_ITEM_ID_NAME = "native_app_item_view"

    /**
     * 从命中的TextView向上回溯，优先找到 native_app_suggest_root_view；找不到就取最近有效父容器
     */
    private fun findSuggestRootFromChild(start: View): View? {
        var cur: View? = start
        var fallback: View? = null
        var depth = 0
        while (cur != null && depth < 12) {
            val idName = try {
                cur.resources.getResourceEntryName(cur.id)
            } catch (_: Throwable) {
                ""
            }
            if (idName == SUGGEST_ROOT_ID_NAME) {
                return cur
            }
            if (idName == SUGGEST_ITEM_ID_NAME && fallback == null) {
                fallback = cur
            }
            cur = cur.parent as? View
            depth++
        }
        return fallback
    }

    /**
     * 递归扫描：找到命中文本 → 向上定位整卡并隐藏
     */
    private fun scanAndHide(root: View) {
        fun dfs(view: View) {
            if (view is TextView) {
                val text = view.text?.toString() ?: return
                if (targetKeywords.any { text.contains(it) }) {
                    val targetRoot = findSuggestRootFromChild(view) ?: view
                    targetRoot.visibility = View.GONE
                    targetRoot.layoutParams?.let { lp -> lp.height = 0 }
                    HookEnv.base.log(Log.INFO, TAG, "$name: 已拦截关联推荐卡")
                    return
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    dfs(view.getChildAt(i))
                }
            }
        }
        dfs(root)
    }

    /**
     * 无硬依赖反射注册RecyclerView子附着监听
     */
    @Suppress("UNCHECKED_CAST")
    private fun attachScrollWatcher(viewRoot: ViewGroup) {
        fun traverse(parent: ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val clzName = child.javaClass.name
                if (clzName.contains("recyclerview") || clzName.endsWith("RecyclerView")) {
                    try {
                        val listenerCls = Class.forName("androidx.recyclerview.widget.RecyclerView\$OnChildAttachStateChangeListener")
                        val addMethod = child.javaClass.getDeclaredMethod(
                            "addOnChildAttachStateChangeListener", listenerCls
                        )
                        val listener = java.lang.reflect.Proxy.newProxyInstance(
                            child.javaClass.classLoader,
                            arrayOf(listenerCls),
                            { _, method, args ->
                                if (method.name == "onChildViewAttachedToWindow") {
                                    val attachedView = args[0] as View
                                    // 绑定后延迟一帧再扫：避开bind还没完成文本为空
                                    Handler(Looper.getMainLooper()).post {
                                        scanAndHide(attachedView)
                                    }
                                }
                                null
                            }
                        )
                        addMethod.invoke(child, listener)
                    } catch (_: Throwable) {
                        // 类缺失静默跳过
                    }
                } else if (child is ViewGroup) {
                    traverse(child)
                }
            }
        }
        traverse(viewRoot)
    }

    override fun init() {
        // 搜索建议 adFlag=0
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
                .hooked { return@hooked true }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索引导页拦截失败", it) }

        // 搜索结果页
        runCatching {
            val fragCls = ClassUtil.loadClass("com.xiaomi.market.business_ui.search.NativeSearchResultFragment")

            fragCls.methodFinder()
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

            fragCls.methodFinder()
                .filterByName("onViewCreated")
                .first()
                .hooked {
                    proceed()
                    val root = args.getOrNull(1) as? ViewGroup ?: return@hooked null

                    // 预绘制 + 延迟二次扫描（解决初次bind滞后）
                    root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        private var once = false
                        override fun onPreDraw(): Boolean {
                            if (once) return true
                            once = true
                            root.viewTreeObserver.removeOnPreDrawListener(this)
                            scanAndHide(root)
                            Handler(Looper.getMainLooper()).postDelayed({ scanAndHide(root) }, 350)
                            return true
                        }
                    })

                    attachScrollWatcher(root)
                    return@hooked null
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果关联推荐拦截失败", it) }
    }
}
