package com.mars.mimarketpurify.hooks.market

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import android.view.View
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.Settings
import com.mars.mimarketpurify.TAG
import com.mars.mimarketpurify.init.BaseHook
import com.mars.mimarketpurify.util.MarketUpdateLauncher
import com.mars.mimarketpurify.util.invokeAs
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil
import java.io.StringReader

/**
 * v4.5 = v4.4(图标统一方案) + 图标样式统一为「圆环 + 上箭头」实心填充风格。
 *
 * 用户要求两个底栏的"更新"图标统一为参考图样式：黑色圆环内一个向上箭头（单色填充）。
 * - 悬浮底栏侧：NavIcons.Update 使用「外圆+内圆 EvenOdd 挖空 + 箭头多边形」单条 fill path
 *   （见 NavIcons.kt）。
 * - 官方底栏侧（本文件）：兜底 vector XML 使用同一组 path（EvenOdd 挖空 + fill），
 *   解析资源取商店 Resources；失败时 bitmap fallback 也用同款 Path 一次性填充。
 *
 * v4.3 修复点保留：tag 必须用 thisObject 显式 receiver。
 */
object Miux4UpdateEntry : BaseHook() {

    override val prefKey: String = Settings.KEY_UPDATE_TAB
    override val name: String get() = "Miux4更新入口兜底"

    private var lastLaunchTs = 0L
    private const val LAUNCH_DEBOUNCE_MS = 800L
    private const val PURIFY_UPDATE = "purify_update"
    @Volatile
    private var cachedPurifyIcon: Drawable? = null

    /**
     * 与 NavIcons.UPDATE_PATH 完全一致：
     * - 外圆 r=9.9；
     * - 内圆 r=8.1（EvenOdd 下挖空成环，环宽 1.8）；
     * - 上箭头闭合多边形（头 12,6.8 → 16.4,11.2 / 7.6,11.2；杆 10.6~13.4 × 11.2~16.2）。
     * 箭头位于内圆挖空区域，覆盖层数 3（奇数），仍会被填充。
     */
    private const val UPDATE_PATH =
        "M12 2.1a9.9 9.9 0 1 0 0 19.8a9.9 9.9 0 1 0 0-19.8" +
            "M12 3.9a8.1 8.1 0 1 1 0 16.2a8.1 8.1 0 1 1 0-16.2" +
            "M12 6.8l4.4 4.4h-3v5h-2.8v-5h-3z"

    override fun init() {
        val clz = runCatching {
            ClassUtil.loadClass("com.xiaomi.market.widget.BottomTabLayoutMiux4")
        }.getOrNull()
        if (clz == null) {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4] BottomTabLayoutMiux4 不存在（旧版商店），跳过")
            return
        }

        val pendingField = runCatching {
            clz.getDeclaredField("pendingItems").apply { isAccessible = true }
        }.getOrNull()
        val tabInfoListField = runCatching {
            clz.getDeclaredField("tabInfoList").apply { isAccessible = true }
        }.getOrNull()

