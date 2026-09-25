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
        hookTopListV4Api() // 新增：拦截apm/toplist/v4接口返回，剔除ads=1广告
        // 诊断扫描仅调试开关开启才执行，正式关闭
        if (isDebug()) {
            diagnosticScan()
        }
    }

    // ===================== 新增：拦截 toplist/v4 接口返回JSON，过滤广告条目 =====================
    private fun hookTopListV4Api() {
        runCatching {
            // 小米市场通用网络回调类：处理api json response
            val apiRespClz = ClassUtil.loadClass("com.xiaomi.market.network.ApiResponse")
            val parseMethod = apiRespClz.methodFinder()
                .filter { it.name.contains("parse") || it.name.contains("getData") }
                .firstOrNull()
            parseMethod?.hooked {
                val rawResp = proceed()
                if(rawResp !is String) return@hooked rawResp
    
                // 判断是否是榜单v4接口返回，从上层调用栈判断
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
                        // ads=1 && adType=0 广告条目直接跳过不加入新列表
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
    

    // ===================== 兜底方案：Binder onBindData 读取model，隐藏广告item =====================
    private fun hookRankItemBindFilter() {
        realRankClasses.forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder()
                    .filterByName("onBindData")
                    .forEach { m ->
                        m.hooked {
                            val dataModel = args[0]
                            runCatching {
                                val ads = dataModel.getFieldValue("ads") as? Int ?: 0
                                val adType = dataModel.getFieldValue("adType") as? Int ?: -1
                                if (ads == 1 && adType == 0) {
                                    // 广告项，跳过渲染
                                    val view = args[1] as? View
                                    view?.visibility = View.GONE
                                    return@hooked null
                                }
                            }
                            proceed()
                        }
                        HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked $className.onBindData 兜底过滤")
                    }
            }.onFailure {}
        }
    }

    // = = = = 诊断扫描（只读 + 日志观察，仅调试开关开启时执行） = = = =
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

        // ★ debug模式下onBindData日志打印
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
            }.onFailure { ex -> }
        }

        HookEnv.base.log(Log.WARN, TAG, "=== 诊断扫描结束 ===")
    }

    // = = = = AI 广告引擎拦截（保留，处理AI重排插入广告） = = = =
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
