package com.mars.mimarketpurify.util

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.mars.mimarketpurify.TAG
import java.lang.reflect.Method

/**
 * 应用商店底栏（TabView 条）的**纯原生**访问器。
 *
 * 设计参考 HyperModifier 针对应用商店底栏的 Hook 思路：不去重建商店的数据模型
 * （TabInfo / PageConfig 这类反射构造在换版本时极易失效），而是**直接定位原生
 * View**——找到底栏容器，反射读取其 TabView 列表与每个 TabView 的可视状态
 * （标题、tag、红点、数字、图标），再对这些原生 View 做增删改。
 *
 * 抗漂移是重点。小米应用商店发版频繁，且**开启了 AndResGuard 资源混淆**，导致：
 *  - 资源名 `tab_container_layout` / `tab_container` 在不同版本被改名（老方案直接 return null → 悬浮底栏永不挂载）；
 *  - 连 `getTabViews()` / `getTitleView()` 等方法名也可能被混淆（仅依赖方法名同样失效）。
 *
 * 因此 [locateBottomBar] 采用**三级定位**，按可靠性从高到低，最后一级是**纯几何/结构**
 * 定位——不依赖任何资源名或方法名，只认「屏幕底部、横向铺开、含多个图标+文字 Tab 子项」
 * 的容器，从根本上抗 AndResGuard 与类名混淆。HyperModifier 的 `findDescendantByClassNames`
 * 思路只认类名，仍会被混淆；本实现进一步泛化到「位置 + 子项形态」，对商店任意版本都生效。
 *
 * 所有反射 [Method] 与资源 id 均按类/名缓存，避免高频路径重复查找。
 * 所有对外方法都用 runCatching 兜底，商店换版本导致找不到时静默降级，不抛异常。
 */
object NativeTabBar {

    /** 承载 TabView 的原生容器（其 `getTabViews()` 返回 TabView 列表）。 */
    private const val ID_TAB_CONTAINER = "tab_container"

    /** 底栏最外层容器（用于整体可见性 / alpha 判断）。 */
    private const val ID_TAB_CONTAINER_LAYOUT = "tab_container_layout"

    /** 子树查找深度上限，防御异常深的布局。 */
    private const val MAX_DEPTH = 24

    // ── 缓存 ──
    // 类名 -> (方法名 -> Method?)；null 表示该类没有此方法（负缓存，避免反复查找）
    private val methodCache = HashMap<String, HashMap<String, Method?>>()
    private val idCache = HashMap<String, Int>()
    // 底栏定位结果按 Activity 缓存（弱键，Activity 销毁后自动回收）：命中后逐帧复用，
    // 避免重试窗口内反复全树扫描；同时 [locateBottomBar] 每次会校验外层是否仍 attach，防止复用陈旧引用。
    private val barRefCache = WeakHashMap<Activity, BottomBarRef>()

    /** 底栏定位结果：outer = 要隐藏的最外层容器（整条原生底栏），tabs = 直接子项即 TabView 的容器。 */
    data class BottomBarRef(val outer: View, val tabs: View)

    // ═══════════════ 资源 id / 容器定位 ═══════════════

    private fun resId(activity: Activity, name: String): Int =
        synchronized(idCache) {
            idCache.getOrPut(name) {
                runCatching {
                    activity.resources.getIdentifier(name, "id", activity.packageName)
                }.getOrDefault(0)
            }
        }

