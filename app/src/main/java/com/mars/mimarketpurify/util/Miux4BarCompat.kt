package com.mars.mimarketpurify.util

import android.view.View
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * 应用商店 4.128.11 Miux4 风格底栏（BottomTabLayoutMiux4 + miuix BottomNavigator）兼容层 v3。
 *
 * 相比 v2 的修正：原生「更新」tab 点击时，performClick 的 this 往往是 item 的**子 View**（标题/图标），
 * v2 用 item === tabView 严格相等会漏判。v3 在定位到 Miux4 容器后，沿父链逐层比对
 * getTabController(i).getItemView()，命中自身或任一祖先即取到下标。
 *
 * 设计约束：旧版本逻辑一律不删——本类只作为 NativeTabBar 旧反射链之后的追加分支；
 * 只认 Miux4 类名特征，非 Miux4 容器一律返回 null 走原路径；全部反射 runCatching 兜底。
 */
object Miux4BarCompat {

    private const val MIUX4_CONTAINER = "com.xiaomi.market.widget.BottomTabLayoutMiux4"
    private const val MAX_PARENT_DEPTH = 16

    // 类名 -> (方法名, 参数个数) -> Method?，null 表示无此方法（负缓存）
    private val methodCache = HashMap<String, HashMap<Pair<String, Int>, Method?>>()
    // item View -> 它在 tabInfoList 中的下标（弱键，随页面销毁自动回收）
    private val indexByItem = WeakHashMap<View, Int>()

    private fun isMiux4Container(view: View): Boolean = view.javaClass.name == MIUX4_CONTAINER

    private fun cachedMethod(target: Any, name: String, paramCount: Int): Method? {
        val cls = target.javaClass
        val key = name to paramCount
        val perClass = synchronized(methodCache) { methodCache.getOrPut(cls.name) { HashMap() } }
        synchronized(perClass) {
            perClass[key]?.let { return it }
            val m = runCatching {
                cls.methods.firstOrNull { it.name == name && it.parameterTypes.size == paramCount }
                    ?: cls.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == paramCount }
                        ?.apply { isAccessible = true }
            }.getOrNull()
            perClass[key] = m
            return m
        }
    }

    /** 反射调用（0..n 个参数），任何异常吞掉返回 null。 */
    private fun invoke(target: Any, name: String, vararg args: Any?): Any? = runCatching {
        val m = cachedMethod(target, name, args.size) ?: return null
        m.invoke(target, *args)
    }.getOrNull()

    /** 容器 + 下标 -> tag（等价旧 TabView.getTag() 语义）。 */
    private fun tagAt(container: View, index: Int): String? {
        val info = (invoke(container, "getTabInfoList") as? List<*>)?.getOrNull(index) ?: return null
        return runCatching { invoke(info, "getTag")?.toString() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Miux4 容器 -> item View 列表，并登记 item 到下标映射；非 Miux4 或失败返回 null。 */
    fun collectTabs(container: View): List<View>? {
        if (!isMiux4Container(container)) return null
        val infoList = invoke(container, "getTabInfoList") as? List<*> ?: return null
        val items = ArrayList<View>(infoList.size)
        for (index in infoList.indices) {
            val controller = invoke(container, "getTabController", index) ?: continue
            val item = invoke(controller, "getItemView") as? View ?: continue
            indexByItem[item] = index
            items += item
        }
        return items.takeIf { it.isNotEmpty() }
    }

    /** 沿父链找 Miux4 容器（至多 [MAX_PARENT_DEPTH] 层）。 */
    private fun findContainer(start: View): View? {
        var current: View? = start
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            if (isMiux4Container(current)) return current
            current = current.parent as? View
            depth++
        }
        return null
    }

    /**
     * Miux4 item（或其子 View）的 tag。三条路径，任一命中即返回：
     * 1) 已登记映射：悬浮底栏挂载时由 collectTabs 建立；
     * 2) 父链比对：找到 Miux4 容器后，沿 click view 向上逐层（含自身）与
     *    getTabController(i).getItemView() 比对——覆盖「点击落在 item 子 View」的场景；
     * 3) 兜底：仅凭容器 + 已缓存映射再试一次。
     * 保证悬浮底栏未开启时，原生「更新」tab 点击拦截依然可用。
     */
    fun tagOf(tabView: View): String? {
        // 路径 1：已登记映射（含从子 View 上溯到已登记 item）
        indexByItem[tabView]?.let { index ->
            findContainer(tabView)?.let { return tagAt(it, index) }
        }
        // 路径 2：父链逐层比对 item 身份
        val container = findContainer(tabView)
        if (container != null) {
            val size = (invoke(container, "getTabInfoList") as? List<*>)?.size ?: return null
            // 预取所有 item，避免每层重复反射
            val items = ArrayList<View>(size)
            for (index in 0 until size) {
                val controller = invoke(container, "getTabController", index) ?: continue
                (invoke(controller, "getItemView") as? View)?.let { items += index to it }
                    ?: continue
            }
            var probe: View? = tabView
            var depth = 0
            while (probe != null && depth < MAX_PARENT_DEPTH) {
                val hit = items.firstOrNull { it.second === probe }
                if (hit != null) {
                    indexByItem[probe] = hit.first
                    return tagAt(container, hit.first)
                }
                if (probe === container) break
                probe = probe.parent as? View
                depth++
            }
            return null
        }
        // 路径 3：上溯过程中先见到已登记 item
        var up: View? = tabView.parent as? View
        var d = 0
        while (up != null && d < MAX_PARENT_DEPTH) {
            indexByItem[up]?.let { index -> findContainer(up)?.let { return tagAt(it, index) } }
            up = up.parent as? View
            d++
        }
        return null
    }
}
