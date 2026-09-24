package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import io.github.libxposed.service.XposedService

abstract class SettingsBaseActivity : Activity(), ServiceStateListener {

    protected var service: XposedService? = null

    protected lateinit var content: LinearLayout

    protected val gatedRows = mutableListOf<SwitchRow>()

    private val switchEntries = mutableListOf<SwitchEntry>()

    private val navRows = mutableListOf<NavRow>()

    private val sliderEntries = mutableListOf<SliderEntry>()

    /** service 未连接期间的待写入整型值，连接后补写。 */
    private val pendingIntWrites = mutableMapOf<String, Int>()

    protected val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

    /**
     * service 断开期间的待写入开关。
     * writeRemote 发现 service 为 null 时暂存到这里，
     * onServiceStateChanged 连接后自动补写。
     */
    private val pendingWrites = mutableMapOf<String, Boolean>()

    // ==================== 生命周期与刷新 ====================

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
        runOnUiThread {
            // 补写 service 断开期间的待写入项
            if (service != null && pendingWrites.isNotEmpty()) {
                val prefs = service?.getRemotePreferences(PREFS_GROUP)
                pendingWrites.forEach { (k, v) ->
                    prefs?.edit()?.putBoolean(k, v)?.apply()
                }
                pendingWrites.clear()
            }
            if (service != null && pendingIntWrites.isNotEmpty()) {
                val prefs = service?.getRemotePreferences(PREFS_GROUP)
                pendingIntWrites.forEach { (k, v) ->
                    prefs?.edit()?.putInt(k, v)?.apply()
                }
                pendingIntWrites.clear()
            }
            refreshAll()
        }
    }

    protected open fun onRefresh() {}

    protected fun refreshAll() {
        switchEntries.forEach { e -> e.sw.isChecked = readLocal(e.key, e.def) }
        navRows.forEach { n -> n.value.text = n.compute() }
        sliderEntries.forEach { e -> e.sync(readLocalInt(e.key, e.def)) }
        onRefresh()
        updateGateState()
    }

    // ==================== 布局骨架 ====================

    protected fun setupRoot(header: LinearLayout) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        root.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.setPadding(dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.top, dp(Ui.PAGE_H), dp(12))
            content.setPadding(
                dp(Ui.PAGE_H), dp(6), dp(Ui.PAGE_H), dp(Ui.PAGE_H) + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    protected fun buildSubTopBar(header: LinearLayout, title: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setBackgroundResource(R.drawable.bg_icon_ripple)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(Ui.TOUCH_MIN), dp(Ui.TOUCH_MIN)).also {
                it.marginStart = -dp(12)
            }
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = Ui.PAGE_TITLE
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Ui.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = -dp(8) }
        })
        header.addView(row)
        header.addView(headerDivider())
    }

    protected fun headerDivider(): View = View(this).apply {
        setBackgroundColor(Ui.DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1).coerceAtLeast(1)
        )
    }

    protected fun addSectionHeader(
        title: String,
        subtitle: String,
        parent: LinearLayout = content
    ) {
        parent.addView(sectionTitle(title))
        parent.addView(TextView(this).apply {
            text = subtitle
            textSize = Ui.MICRO
            setTextColor(Ui.TEXT_TERTIARY)
            setPadding(dp(4), 0, 0, dp(6))
        })
    }

    // ==================== 功能行 ====================

    protected fun addSwitchRow(
        group: LinearLayout,
        title: String,
        summary: String,
        checked: Boolean,
        tag: String,
        default: Boolean = true,
        gated: Boolean = true,
        remote: Boolean = true,
        onChanged: (Boolean) -> Unit
    ): CompoundButton {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(12) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary(summary)
        textWrap.addView(titleView)
        textWrap.addView(summaryView)

        val sw = Switch(this).apply {
            this.tag = tag
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            getDrawable(R.drawable.switch_track)
                ?.let { trackDrawable = it.tinted(Ui.ACCENT, Ui.SWITCH_TRACK_OFF) }
            getDrawable(R.drawable.switch_thumb)
                ?.let { thumbDrawable = it }
            switchMinWidth = dp(48)
        }

        row.addView(textWrap)
        row.addView(sw)
        row.tappable(this, R.drawable.bg_row_ripple)
        row.setOnClickListener { sw.toggle() }
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)

        if (remote) switchEntries += SwitchEntry(tag, default, sw)
        if (gated) gatedRows += SwitchRow(row, sw, titleView, summaryView)
        return sw
    }

    /**
     * 数值调节行（纯原生 SeekBar）：上排「标题 + 当前值」，下排滑杆。
     * 只在**拖动结束**时落盘，避免 onProgressChanged 每像素打一次 binder 写。
     */
    protected fun addSliderRow(
        group: LinearLayout,
        title: String,
        summary: String,
        key: String,
        minValue: Int,
        maxValue: Int,
        initialValue: Int,
        defaultValue: Int,
        gated: Boolean = true,
        format: (Int) -> String,
        onChanged: (Int) -> Unit
    ): SeekBar {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V), dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = dp(12) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary(summary)
        textWrap.addView(titleView)
        textWrap.addView(summaryView)
        val valueView = TextView(this).apply {
            text = format(initialValue)
            textSize = Ui.CAPTION
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Ui.ACCENT)
        }

        topRow.addView(textWrap)
        topRow.addView(valueView)

        val span = maxValue - minValue
        val startProgress = (initialValue - minValue).coerceIn(0, span)
        val seek = SeekBar(this).apply {
            tag = key
            max = span
            progress = startProgress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueView.text = format(minValue + progress)
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}

                override fun onStopTrackingTouch(sb: SeekBar?) {
                    val v = (sb?.progress ?: 0) + minValue
                    if (v.coerceIn(minValue, maxValue) != readLocalInt(key, defaultValue)) onChanged(v)
                }
            })
        }
        row.addView(topRow)
        row.addView(seek)
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)

        sliderEntries += SliderEntry(key, defaultValue, seek, valueView, minValue, maxValue, format)
        if (gated) gatedRows += SwitchRow(row, null, titleView, summaryView)
        return seek
    }

    protected fun addNavRow(
        group: LinearLayout,
        title: String,
        summary: String,
        gated: Boolean = true,
        value: () -> String,
        onClick: () -> Unit
    ) {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(8) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary(summary)
        textWrap.addView(titleView)
        textWrap.addView(summaryView)

        val valueView = TextView(this).apply {
            text = value()
            textSize = Ui.CAPTION
            setTextColor(Ui.TEXT_SECONDARY)
        }
        val arrow = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams =
                LinearLayout.LayoutParams(dp(20), dp(20)).also { it.marginStart = dp(6) }
        }

        row.addView(textWrap)
        row.addView(valueView)
        row.addView(arrow)
        row.tappable(this, R.drawable.bg_row_ripple)
        row.setOnClickListener { onClick() }
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)

        navRows += NavRow(valueView, value)
        if (gated) gatedRows += SwitchRow(row, null, titleView, summaryView)
    }

    // ===================== 颜色选择行 =====================
    protected fun addColorPickerRow(
        group: LinearLayout,
        title: String,
        tag: String,
        defaultColor: Int,
        gated: Boolean = true
    ) {
        val row = row()
        val textWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.marginEnd = dp(12) }
        }
        val titleView = rowTitle(title)
        val summaryView = rowSummary("点击选择颜色（支持 #AARRGGBB 带透明度）")
        textWrap.addView(titleView)
        textWrap.addView(summaryView)

        val previewBox = View(this).apply {
            val colorVal = readLocalInt(tag, defaultColor)
            background = GradientDrawable().apply {
                setColor(colorVal)
                cornerRadius = dpf(8f)
            }
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }

        row.addView(textWrap)
        row.addView(previewBox)
        row.tappable(this, R.drawable.bg_row_ripple)
        row.setOnClickListener {
            val current = readLocalInt(tag, defaultColor)
            showColorPickerDialog(current) { newColor ->
                writeRemoteInt(tag, newColor)
                previewBox.background = GradientDrawable().apply {
                    setColor(newColor)
                    cornerRadius = dpf(8f)
                }
            }
        }
        if (group.childCount > 0) {
            (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Ui.ROW_GAP)
        }
        group.addView(row)
        if (gated) gatedRows += SwitchRow(row, null, titleView, summaryView)
    }

    protected fun showColorPickerDialog(initColor: Int, onPick: (Int) -> Unit) {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }

        val picker = ColorPickerView(this).apply {
            setColor(initColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (300 * resources.displayMetrics.density).toInt()
            )
        }
        rootLayout.addView(picker)

        // 当前颜色 hex 显示 + 可手动微调
        val inputField = android.widget.EditText(this).apply {
            hint = "#AARRGGBB"
            setText(String.format("#%08X", initColor))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(12) }
        }
        rootLayout.addView(inputField)

        // 拖动取色器时同步 hex 文本
        picker.onColorChanged = { argb ->
            inputField.setText(String.format("#%08X", argb))
        }

        var dialogRef: android.app.AlertDialog? = null
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("选择颜色")
            .setView(rootLayout)
            .setPositiveButton("确定") { _, _ ->
                val raw = inputField.text.toString().trim()
                runCatching {
                    // 优先用手动输入；解析失败则用取色器当前色
                    val parsed = android.graphics.Color.parseColor(raw)
                    onPick(parsed)
                }.onFailure {
                    onPick(picker.color)
                }
            }
            .setNegativeButton("取消", null)
            .create()
        dialogRef = dialog
        dialog.show()
    }

    protected open fun updateGateState() {
        val master = readLocal(Settings.KEY_MASTER, true)
        sliderEntries.forEach { it.seek.isEnabled = master }
        gatedRows.forEach { r ->
            r.sw?.isEnabled = master
            r.row.isClickable = master
            r.row.isFocusable = master
            r.title.setTextColor(if (master) Ui.TEXT_PRIMARY else Ui.TEXT_TERTIARY)
            r.summary.setTextColor(if (master) Ui.TEXT_SECONDARY else Ui.TEXT_TERTIARY)
        }
    }

    // ==================== 远程偏好 ====================

    protected fun readLocal(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    /**
     * 写远程偏好。
     * service 为 null 时暂存到 [pendingWrites]，
     * 等 onServiceStateChanged 连接后自动补写。
     */
    protected fun writeRemote(key: String, value: Boolean) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            // service 未连接：暂存，等连接后补写
            pendingWrites[key] = value
            return
        }
        runCatching {
            prefs.edit()?.putBoolean(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    protected fun readLocalTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(Settings.KEY_TAB_KEEP, Settings.DEFAULT_TAB_KEEP)
            ?: Settings.DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    protected fun readLocalInt(key: String, def: Int): Int =
        service?.getRemotePreferences(PREFS_GROUP)?.getInt(key, def) ?: def

    /** 写整型远程偏好；service 未连接时暂存，连接后补写。 */
    protected fun writeRemoteInt(key: String, value: Int) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            pendingIntWrites[key] = value
            return
        }
        runCatching {
            prefs.edit()?.putInt(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    protected fun writeRemoteString(key: String, value: String) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            return
        }
        runCatching {
            prefs.edit()?.putString(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 模块自身 ====================

    protected fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    protected fun applyHideIcon(hide: Boolean) {
        runCatching {
            EntryGuardReceiver.ensureEntryEnabled(this)
            val state = if (hide) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
            packageManager.setComponentEnabledSetting(
                launcherAlias, state, PackageManager.DONT_KILL_APP
            )
            Toast.makeText(
                this,
                if (hide) "已隐藏桌面图标，可在 LSPosed 模块列表中进入主页"
                else "已恢复桌面图标",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ==================== 工具辅助函数 ====================
    protected fun dp(value: Int): Int = resources.displayMetrics.density.times(value).toInt()
    protected fun dpf(value: Float): Float = resources.displayMetrics.density * value

    protected fun row(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V), dp(Ui.ROW_PAD_H), dp(Ui.ROW_PAD_V))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    protected fun rowTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.ROW_TITLE
        setTextColor(Ui.TEXT_PRIMARY)
    }

    protected fun rowSummary(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.ROW_SUMMARY
        setTextColor(Ui.TEXT_SECONDARY)
        setPadding(0, dp(2), 0, 0)
    }

    protected fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = Ui.SECTION
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Ui.TEXT_SECTION)
    }

    protected fun groupCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = dpf(16f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    // 扩展：drawable 着色
    private fun android.graphics.drawable.Drawable.tinted(on: Int, off: Int): android.graphics.drawable.Drawable {
        return mutate().apply {
            setTintList(android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(on, off)
            ))
        }
    }

    private fun View.tappable(ctx: Activity, rippleRes: Int) {
        background = ctx.getDrawable(rippleRes)
        isClickable = true
        isFocusable = true
    }
    
        // ===================== 内置颜色取色器（色相条 + SV 面板 + Alpha 条） =====================
    protected inner class ColorPickerView(context: android.content.Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val densityF = resources.displayMetrics.density

        /** 当前选中色（ARGB） */
        var color: Int = android.graphics.Color.WHITE
            private set

        /** 拖动时回调 */
        var onColorChanged: ((Int) -> Unit)? = null

        private val hsv = floatArrayOf(0f, 0f, 1f)
        private var alpha = 255f

        // 各区域几何（onSizeChanged 后计算）
        private var svRect = android.graphics.RectF()
        private var hueRect = android.graphics.RectF()
        private var alphaRect = android.graphics.RectF()

        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var svBitmap: android.graphics.Bitmap? = null
        private var hueShader: android.graphics.Shader? = null
        private var alphaShader: android.graphics.Shader? = null

        private val borderPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1f * densityF
            color = 0x33000000
        }

        fun setColor(argb: Int) {
            android.graphics.Color.colorToHSV(argb, hsv)
            alpha = android.graphics.Color.alpha(argb).toFloat()
            rebuildShaders()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val pad = 0f
            val hueBarW = 26f * densityF
            val alphaBarH = 26f * densityF
            svRect.set(pad, pad, w - hueBarW - pad, h - alphaBarH - pad)
            hueRect.set(w - hueBarW - pad, pad, w - pad, h - alphaBarH - pad)
            alphaRect.set(pad, h - alphaBarH - pad, w - pad, h - pad)
            rebuildShaders()
        }

        private fun rebuildShaders() {
            // SV 面板位图：横向 Saturation，纵向 Value
            if (svRect.width() <= 0 || svRect.height() <= 0) return
            val w = svRect.width().toInt()
            val h = svRect.height().toInt()
            if (w <= 0 || h <= 0) return
            svBitmap?.recycle()
            svBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val hueColor = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val v = 1f - y.toFloat() / (h - 1)
                for (x in 0 until w) {
                    val s = x.toFloat() / (w - 1)
                    pixels[y * w + x] = blendWhiteBlack(hueColor, s, v)
                }
            }
            svBitmap!!.setPixels(pixels, 0, w, 0, 0, w, h)

            // 色相条：彩虹渐变
            hueShader = android.graphics.LinearGradient(
                0f, hueRect.top, 0f, hueRect.bottom,
                intArrayOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.YELLOW,
                    android.graphics.Color.GREEN,
                    android.graphics.Color.CYAN,
                    android.graphics.Color.BLUE,
                    android.graphics.Color.MAGENTA,
                    android.graphics.Color.RED
                ),
                null, android.graphics.Shader.TileMode.CLAMP
            )

            // alpha 条：棋盘格底 + 当前不透明色渐变
            val opaque = android.graphics.Color.HSVToColor(255, hsv)
            alphaShader = android.graphics.LinearGradient(
                alphaRect.left, 0f, alphaRect.right, 0f,
                android.graphics.Color.argb(0,
                    android.graphics.Color.red(opaque),
                    android.graphics.Color.green(opaque),
                    android.graphics.Color.blue(opaque)),
                opaque,
                android.graphics.Shader.TileMode.CLAMP
            )
            invalidate()
        }

        /** SV 面板取色：给定 s/v 返回对应 RGB */
        private fun blendWhiteBlack(hueColor: Int, s: Float, v: Float): Int {
            // 标准 HSV 转 RGB
            return android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], s, v))
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            // 1) SV 面板
            svBitmap?.let { canvas.drawBitmap(it, svRect.left, svRect.top, paint) }
            canvas.drawRect(svRect, borderPaint)
            // SV 手柄
            val sx = svRect.left + hsv[1] * svRect.width()
            val sy = svRect.top + (1f - hsv[2]) * svRect.height()
            drawHandle(canvas, sx, sy)

            // 2) 色相条
            paint.shader = hueShader
            canvas.drawRect(hueRect, paint)
            paint.shader = null
            canvas.drawRect(hueRect, borderPaint)
            val hy = hueRect.top + (hsv[0] / 360f) * hueRect.height()
            drawHandle(canvas, hueRect.centerX(), hy)

            // 3) alpha 条：先画棋盘格底
            drawCheckerboard(canvas, alphaRect)
            paint.shader = alphaShader
            canvas.drawRect(alphaRect, paint)
            paint.shader = null
            canvas.drawRect(alphaRect, borderPaint)
            val ax = alphaRect.left + (alpha / 255f) * alphaRect.width()
            drawHandle(canvas, ax, alphaRect.centerY())
        }

        private fun drawCheckerboard(canvas: android.graphics.Canvas, rect: android.graphics.RectF) {
            val cell = 6f * densityF
            paint.color = 0xFFDDDDDD.toInt()
            var row = 0
            var y = rect.top
            while (y < rect.bottom) {
                var col = 0
                var x = rect.left
                while (x < rect.right) {
                    if ((row + col) % 2 == 0) canvas.drawRect(x, y, x + cell, y + cell, paint)
                    x += cell; col++
                }
                y += cell; row++
            }
        }

        private fun drawHandle(canvas: android.graphics.Canvas, cx: Float, cy: Float) {
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.WHITE
            paint.setShadowLayer(3f * densityF, 0f, 1f, 0x66000000)
            canvas.drawCircle(cx, cy, 9f * densityF, paint)
            paint.clearShadowLayer()
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 2f * densityF
            paint.color = 0xFF333333.toInt()
            canvas.drawCircle(cx, cy, 9f * densityF, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }

        private enum class DragTarget { NONE, SV, HUE, ALPHA }
        private var dragging = DragTarget.NONE

        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dragging = hitTest(event.x, event.y)
                    if (dragging != DragTarget.NONE) {
                        updateFromTouch(event.x, event.y)
                        return true
                    }
                    return false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (dragging != DragTarget.NONE) {
                        updateFromTouch(event.x, event.y)
                        return true
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> dragging = DragTarget.NONE
            }
            return super.onTouchEvent(event)
        }

        private fun hitTest(x: Float, y: Float): DragTarget {
            if (alphaRect.contains(x, y)) return DragTarget.ALPHA
            if (hueRect.contains(x, y)) return DragTarget.HUE
            if (svRect.contains(x, y)) return DragTarget.SV
            return DragTarget.NONE
        }

        private fun updateFromTouch(x: Float, y: Float) {
            when (dragging) {
                DragTarget.SV -> {
                    hsv[1] = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                    hsv[2] = (1f - (y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
                    rebuildShaders()
                }
                DragTarget.HUE -> {
                    hsv[0] = ((y - hueRect.top) / hueRect.height() * 360f).coerceIn(0f, 360f)
                    rebuildShaders()
                }
                DragTarget.ALPHA -> {
                    alpha = ((x - alphaRect.left) / alphaRect.width() * 255f).coerceIn(0f, 255f)
                    invalidate()
                }
                DragTarget.NONE -> {}
            }
            color = android.graphics.Color.HSVToColor(alpha.toInt(), hsv)
            onColorChanged?.invoke(color)
        }
    }


    // ==================== 数据结构 ====================

    private data class SwitchEntry(val key: String, val def: Boolean, val sw: CompoundButton)

    /** 滑块行登记项：service 重连或外部改动后据此刷新显示值。 */
    private data class SliderEntry(
        val key: String,
        val def: Int,
        val seek: SeekBar,
        val valueView: TextView,
        val min: Int,
        val max: Int,
        val format: (Int) -> String,
    ) {
        fun sync(v: Int) {
            seek.progress = (v - min).coerceIn(0, max - min)
            valueView.text = format(v)
        }
    }

    protected data class SwitchRow(
        val row: LinearLayout,
        val sw: CompoundButton?,
        val title: TextView,
        val summary: TextView
    )

    private data class NavRow(val value: TextView, val compute: () -> String)
}
