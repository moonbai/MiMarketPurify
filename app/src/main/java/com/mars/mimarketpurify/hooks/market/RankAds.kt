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

    private val adTokens = setOf("ad", "ads", "advert", "advertisement", "advertorial",
        "banner", "promo", "promote", "promotion", "sponsor", "sponsored")

    private val typeGetters = listOf("getComponentType", "getItemComponentType", "getItemType",
        "getType", "getViewType", "getItemViewType", "getTemplateType", "getCardType",
        "getBizType", "getStyleType", "getStyle", "getComponentName")

    private val adFlags = listOf("isAd", "getIsAd", "isAdvertise", "isAdvertisement",
        "isAdItem", "isPromote", "isPromotion", "isPromotionItem", "isSponsor", "isSponsored")

    /** 实际 rank 类（MT-MCP 反编译确认） */
    private val realRankClasses = listOf(
        "com.xiaomi.market.agent.AgentRankItemBinder",
        "com.xiaomi.market.agent.AgentRankItemView",
        "com.xiaomi.market.agent.AgentRankVerticalCardBinder",
        "com.xiaomi.market.agent.AgentRankVerticalCardView",
        "com.xiaomi.market.agent.AgentSubPageRankActivity",
        "com.xiaomi.market.agent.AgentRankPagerFragment",
        "com.xiaomi.market.agent.AgentRankTabFragment"
    )

    private val resolvedIds = Collections.synchronizedMap(mutableMapOf<String, Int>())

    private val cnLabels = setOf("广告", "推广", "赞助", "热推", "游戏推荐", "精品推荐", "编辑推荐", "推荐安装")
    private val enLabels = setOf("ad", "ads", "sponsor", "sponsored", "promoted")
    private val knownNormalTypes = setOf("nativeranklistapps", "ranklistapps", "rankapp")

    override fun init() {
        // 策略1：hook AI 广告重排引擎（suspend 函数）
        hookAdEngine()

        // 策略2：hook 实际 rank 类的 onBindData
        var bound = 0
        val discovered = discoverRankClasses()
        HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描发现 ${discovered.size} 个额外 rank 类")

        (realRankClasses + discovered).distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder().filterByName("onBindData").forEach { m ->
                    m.hooked {
                        val view = thisObject as? View ?: return@hooked proceed()
                        val bean = args.firstOrNull { isCandidateBean(it) }

                        if (bean != null && isAdBean(bean)) {
                            debugLog("onBindData: 广告 bean → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }
                        if (containsAdResourceNames(view)) {
                            debugLog("onBindData: 广告 resource → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }
                        if (containsAdLabels(view)) {
                            debugLog("onBindData: 广告标签 → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }
                        return@hooked proceed()
                    }
                    bound++
                }
            }.onFailure { }
        }

        HookEnv.base.log(Log.WARN, TAG, "$name: 已挂载 $bound 个绑定点（候选 ${realRankClasses.size} + 扫描 ${discovered.size}）")
    }

    // = = = = AI 广告引擎拦截 = = = =

    private fun hookAdEngine() {
        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            // compute(ClientAIAdRequest, Continuation) 是 suspend 函数
            val computeMethod = engineClz.declaredMethods.firstOrNull {
                it.name == "compute" && it.parameterTypes.size == 2
            }
            if (computeMethod != null) {
                computeMethod.hooked {
                    debugLog("AdReRankEngine.compute: 拦截 suspend 函数")
                    // suspend 函数返回 null 会让协程正常完成（Flow 级别），不会崩溃
                    // 如果上游对 null 有特殊处理，这里返回 proceed() 也可
                    return@hooked null
                }
                HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked AdReRankEngine.compute ✓")
            } else {
                HookEnv.base.log(Log.WARN, TAG, "[榜单广告] AdReRankEngine.compute 未找到")
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] AdReRankEngine hook 失败: ${it.message}")
        }
    }

    // = = = = Bean 检测 = = = =

    private fun isCandidateBean(arg: Any?): Boolean {
        if (arg == null) return false
        if (arg is View || arg is Number || arg is Boolean || arg is CharSequence) return false
        val n = arg.javaClass.name
        return !n.contains("Fragment") && !n.contains("Activity") && !n.contains("Context")
    }

    private fun isAdBean(bean: Any): Boolean {
        val cls = bean.javaClass
        if (tokens(cls.simpleName).any { it in adTokens }) return true
        typeGetters.forEach { getter ->
            val text = runCatching { bean.invokeAs<Any?>(getter)?.toString() }.getOrNull() ?: return@forEach
            if (tokens(text).any { it in adTokens }) return true
        }
        adFlags.forEach { flag ->
            if (runCatching { bean.invokeAs<Boolean?>(flag) }.getOrNull() == true) return true
        }
        return false
    }

    // = = = = View 检测 = = = =

    private fun containsAdResourceNames(view: View, depth: Int = 0): Boolean {
        if (depth > 5) return false
        val id = view.id
        if (id != View.NO_ID && id > 0) {
            val name = runCatching { view.resources.getResourceEntryName(id) }.getOrNull()?.lowercase() ?: ""
            if (name == "ad" || name.startsWith("ad_") || name.endsWith("_ad") ||
                name.contains("banner") || name.contains("advert")) return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount.coerceAtMost(16)) {
                val child = view.getChildAt(i) ?: continue
                if (containsAdResourceNames(child, depth + 1)) return true
            }
        }
        return false
    }

    private fun containsAdLabels(view: View, depth: Int = 0): Boolean {
        if (depth > 5) return false
        if (view is TextView) {
            val text = view.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) return false
            if (cnLabels.any { text.contains(it) }) return true
            val noSpace = text.replace(Regex("\\s+"), "").lowercase()
            if (tokens(noSpace).any { it in enLabels }) return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount.coerceAtMost(16)) {
                val child = view.getChildAt(i) ?: continue
                if (containsAdLabels(child, depth + 1)) return true
            }
        }
        return false
    }

    // = = = = 工具 = = = =

    private fun tokens(raw: String): List<String> =
        raw.replace(Regex("[^a-zA-Z0-9]+"), " ")
            .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
            .lowercase().split(' ').filter { it.isNotEmpty() }

    private fun hide(view: View) {
        runCatching {
            view.visibility = View.GONE
            view.layoutParams?.let { lp -> lp.height = 0; view.layoutParams = lp }
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
