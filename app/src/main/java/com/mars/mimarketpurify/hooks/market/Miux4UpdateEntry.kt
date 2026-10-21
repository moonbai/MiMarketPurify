package com.mars.mimarketpurify.hooks.market

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil

/**
 * Miux4 底栏「更新」入口兜底 v4 = v3.1 全部日志/点击 + 图标根因修复。
 *
 * v4 根因（2026-10-21 17:20 日志 + APK 静态终审）：
 *   日志：addFragmentTab tag=purify_update ✓ → commit pendingItems=3 ✓，底栏仍只显示 2 项。
 *   静态：commitNavigationItems → bottomNavigator.H(list) 逐项过两道闸：
 *     - ba/c 适配器上限 d=5（非瓶颈）
 *     - R0(item)：要求 item.drawable != null（type 0/1/4 所有路径都依赖 drawable）
 *   drawable 来源：createNavigationItem → TabInfo.getOs4LocalIconDrawable()
 *     → DefaultTabResource.defaultOs4TabIcons[tag] 查表；表内只有原生 native_market_*，
 *       "purify_update" 必然查到 null → R0=false → 第 3 项被静默丢弃 → 底栏只剩 2 项。
 *
 * 修复：hook TabInfo.getOs4LocalIconDrawable()，purify_update 返回 null 时注入兜底
 * 更新图标（黑色箭头位图，miuix 底栏统一 tint，深浅色模式均可见）。①②③ 与 v3.1 一致。
 */
object Miux4UpdateEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "Miux4更新入口兜底"

    private var lastLaunchTs = 0L
    private const val LAUNCH_DEBOUNCE_MS = 800L
    private const val PURIFY_UPDATE = "purify_update"
    @Volatile
    private var cachedPurifyIcon: Drawable? = null

    override fun init() {
        val clz = runCatching {
            ClassUtil.loadClass("com.xiaomi.market.widget.BottomTabLayoutMiux4")
        }.getOrNull()
        if (clz == null) {
            debugLog("BottomTabLayoutMiux4 不存在（旧版商店），跳过")
            return
        }

        val pendingField = runCatching {
            clz.getDeclaredField("pendingItems").apply { isAccessible = true }
        }.getOrNull()
        val tabInfoListField = runCatching {
            clz.getDeclaredField("tabInfoList").apply { isAccessible = true }
        }.getOrNull()

        // ① 渲染入口验证（每次实际进入原生 addFragmentTab 的 tag）
        val addTab = clz.declaredMethods.firstOrNull {
            it.name == "addFragmentTab" && it.parameterTypes.size == 2
        }
        if (addTab != null) {
            addTab.hooked {
                val tag = runCatching {
                    (args[0] as? Any)?.invokeAs<String>("getTag")
                }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] addFragmentTab tag=$tag")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked addFragmentTab")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] addFragmentTab 未找到！")
        }

        // ② 提交层验证（pendingItems=真正交给 navigator 的项数）
        val commit = clz.declaredMethods.firstOrNull {
            it.name == "commitNavigationItems" && it.parameterTypes.isEmpty()
        }
        if (commit != null) {
            commit.hooked {
                val pending = runCatching {
                    (pendingField?.get(thisObject) as? List<*>)?.size
                }.getOrNull()
                val tabs = runCatching {
                    (tabInfoListField?.get(thisObject) as? List<*>)?.size
                }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] commit pendingItems=$pending tabInfoList=$tabs")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked commitNavigationItems")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] commitNavigationItems 未找到！")
        }

        // ③ 点击拉起更新页
        val onSelect = clz.declaredMethods.firstOrNull {
            it.name == "onTabSelected" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (onSelect == null) {
            debugLog("BottomTabLayoutMiux4.onTabSelected(I) 未找到，跳过")
        } else {
            onSelect.hooked {
                val index = args[0] as? Int ?: return@hooked proceed()
                val tag = runCatching {
                    val infos = thisObject?.invokeAs<List<*>>("getTabInfoList")
                    infos?.getOrNull(index)?.invokeAs<String>("getTag")
                }.getOrNull()
                debugLog("Miux4 onTabSelected($index) tag=$tag")
                if (tag == PURIFY_UPDATE) {
                    val now = System.currentTimeMillis()
                    if (now - lastLaunchTs >= LAUNCH_DEBOUNCE_MS) {
                        lastLaunchTs = now
                        val ctx = (thisObject as? View)?.context
                        if (ctx != null) {
                            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4更新入口] 命中 purify_update，拉起更新页")
                            MarketUpdateLauncher.launch(ctx)
                        }
                    }
                }
                proceed()
            }
            debugLog("hooked BottomTabLayoutMiux4.onTabSelected(I)")
        }

        // ④ v4 根因修复：purify_update 图标兜底
        hookIconFallback()
    }

    /** v4：TabInfo.getOs4LocalIconDrawable() 对 purify_update 必然 null → 注入兜底图标。 */
    private fun hookIconFallback() {
        val tabInfoClz = runCatching {
            ClassUtil.loadClass("com.xiaomi.market.model.TabInfo")
        }.getOrNull()
        if (tabInfoClz == null) {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4图标] TabInfo 不存在，跳过")
            return
        }
        val getIcon = tabInfoClz.declaredMethods.firstOrNull {
            it.name == "getOs4LocalIconDrawable" && it.parameterTypes.isEmpty()
        }
        if (getIcon == null) {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4图标] getOs4LocalIconDrawable 未找到！")
            return
        }
        getIcon.hooked {
            val result = proceed()
            if (result != null) return@hooked result
            val tag = runCatching { invokeAs<String>("getTag") }.getOrNull()
            if (tag != PURIFY_UPDATE) return@hooked result
            val icon = cachedPurifyIcon ?: buildUpdateIcon().also { cachedPurifyIcon = it }
            val obj = thisObject ?: return@hooked result
            val title = runCatching { obj.invokeAs<String>("getTitle") }.getOrNull()
            val showTitle = runCatching {
                val f = obj.javaClass.getDeclaredField("showTitle").apply { isAccessible = true }
                f.getBoolean(obj)
            }.getOrDefault(false)
            HookEnv.base.log(
                Log.DEBUG, TAG,
                "[Miux4图标] purify 图标缺失→注入兜底 title=$title showTitle=$showTitle"
            )
            return@hooked icon
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[Miux4图标] hooked TabInfo.getOs4LocalIconDrawable")
    }

    /** 更新箭头图标：下箭头 + 托盘，黑色描边位图；miuix 底栏统一 tint，深浅色均可见。 */
    private fun buildUpdateIcon(): Drawable {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        canvas.drawLine(48f, 14f, 48f, 54f, stroke)
        val head = Path().apply {
            moveTo(25f, 50f)
            lineTo(48f, 74f)
            lineTo(71f, 50f)
            close()
        }
        canvas.drawPath(head, fill)
        canvas.drawLine(18f, 84f, 78f, 84f, stroke)
        return BitmapDrawable(bmp)
    }
}
