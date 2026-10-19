package com.mars.mimarketpurify.util

import android.view.View
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * 应用商店 4.128.11 新增 Miux4 风格底栏（BottomTabLayoutMiux4 + miuix BottomNavigator）兼容层。
 *
 * 背景：4.128.11 起，云控 enableUseOS4BottomTab 开启且 OS 大版本≥4 时，主界面改用
 * market_tab_with_bottom_tab_miux4 布局——tab_container 指向 BottomTabLayoutMiux4，它没有旧版
 * BottomTabLayout.getTabViews()，item 也不再是 TabView（无 getTabViewTag()），导致旧反射链
 * 读不到 tab 列表与 tag，悬浮底栏与「更新」入口点击拦截一并失效。
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

    /**
     * Miux4 item 的 tag。两条路径：
     * 1) 已登记映射（悬浮底栏挂载时由 collectTabs 建立）；
     * 2) 自足解析：沿父链找 Miux4 容器，逐下标比对 getTabController(i).getItemView() 身份——
     *    保证悬浮底栏未开启时，原生「更新」tab 点击拦截依然可用。
     */
    fun tagOf(tabView: View): String? {
        // 路径 1：已登记映射
        indexByItem[tabView]?.let { index ->
            var p: View? = tabView.parent as? View
            var d = 0
            while (p != null && d < MAX_PARENT_DEPTH) {
                if (isMiux4Container(p)) return tagAt(p, index)
                p = p.parent as? View
                d++
            }
        }
        // 路径 2：自足解析
        var current: View? = tabView.parent as? View
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            if (isMiux4Container(current)) {
                val infos = invoke(current, "getTabInfoList") as? List<*> ?: return null
                for (index in infos.indices) {
                    val controller = invoke(current, "getTabController", index) ?: continue
                    val item = invoke(controller, "getItemView") as? View ?: continue
                    if (item === tabView) {
                        indexByItem[tabView] = index
                        return tagAt(current, index)
                    }
                }
                return null
            }
            current = current.parent as? View
            depth++
        }
        return null
    }
}
