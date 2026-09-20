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
 * 重点适配：点击安装后异步插入的「安装XX的用户还喜欢」卡片。
 * 卡片根容器 id 随商店版本漂移过多次（native_app_suggest_root_view → native_app_item_view），
 * 因此锚点做成集合；仍漂移时开启调试开关（KEY_RANK_DEBUG），logcat 会打印命中文案
 * 对应的容器链，可直接定位新 id 补进集合。
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

    /** 推荐卡根容器的已知资源 id（新版本若更换 id，从调试日志定位后追加到这里） */
    private val SUGGEST_ROOT_IDS = setOf(
        "native_app_suggest_root_view",
        "native_app_item_view"
    )

    /** 全局布局监听的最小扫描间隔：动画/滚动期间 onGlobalLayout 每帧都会触发，需要节流 */
    private const val GLOBAL_SCAN_INTERVAL_MS = 400L

    /** 全局布局扫描次数上限：足够覆盖「点击安装后动态插入」的时间窗，之后移除监听避免空转 */
    private const val GLOBAL_SCAN_MAX_COUNT = 30

    /**
     * 从命中TextView向上回溯找到整卡容器（匹配 [SUGGEST_ROOT_IDS] 中的任一资源 id）
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
            if (idName in SUGGEST_ROOT_IDS) {
                return cur
            }
            cur = cur.parent as? View
            depth++
        }
        return null
    }

    /** 调试用：打印命中 TextView 及向上几层容器的 类名(id)，用于 id 再次漂移时定位新锚点 */
    private fun debugAncestors(start: View): String {
        val sb = StringBuilder()
        var cur: View? = start
        repeat(4) {
            if (cur == null) return@repeat
            if (sb.isNotEmpty()) sb.append(" → ")
            val idName = runCatching { cur.resources.getResourceEntryName(cur.id) }
                .getOrNull() ?: "id=${cur.id}"
            sb.append("${cur.javaClass.simpleName}($idName)")
            cur = cur.parent as? View
        }
        return sb.toString()
    }

    /** 全树扫描并隐藏推荐卡；返回本次是否命中目标（用于决定全局监听是否保留） */
    private fun scanAndHide(root: View): Boolean {
        var hit = false
        fun dfs(view: View) {
            if (view is TextView) {
                val text = view.text?.toString() ?: return
                if (targetKeywords.any { text.contains(it) }) {
                    val targetRoot = findSuggestRootFromChild(view)
                    if (targetRoot == null) {
                        // 命中文案但没找到根容器：说明 id 又漂移了，打印容器链便于补锚点
                        debugLog("命中推荐文案「${text.take(30)}」但未匹配根容器 id，容器链: ${debugAncestors(view)}")
                        return
                    }
                    if (targetRoot.visibility != View.GONE) {
                        targetRoot.visibility = View.GONE
                        targetRoot.layoutParams?.let { lp -> lp.height = 0 }
                        HookEnv.base.log(Log.INFO, TAG, "$name: 拦截安装后关联推荐卡")
                    }
                    hit = true
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
        return hit
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
                    // 节流 + 智能上限：
                    //  - 动画/滚动期间 onGlobalLayout 每帧触发，间隔 < 400ms 的扫描直接丢弃；
                    //  - 命中过目标则保留监听（持续防恢复/防后续卡片）；从未命中且扫描满
                    //    上限才移除监听，避免页面长时间停留时全树扫描空转。
                    root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        private var lastScan = 0L
                        private var scanCount = 0
                        private var everHit = false
                        override fun onGlobalLayout() {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastScan < GLOBAL_SCAN_INTERVAL_MS) return
                            lastScan = now
                            if (!everHit && scanCount >= GLOBAL_SCAN_MAX_COUNT) {
                                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                return
                            }
                            scanCount++
                            if (scanAndHide(root)) {
                                everHit = true
                            }
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
