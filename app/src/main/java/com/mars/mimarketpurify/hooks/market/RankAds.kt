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
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

object RankAds : BaseHook() {

    override val prefKey: String = Settings.KEY_RANK
    override val name: String get() = "移除榜单广告"

    // 手写fallback列表，dex扫描失败就用这份
    private val fallbackRankClasses = listOf(
        "com.xiaomi.market.agent.AgentRankItemBinder",
        "com.xiaomi.market.agent.AgentRankItemView",
        "com.xiaomi.market.agent.AgentRankVerticalCardBinder",
        "com.xiaomi.market.agent.AgentRankVerticalCardView",
        "com.xiaomi.market.agent.AgentSubPageRankActivity",
        "com.xiaomi.market.agent.AgentRankPagerFragment",
        "com.xiaomi.market.agent.AgentRankTabFragment"
    )

    // ThreadLocal标记：标记当前线程是否正在处理toplist/v4榜单请求
    private val isRankApiThread = ThreadLocal<Boolean>()

    override fun init() {
        if (!Settings.isEnabled(Settings.KEY_RANK, true)) {
            debugLog("榜单广告：开关关闭，不执行hook")
            return
        }
        debugLog("榜单广告模块初始化开始")

        hookAdEngine()
        hookTopListV4Api()
        hookRankItemBindFilter()

        if (isDebug()) {
            diagnosticScan()
        }
    }

    private fun hookTopListV4Api() {
        runCatching {
            // 1. hook请求发出处，检测url包含toplist/v4打上ThreadLocal标记
            val okhttpClz = ClassUtil.loadClass("okhttp3.Request\$Builder")
            okhttpClz?.methodFinder()?.filterByName("url")?.forEach { method ->
                method.hooked { chain ->
                    val urlArg = chain.args[0]
                    if(urlArg is String && urlArg.contains("/apm/toplist/v4")){
                        isRankApiThread.set(true)
                        debugLog("识别到榜单toplist/v4请求，设置线程标记")
                    }
                    val ret = chain.proceed()
                    isRankApiThread.remove()
                    return@hooked ret
                }
            }

            // 2. hook ApiResponse.parse，读取ThreadLocal标记做JSON过滤
            val apiRespClz = ClassUtil.loadClass("com.xiaomi.market.network.ApiResponse")
            val parseMethod = apiRespClz.declaredMethods.firstOrNull { m ->
                m.name.contains("parse") || m.name.contains("getData")
            }
            parseMethod?.hooked { chain ->
                val rawResp = chain.proceed()
                val isRankApi = isRankApiThread.get() == true
                debugLog("ApiResponse parse, isRankApi=$isRankApi")
                if (!isRankApi || rawResp !is String) return@hooked rawResp

                runCatching {
                    val root = JSONObject(rawResp)
                    // ========== 修复真实JSON层级！！ ==========
                    val outerList = root.optJSONArray("list") ?: return@runCatching
                    val firstItem = outerList.optJSONObject(0) ?: return@runCatching
                    val innerData = firstItem.optJSONObject("data") ?: return@runCatching
                    val listApp: JSONArray = innerData.optJSONArray("listApp") ?: return@runCatching

                    val newListApp = JSONArray()
                    for (i in 0 until listApp.length()) {
                        val item = listApp.optJSONObject(i) ?: continue
                        val ads = item.optInt("ads", 0)
                        val adType = item.optInt("adType", -1)
                        // 业务规则：仅 ads=1 && adType=0 才是商业广告；adType=2运营位保留
                        if (ads == 1 && adType == 0) {
                            debugLog("[JSON过滤] 剔除商业广告 ads=$ads,adType=$adType")
                            continue
                        }
                        newListApp.put(item)
                    }
                    innerData.put("listApp", newListApp)
                    return@hooked root.toString()
                }.onFailure { ex ->
                    HookEnv.base.log(Log.ERROR, TAG, "[榜单接口过滤] json解析异常", ex)
                }
                rawResp
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hook toplist/v4 ApiResponse.parse ✓")
        }.onFailure { ex ->
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] toplist/v4 hook失败，仅保留兜底Binder方案: ${ex.message}")
        }
    }

    private fun hookRankItemBindFilter() {
        var rankClassNames = discoverRankClasses()
        debugLog("dex扫描rank类数量=${rankClassNames.size}")
        // 扫描结果为空 → fallback手写列表，防止dexElements遍历失效
        if(rankClassNames.isEmpty()){
            debugLog("dex扫描无结果，回退fallback硬编码类名单")
            rankClassNames = fallbackRankClasses
        }

        rankClassNames.forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className) ?: return@forEach
                clz.declaredMethods.filter { m -> m.name == "onBindData" }.forEach { m ->
                    m.hooked { chain ->
                        val dataModel = chain.args[0]
                        runCatching {
                            val ads = dataModel.getFieldValue("ads") as? Int ?: 0
                            val adType = dataModel.getFieldValue("adType") as? Int ?: -1
                            HookEnv.base.log(Log.DEBUG, TAG, "onBindData ads=$ads adType=$adType")

                            // 业务条件：仅 ads=1 && adType=0 屏蔽
                            if (ads == 1 && adType == 0) {
                                val itemView = chain.args[1] as? View
                                itemView?.visibility = View.GONE
                                debugLog("[兜底过滤] 隐藏商业广告Item ads=$ads adType=$adType")
                            }
                        }
                        chain.proceed()
                    }
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
                methods.forEach { m -> HookEnv.base.log(Log.WARN, TAG, "[诊断]   $m") }
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
                computeMethod.hooked { chain ->
                    debugLog("[广告引擎] compute 被调用，返回安全空值")
                    return@hooked when {
                        returnType == java.lang.Boolean.TYPE || returnType == java.lang.Boolean::class.java -> false
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
        return found.distinct()
    }
}
