package com.mars.mimarketpurify.hooks.market

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * 重点适配：点击安装后异步插入的「安装XX的用户还喜欢」(native_app_suggest_root_view)
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

    private const val SUGGEST_ROOT_ID_NAME = "native_app_suggest_root_view"

    /** 全局布局监听的最小扫描间隔：动画/滚动期间 onGlobalLayout 每帧都会触发，需要节流 */
    private const val GLOBAL_SCAN_INTERVAL_MS = 400L

    /** 全局布局扫描次数上限：足够覆盖「点击安装后动态插入」的时间窗，之后移除监听避免空转 */
    private const val GLOBAL_SCAN_MAX_COUNT = 30

    /**
     * 从命中TextView向上回溯找到整卡容器 native_app_suggest_root_view
     */
    private fun findSuggestRootFromChild(start: View): View? {
        var cur: View? = start
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
            cur = cur.parent as? View
            depth++
        }
        return null
    }

    private fun scanAndHide(root: View) {
        fun dfs(view: View) {
            if (view is TextView) {
                val text = view.text?.toString() ?: return
                if (targetKeywords.any { text.contains(it) }) {
                    val targetRoot = findSuggestRootFromChild(view) ?: return
                    if (targetRoot.visibility != View.GONE) {
                        targetRoot.visibility = View.GONE
                        targetRoot.layoutParams?.let { lp -> lp.height = 0 }
                        HookEnv.base.log(Log.INFO, TAG, "$name: 拦截安装后关联推荐卡")
                    }
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
     * 无硬依赖 RV 子附着监听
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
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        scanAndHide(attachedView)
                                    }, 120)
                                }
                                null
                            }
                        )
                        addMethod.invoke(child, listener)
                    } catch (_: Throwable) { }
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

        // 搜索结果页：增加全局布局变化监听，捕获【点击安装后动态插入】的卡片
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

                    // 持续监听布局变化 → 专门抓安装后异步新增的View。
                    // 原实现每次布局变化都全树 DFS 且监听永不移除；这里做节流 + 次数上限：
                    //  - 动画/滚动期间 onGlobalLayout 每帧触发，间隔 < 400ms 的扫描直接丢弃；
                    //  - 扫描满 30 次（约 12 秒窗口，足够覆盖安装后插入）自动移除监听，防无限空转。
                    root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        private var lastScan = 0L
                        private var scanCount = 0
                        override fun onGlobalLayout() {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastScan < GLOBAL_SCAN_INTERVAL_MS) return
                            lastScan = now
                            if (scanCount >= GLOBAL_SCAN_MAX_COUNT) {
                                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                return
                            }
                            scanCount++
                            scanAndHide(root)
                        }
                    })

                    // 初始兜底扫描
                    Handler(Looper.getMainLooper()).postDelayed({ scanAndHide(root) }, 300)
                    attachScrollWatcher(root)

                    return@hooked null
                }
        }.onFailure { HookEnv.base.log(Log.ERROR, TAG, "$name: 搜索结果关联推荐拦截失败", it) }
    }
}
