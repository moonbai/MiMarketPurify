package com.mars.mimarketpurify.hooks.market

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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

    // = = = = 广告源拦截：AI 重排引擎 = = = =

    /**
     * 最高效的策略：hook ClientAIAdReRankEngine.compute()，
     * 在 AI 引擎往榜单数据里注入广告之前拦截。
     */
    private fun hookAdEngine() {
        runCatching {
            val engineClz = ClassUtil.loadClass("com.xiaomi.market.ai.ClientAIAdReRankEngine")
            // hook compute 方法：suspend fun compute(ClientAIAdRequest, Continuation)
            val computeMethod = engineClz.declaredMethods.firstOrNull {
                it.name == "compute" && it.parameterTypes.size >= 2
            }
            if (computeMethod != null) {
                computeMethod.hooked {
                    // 直接返回空的 ClientAIAdResponse，阻止广告注入
                    debugLog("AdReRankEngine.compute: 拦截，返回空响应")
                    return@hooked null
                }
                HookEnv.base.log(Log.DEBUG, TAG, "[榜单广告] hooked AdReRankEngine.compute ✓")
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "[榜单广告] AdReRankEngine hook 失败: ${it.message}")
        }
    }

    // = = = = 视图层拦截：hook 实际的 rank 类 = = = =

    /** 实际存在的 rank 类（通过 MT-MCP 反编译确认） */
    private val realRankClasses = listOf(
        "com.xiaomi.market.agent.AgentRankItemBinder",
        "com.xiaomi.market.agent.AgentRankItemView",
        "com.xiaomi.market.agent.AgentRankVerticalCardBinder",
        "com.xiaomi.market.agent.AgentRankVerticalCardView",
        "com.xiaomi.market.agent.AgentSubPageRankActivity",
        "com.xiaomi.market.agent.AgentRankPagerFragment",
        "com.xiaomi.market.agent.AgentRankTabFragment"
    )

    /** 扫描发现的额外 rank 类 */
    private var discoveredClasses = listOf<String>()

    private val resolvedIds = Collections.synchronizedMap(mutableMapOf<String, Int>())

    // rank item 内的 resource ID 名
    private const val RANK_NUMBER = "tv_app_ranking"
    private const val RANK_BADGE = "iv_app_ranking"

    // 广告标签
    private val cnLabels = setOf(
        "广告", "推广", "赞助", "热推", "游戏推荐",
        "精品推荐", "编辑推荐", "推荐安装"
    )
    private val enLabels = setOf("ad", "ads", "sponsor", "sponsored", "promoted")

    // 已知正常类型（不隐藏）
    private val knownNormalTypes = setOf(
        "nativeranklistapps", "ranklistapps", "rankapp"
    )

    override fun init() {
        // 策略1：从源头拦截 AI 广告注入
        hookAdEngine()

        // 策略2：hook 实际 rank 类的 onBindData
        var bound = 0
        discoveredClasses = discoverRankClasses()
        HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描发现 ${discoveredClasses.size} 个额外 rank 类")

        (realRankClasses + discoveredClasses).distinct().forEach { className ->
            runCatching {
                val clz = ClassUtil.loadClass(className)
                clz.methodFinder().filterByName("onBindData").forEach { m ->
                    m.hooked {
                        val view = thisObject as? View ?: return@hooked proceed()
                        val bean = args.firstOrNull { isCandidateBean(it) }

                        // 检查1：bean 类型是否含广告标记
                        if (bean != null && isAdBean(bean)) {
                            debugLog("onBindData: 检测到广告 bean → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }

                        // 检查2：resource ID 命名含广告关键词
                        if (containsAdResourceNames(view)) {
                            debugLog("onBindData: 检测到广告 resource → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }

                        // 检查3：View 文本含中文广告标签
                        if (containsAdLabels(view)) {
                            debugLog("onBindData: 检测到广告标签文本 → 隐藏 ${clz.simpleName}")
                            hide(view)
                            return@hooked null
                        }

                        return@hooked proceed()
                    }
                    bound++
                }
            }.onFailure {
                // 静默跳过不存在的类
            }
        }

        HookEnv.base.log(Log.WARN, TAG, "$name: 已挂载 $bound 个绑定点（候选 ${realRankClasses.size} + 扫描 ${discoveredClasses.size}）")
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

        // 检查类名
        if (tokens(cls.simpleName).any { it in adTokens }) return true

        // 检查各种 getter 返回值
        typeGetters.forEach { getter ->
            val text = runCatching { bean.invokeAs<Any?>(getter)?.toString() }.getOrNull()
            if (text != null) {
                if (tokens(text).any { it in adTokens }) return true
                if (text.lowercase() !in knownNormalTypes) {
                    // 非正常类型，标记可疑
                    debugLog("isAdBean: 可疑类型 ${cls.simpleName}.$getter → $text")
                }
            }
        }

        // 检查布尔标记
        adFlags.forEach { flag ->
            if (runCatching { bean.invokeAs<Boolean?>(flag) }.getOrNull() == true) return true
        }

        return false
    }

    private val adTokens = setOf(
        "ad", "ads", "advert", "advertisement", "advertorial",
        "banner", "promo", "promote", "promotion", "sponsor", "sponsored"
    )

    private val typeGetters = listOf(
        "getComponentType", "getItemComponentType", "getItemType", "getType",
        "getViewType", "getItemViewType", "getTemplateType", "getCardType",
        "getBizType", "getStyleType", "getStyle", "getComponentName"
    )

    private val adFlags = listOf(
        "isAd", "getIsAd", "isAdvertise", "isAdvertisement", "isAdItem",
        "isPromote", "isPromotion", "isPromotionItem", "isSponsor", "isSponsored"
    )

    // = = = = View 检测 = = = =

    private fun containsAdResourceNames(view: View, depth: Int = 0): Boolean {
        if (depth > 5) return false
        val id = view.id
        if (id != View.NO_ID && id > 0) {
            val name = runCatching { view.resources.getResourceEntryName(id) }
                .getOrNull()?.lowercase() ?: ""
            if (name == "ad" || name.startsWith("ad_") || name.endsWith("_ad") ||
                name.contains("banner") || name.contains("advert")) {
                return true
            }
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

    // = = = = 工具方法 = = = =

    private fun tokens(raw: String): List<String> =
        raw.replace(Regex("[^a-zA-Z0-9]+"), " ")
            .replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
            .lowercase().split(' ').filter { it.isNotEmpty() }

    private fun hide(view: View) {
        runCatching {
            view.visibility = View.GONE
            view.layoutParams?.let { lp ->
                lp.height = 0
                view.layoutParams = lp
            }
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
                    if (name.startsWith("com.xiaomi.market") && name.contains("rank", true)) {
                        found += name
                    }
                }
            }
        }.onFailure {
            HookEnv.base.log(Log.WARN, TAG, "$name: dex 扫描不可用：${it.message}")
        }
        return found
    }
}
