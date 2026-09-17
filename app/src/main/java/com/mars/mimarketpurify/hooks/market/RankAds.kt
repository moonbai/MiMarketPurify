package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import com.mars.mimarketpurify.util.invokeAs
import dalvik.system.DexFile
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK
    override val name: String get() = "移除榜单广告"

    private val realRankClasses = listOf(
        "com.xiaomi.market.agent.AgentRankItemBinder",
        "com.xiaomi.market.agent.AgentRankItemView",
        "com.xiaomi.market.agent.AgentRankVerticalCardBinder",
        "com.xiaomi.market.agent.AgentRankVerticalCardView",
        "com.xiaomi.market.agent.AgentSubPageRankActivity",
        "com.xiaomi.market.agent.AgentRankPagerFragment",
        "com.xiaomi.market.agent.AgentRankTabFragment"
    )

    override fun init() {
        hookAdEngine()
        diagnosticScan()
    }

    // = = = = 诊断扫描（只读，不 hook） = = = =

    private fun diagnosticScan() {
        var discovered = listOf<String>()
        runCatching { discovered = discoverRankClasses() }

        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描开始 ===")

        (realRankClasses + discovered).distinct().forEach { className ->
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
                    if (name.contains("ad") || name.contains("sponsor") || name.contains("promo") ||
                        name.contains("type") || name.contains("tag")) {
                        HookEnv.base.log(Log.WARN, TAG, "[诊断]   字段: ${f.name} (${f.type.simpleName})")
                    }
                }
            }.onFailure {
                HookEnv.base.log(Log.VERBOSE, TAG, "[诊断] $className 不存在")
            }
        }

        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            val methods = engineClz.declaredMethods.map {
                "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})"
            }
            HookEnv.base.log(Log.WARN, TAG, "[诊断] AdReRankEngine: ${methods.size} 个方法")
            methods.forEach { m ->
                HookEnv.base.log(Log.WARN, TAG, "[诊断]   $m")
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[诊断] AdReRankEngine 不存在: ${it.message}")
        }

        // ★ 只 hook onBindData（精确方法名），不 hook 其他方法
        (realRankClasses + discovered).distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder()
                    .filterByName("onBindData")
                    .forEach { m ->
                        m.hooked {
                            val argsStr = args.joinToString(", ") { arg ->
                                when (arg) {
                                    null -> "null"
                                    is View -> "View#${arg.javaClass.simpleName}"
                                    is CharSequence -> "String='${arg.take(50)}'"
                                    else -> "${arg.javaClass.simpleName}"
                                }
                            }
                            HookEnv.base.log(Log.WARN, TAG, "[绑定] ${clz.simpleName}.onBindData($argsStr)")
                            return@hooked proceed()
                        }
                        HookEnv.base.log(Log.DEBUG, TAG, "[绑定] hooked ${className}.onBindData")
                    }
            }.onFailure { }
        }

        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描结束 ===")
    }

    // = = = = AI 广告引擎拦截 = = = =

    private fun hookAdEngine() {
        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            val computeMethod = engineClz.declaredMethods.firstOrNull {
                it.name == "compute" && it.parameterTypes.size == 2
            }
            if (computeMethod != null) {
                computeMethod.hooked {
                    HookEnv.base.log(Log.WARN, TAG, "[广告引擎] compute 被调用！拦截中...")
                    return@hooked null
                }
                HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked AdReRankEngine.compute ✓")
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] AdReRankEngine hook 失败: ${it.message}")
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
                    if (name.startsWith("com.xiaomi.market") && name.contains("rank", true)) found += name
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描不可用：${it.message}")
        }
        return found
    }
}
