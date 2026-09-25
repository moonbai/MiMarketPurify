package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import dalvik.system.DexFile
import io.github.kyuubiran.ezxhelper.core.XposedInterface
import io.github.kyuubiran.ezxhelper.core.hookMethod
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
        var rankClassNames = discoverRankClasses()
        debugLog("dex扫描rank类数量=${rankClassNames.size}")
        if (rankClassNames.isEmpty()) {
            debugLog("dex扫描无结果，回退fallback硬编码类名单")
            rankClassNames = fallbackRankClasses
        }

        rankClassNames.forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className) ?: return@forEach
                val onBindMethods = clz.declaredMethods.filter { it.name == "onBindData" }
                onBindMethods.forEach { m ->
                    val beforeBlock: (XposedInterface.Chain) -> Unit = { chain ->
                        runCatching {
                            // 区分Binder 和 View 的onBindData参数
                            val model = chain.args[0]
                            debugLog("onBindData invoked | class=$className, model=$model")

                            // 兼容多种广告字段名
                            val isAd = model.getFieldValue("ads") as? Int
                                ?: model.getFieldValue("isAd") as? Int
                                ?: model.getFieldValue("adFlag") as? Int ?: 0
                            val adType = model.getFieldValue("adType") as? Int ?: -1

                            debugLog("onBindData ads=$isAd adType=$adType")
                            if (isAd == 1) {
                                val itemView: View? = when {
                                    // Binder: args[1] = viewHolder
                                    className.contains("Binder") -> {
                                        val vh = chain.args[1]
                                        vh.getFieldValue("itemView") as? View
                                    }
                                    else -> chain.args[1] as? View
                                }
                                itemView?.visibility = View.GONE
                                debugLog("[兜底过滤] 隐藏商业广告Item ads=$isAd adType=$adType")
                            }
                        }.onFailure {
                            debugLog("onBindData 读取字段失败: ${it.message}")
                        }
                    }
                    hookMethod(m, before = beforeBlock, after = null)
                    HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked $className.onBindData 兜底过滤")
                }
            }.onFailure {
                debugLog("加载类失败 $className")
            }
        }
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
                val afterBlock: (XposedInterface.Chain) -> Any? = { chain ->
                    debugLog("[广告引擎] compute 被调用，返回安全空值")
                    when {
                        returnType == java.lang.Boolean.TYPE || returnType == java.lang.Boolean::class.java -> false
                        List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
                        else -> null
                    }
                }
                hookMethod(computeMethod, before = null, after = afterBlock)
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
