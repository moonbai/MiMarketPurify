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
 * v4.2 = v4(图标兜底修复) + 全链路诊断日志。
 *
 * v4 现状(17:30 日志)：4 个 hook 全部安装成功，但 [Miux4图标] purify 注入分支从未触发 ——
 * 与静态推演(createNavigationItem 无条件调 getOs4LocalIconDrawable → 查表必 null)矛盾。
 * v4.2 在每个环节插桩，下次日志直接定位断点：
 *   ⑤ getOs4LocalIconDrawable 入口全量日志（是否被调 / 是否 null）
 *   ⑥ createNavigationItem 出参（ba/b 的 title/drawable/type 实际值）
 *   ⑦ BottomNavigator.H 收到几项、R0 每项过闸结果（渲染层终审）
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

        // ④ 图标兜底（v4 根因修复）+ ⑤ 入口全量诊断
        hookIconFallback()

        // ⑥ createNavigationItem 出参诊断
        val createItem = clz.declaredMethods.firstOrNull {
            it.name == "createNavigationItem" && it.parameterTypes.size == 1
        }
        if (createItem != null) {
            val fieldNames = runCatching {
                createItem.returnType.declaredFields.filter { !it.isSynthetic }.joinToString(",") { it.name }
            }.getOrDefault("?")
            createItem.hooked {
                val tab = args[0]
                val item = proceed()
                val tag = runCatching { (tab as? Any)?.invokeAs<String>("getTag") }.getOrNull()
                HookEnv.base.log(
                    Log.DEBUG, TAG,
                    "[图标诊断] createItem tag=$tag item=${dumpItem(item)} (fields=$fieldNames)"
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

    /** ④⑤：purify 图标注入 + getOs4LocalIconDrawable 入口全量日志。 */
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
            val tag = runCatching { invokeAs<String>("getTag") }.getOrNull()
            HookEnv.base.log(
                Log.DEBUG, TAG,
                "[图标诊断] getOs4LocalIconDrawable tag=$tag resultNull=${result == null}"
            )
            if (result == null && tag == PURIFY_UPDATE) {
                val icon = cachedPurifyIcon ?: buildUpdateIcon().also { cachedPurifyIcon = it }
                HookEnv.base.log(Log.DEBUG, TAG, "[Miux4图标] purify 图标缺失→注入兜底")
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