    /**
     * 定位商店底栏，返回 (outer, tabs)。三级策略：
     *  1. 资源名 `tab_container_layout`(outer) + `tab_container`(tabs) —— 未混淆版本；
     *  2. 暴露 `getTabViews()` 的 View —— 方法名未被改写的版本；
     *  3. 几何/结构定位 —— 屏幕底部、横向、含多个「图标+文字」Tab 子项的最浅容器，
     *     不依赖任何资源名或方法名，抗 AndResGuard 与类名混淆（最终兜底）。
     *
     * 结果按 Activity 缓存：一旦命中便逐帧复用（仅当底栏被 detach / 隐藏才重新定位）。
     */
    fun locateBottomBar(activity: Activity): BottomBarRef? {
        synchronized(barRefCache) {
            barRefCache[activity]?.let { cached ->
                if (cached.outer.isAttachedToWindow && cached.outer.visibility != View.GONE &&
                    cached.tabs.isAttachedToWindow
                ) {
                    return cached
                }
                barRefCache.remove(activity)
            }
        }

        // 1) 资源名 fast path（未混淆版本，含 HyperModifier 目标版本）
        val nameOuter = resId(activity, ID_TAB_CONTAINER_LAYOUT).takeIf { it != 0 }
            ?.let { activity.findViewById<View>(it) }
        val nameTabs = resId(activity, ID_TAB_CONTAINER).takeIf { it != 0 }
            ?.let { activity.findViewById<View>(it) }
        if (nameOuter != null && nameTabs != null) {
            return cache(activity, BottomBarRef(nameOuter, nameTabs))
        }

        // 2) 方法名回退（getTabViews 未被改写）
        findByGetTabViews(activity)?.let { return cache(activity, it) }

        // 3) 几何/结构定位（抗混淆最终兜底）
        findBottomBarByGeometry(activity)?.let {
            Log.i(TAG, "悬浮底栏：几何定位底栏成功 outer=${it.outer.javaClass.simpleName} tabs=${it.tabs.javaClass.simpleName}")
            return cache(activity, it)
        }
        return null
    }

    /** 底栏最外层容器（整条原生底栏），找不到返回 null。供悬浮底栏宿主隐藏、EarlySuppressor 压制复用。 */
    fun bottomContainer(activity: Activity): View? = locateBottomBar(activity)?.outer

    private fun cache(activity: Activity, ref: BottomBarRef): BottomBarRef {
        synchronized(barRefCache) { barRefCache[activity] = ref }
        return ref
    }

    /** 释放某个 Activity 的底栏定位缓存（宿主卸载时调用，避免跨重建复用陈旧引用）。 */
    fun clearBarCache(activity: Activity) {
        synchronized(barRefCache) { barRefCache.remove(activity) }
    }

