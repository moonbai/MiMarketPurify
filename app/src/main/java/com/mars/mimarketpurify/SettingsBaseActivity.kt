package com.mars.mimarketpurify

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
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
            refreshAll()
        }
    }

    protected open fun onRefresh() {}

    protected fun refreshAll() {
        switchEntries.forEach { e -> e.sw.isChecked = readLocal(e.key, e.def) }
        navRows.forEach { n -> n.value.text = n.compute() }
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

    protected fun addSectionHeader(title: String, subtitle: String) {
        content.addView(sectionTitle(title))
        content.addView(TextView(this).apply {
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

    protected open fun updateGateState() {
        val master = readLocal(Settings.KEY_MASTER, true)
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
        }.onFailure {
            Toast.makeText(this, "操作失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 数据结构 ====================

    private data class SwitchEntry(val key: String, val def: Boolean, val sw: CompoundButton)

    protected data class SwitchRow(
        val row: LinearLayout,
        val sw: CompoundButton?,
        val title: TextView,
        val summary: TextView
    )

    private data class NavRow(val value: TextView, val compute: () -> String)
}
