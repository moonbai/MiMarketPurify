package com.mars.mimarketpurify.hooks.market

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.util.Collections

object UpdateCardSkin : BaseHook() {
    override val prefKey: String? = null
    override val name: String = "更新卡片皮肤修复"

    private const val CARD_RADIUS_DP = 16f

    private val orchardDrawableNames = setOf(
        "mine_update_orchard_bg",
        "mine_update_orchard_tree_bg",
        "mine_update_dark_bg",
        "mine_update_dark_arrow"
    )
    private val orchardIconNames = setOf(
        "no_update_history",
        "no_update_new"
    )
    private val orchardDrawableIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val orchardIconIds = Collections.synchronizedSet(mutableSetOf<Int>())

    private val orchardMethods = listOf(
        "applyUpdateViewOrchardStyle",
        "applyViewOrchardState",
        "applyEmptyViewOrchardState"
    )
    private val updateViewClasses = listOf(
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateView",
        "com.xiaomi.market.business_ui.main.mine.view.MineUpdateLayout"
    )

    override fun init() {
        hookUpdateCardSkin()
    }

    private fun isNightResources(res: android.content.res.Resources): Boolean {
        return (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getCardBackgroundDrawable(res: android.content.res.Resources, isNight: Boolean): GradientDrawable {
        val bgColor = if (isNight) 0xFF242424.toInt() else 0xFFFFFFFF.toInt()
        val density = res.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = CARD_RADIUS_DP * density
        }
    }

    private fun hookUpdateCardSkin() {
        updateViewClasses.forEach { owner ->
            runCatching {
                val cls = ClassUtil.loadClass(owner)
                orchardMethods.forEach { method ->
                    cls?.methodFinder()?.filterByName(method)?.forEach { m ->
                        m.hooked { proceed() }
                    }
                }
            }.onFailure {
                HookEnv.base.log(android.util.Log.VERBOSE, TAG, "$name: 无 $owner，跳过更新卡片皮肤处理", null)
            }
        }

        runCatching {
            val resCls = ClassUtil.loadClass("android.content.res.Resources")
            resCls?.methodFinder()
                ?.filterByName("getDrawable")
                ?.forEach { m ->
                    m.hooked {
                        if (!Settings.isEnabled(Settings.KEY_ORCHARD_SKIN, false)) {
                            return@hooked proceed()
                        }
                        val res = thisObject as? android.content.res.Resources ?: return@hooked proceed()
                        // 修复：不要在indexOfFirst内部使用it，重新拿到参数索引
                        var idIdx = -1
                        for(pi in m.parameterTypes.indices){
                            if(m.parameterTypes[pi] == Int::class.javaPrimitiveType){
                                idIdx = pi
                                break
                            }
                        }
                        if(idIdx < 0) return@hooked proceed()
                        val id = args[idIdx] as? Int ?: return@hooked proceed()
                        if (id <= 0) return@hooked proceed()

                        orchardDrawableIds.clear()
                        orchardIconIds.clear()
                        orchardDrawableNames.forEach { name ->
                            runCatching {
                                val rid = res.getIdentifier(name, "drawable", "com.xiaomi.market")
                                if (rid > 0) orchardDrawableIds.add(rid)
                            }
                        }
                        orchardIconNames.forEach { name ->
                            runCatching {
                                val rid = res.getIdentifier(name, "drawable", "com.xiaomi.market")
                                if (rid > 0) orchardIconIds.add(rid)
                            }
                        }

                        if (id in orchardIconIds) {
                            return@hooked android.graphics.drawable.ColorDrawable(0)
                        }
                        if (id in orchardDrawableIds) {
                            return@hooked getCardBackgroundDrawable(res, isNightResources(res))
                        }
                        proceed()
                    }
                }
        }.onFailure {
            HookEnv.base.log(android.util.Log.ERROR, TAG, "$name: hook Resources.getDrawable 失败", it)
        }
    }
}