        // ① 渲染入口
        val addTab = clz.declaredMethods.firstOrNull {
            it.name == "addFragmentTab" && it.parameterTypes.size == 2
        }
        if (addTab != null) {
            addTab.hooked {
                val tag = runCatching { (args[0] as? Any)?.invokeAs<String>("getTag") }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] addFragmentTab tag=$tag")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked addFragmentTab")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] addFragmentTab 未找到！")
        }

        // ② 提交层
        val commit = clz.declaredMethods.firstOrNull {
            it.name == "commitNavigationItems" && it.parameterTypes.isEmpty()
        }
        if (commit != null) {
            commit.hooked {
                val pending = runCatching { (pendingField?.get(thisObject) as? List<*>)?.size }.getOrNull()
                val tabs = runCatching { (tabInfoListField?.get(thisObject) as? List<*>)?.size }.getOrNull()
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] commit pendingItems=$pending tabInfoList=$tabs")
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4渲染] hooked commitNavigationItems")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4渲染] commitNavigationItems 未找到！")
        }

        // ③ 点击拉起更新页
        val onSelect = clz.declaredMethods.firstOrNull {
            it.name == "onTabSelected" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (onSelect != null) {
            onSelect.hooked {
                val index = args[0] as? Int ?: return@hooked proceed()
                val tag = runCatching {
                    val infos = thisObject?.invokeAs<List<*>>("getTabInfoList")
                    infos?.getOrNull(index)?.invokeAs<String>("getTag")
                }.getOrNull()
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
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4更新入口] hooked onTabSelected(I)")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[Miux4更新入口] onTabSelected 未找到！")
        }

        // ④⑤ 图标兜底 + 入口全量诊断
        hookIconFallback()

        // ⑥ createNavigationItem 出参诊断
        val createItem = clz.declaredMethods.firstOrNull {
            it.name == "createNavigationItem" && it.parameterTypes.size == 1
        }
        if (createItem != null) {
            createItem.hooked {
                val tab = args[0]
                val item = proceed()
                val tag = runCatching { (tab as? Any)?.invokeAs<String>("getTag") }.getOrNull()
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "[图标诊断] createItem tag=$tag item=${dumpItem(item)}"
                )
                return@hooked item
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[图标诊断] hooked createNavigationItem")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] createNavigationItem 未找到！")
        }

        // ⑦ navigator 渲染闸门诊断
        hookNavigatorDiag()
    }

    /**
     * ④⑤：purify 图标注入 + getOs4LocalIconDrawable 入口全量日志。
     * tag 用 thisObject 显式 receiver（v4.3 修复点，隐式 this 恒读出 null）。
     */
    private fun hookIconFallback() {
        val tabInfoClz = runCatching { ClassUtil.loadClass("com.xiaomi.market.model.TabInfo") }.getOrNull()
        if (tabInfoClz == null) {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] TabInfo 不存在，跳过")
            return
        }
        val getIcon = tabInfoClz.declaredMethods.firstOrNull {
            it.name == "getOs4LocalIconDrawable" && it.parameterTypes.isEmpty()
        }
        if (getIcon == null) {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] getOs4LocalIconDrawable 未找到！")
            return
        }
        getIcon.hooked {
            val result = proceed()
            val tag = runCatching { (thisObject as? Any)?.invokeAs<String>("getTag") }.getOrNull()
            HookEnv.base.log(
                Log.DEBUG, TAG,
                "[图标诊断] getOs4LocalIconDrawable tag=$tag resultNull=${result == null}"
            )
            if (result == null && tag == PURIFY_UPDATE) {
                val icon = cachedPurifyIcon ?: buildUpdateIcon().also { cachedPurifyIcon = it }
                val kind = if (icon is android.graphics.drawable.VectorDrawable) "vector" else "bitmap"
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4图标] purify 图标缺失→注入兜底($kind)")
                return@hooked icon
            }
            return@hooked result
        }
        HookEnv.base.log(Log.DEBUG, TAG, "[图标诊断] hooked TabInfo.getOs4LocalIconDrawable")
    }

    /** ⑦：BottomNavigator.H（收到几项）与 R0（每项过闸结果）。 */
    private fun hookNavigatorDiag() {
        val navClz = runCatching { ClassUtil.loadClass("miuix.bottomnavigation.BottomNavigator") }.getOrNull()
        if (navClz == null) {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] BottomNavigator 不存在，跳过")
            return
        }
        val h = navClz.declaredMethods.firstOrNull {
            it.name == "H" && it.parameterTypes.size == 1 &&
                List::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (h != null) {
            h.hooked {
                val list = args[0] as? List<*>
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "[图标诊断] navigator.H 收到 ${list?.size} 项: " +
                        (list?.joinToString("; ") { dumpItem(it) } ?: "null")
                )
                return@hooked proceed()
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[图标诊断] hooked navigator.H")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] navigator.H 未找到！")
        }
        val r0 = navClz.declaredMethods.firstOrNull {
            it.name == "R0" && it.parameterTypes.size == 1
        }
        if (r0 != null) {
            r0.hooked {
                val result = proceed()
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "[图标诊断] R0=$result item=${dumpItem(args[0])}"
                )
                return@hooked result
            }
            HookEnv.base.log(Log.DEBUG, TAG, "[图标诊断] hooked navigator.R0")
        } else {
            HookEnv.base.log(Log.WARN, TAG, "[图标诊断] navigator.R0 未找到！")
        }
    }

    /** 反射 dump 对象全部非合成字段（title/type/drawable 等）。 */
    private fun dumpItem(item: Any?): String {
        if (item == null) return "null"
        return runCatching {
            item.javaClass.declaredFields.filter { !it.isSynthetic }.joinToString(", ") { f ->
                f.isAccessible = true
                val v = f.get(item)
                val short = when {
                    v == null -> "null"
                    v is String -> "\"$v\""
                    v is Drawable -> "Drawable#" + Integer.toHexString(System.identityHashCode(v))
                    else -> v.toString().take(60)
                }
                "$f=$short".replace("class ", "").let { it.substringAfterLast(' ') }
            }
        }.getOrDefault("dump失败:" + item.javaClass.name)
    }

    /**
     * v4.5：官方底栏兜底图标 = 圆环 + 上箭头（实心填充，与参考图/悬浮底栏同款）。
     * 解析硬编码 VectorDrawable XML，Resources 取商店的（dp→px 一致）；
     * AppGlobals 不可用或解析失败时退回手绘同款位图，保证返回非 null。
     *
     * XML 使用 `android:fillType="evenOdd"` 让内圆挖空成环；箭头在同一 path 内，
     * 落在挖空区（覆盖 3 层，奇数），仍会被填充。
     */
    private fun buildUpdateIcon(): Drawable {
        runCatching {
            val appCtx = ClassUtil.loadClass("com.xiaomi.market.AppGlobals")
                .getMethod("getContext").invoke(null) as? Context
            if (appCtx != null) {
                val xml = """
                    <vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="24dp" android:height="24dp"
                        android:viewportWidth="24" android:viewportHeight="24">
                        <path android:fillColor="#FF000000" android:fillType="evenOdd"
                            android:pathData="$UPDATE_PATH" />
                    </vector>
                """.trimIndent()
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(xml))
                val vector = Drawable.createFromXml(appCtx.resources, parser)
                if (vector != null) return vector
            }
        }.onFailure {
            HookEnv.base.log(Log.DEBUG, TAG, "[Miux4图标] vector 解析失败，退回手绘: ${it.message}")
        }
        return drawBitmapFallback()
    }

    /** 手绘同款位图（圆环 + 上箭头，实心填充），仅作为 vector 解析失败时的兜底，保证非 null。 */
    private fun drawBitmapFallback(): Drawable {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        // viewport 24 → 96px，scale=4
        val path = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(48f, 48f, 39.6f, Path.Direction.CW) // 外圆 r=9.9
            addCircle(48f, 48f, 32.4f, Path.Direction.CW) // 内圆 r=8.1（EvenOdd 挖空）
            // 箭头多边形（(12,6.8)→(16.4,11.2)→(13.4,11.2)→(13.4,16.2)→(10.6,16.2)→(10.6,11.2)→(7.6,11.2)）
            moveTo(48f, 27.2f)
            lineTo(65.6f, 44.8f)
            lineTo(53.6f, 44.8f)
            lineTo(53.6f, 64.8f)
            lineTo(42.4f, 64.8f)
            lineTo(42.4f, 44.8f)
            lineTo(30.4f, 44.8f)
            close()
        }
        canvas.drawPath(path, fill)
        return BitmapDrawable(bmp)
    }
}