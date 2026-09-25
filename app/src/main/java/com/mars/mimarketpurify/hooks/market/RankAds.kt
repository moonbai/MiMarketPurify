package com.mars.mimarketpurify.hooks.market

import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.getFieldValue
import dalvik.system.DexFile
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import org.json.JSONArray
import org.json.JSONObject

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
        hookTopListV4Api()
        if (isDebug()) {
            diagnosticScan()
        }
    }

    private fun hookTopListV4Api() {
        runCatching {
            val apiRespClz = ClassUtil.loadClass("com.xiaomi.market.network.ApiResponse")
            val parseMethod = apiRespClz.methodFinder()
                .filter { methodItem -> methodItem.name.contains("parse") || methodItem.name.contains("getData") }
                .firstOrNull()
            parseMethod?.hooked { hookParam ->
                val rawResp = hookParam.proceed()
                if(rawResp !is String) return@hooked rawResp

                val stackTrace = Thread.currentThread().stackTrace
                var isRankV4Api = false
                for (stackElement in stackTrace) {
                    if(stackElement.className.contains("toplist") || stackElement.methodName.contains("toplist")){
                        isRankV4Api = true
                        break
                    }
                }
                if (!isRankV4Api) return@hooked rawResp

                runCatching {
                    val root = JSONObject(rawResp)
                    val data = root.optJSONObject("data") ?: return@runCatching
                    val listJson: JSONArray = data.optJSONArray("list") ?: return@runCatching

                    val newList = JSONArray()
                    for (i in 0 until listJson.length()) {
                        val item = listJson.optJSONObject(i) ?: continue
                        val ads = item.optInt("ads", 0)
                        val adType = item.optInt("adType", -1)
                        if (ads == 1 && adType == 0) {
                            debugLog("[榜单广告过滤] 剔除广告条目 ads=$ads,adType=$adType")
                            continue
                        }
                        newList.put(item)
                    }
                    data.put("list", newList)
                    return@hooked root.toString()
                }.onFailure { ex ->
                    HookEnv.base.log(Log.ERROR, TAG, "[榜单接口过滤] json解析异常", ex)
                }
                rawResp
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hook toplist/v4 ApiResponse.parse ✓")
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] toplist/v4 hook失败，切换兜底Binder方案: ${ex.message}")
            hookRankItemBindFilter()
        }
    }

    private fun hookRankItemBindFilter() {
        realRankClasses.forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder()
                    .filterByName("onBindData")
                    .forEach { m ->
                        m.hooked { hookParam ->
                            val dataModel = hookParam.args[0]
                            runCatching {
                                val ads = dataModel.getFieldValue("ads") as? Int ?: 0
                                val adType = dataModel.getFieldValue("adType") as? Int ?: -1
                                if (ads == 1 && adType == 0) {
                                    val view = hookParam.args[1] as? View
                                    view?.visibility = View.GONE
                                    return@hooked null
                                }
                            }
                            hookParam.proceed()
                        }
                        HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked $className.onBindData 兜底过滤")
                    }
            }.onFailure { ex -> }
        }
    }

    private fun diagnosticScan() {
        var discovered = listOf<String>()
        runCatching { discovered = discoverRankClasses() }

        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描开始 ===")

        (realRankClasses + discovered).distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                val methods = clz.declaredMethods
                    .filter { methodItem -> java.lang.reflect.Modifier.isPublic(methodItem.modifiers) }
                    .map { methodItem -> "${methodItem.name}(${methodItem.parameterTypes.joinToString { p -> p.simpleName }})" }
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
            methods.forEach { m ->
                HookEnv.base.log(Log.WARN, TAG, "[诊断]   $m")
            }
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "[诊断] AdReRankEngine 不存在: ${ex.message}", ex)
        }

        (realRankClasses + discovered).distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder()
                    .filterByName("onBindData")
                    .forEach { m ->
                        m.hooked { hookParam ->
                            val argsStr = hookParam.args.joinToString(", ") { arg ->
                                when (arg) {
                                    null -> "null"
                                    is View -> "View#${arg.javaClass.simpleName}"
                                    is CharSequence -> "String='${arg.take(50)}'"
                                    else -> "${arg.javaClass.simpleName}"
                                }
                            }
                            HookEnv.base.log(Log.WARN, TAG, "[绑定] ${clz.simpleName}.onBindData($argsStr)")
                            return@hooked hookParam.proceed()
                        }
                        HookEnv.base.log(Log.DEBUG, TAG, "[绑定] hooked ${className}.onBindData")
                    }
            }.onFailure { ex -> }
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
                computeMethod.hooked { hookParam ->
                    debugLog("[广告引擎] compute 被调用，返回安全空值")
                    return@hooked when {
                        returnType == java.lang.Boolean.TYPE ||
                                returnType == java.lang.Boolean::class.java -> false
                        List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
                        else -> null
                    }
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
        return found
    }
}
