package com.mars.mimarketpurify.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.Settings

/**
 * **纯原生**悬浮底栏宿主：用一个自绘的胶囊形浮层替换商店原生底栏的视觉，
 * 但**导航、埋点、页面切换全部仍由原生 TabView 负责**（点击转发给原生 tab 的
 * [View.performClick]），因此不改变商店任何业务语义。
 *
 * 实现参考 HyperModifier 针对应用商店底栏的做法，但**不引入 Compose / Miuix KMP**
 * 等 UI 框架——本模块只依赖 androidx.core，全部用 android.view 原生控件手写：
 *
 * 1) **定位**：按资源 id 找到 `tab_container_layout`（原底栏容器）、
 *    `tab_container`（原生 TabLayout）、`fragment_container`（内容区）、
 *    `navigation_bar_placeholder`（手势导航占位）、`tab_basic_mode_container_layout`（精简模式）；
 * 2) **读取**：反射枚举原生 TabView，读取标题 / tag / 红点 / 数字 / 图标；
 * 3) **压制**：把原底栏 `alpha=0` + 屏蔽无障碍，同时把内容区底部预留高度归零，
 *    让页面内容真正延伸到底部，形成「悬浮」而非「替换一块底栏」；
 *    同时用 [OnPreDrawListener] 在首帧前就压制，避免原生底栏闪一下；
 * 4) **浮层**：往 `android.R.id.content` 里加一个底部居中的圆角胶囊，
 *    图标**直接拷贝商店自己的 Drawable**（不猜图标语义），红点 / 数字角标照抄；
 *    点击胶囊 → 转发原生 tab → 从原生读取选中态回写，形成闭环；
 * 5) **可逆**：宿主销毁时同步还原所有被改动的原生属性（alpha、无障碍、
 *    内容底部 margin、占位可见性、监听），做到关掉开关即可完全回到原样。
 *
 * 所有对外入口都在主线程调用（由 Activity 生命周期 Hook 触发）。
 */
