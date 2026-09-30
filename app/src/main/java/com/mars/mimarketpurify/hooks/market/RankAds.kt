package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import dalvik.system.DexFile
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK
    override val name: String get() = "移除榜单广告"

    private val fallbackRankClasses = listOf(
        "com.xiaomi.market.agent.AgentRankItemBinder",
        "com.xiaomi.market.agent.AgentRankItemView",
        "com.xiaomi.market.agent.AgentRankVerticalCardBinder",
        "com.xiaomi.market.agent.AgentRankVerticalCardView",
    )

    override fun init() {
        if (!Settings.isEnabled(Settings.KEY_RANK, true)) {
            debugLog("榜单广告：开关关闭，不执行hook")
            return
        }
        debugLog("榜单广告模块初始化开始")

        hookAdEngine()
        hookRankItemBindFilter()

        if (isDebug()) {
            diagnosticScan()
        }
    }

    private fun hookRankItemBindFilter() {
        val discovered = discoverRankClasses()
        debugLog("dex扫描rank类数量=${discovered.size}")
        // 始终并集 fallback 名单：即使 dex 扫描命中了部分类，也能兜住其它版本里命名不同的广告 Binder
        val rankClassNames = (discovered + fallbackRankClasses).distinct()
        debugLog("最终参与挂钩的 rank 类数量=${rankClassNames.size}")

        rankClassNames.forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className) ?: return@forEach
                val onBindMethods = clz.declaredMethods.filter { it.name == "onBindData" }
                onBindMethods.forEach { m ->
                    m.hooked {
                        runCatching {
                            val model = args[0]
                            debugLog("onBindData invoked | class=$className, model=$model")

                            val isAd = isAdModel(model)
                            debugLog("onBindData isAd=$isAd")
                            if (isAd) {
                                val itemView = extractItemView(args, className)
                                itemView?.visibility = View.GONE
                                debugLog("[兜底过滤] 隐藏商业广告Item | class=$className")
                            }
                        }.onFailure {
                            debugLog("onBindData 读取字段失败: ${it.message}")
                        }
                        proceed()
                    }
                    HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked $className.onBindData 兜底过滤")
                }
            }.onFailure {
                debugLog("加载类失败 $className")
            }
        }
    }

    /**
     * 多版本兼容地判断一个榜单条目 model 是否为广告：
     * 依次模糊读取常见广告标记字段，命中 Int==1 或 Boolean==true 即判为广告。
     * 不同 HyperOS / MIUI 版本的字段名不一（ads / isAd / adFlag / isAdItem / ad / sponsor），
     * 全部用 [getFieldValue] 容错读取，读不到也不影响其它判断。
     */
    private fun isAdModel(model: Any?): Boolean {
        if (model == null) return false
        val candidates = listOf("ads", "isAd", "adFlag", "isAdItem", "ad", "sponsor")
        for (f in candidates) {
            when (val v = runCatching { model.getFieldValue(f) }.getOrNull()) {
                is Int -> if (v == 1) return true
                is Boolean -> if (v) return true
                else -> {}
            }
        }
        return false
    }

    /**
     * 从 onBindData 参数里稳健地取出条目根 View：
     *  - 第二个参数本身就是 View（多数 Binder 的 onBindData(model, itemView)）→ 直接用；
     *  - 否则退化为读取其 itemView 字段（ViewHolder 形态），兼容 `...Binder` 之外的写法。
     */
    private fun extractItemView(args: List<Any?>, className: String): View? {
        val second = args.getOrNull(1)
        if (second is View) return second
        return runCatching { second?.getFieldValue("itemView") }.getOrNull() as? View
    }

    private fun diagnosticScan() {
        var discovered = listOf<String>()
        runCatching { discovered = discoverRankClasses() }

        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描开始 ===")
        discovered.distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                val methods = clz.declaredMethods
                    .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
                    .map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                HookEnv.base.log(Log.WARN, TAG, "[诊断] $className: ${methods.size} 个方法")
                methods.forEach { m ->
                    HookEnv.base.log(Log.WARN, TAG, "[诊断]   $m")
                }
                clz.declaredFields.forEach { f ->
                    val name = f.name.lowercase()
                    if (name.contains("ad") || name.contains("sponsor") || name.contains("promo")
                        || name.contains("type") || name.contains("tag")) {
                        HookEnv.base.log(Log.WARN, TAG, "[诊断]   字段: ${f.name} (${f.type.simpleName})")
                    }
                }
            }.onFailure { ex ->
                HookEnv.base.log(Log.VERBOSE, TAG, "[诊断] $className 不存在", ex)
            }
        }

        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            val methods = engineClz.declaredMethods.map {
                "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})"
            }
            HookEnv.base.log(Log.WARN, TAG, "[诊断] AdReRankEngine: ${methods.size} 个方法")
            methods.forEach { m -> HookEnv.base.log(Log.WARN, TAG, "[诊断]   $m") }
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "[诊断] AdReRankEngine 不存在: ${ex.message}", ex)
        }
        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描结束 ===")
    }

    private fun hookAdEngine() {
        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            val computeMethod = engineClz.declaredMethods.firstOrNull {
                it.name == "compute" && it.parameterTypes.size == 2
            }
            if (computeMethod != null) {
                val returnType = computeMethod.returnType
                computeMethod.hooked {
                    debugLog("[广告引擎] compute 被调用，返回安全空值")
                    proceed()
                    val ret = when {
                        returnType == java.lang.Boolean.TYPE || returnType == java.lang.Boolean::class.java -> false
                        List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
                        else -> null
                    }
                    return@hooked ret
                }
                HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked AdReRankEngine.compute ✓")
            }
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] AdReRankEngine hook 失败: ${ex.message}", ex)
        }
    }

    private fun discoverRankClasses(): List<String> {
        val found = mutableListOf<String>()
        runCatching {
            val pathList = getClassLoader().getFieldValue("pathList")
            val elements = pathList?.getFieldValue("dexElements") as? Array<*> ?: return found
            elements.forEach { element ->
                val dex = element?.getFieldValue("dexFile") as? DexFile
                    ?: (element?.getFieldValue("path") as? String)
                        ?.let { p -> runCatching { DexFile(p) }.getOrNull() }
                    ?: return@forEach
                val entries = dex.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith("com.xiaomi.market") && name.contains("rank", ignoreCase = true)) found += name
                }
            }
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描不可用：${ex.message}", ex)
        }
        return found.distinct()
    }
}
