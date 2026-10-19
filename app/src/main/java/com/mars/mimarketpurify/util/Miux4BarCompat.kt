package com.mars.mimarketpurify.util

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.TAG
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * 应用商店 4.128.11 新增的 **Miux4 风格底栏**（`BottomTabLayoutMiux4` + `miuix.bottomnavigation.BottomNavigator`）
 * 兼容层。
 *
 * 背景：4.128.11 起，云控 `ClientConfig.enableUseOS4BottomTab` 打开且 OS 大版本 ≥ 4 时，
 * 主界面改用 `market_tab_with_bottom_tab_miux4` 布局——`tab_container` 这个 id 指向的是
 * `BottomTabLayoutMiux4`，它**没有旧版 `BottomTabLayout.getTabViews()`**，其子项 item 也不再是
 * `TabView`（没有 `getTabViewTag()` / `getIconView()`），导致旧反射链读不到 tab 列表与 tag。
 *
 * 设计约束（与全仓兼容策略一致）：
 * - **旧版本逻辑一律不删**：本类只作为 [NativeTabBar] 旧反射链之后的**追加分支**；
 * - 只认 Miux4 特征（类名完全匹配），非 Miux4 容器一律返回 null，调用方按原逻辑继续；
 * - 所有反射 runCatching 兜底，任何一步失败返回 null，不抛异常。
 */
object Miux4BarCompat {

    private const val MIUX4_CONTAINER = "com.xiaomi.market.widget.BottomTabLayoutMiux4"
    private const val MAX_PARENT_DEPTH = 16

    // 类名 -> (方法名+参数个数) -> Method?，null 表示无此方法（负缓存）
    private val methodCache = HashMap<String, HashMap<Pair<String, Int>, Method?>>()
    // item View -> 它在 tabInfoList 里的下标（弱键，Activity 销毁自动回收）
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

    /** 反射调用（支持 0..n 个参数），任何异常吞掉返回 null。 */
    private fun invoke(target: Any, name: String, vararg args: Any?): Any? = runCatching {
        val paramTypes = Array(args.size) { i -> args[i]?.javaClass ?: Any::class.java }
        // 基本类型参数（如 int）传入时是包装类，需要按声明类型精确匹配
        val m = cachedMethod(target, name, args.size) ?: return null
        if (paramTypes.isNotEmpty()) {
            val exact = runCatching {
                target.javaClass.getMethod(name, *paramTypes).apply { isAccessible = true }
            }.getOrNull()
            (exact ?: m).invoke(target, *args)
        } else {
            m.invoke(target)
        }
    }.getOrNull()

    /**
     * 从 Miux4 容器读取 tab item View 列表，并建立 item → 下标映射（供 [tagOf] 使用）。
     * 非 Miux4 容器或任何一步失败返回 null —— 调用方继续走旧逻辑。
     */
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
     * Miux4 item View 的 tag（等价于旧 `TabView.getTabViewTag()`）。
     * 先查 [indexByItem] 拿下标，再沿父链找 Miux4 容器，读 `getTabInfoList()[i].getTag()`。
     * 任一环节缺失返回 null，不影响旧逻辑结果。
     */
    fun tagOf(tabView: View): String? {
        // 1) 已登记映射（悬浮底栏挂载时由 collectTabs 建立）
        indexByItem[tabView]?.let { index ->
            var p: View? = tabView.parent as? View
            var d = 0
            while (p != null && d < MAX_PARENT_DEPTH) {
                if (isMiux4Container(p)) {
                    val info = (invoke(p, "getTabInfoList") as? List<*>)?.getOrNull(index) ?: return null
                    return runCatching { invoke(info, "getTag")?.toString() }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
                p = p.parent as? View
                d++
            }
        }
        // 2) 自足解析：悬浮底栏未开启时，沿父链找 Miux4 容器，逐下标比对
        //    getTabController(i).getItemView() 身份，保证原生「更新」tab 点击拦截可用
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
                        val info = infos.getOrNull(index) ?: return null
                        return runCatching { invoke(info, "getTag")?.toString() }.getOrNull()?.takeIf { it.isNotEmpty() }
                    }
                }
                return null
            }
            current = current.parent as? View
            depth++
        }
        return null
    }

    /** 调试日志：Miux4 识别结果，便于版本升级后定位。 */
    fun debug(msg: String) {
        runCatching { Log.d(TAG, "Miux4BarCompat: $msg") }
    }
}
