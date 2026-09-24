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
import com.mars.mimarketpurify.HookEnv
import com.mars.mimarketpurify.TAG
import androidx.core.view.WindowInsetsCompat
import android.view.animation.OvershootInterpolator
import com.mars.mimarketpurify.Settings
import kotlin.math.min

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
    initBottomContainer: View,
    initBasicModeContainer: View?,
    initTabLayout: View,
    initContent: View,
    initNavPlaceholder: View?,
) {

    // 商店切页（如进榜单）时会重建底栏 View 实例。这些引用必须可替换，
    // 否则我们会一直压制那个已脱离视图树的旧实例，新底栏反而不受控。
    // 构造参数刻意用 initXxx 命名，避免与属性同名造成作用域歧义。
    private var originalBottomContainer: View = initBottomContainer
    private var basicModeContainer: View? = initBasicModeContainer
    private var nativeTabLayout: View = initTabLayout
    private var contentView: View = initContent
    private var navigationBarPlaceholder: View? = initNavPlaceholder

    private val resources = activity.resources
    private val density = resources.displayMetrics.density

    /**
     * 调试日志。
     * 注意：本类**不是 BaseHook 子类**，拿不到 BaseHook.debugLog，
     * 故在此实现同语义版本（同样受 KEY_RANK_DEBUG 门控），前缀保持与模块日志一致。
     */
    private fun debugLog(msg: String) {
        runCatching {
            if (Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) {
                HookEnv.base.log(android.util.Log.DEBUG, TAG, "[悬浮底栏] $msg")
            }
        }
    }

    private var originalBottomAlpha = originalBottomContainer.alpha
    private var originalBottomA11y = originalBottomContainer.importantForAccessibility
    private var originalContentBottomMargin: Int? = null
    private var originalPlaceholderVisibility: Int? = null

    private var suppressing = false
    private var disposed = false

    /** 上一次同步时的底栏指纹（标签 tag / 选中项 / 可见性），用于跳过无变化帧。 */
    private var lastSignature: String = ""

    /** 上一次判定「不可见」的原因，用于只在原因变化时打日志，避免刷屏。 */
    private var lastBlocker: String? = null

    private val items = ArrayList<View>()
    private val iconViews = ArrayList<ImageView>()
    private val labelViews = ArrayList<TextView>()
    // 角标是 TextView（要用到 text 属性），不能退化成 View，否则 badgeView.text 无法解析
    private val badgeViews = ArrayList<TextView>()

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        sync()
        true
    }

    /** 液态选中高亮层：必须先于 itemsRow 加入，才能画在各项之下。 */
    private val pill = LiquidSelectionView(activity)

    /** 承载各 tab 项的横向行。 */
    private val itemsRow: LinearLayout = buildItemsRow()

    /** 悬浮底栏根容器（圆角胶囊背景 + 阴影 + 系统导航避让）。 */
    private val barRoot: FrameLayout = buildBarRoot()

    /** 上一次同步的选中项，用于判断是否需要播放液态过渡动画。 */
    private var lastSelected = -1

    // ═══════════════════════ 构建 ═══════════════════════

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** Float 版：cornerRadius / elevation 这类需要浮点的场合用 */
    private fun dpf(v: Float): Float = v * density

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 圆角半径 px：用户可调，并收敛到「不超过胶囊高度一半」。 */
    private fun barRadiusPx(): Float =
        min(dpf(Settings.floatingBarRadiusDp().toFloat()), dpf(BAR_HEIGHT_DP / 2f))

    /** 底色：深浅色各一套基色，透明度由用户设置（百分比）写入 alpha 通道。 */
    private fun barFillPx(): Int {
        val base = if (isNight()) 0x1C1C1E else 0xFFFFFF
        val a = (Settings.floatingBarAlphaPercent() * 255 / 100).coerceIn(0, 255)
        return (a shl 24) or base
    }

    /** 半透明胶囊底色 + 1px 描边；透明度与圆角均为用户可调参数。 */
    private fun barBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(barFillPx())
        cornerRadius = barRadiusPx()
        setStroke(dp(1), if (isNight()) 0x33FFFFFF else 0x14000000)
    }

    /** 参数变化时重绘外观（透明度 / 圆角 / 液态开关）。 */
    private fun refreshBarStyle() {
        barRoot.background = barBackground()
        barRoot.outlineProvider = roundedOutline()
        pill.configure(selectedColor(), min(barRadiusPx(), dpf(PILL_HEIGHT_DP / 2f)))
        pill.visibility = if (liquidOn()) View.VISIBLE else View.GONE
    }

    private fun roundedOutline(): ViewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, barRadiusPx())
        }
    }

    /** 各 tab 项所在的横向行，铺满根容器。 */
    private fun buildItemsRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun buildBarRoot(): FrameLayout {
        val bar = FrameLayout(activity).apply {
            background = barBackground()
            elevation = dpf(12f)
            outlineProvider = roundedOutline()
            clipToOutline = true
            visibility = View.GONE
        }
        // 先加 pill 再加 itemsRow：FrameLayout 后加入的子视图绘制在上层，
        // 因此高亮胶囊会落在各项之下，不会盖住图标和文字。
        bar.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        bar.addView(itemsRow)

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
        // 尺寸变化（旋转 / 导航条收起）后重算液态胶囊几何
        itemsRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            runCatching { updatePillSlots() }
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

        // 容器宽度取 wrap_content 并显式居中：这样角标(TOP|END)贴在图标右上角，
        // 而不是被推到整列的最右侧，导致「图标居中、角标飘在外」的错位观感。
        val iconHolder = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(ICON_AREA_DP)
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val icon = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply {
                gravity = Gravity.CENTER
            }
            // CENTER_INSIDE 而非 FIT_CENTER：FIT_CENTER 会把小图标放大，
            // 商店各图标原始尺寸不同，放大后各 tab 视觉大小不一，看起来就是没对齐。
            scaleType = ImageView.ScaleType.CENTER_INSIDE
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
                topMargin = dp(1)
                marginEnd = dp(1)
            }
        }
        iconHolder.addView(icon)
        iconHolder.addView(badge)

        val label = TextView(activity).apply {
            textSize = 10.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            // 显式居中：标题被 tab 宽度约束时若不设 gravity，文本会左对齐，
            // 与居中的图标就错开了。
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(1)
            }
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

    /**
     * 读取原生 TabView：**只用宿主绑定的那个容器**，不再按资源名全局查找。
     * 榜单/游戏页会自建同名 `tab_container` 的子标签栏，全局查找会读到它
     * （实测「标签数=1」），从而误判底栏不存在并把原生底栏放回去。
     */
    private fun tabViews(): List<View> = NativeTabBar.tabViewsOf(nativeTabLayout)

    /**
     * 悬浮底栏是否应当显示。
     * 与 HyperModifier 一致的判定：原底栏与原生 TabLayout 均可见、
     * 精简模式未启用、且标签数 > 1（少于两个标签没有底栏可言）。
     */
    private fun shouldShow(tabs: List<View>): Boolean =
        visibilityBlocker(tabs) == null

    /**
     * 返回「当前为何不能显示悬浮底栏」的具体原因，可显示时返回 null。
     * 关键：**必须判 isAttachedToWindow**——已脱离视图树的旧 View 依然报告
     * visibility==VISIBLE，只看 visibility 会误判为「可以继续压制」。
     */
    private fun visibilityBlocker(tabs: List<View>): String? = when {
        !originalBottomContainer.isAttachedToWindow -> "底栏容器已脱离视图树"
        originalBottomContainer.visibility != View.VISIBLE -> "底栏容器不可见"
        !nativeTabLayout.isAttachedToWindow -> "tab_container 已脱离视图树"
        nativeTabLayout.visibility != View.VISIBLE -> "tab_container 不可见"
        basicModeContainer?.visibility == View.VISIBLE -> "精简模式底栏占用"
        tabs.size <= 1 -> "标签数=${tabs.size} container=${nativeTabLayout.javaClass.simpleName}/${tabs.size}"
        else -> null
    }

    /**
     * 重新解析可能已被商店替换的原生 View 引用。
     * 找到新实例时一并重置「原始值快照」，并把 suppressing 置回，
     * 让新实例在下一帧被重新压制。
     *
     * @return 是否真的换掉了引用（换掉则本帧数据已过期，应作废重算）
     */
    private fun refreshViewRefs(force: Boolean = false): Boolean {
        var rebound = false
        // force=true 用于切页时机：部分版本榜单页会另建一套底栏，
        // 此时旧实例「仍挂在树上」，只靠 isAttachedToWindow 判断换不过来。
        if (force || !originalBottomContainer.isAttachedToWindow) {
            NativeTabBar.bottomContainer(activity)
                ?.takeIf { it !== originalBottomContainer && it.isAttachedToWindow }
                ?.let {
                    debugLog("底栏容器实例已切换，重新绑定并重设压制")
                    originalBottomContainer = it
                    originalBottomAlpha = it.alpha
                    originalBottomA11y = it.importantForAccessibility
                    suppressing = false
                    rebound = true
                }
        }
        if (force || !nativeTabLayout.isAttachedToWindow) {
            // 同样只在底栏容器子树内找，找不到再退回全局（兼容结构不同的版本）
            (NativeTabBar.tabContainerIn(originalBottomContainer)
                ?: NativeTabBar.tabContainer(activity))
                ?.takeIf { it !== nativeTabLayout && it.isAttachedToWindow }
                ?.let { nativeTabLayout = it; rebound = true }
        }
        if (!contentView.isAttachedToWindow) {
            NativeTabBar.viewByResName(activity, "fragment_container")
                ?.takeIf { it !== contentView && it.isAttachedToWindow }
                ?.let {
                    contentView = it
                    originalContentBottomMargin = null
                }
        }
        basicModeContainer?.takeIf { !it.isAttachedToWindow }?.let { basicModeContainer = null }
        return rebound
    }

    /** 连续多少帧判定不可见才真正还原，避免切页瞬间的单帧空状态把原生底栏放回去。 */
    private var invisibleFrames = 0

    /**
     * 每帧同步：从原生 TabView 读状态 → 回写浮层。
     * 用「指纹」跳过无变化的帧，避免每帧都重建子 View 与重设 margin。
     */
    private fun sync() {
        if (disposed) return
        runCatching {
            if (refreshViewRefs()) {
                // 引用刚换过，本帧读到的还是旧树的数据，直接作废等下一帧
                lastSignature = ""
                return
            }
            val tabs = tabViews()
            val blocker = visibilityBlocker(tabs)
            if (blocker != null) {
                invisibleFrames++
                if (blocker != lastBlocker) {
                    debugLog("悬浮底栏暂停（$blocker），连续 $invisibleFrames 帧")
                    lastBlocker = blocker
                }
                // 去抖：连续多帧不可见才还原原生底栏，避免切页瞬间抖动
                if (invisibleFrames >= RESTORE_AFTER_FRAMES) {
                    if (barRoot.visibility != View.GONE) barRoot.visibility = View.GONE
                    if (suppressing) {
                        restoreNativeChrome()
                        debugLog("已还原原生底栏（$blocker）")
                    }
                    lastSignature = ""
                }
                return
            }
            if (lastBlocker != null) {
                debugLog("恢复显示悬浮底栏（先前阻塞：$lastBlocker）")
            }
            lastBlocker = null
            invisibleFrames = 0

            val selected = NativeTabBar.selectedIndexOf(nativeTabLayout).coerceIn(0, tabs.size - 1)
            val selectionChanged = lastSelected != selected
            val signature = buildSignature(tabs, selected)
            if (signature == lastSignature) {
                // 状态未变：仅确保压制与可见性处于目标态
                if (barRoot.visibility != View.VISIBLE) barRoot.visibility = View.VISIBLE
                applyNativeChromeReplacement()
                return
            }
            lastSignature = signature
            styleDirty = true
            // 切页时强制重查一次，抓住商店可能另建的那套底栏
            if (refreshViewRefs(force = true)) {
                lastSignature = ""
                return
            }

            val rebuilt = itemsRow.childCount != tabs.size
            if (rebuilt) {
                pillHasSlots = false
                itemsRow.removeAllViews()
                items.clear(); iconViews.clear(); labelViews.clear(); badgeViews.clear()
                repeat(tabs.size) { itemsRow.addView(buildItem(it)) }
            }
            // 透明度 / 圆角 / 液态开关可能刚被用户改动，随状态变化一并应用
            if (rebuilt || styleDirty) {
                refreshBarStyle()
                styleDirty = false
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

                val onPill = liquidOn() && selected == i
                val color = when {
                    onPill -> ON_PILL_CONTENT                    // 胶囊上的内容反白
                    selected == i -> selectedColor()             // 无胶囊时退回强调色
                    else -> unselectedColor()
                }
                val showLabel = labelsAllowed()
                labelViews.getOrNull(i)?.apply {
                    setTextColor(color)
                    visibility = if (showLabel) View.VISIBLE else View.GONE
                }
                iconViews.getOrNull(i)?.let { iv ->
                    iv.alpha = if (selected == i) 1f else 0.72f
                    iv.clearColorFilter()
                    if (onPill) iv.setColorFilter(ON_PILL_CONTENT)
                    else if (tintWhenSelected && selected == i) iv.setColorFilter(color)
                    // 选中图标轻微放大 + 过冲，配合胶囊一起构成液态观感
                    val scale = if (selected == i) ICON_SCALE_SELECTED else 1f
                    if (selectionChanged && !rebuilt) {
                        iv.animate().scaleX(scale).scaleY(scale)
                            .setDuration(ICON_ANIM_MS)
                            .setInterpolator(OvershootInterpolator(ICON_OVERSHOOT))
                            .start()
                    } else {
                        iv.scaleX = scale; iv.scaleY = scale
                    }
                }
            }

            // 液态高亮：重建或首次拿到选中位时跳变落位，仅切页时播放过渡
            if (liquidOn()) {
                barRoot.visibility = View.VISIBLE
                if (rebuilt || !pillHasSlots || lastSelected < 0) {
                    lastSelected = selected
                    updatePillSlots()
                } else if (selectionChanged) {
                    pill.select(selected)
                    lastSelected = selected
                }
            } else {
                pill.visibility = View.GONE
                lastSelected = selected
            }

            barRoot.visibility = View.VISIBLE
            applyNativeChromeReplacement()
        }
    }

    /**
     * 把各项的实际几何喂给液态胶囊。
     * itemsRow 与 pill 都是 barRoot 的 MATCH_PARENT 子节点，坐标系一致，
     * 因此可直接用 item 的 left/right。
     * 宽度为 0 说明尚未完成布局，此时直接返回——OnLayoutChangeListener 会在
     * 布局完成后补一次，避免自我 post 造成空转。
     */
    private fun updatePillSlots() {
        if (itemsRow.width == 0 || items.isEmpty()) return
        // 刚 addView 的子项还没测量，left/right 全为 0 —— 此时算出的几何是错的，
        // 交给 OnLayoutChangeListener 在布局完成后重算。
        if (items.none { it.width > 0 }) return
        val inset = dp(PILL_INSET_DP).toFloat()
        val slots = items.map { item ->
            val l = item.left + inset
            val r = (item.right - inset).coerceAtLeast(l + 1f)
            floatArrayOf((l + r) / 2f, (r - l) / 2f)
        }
        pill.setSlots(slots, lastSelected.coerceIn(0, slots.size - 1))
        pillHasSlots = true
    }

    private fun buildSignature(tabs: List<View>, selected: Int): String {
        val sb = StringBuilder(tabs.size * 8)
        // 子开关变化也要触发重绘，故一并纳入指纹
        sb.append(if (badgesAllowed()) 'b' else '-').append(if (labelsAllowed()) 'l' else '-')
            .append(if (liquidOn()) 'q' else '-').append(Settings.floatingBarAlphaPercent())
            .append(Settings.floatingBarRadiusDp())
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

    /** 是否启用 iOS 风格液态选中高亮。 */
    private fun liquidOn(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_LIQUID, true)

    /** 液态胶囊是否已拿到有效几何（未拿到前只能跳变，不能播放过渡）。 */
    private var pillHasSlots = false

    /** 外观参数是否待应用（透明度 / 圆角 / 液态开关变化时置位）。 */
    private var styleDirty = true

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
        barRoot.post { runCatching { lastSignature = ""; sync() } }
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
            (barRoot.parent as? ViewGroup)?.removeView(barRoot)
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
        private const val PILL_INSET_DP = 5
        private const val PILL_HEIGHT_DP = 44

        /** 液态胶囊上的内容色（白），与强调色底形成反差。 */
        private val ON_PILL_CONTENT = 0xFFFFFFFF.toInt()
        private const val ICON_SCALE_SELECTED = 1.12f
        private const val ICON_ANIM_MS = 240L
        private const val ICON_OVERSHOOT = 1.7f

        private val ACCENT_FALLBACK = 0xFF0A84FF.toInt()
        private val BADGE_RED = 0xFFFF3B30.toInt()

        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")

        /**
         * target 是否确实位于 scope 子树内。
         * 定义在 companion 内，因为唯一调用方 attach() 是 companion 成员，
         * 放成实例方法会直接 Unresolved reference。
         */
        private fun isInside(scope: View, target: View): Boolean {
            var p: View? = target
            while (p != null) {
                if (p === scope) return true
                p = p.parent as? View
            }
            return false
        }

        /** 连续 N 帧不可见才还原原生底栏 */
        private const val RESTORE_AFTER_FRAMES = 2

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
            // 关键：先只在底栏容器子树内定位 tab_container。
            // 直接全局 findViewById 会命中榜单/游戏页自建的同名子标签栏。
            val tabLayout = NativeTabBar.tabContainerIn(bottom)
                ?: NativeTabBar.tabContainer(activity) ?: return null
            if (tabLayout !== bottom && !isInside(bottom, tabLayout)) return null
            val content = NativeTabBar.viewByResName(activity, "fragment_container") ?: return null
            val overlay = activity.findViewById<View>(android.R.id.content) as? ViewGroup ?: return null

            val host = FloatingBarHost(
                activity = activity,
                overlayParent = overlay,
                initBottomContainer = bottom,
                initBasicModeContainer = NativeTabBar.viewByResName(activity, "tab_basic_mode_container_layout"),
                initTabLayout = tabLayout,
                initContent = content,
                initNavPlaceholder = NativeTabBar.viewByResName(activity, "navigation_bar_placeholder"),
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