    /** 方法名回退：遍历内容树，找暴露 [getTabViews] 的容器作为 tabs，并回溯其外层 frame。 */
    private fun findByGetTabViews(activity: Activity): BottomBarRef? {
        val root = activity.window.decorView ?: return null
        var tabs: View? = null
        fun visit(v: View) {
            if (tabs != null) return
            if (cachedMethod(v, "getTabViews") != null) {
                tabs = v
                return
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i) ?: continue)
            }
        }
        visit(root)
        return tabs?.let { BottomBarRef(outerFrameOf(it, activity) ?: it, it) }
    }

    /**
     * 几何/结构定位底栏：遍历 decorView，找「屏幕底部区域 + 够宽 + ≥2 个 tab 样直接子项」的
     * 最浅 ViewGroup 作为 tabs 容器；再向上回溯到「仍贴底且高度条状」的最浅祖先作为 outer frame。
     * 该策略只认位置与子项形态，完全不依赖资源名 / 方法名 / 类名，对混淆版本稳定生效。
     */
    private fun findBottomBarByGeometry(activity: Activity): BottomBarRef? {
        val root = activity.window.decorView ?: return null
        val screenH = root.height.takeIf { it > 0 } ?: return null
        val screenW = root.width.takeIf { it > 0 } ?: return null
        val bottomZoneTop = (screenH * 0.62f).toInt()      // 屏幕底部 38% 起算为「底栏候选区」
        val minWidth = (screenW * 0.5f).toInt()            // 至少占半屏宽，排除窄条
        val maxBarH = (screenH * 0.2f).toInt().coerceAtLeast(1)

        var bestTabs: View? = null
        var bestScore = -1
        fun scan(v: View, depth: Int) {
            if (depth > MAX_DEPTH || isComposeView(v)) return
            if (v is ViewGroup) {
                val tabs = tabLikeChildren(v)
                if (tabs.size >= 2) {
                    val pos = IntArray(2)
                    v.getLocationOnScreen(pos)
                    val bottom = pos[1] + v.height
                    val wide = v.width >= minWidth
                    val inZone = bottom >= bottomZoneTop
                    if (wide && inZone) {
                        // 评分：tab 数越多、越靠屏幕底部越优先（区分真底栏与顶部/中部子标签栏）
                        val score = tabs.size * 100_000 + bottom
                        if (score > bestScore) {
                            bestScore = score
                            bestTabs = v
                        }
                    }
                }
                for (i in 0 until v.childCount) scan(v.getChildAt(i) ?: continue, depth + 1)
            }
        }
        scan(root, 0)
        val tabsView = bestTabs ?: return null
        val outer = outerFrameOf(tabsView, activity, maxBarH, minWidth, bottomZoneTop) ?: tabsView
        return BottomBarRef(outer, tabsView)
    }

    /**
     * 从 tabs 容器向上回溯，找「仍贴屏幕底部、高度条状（≤ maxBarH）、够宽」的最浅祖先作为整条
     * 底栏的 outer frame。若 tabs 自身已是 frame（无更高条状祖先），返回 null（调用方回退用 tabs）。
     */
    private fun outerFrameOf(
        tabs: View,
        activity: Activity,
        maxBarH: Int,
        minWidth: Int,
        bottomZoneTop: Int,
    ): View? {
        val decor = activity.window.decorView ?: return null
        var cur = tabs.parent as? View ?: return null
        var chosen: View? = null
        while (cur != null && cur !== decor) {
            val pos = IntArray(2)
            cur.getLocationOnScreen(pos)
            val bottom = pos[1] + cur.height
            val thin = cur.height <= maxBarH
            val wide = cur.width >= minWidth
            val inZone = bottom >= bottomZoneTop
            if (thin && wide && inZone) {
                chosen = cur
                cur = cur.parent as? View
            } else {
                break
            }
        }
        return chosen
    }

    /** 跳过本模块注入的 Compose 浮层，避免它自己被误判成底栏 / TabView。 */
    private fun isComposeView(v: View): Boolean =
        v.javaClass.name.contains("compose", ignoreCase = true)

    /** 是否像「Tab」：含图标(ImageView) 或 文字(TextView)，且（图标+文字）或（可点击）。 */
    private fun isTabLike(v: View): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val hasIcon = v.descendantsAny { it is ImageView && it.width > 0 && it.height > 0 }
        val hasText = v.descendantsAny {
            it is TextView && it.visibility == View.VISIBLE && !(it.text?.isNullOrBlank() ?: true)
        }
        val clickable = v.isClickable || v.hasOnClickListeners()
        return (hasIcon && hasText) || (hasIcon && clickable) || (hasText && clickable)
    }

    /** 一个 ViewGroup 的「直接子项里像 Tab 的」集合（用于几何定位底栏）。 */
    private fun tabLikeChildren(v: ViewGroup): List<View> {
        val out = ArrayList<View>(v.childCount)
        for (i in 0 until v.childCount) {
            val c = v.getChildAt(i) ?: continue
            if (isTabLike(c)) out.add(c)
        }
        return out
    }

    /**
     * 结构读取 TabView：返回 container 子树里「自身像 Tab 且直接子项不再像 Tab」的叶子
     * （即真正的 TabView，无论它是 container 的直接子项还是被多包了一层）。
     */
    private fun collectLeafTabs(container: View): List<View> {
        val out = mutableListOf<View>()
        fun visit(v: View) {
            if (v is ViewGroup && v.childCount > 0) {
                val anyTabChild = (0 until v.childCount).any {
                    isTabLike(v.getChildAt(it) ?: return@any false)
                }
                if (isTabLike(v) && !anyTabChild) {
                    out += v
                    return
                }
                for (i in 0 until v.childCount) visit(v.getChildAt(i) ?: continue)
            } else if (isTabLike(v)) {
                out += v
            }
        }
        visit(container)
        return out
    }

    private fun View.descendantsAny(pred: (View) -> Boolean): Boolean {
        if (pred(this)) return true
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                if (getChildAt(i)?.descendantsAny(pred) == true) return true
            }
        }
        return false
    }

    /** 按资源名定位商店底栏相关 View，找不到返回 null（供 content / 占位容器复用）。 */
    fun viewByResName(activity: Activity, name: String): View? =
        resId(activity, name).takeIf { it != 0 }?.let(activity::findViewById)

    /**
     * 原生底栏当前选中项下标。
     * 优先反射容器的 `getSelectedIndex()`，失败回退为按 selected 状态查找（抗方法改名）。
     */
    fun selectedIndexOf(container: View): Int {
        (invoke(container, "getSelectedIndex") as? Int)?.let { return it }
        return tabViewsOf(container).indexOfFirst { it.isSelected }
    }

    /** 读取指定容器的原生 TabView 列表：优先 getTabViews，失败/为空则按结构读取叶子 Tab。 */
    fun tabViewsOf(container: View): List<View> {
        (invoke(container, "getTabViews") as? List<*>)
            .filterIsInstance<View>()
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        return collectLeafTabs(container)
    }

    /** 承载 TabView 的容器（tab_container），找不到返回 null。 */
    fun tabContainer(activity: Activity): View? =
        resId(activity, ID_TAB_CONTAINER).takeIf { it != 0 }?.let(activity::findViewById)

    /** 底栏容器内的 tab_container（含自身），只在该子树内查找，规避同名子标签栏。 */
    fun tabContainerIn(scope: View): View? {
        val id = runCatching {
            scope.resources.getIdentifier(ID_TAB_CONTAINER, "id", scope.context.packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        if (scope.id == id) return scope
        return findViewInside(scope, id, 0)
    }

    private fun findViewInside(view: View, targetId: Int, depth: Int): View? {
        if (depth > MAX_DEPTH) return null
        if (view.id == targetId) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val hit = findViewInside(view.getChildAt(i) ?: continue, targetId, depth + 1)
                if (hit != null) return hit
            }
        }
        return null
    }

    /**
     * 枚举底栏的原生 TabView 列表：优先用 [locateBottomBar] 拿到的 tabs 容器，
     * 找不到才退回全局资源名查找（兼容资源结构不同的版本）。
     */
    fun tabViews(activity: Activity): List<View> {
        locateBottomBar(activity)?.let { return tabViewsOf(it.tabs) }
        tabContainer(activity)?.let { return tabViewsOf(it) }
        return emptyList()
    }

    // ═══════════════ TabView 状态读取（反射，带缓存） ═══════════════

    /** 读取 TabView 的 tag（native_market_home / purify_update 等），失败返回 null。 */
    fun tagOf(tabView: View): String? = invoke(tabView, "getTabViewTag")?.toString()

    /**
     * 读取 TabView 标题文本。
     * 优先反射 `getTitleView()`（未改名版本）；失败则扫描子树里的可见 TextView
     * （抗 getTitleView 改名——这是「标签名 / 图标映射 / 更新 tab 识别」正确与否的关键）。
     */
    fun titleOf(tabView: View): String? {
        (invoke(tabView, "getTitleView") as? TextView)?.text?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        var found: String? = null
        fun visit(v: View) {
            if (found != null) return
            if (v is TextView && v.visibility == View.VISIBLE && !v.text.isNullOrBlank()) {
                found = v.text.toString().trim()
                return
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i) ?: continue)
            }
        }
        visit(tabView)
        return found
    }

    /** 读取 TabView 是否有红点，失败返回 false。 */
    fun hasRedPoint(tabView: View): Boolean = (invoke(tabView, "hasRedPoint") as? Boolean) ?: false

    /** 读取 TabView 的数字角标，失败返回 0。 */
    fun numberOf(tabView: View): Int = (invoke(tabView, "getNumber") as? Int) ?: 0

    /** 读取 TabView 的图标 ImageView，失败返回 null。 */
    fun iconViewOf(tabView: View): ImageView? = invoke(tabView, "getIconView") as? ImageView

    /**
     * 该 TabView 当前是否显示了角标（红点或数字 > 0）。
     * 供上层做「有角标才处理」的判断与调试日志。
     */
    fun hasBadge(tabView: View): Boolean = hasRedPoint(tabView) || numberOf(tabView) > 0

    // ═══════════════ 角标 View 定位（打分，避开图标） ═══════════════

    /**
     * 找出一个 TabView 子树内所有「角标类」View（红点 / 数字 / new 标），用于隐藏。
     *
     * 判定参考 HyperModifier 的 `findNativeTabIconView` 打分思路，但目标相反——
     * 我们要的是**角标**而不是图标，因此：
     *  - 资源名含 red / badge / point / number / indicator / tag / new → 角标；
     *  - 纯数字文本的 TextView → 数字角标；
     *  - 资源名含 icon，或是 [iconViewOf] 返回的那个 ImageView → **排除**（不误伤图标）。
     */
    fun badgeViews(tabView: View): List<View> {
        val icon = iconViewOf(tabView)
        val result = mutableListOf<View>()
        fun visit(v: View) {
            if (v !== tabView) {
                val name = runCatching { v.resources.getResourceEntryName(v.id) }
                    .getOrDefault("").lowercase()
                val isIcon = v === icon || name == "icon" || name.endsWith("_icon")
                val nameHit = !isIcon && (
                    name.contains("red") || name.contains("badge") ||
                        name.contains("point") || name.contains("number") ||
                        name.contains("indicator") || name.contains("tag") ||
                        name.contains("new")
                    )
                val numberTextHit = !isIcon && v is TextView &&
                    v.text?.toString()?.trim()?.let { s -> s.isNotEmpty() && s.all { c -> c.isDigit() } } == true
                if (nameHit || numberTextHit) result += v
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) v.getChildAt(i)?.let(::visit)
            }
        }
        visit(tabView)
        return result
    }

    /** 隐藏单个 TabView 的所有角标 View，返回本次由可见转为 GONE 的数量。 */
    fun hideBadgesOn(tabView: View): Int {
        var n = 0
        badgeViews(tabView).forEach { v ->
            if (v.visibility != View.GONE) {
                v.visibility = View.GONE
                n++
            }
        }
        return n
    }

    /** 隐藏整个底栏所有 TabView 的角标，返回本次清除的角标 View 总数。 */
    fun hideAllBadges(activity: Activity): Int =
        tabViews(activity).sumOf { hideBadgesOn(it) }

    // ═══════════════ 反射底座 ═══════════════

    /** 取（并缓存）目标对象的无参方法；找不到返回 null（负缓存）。 */
    private fun cachedMethod(target: Any, name: String): Method? {
        val cls = target.javaClass
        val perClass = synchronized(methodCache) {
            methodCache.getOrPut(cls.name) { HashMap() }
        }
        synchronized(perClass) {
            if (perClass.containsKey(name)) return perClass[name]
            val m = runCatching {
                cls.getMethod(name).apply { isAccessible = true }
            }.recoverCatching {
                // 部分方法可能非 public：退化为遍历 declaredMethods（含父类）按名匹配
                var cur: Class<*>? = cls
                var found: Method? = null
                while (cur != null && cur != Any::class.java && found == null) {
                    found = cur.declaredMethods.firstOrNull {
                        it.name == name && it.parameterTypes.isEmpty()
                    }
                    cur = cur.superclass
                }
                found?.apply { isAccessible = true }
            }.getOrNull()
            perClass[name] = m
            return m
        }
    }

    /** 反射调用无参方法，任何异常都吞掉返回 null。 */
    private fun invoke(target: Any, name: String): Any? =
        runCatching { cachedMethod(target, name)?.invoke(target) }.getOrNull()
}
