package com.mars.mimarketpurify.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import java.lang.reflect.Method

/**
 * 应用商店底栏（TabView 条）的**纯原生**访问器。
 *
 * 设计参考 HyperModifier 针对应用商店底栏的 Hook 思路：不去重建商店的数据模型
 * （TabInfo / PageConfig 这类反射构造在换版本时极易失效），而是**直接定位原生
 * View**——按资源 id 找到 `tab_container`，反射读取其 `TabView` 列表与每个 TabView
 * 的可视状态（标题、tag、红点、数字、图标），再对这些原生 View 做增删改。
 *
 * 这样做的收益：
 *  - 稳定：只依赖「资源 id 名」和「TabView 的公开方法名」，比反射构造私有数据类抗漂移；
 *  - 通用：本类不绑定任何具体功能，角标隐藏、底栏净化、tab 读取都可复用；
 *  - 零依赖：只用 android.view / 反射，不引入任何 UI 框架。
 *
 * 所有反射 [Method] 与资源 id 均按类/名缓存，避免高频路径重复查找。
 * 所有对外方法都用 runCatching 兜底，商店换版本导致找不到时静默降级，不抛异常。
 */
object NativeTabBar {

    /** 承载 TabView 的原生容器（其 `getTabViews()` 返回 TabView 列表）。 */
    private const val ID_TAB_CONTAINER = "tab_container"

    /** 底栏最外层容器（用于整体可见性 / alpha 判断）。 */
    private const val ID_TAB_CONTAINER_LAYOUT = "tab_container_layout"

    // ── 缓存 ──
    // 类名 -> (方法名 -> Method?)；null 表示该类没有此方法（负缓存，避免反复查找）
    private val methodCache = HashMap<String, HashMap<String, Method?>>()
    private val idCache = HashMap<String, Int>()

    // ═══════════════ 资源 id / 容器定位 ═══════════════

    private fun resId(activity: Activity, name: String): Int =
        synchronized(idCache) {
            idCache.getOrPut(name) {
                runCatching {
                    activity.resources.getIdentifier(name, "id", activity.packageName)
                }.getOrDefault(0)
            }
        }

    /** 底栏最外层容器（tab_container_layout），找不到返回 null。 */
    fun bottomContainer(activity: Activity): View? =
        resId(activity, ID_TAB_CONTAINER_LAYOUT).takeIf { it != 0 }?.let(activity::findViewById)

    /** 承载 TabView 的容器（tab_container），找不到返回 null。 */
    fun tabContainer(activity: Activity): View? =
        resId(activity, ID_TAB_CONTAINER).takeIf { it != 0 }?.let(activity::findViewById)

    /** 按资源名定位商店底栏相关 View，找不到返回 null（供悬浮底栏宿主复用）。 */
    fun viewByResName(activity: Activity, name: String): View? =
        resId(activity, name).takeIf { it != 0 }?.let(activity::findViewById)

    /**
     * 原生底栏当前选中项下标。
     * 优先反射容器的 `getSelectedIndex()`，失败回退为按 selected 状态查找。
     */
    fun selectedIndexOf(activity: Activity): Int {
        val container = tabContainer(activity) ?: return -1
        (invoke(container, "getSelectedIndex") as? Int)?.let { return it }
        return tabViews(activity).indexOfFirst { it.isSelected }
    }

    /**
     * 枚举底栏的原生 TabView 列表。
     * 优先反射容器的 `getTabViews()`；失败则回退为遍历容器直接子 View。
     */
    fun tabViews(activity: Activity): List<View> {
        val container = tabContainer(activity) ?: return emptyList()
        (invoke(container, "getTabViews") as? List<*>)
            ?.filterIsInstance<View>()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return if (container is ViewGroup) {
            (0 until container.childCount).mapNotNull { container.getChildAt(it) }
        } else {
            emptyList()
        }
    }

    // ═══════════════ TabView 状态读取（反射，带缓存） ═══════════════

    /** 读取 TabView 的 tag（native_market_home / purify_update 等），失败返回 null。 */
    fun tagOf(tabView: View): String? = invoke(tabView, "getTabViewTag")?.toString()

    /** 读取 TabView 标题文本，失败返回 null。 */
    fun titleOf(tabView: View): String? =
        (invoke(tabView, "getTitleView") as? TextView)?.text?.toString()

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