class FloatingBarHost private constructor(
    private val activity: Activity,
    private val overlayParent: ViewGroup,
    private val originalBottomContainer: View,
    private val basicModeContainer: View?,
    private val nativeTabLayout: View,
    private val contentView: View,
    private val navigationBarPlaceholder: View?,
) {

    private val resources = activity.resources
    private val density = resources.displayMetrics.density

    private var originalBottomAlpha = originalBottomContainer.alpha
    private var originalBottomA11y = originalBottomContainer.importantForAccessibility
    private var originalContentBottomMargin: Int? = null
    private var originalPlaceholderVisibility: Int? = null

    private var suppressing = false
    private var disposed = false

    /** 上一次同步时的底栏指纹（标签 tag / 选中项 / 可见性），用于跳过无变化帧。 */
    private var lastSignature: String = ""

    private val items = ArrayList<View>()
    private val iconViews = ArrayList<ImageView>()
    private val labelViews = ArrayList<TextView>()
    private val badgeViews = ArrayList<TextView>()


    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        sync()
        true
    }

    private val floatingBar: LinearLayout = buildFloatingBar()

    // ═══════════════════════ 构建 ═══════════════════════

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** Float 版：cornerRadius / elevation 这类需要浮点的场合用 */
    private fun dpf(v: Float): Float = v * density

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 半透明胶囊底色 + 1px 描边，深浅色各一套，贴合 HyperOS 浮层观感。 */
    private fun barBackground(): GradientDrawable = GradientDrawable().apply {
        val night = isNight()
        // 超出 Int 范围的十六进制字面量会被推断为 Long，必须显式 toInt()
        setColor(if (night) 0xCC1C1C1E.toInt() else 0xF2FFFFFF.toInt())
        cornerRadius = dpf(BAR_HEIGHT_DP / 2f)
        setStroke(dp(1), if (night) 0x33FFFFFF else 0x14000000)
    }

    private fun buildFloatingBar(): LinearLayout {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = barBackground()
            elevation = dpf(12f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height, dpf(BAR_HEIGHT_DP / 2f)
                    )
                }
            }
            clipToOutline = true
            visibility = View.GONE
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(BAR_HEIGHT_DP)
        ).apply {
            gravity = Gravity.BOTTOM
            marginStart = dp(BAR_MARGIN_DP)
            marginEnd = dp(BAR_MARGIN_DP)
            bottomMargin = dp(BAR_BOTTOM_DP)
        }
        overlayParent.addView(bar, params)

        // 手势导航条高度：把胶囊抬到系统导航条之上
        ViewCompat.setOnApplyWindowInsetsListener(bar) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            (v.layoutParams as? FrameLayout.LayoutParams)?.let { p ->
                val bottom = dp(BAR_BOTTOM_DP) + nav.bottom
                if (p.bottomMargin != bottom) {
                    p.bottomMargin = bottom
                    v.layoutParams = p
                }
            }
            insets
        }
        return bar
    }

    /** 单个胶囊项：图标（叠红点/数字）+ 文字，竖向。 */
    private fun buildItem(index: Int): LinearLayout {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onItemClicked(index) }
        }

        val iconHolder = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ICON_AREA_DP)
            )
        }
        val icon = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val badge = TextView(activity).apply {
            gravity = Gravity.CENTER
            minWidth = dp(15)
            setPadding(dp(4), 0, dp(4), 0)
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(BADGE_RED)
                cornerRadius = dpf(8f)
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(15)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(2)
                marginEnd = dp(10)
            }
        }
        iconHolder.addView(icon)
        iconHolder.addView(badge)

        val label = TextView(activity).apply {
            textSize = 10.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(0, dp(1), 0, 0)
        }

        item.addView(iconHolder)
        item.addView(label)

        // 仅在重建整排时调用（调用方已清空 items 列表），故直接按序追加
        items += item
        iconViews += icon
        labelViews += label
        badgeViews += badge
        return item
    }

    // ═══════════════════════ 同步 ═══════════════════════

    private fun tabViews(): List<View> = NativeTabBar.tabViews(activity)

    /**
     * 悬浮底栏是否应当显示。
     * 与 HyperModifier 一致的判定：原底栏与原生 TabLayout 均可见、
     * 精简模式未启用、且标签数 > 1（少于两个标签没有底栏可言）。
     */
    private fun shouldShow(tabs: List<View>): Boolean =
        originalBottomContainer.visibility == View.VISIBLE &&
            nativeTabLayout.visibility == View.VISIBLE &&
            basicModeContainer?.visibility != View.VISIBLE &&
            tabs.size > 1

    /**
     * 每帧同步：从原生 TabView 读状态 → 回写浮层。
     * 用「指纹」跳过无变化的帧，避免每帧都重建子 View 与重设 margin。
     */
    private fun sync() {
        if (disposed) return
        runCatching {
            val tabs = tabViews()
            val visible = shouldShow(tabs)
            if (!visible) {
                if (floatingBar.visibility != View.GONE) floatingBar.visibility = View.GONE
                if (suppressing) restoreNativeChrome()
                lastSignature = ""
                return
            }

            val selected = NativeTabBar.selectedIndexOf(activity).coerceIn(0, tabs.size - 1)
            val signature = buildSignature(tabs, selected)
            if (signature == lastSignature) {
                // 状态未变：仅确保压制与可见性处于目标态
                if (floatingBar.visibility != View.VISIBLE) floatingBar.visibility = View.VISIBLE
                applyNativeChromeReplacement()
                return
            }
            lastSignature = signature

            if (floatingBar.childCount != tabs.size) {
                floatingBar.removeAllViews()
                items.clear(); iconViews.clear(); labelViews.clear(); badgeViews.clear()
                repeat(tabs.size) { floatingBar.addView(buildItem(it)) }
            }

            tabs.forEachIndexed { i, tab ->
                val label = NativeTabBar.titleOf(tab) ?: FALLBACK_LABELS.getOrElse(i) { "入口${i + 1}" }
                labelViews.getOrNull(i)?.text = label
                copyIcon(tab, i, selected == i)

                val badgeView = badgeViews.getOrNull(i) ?: return@forEachIndexed
                // 「隐藏底栏角标」开启时浮层同样不展示角标，与净化目标保持一致
                val number = if (badgesAllowed()) NativeTabBar.numberOf(tab) else 0
                val hasRed = badgesAllowed() && NativeTabBar.hasRedPoint(tab)
                when {
                    number > 0 -> {
                        badgeView.text = if (number > 99) "99+" else number.toString()
                        badgeView.visibility = View.VISIBLE
                    }
                    hasRed -> {
                        badgeView.text = ""
                        badgeView.visibility = View.VISIBLE
                    }
                    else -> badgeView.visibility = View.GONE
                }

                val color = if (selected == i) selectedColor() else unselectedColor()
                val showLabel = labelsAllowed()
                labelViews.getOrNull(i)?.apply {
                    setTextColor(color)
                    visibility = if (showLabel) View.VISIBLE else View.GONE
                }
                iconViews.getOrNull(i)?.let { iv ->
                    iv.alpha = if (selected == i) 1f else 0.72f
                    iv.clearColorFilter()
                    if (tintWhenSelected && selected == i) iv.setColorFilter(color)
                }
            }

            floatingBar.visibility = View.VISIBLE
            applyNativeChromeReplacement()
        }
    }

    private fun buildSignature(tabs: List<View>, selected: Int): String {
        val sb = StringBuilder(tabs.size * 8)
        // 子开关变化也要触发重绘，故一并纳入指纹
        sb.append(if (badgesAllowed()) 'b' else '-').append(if (labelsAllowed()) 'l' else '-')
        tabs.forEachIndexed { i, tab ->
            sb.append(NativeTabBar.tagOf(tab) ?: "?").append('|')
                .append(NativeTabBar.titleOf(tab) ?: '?').append('|')
                .append(NativeTabBar.numberOf(tab)).append('|')
                .append(if (NativeTabBar.hasRedPoint(tab)) 1 else 0).append('|')
                .append(if (i == selected) 1 else 0)
                .append(';')
        }
        return sb.toString()
    }

    /** 拷贝商店自己的图标 Drawable，避免我们自带图标与商店语义不一致。 */
    private fun copyIcon(tab: View, index: Int, selected: Boolean) {
        val iv = iconViews.getOrNull(index) ?: return
        val source = NativeTabBar.iconViewOf(tab)?.drawable ?: return
        val current = iv.drawable
        if (source === current && current?.isStateful != true) return
        runCatching {
            val copy = source.constantState?.newDrawable(resources, activity.theme)?.mutate()
                ?: return@runCatching
            iv.setImageDrawable(copy)
        }
    }

    /**
     * 商店图标多为 StateListDrawable，选中态由原生自己画。
     * 纯原生浮层这里只在「选中」时按强调色着色，未选中保留原色，
     * 视觉上接近 HyperOS 底栏的选中高亮。
     */
    private val tintWhenSelected: Boolean get() = true

    private fun selectedColor(): Int {
        resolveAccent()?.let { return it }
        return ACCENT_FALLBACK
    }

    private fun unselectedColor(): Int = if (isNight()) 0xFF9B9BA0.toInt() else 0xFF8E8E93.toInt()

    /** 悬浮底栏是否展示角标：受自身子开关与「隐藏底栏角标」共同约束。 */
    private fun badgesAllowed(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_BADGE, true) &&
            !Settings.isEnabled(Settings.KEY_TAB_BADGE, true)

    /** 悬浮底栏是否展示标签文字（关闭则纯图标，图标随之放大）。 */
    private fun labelsAllowed(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)

    /** 尽量取主题的强调色（accent / colorControlActivated），取不到用兜底蓝。 */
    private fun resolveAccent(): Int? = runCatching {
        val tv = TypedValue()
        val ok = activity.theme.resolveAttribute(android.R.attr.colorAccent, tv, true) ||
            activity.theme.resolveAttribute(android.R.attr.colorControlActivated, tv, true)
        if (ok && tv.data != 0) tv.data else null
    }.getOrNull()

    // ═══════════════════════ 原生底栏压制 / 还原 ═══════════════════════

    /** 压制原生底栏：透明化 + 取消无障碍 + 收回内容区底部预留，形成真·悬浮。 */
    private fun applyNativeChromeReplacement() {
        if (originalBottomContainer.alpha != 0f) originalBottomContainer.alpha = 0f
        if (originalBottomContainer.importantForAccessibility !=
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        ) {
            originalBottomContainer.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (!suppressing) {
            suppressing = true
            (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { p ->
                if (originalContentBottomMargin == null) originalContentBottomMargin = p.bottomMargin
                if (p.bottomMargin != 0) {
                    p.bottomMargin = 0
                    contentView.layoutParams = p
                }
            }
            navigationBarPlaceholder?.let { ph ->
                if (originalPlaceholderVisibility == null) {
                    originalPlaceholderVisibility = ph.visibility
                }
                if (ph.visibility != View.GONE) ph.visibility = View.GONE
            }
        }
    }

    /** 还原原生底栏的全部改动：开关关闭 / 底栏本就该显示 / 宿主销毁时调用。 */
    private fun restoreNativeChrome() {
        originalBottomContainer.alpha = originalBottomAlpha
        originalBottomContainer.importantForAccessibility = originalBottomA11y
        originalContentBottomMargin?.let { margin ->
            (contentView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { p ->
                if (p.bottomMargin != margin) {
                    p.bottomMargin = margin
                    contentView.layoutParams = p
                }
            }
        }
        originalPlaceholderVisibility?.let { navigationBarPlaceholder?.visibility = it }
        suppressing = false
    }

    // ═══════════════════════ 交互 ═══════════════════════

    /** 点击浮层项 → 转发到原生 TabView，导航与埋点全部由商店自己完成。 */
    private fun onItemClicked(index: Int) {
        val tab = tabViews().getOrNull(index) ?: return
        runCatching { tab.performClick() }
        // 原生切页有动画，下一帧再同步一次，避免选中态滞后
        floatingBar.post { runCatching { lastSignature = ""; sync() } }
    }

    // ═══════════════════════ 生命周期 ═══════════════════════

    fun start() {
        if (disposed) return
        overlayParent.viewTreeObserver.takeIf { it.isAlive }
            ?.addOnPreDrawListener(preDrawListener)
        sync()
    }

    /** 彻底销毁：移除浮层、摘掉监听、还原所有原生属性。 */
    fun dispose() {
        if (disposed) return
        disposed = true
        runCatching {
            if (overlayParent.viewTreeObserver.isAlive) {
                overlayParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
            (floatingBar.parent as? ViewGroup)?.removeView(floatingBar)
            restoreNativeChrome()
        }
        items.clear(); iconViews.clear(); labelViews.clear(); badgeViews.clear()
    }

    companion object {
        private const val BAR_HEIGHT_DP = 58
        private const val BAR_MARGIN_DP = 14
        private const val BAR_BOTTOM_DP = 8
        private const val ICON_AREA_DP = 26
        private const val ICON_DP = 22

        private val ACCENT_FALLBACK = 0xFF0A84FF.toInt()
        private val BADGE_RED = 0xFFFF3B30.toInt()

        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")

        /** 每个 Activity 仅允许一个宿主实例。 */
        private val active = WeakHashMapOfActivity()

        /**
         * 尝试在 [activity] 上挂载悬浮底栏。
         * @return 成功返回宿主；商店版本找不到所需原生 View 时返回 null（调用方应静默降级）。
         */
        fun attach(activity: Activity): FloatingBarHost? {
            if (activity.isFinishing || activity.isDestroyed) return null
            active.get(activity)?.let { return it }

            val bottom = NativeTabBar.bottomContainer(activity) ?: return null
            val tabLayout = NativeTabBar.tabContainer(activity) ?: return null
            val content = NativeTabBar.viewByResName(activity, "fragment_container") ?: return null
            val overlay = activity.findViewById<View>(android.R.id.content) as? ViewGroup ?: return null

            val host = FloatingBarHost(
                activity = activity,
                overlayParent = overlay,
                originalBottomContainer = bottom,
                basicModeContainer = NativeTabBar.viewByResName(activity, "tab_basic_mode_container_layout"),
                nativeTabLayout = tabLayout,
                contentView = content,
                navigationBarPlaceholder = NativeTabBar.viewByResName(activity, "navigation_bar_placeholder"),
            )
            active.put(activity, host)
            host.start()
            return host
        }

        fun get(activity: Activity): FloatingBarHost? = active.get(activity)

        fun release(activity: Activity) {
            active.remove(activity)?.dispose()
        }
    }

    /** Activity 为键的弱引用表：避免宿主泄漏已销毁的 Activity。 */
    private class WeakHashMapOfActivity {
        private val map = java.util.WeakHashMap<Activity, FloatingBarHost>()

        @Synchronized fun get(a: Activity): FloatingBarHost? = map[a]

        @Synchronized fun put(a: Activity, h: FloatingBarHost) {
            map[a] = h
        }

        @Synchronized fun remove(a: Activity): FloatingBarHost? = map.remove(a)
    }
}
