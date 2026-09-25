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

    private var originalBottomContainer: View = initBottomContainer
    private var basicModeContainer: View? = initBasicModeContainer
    private var nativeTabLayout: View = initTabLayout
    private var contentView: View = initContent
    private var navigationBarPlaceholder: View? = initNavPlaceholder

    private val resources = activity.resources
    private val density = resources.displayMetrics.density

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

    private var lastSignature: String = ""
    private var lastBlocker: String? = null

    private val items = ArrayList<View>()
    private val iconViews = ArrayList<ImageView>()
    private val labelViews = ArrayList<TextView>()
    private val badgeViews = ArrayList<TextView>()

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        sync()
        true
    }

    private val pill = LiquidSelectionView(activity)
    private val itemsRow: LinearLayout = buildItemsRow()
    private val barRoot: FrameLayout = buildBarRoot()
    private var lastSelected = -1

    // ═══════════════════════ 构建 ═══════════════════════

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    private fun dpf(v: Float): Float = v * density

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun barRadiusPx(): Float =
        min(dpf(Settings.floatingBarRadiusDp().toFloat()), dpf(BAR_HEIGHT_DP / 2f))

    /**
     * 底栏背景色。
     * 直接返回用户设置的完整 #AARRGGBB：alpha 通道只作用于背景 drawable，
     * 不再整体设 barRoot.alpha（那样会连带选中胶囊一起透明）。
     * 选中胶囊的透明度由 KEY_FLOAT_SELECT_BG_COLOR 自己的 alpha 独立控制。
     */
    private fun barFillPx(): Int {
        // 确保不残留之前的整体透明
        if (barRoot.alpha != 1f) barRoot.alpha = 1f
        val custom = Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1)
        if (custom != -1) return custom
        return if (isNight()) 0xE61C1C1E.toInt() else 0xE6FFFFFF.toInt()
    }

    private val roundedOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, barRadiusPx())
        }
    }

    private val barBg: GradientDrawable = GradientDrawable()

    private fun refreshBarStyle() {
        barBg.setColor(barFillPx())
        barBg.cornerRadius = barRadiusPx()
        // 去掉这行：barBg.setStroke(dp(1), if (isNight()) 0x33FFFFFF else 0x14000000)
        barRoot.background = barBg
        barRoot.outlineProvider = roundedOutlineProvider
        pill.configure(selectedColor(), min(barRadiusPx(), dpf(PILL_HEIGHT_DP / 2f)))
        pill.liquid3D = Settings.isEnabled(Settings.KEY_FLOATING_BAR_LIQUID_3D, true)
        pill.visibility = if (liquidOn()) View.VISIBLE else View.GONE
    }


    private fun buildItemsRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun buildBarRoot(): FrameLayout {
        val bar = object : FrameLayout(activity) {
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
                when (ev.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        touchDownX = ev.x
                        touchDownY = ev.y
                        lastDragDx = 0f
                        isDragging = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = ev.x - touchDownX
                        val dy = ev.y - touchDownY
                        if (!isDragging &&
                            kotlin.math.abs(dx) > dp(12).toFloat() &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            isDragging = true
                            pill.cancelAnimation()
                        }
                    }
                }
                return isDragging
            }
        }.apply {
            background = barBg
            // 去掉这行：elevation = dpf(12f)
            outlineProvider = roundedOutlineProvider
            clipToOutline = true
            visibility = View.GONE
        }
        bar.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        bar.addView(itemsRow)
        
        // 只处理被 onInterceptTouchEvent 拦截后的滑动事件；
        // 非滑动时事件透给 item，由 item 自己的 onClickListener 处理点击
        bar.setOnTouchListener { _, ev ->
            when (ev.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = ev.x - touchDownX
                        pill.dragBy(dx - lastDragDx)
                        lastDragDx = dx
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val dx = ev.x - touchDownX
                        val slotW = if (items.isNotEmpty()) bar.width / items.size else 0
                        val threshold = slotW * 0.3f
                        when {
                            // 手指右滑 → pill 右移到右边 tab → 切右边
                            dx > threshold && lastSelected < items.size - 1 ->
                                onItemClicked(lastSelected + 1)
                            // 手指左滑 → pill 左移到左边 tab → 切左边
                            dx < -threshold && lastSelected > 0 ->
                                onItemClicked(lastSelected - 1)
                            else ->
                                pill.select(lastSelected, animated = true)
                        }
                    }
                    isDragging = false
                    lastDragDx = 0f
                    true
                }
                else -> false
            }
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
        itemsRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            runCatching { updatePillSlots() }
        }
        return bar
    }

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
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(ICON_AREA_DP)
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        val icon = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply {
                gravity = Gravity.CENTER
            }
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

        items += item
        iconViews += icon
        labelViews += label
        badgeViews += badge
        return item
    }

    // ═══════════════════════ 同步 ═══════════════════════

    private fun tabViews(): List<View> = NativeTabBar.tabViewsOf(nativeTabLayout)

    private fun visibilityBlocker(tabs: List<View>): String? = when {
        !originalBottomContainer.isAttachedToWindow -> "底栏容器已脱离视图树"
        originalBottomContainer.visibility != View.VISIBLE -> "底栏容器不可见"
        !nativeTabLayout.isAttachedToWindow -> "tab_container 已脱离视图树"
        nativeTabLayout.visibility != View.VISIBLE -> "tab_container 不可见"
        basicModeContainer?.visibility == View.VISIBLE -> "精简模式底栏占用"
        tabs.size <= 1 -> "标签数=${tabs.size} container=${nativeTabLayout.javaClass.simpleName}/${tabs.size}"
        else -> null
    }

    private fun refreshViewRefs(force: Boolean = false): Boolean {
        var rebound = false
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

    private var invisibleFrames = 0

    private fun sync() {
        if (disposed) return
        runCatching {
            if (refreshViewRefs()) {
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
                if (barRoot.visibility != View.VISIBLE) barRoot.visibility = View.VISIBLE
                applyNativeChromeReplacement()
                return
            }
            lastSignature = signature
            styleDirty = true
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
            if (rebuilt || styleDirty) {
                refreshBarStyle()
                styleDirty = false
            }

            tabs.forEachIndexed { i, tab ->
                val label = NativeTabBar.titleOf(tab) ?: FALLBACK_LABELS.getOrElse(i) { "入口${i + 1}" }
                labelViews.getOrNull(i)?.text = label
                copyIcon(tab, i, selected == i)

                val badgeView = badgeViews.getOrNull(i) ?: return@forEachIndexed
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
                    onPill -> selectedContentColor()
                    selected == i -> selectedColor()
                    else -> unselectedColor()
                }
                val showLabel = labelsAllowed()
                labelViews.getOrNull(i)?.apply {
                    setTextColor(color)
                    visibility = if (showLabel) View.VISIBLE else View.GONE
                }
                iconViews.getOrNull(i)?.let { iv ->
                    iv.alpha = 1f
                    iv.setColorFilter(color)
                    // 选中/未选中 state 只在切换时写一次，平时不碰，
                    // 让 ImageView 自己响应 pressed（按压填充），不被每帧覆盖
                    if (selectionChanged) {
                        val state = if (selected == i) {
                            intArrayOf(
                                android.R.attr.state_enabled,
                                android.R.attr.state_checked,
                                android.R.attr.state_selected
                            )
                        } else {
                            intArrayOf(android.R.attr.state_enabled)
                        }
                        iv.setImageState(state, false)
                    }
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

    private fun updatePillSlots() {
        if (itemsRow.width == 0 || items.isEmpty()) return
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
        sb.append(if (badgesAllowed()) 'b' else '-').append(if (labelsAllowed()) 'l' else '-')
            .append(if (liquidOn()) 'q' else '-').append(Settings.floatingBarRadiusDp())
        sb.append(Settings.getInt(Settings.KEY_FLOAT_BG_COLOR, -1)).append('|')
            .append(Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1)).append('|')
            .append(Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1)).append('|')
            .append(Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1))
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

    private val tintWhenSelected: Boolean get() = true

    private fun selectedColor(): Int {
        Settings.getInt(Settings.KEY_FLOAT_SELECT_BG_COLOR, -1)
            .takeIf { it != -1 }?.let { return it }
        resolveAccent()?.let { return it }
        return ACCENT_FALLBACK
    }

    private fun unselectedColor(): Int {
        Settings.getInt(Settings.KEY_FLOAT_TEXT_NORMAL_COLOR, -1)
            .takeIf { it != -1 }?.let { return it }
        return if (isNight()) 0xFF9B9BA0.toInt() else 0xFF8E8E93.toInt()
    }

    private fun selectedContentColor(): Int {
        Settings.getInt(Settings.KEY_FLOAT_TEXT_SELECT_COLOR, -1)
            .takeIf { it != -1 }?.let { return it }
        return ON_PILL_CONTENT
    }

    private fun badgesAllowed(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_BADGE, true) &&
            !Settings.isEnabled(Settings.KEY_TAB_BADGE, true)

    private fun labelsAllowed(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_LABEL, true)

    private fun liquidOn(): Boolean =
        Settings.isEnabled(Settings.KEY_FLOATING_BAR_LIQUID, true)

    private var pillHasSlots = false
    private var styleDirty = true

    // ── 滑动切换 ──
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastDragDx = 0f
    private var isDragging = false


    private fun resolveAccent(): Int? = runCatching {
        val tv = TypedValue()
        val ok = activity.theme.resolveAttribute(android.R.attr.colorAccent, tv, true) ||
            activity.theme.resolveAttribute(android.R.attr.colorControlActivated, tv, true)
        if (ok && tv.data != 0) tv.data else null
    }.getOrNull()

    // ═══════════════════════ 原生底栏压制 / 还原 ═══════════════════════

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

    private fun onItemClicked(index: Int) {
        val tab = tabViews().getOrNull(index) ?: return
        runCatching { tab.performClick() }
        barRoot.post { runCatching { lastSignature = ""; sync() } }
    }

    // ═══════════════════════ 生命周期 ═══════════════════════

    fun start() {
        if (disposed) return
        overlayParent.viewTreeObserver.takeIf { it.isAlive }
            ?.addOnPreDrawListener(preDrawListener)
        sync()
    }

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

        private val ON_PILL_CONTENT = 0xFFFFFFFF.toInt()
        private const val ICON_SCALE_SELECTED = 1.12f
        private const val ICON_ANIM_MS = 240L
        private const val ICON_OVERSHOOT = 1.7f

        private val ACCENT_FALLBACK = 0xFF0A84FF.toInt()
        private val BADGE_RED = 0xFFFF3B30.toInt()

        private val FALLBACK_LABELS = listOf("首页", "游戏", "榜单", "我的")

        private fun diag(msg: String) {
            runCatching {
                if (Settings.isEnabled(Settings.KEY_RANK_DEBUG, false)) {
                    HookEnv.base.log(android.util.Log.DEBUG, TAG, "[悬浮底栏] $msg")
                }
            }
        }

        private fun isInside(scope: View, target: View): Boolean {
            var p: View? = target
            while (p != null) {
                if (p === scope) return true
                p = p.parent as? View
            }
            return false
        }

        private const val RESTORE_AFTER_FRAMES = 2
        private val active = WeakHashMapOfActivity()

        fun attach(activity: Activity): FloatingBarHost? {
            if (activity.isFinishing || activity.isDestroyed) return null
            active.get(activity)?.let { return it }

            val bottom = NativeTabBar.bottomContainer(activity)
            if (bottom == null) {
                diag("attach 失败：bottomContainer(tab_container_layout) 未找到")
                return null
            }
            val tabLayout = NativeTabBar.tabContainerIn(bottom)
                ?: NativeTabBar.tabContainer(activity)
            if (tabLayout == null) {
                diag("attach 失败：tabContainer(tab_container) 未找到（bottom 存在）")
                return null
            }
            if (tabLayout !== bottom && !isInside(bottom, tabLayout)) {
                diag("attach 失败：tab_container 不在 bottom 子树内，class=${tabLayout.javaClass.simpleName}")
                return null
            }
            val content = NativeTabBar.viewByResName(activity, "fragment_container")
            if (content == null) {
                diag("attach 失败：fragment_container 未找到")
                return null
            }
            val overlay = activity.findViewById<View>(android.R.id.content) as? ViewGroup
            if (overlay == null) {
                diag("attach 失败：android.R.id.content 非 ViewGroup")
                return null
            }

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

    private class WeakHashMapOfActivity {
        private val map = java.util.WeakHashMap<Activity, FloatingBarHost>()

        @Synchronized fun get(a: Activity): FloatingBarHost? = map[a]

        @Synchronized fun put(a: Activity, h: FloatingBarHost) {
            map[a] = h
        }

        @Synchronized fun remove(a: Activity): FloatingBarHost? = map.remove(a)
    }
}
