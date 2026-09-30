package com.mars.mimarketpurify

import android.content.ComponentName
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mars.mimarketpurify.App.ServiceStateListener
import com.mars.mimarketpurify.Settings.PREFS_GROUP
import io.github.libxposed.service.XposedService

/**
 * 设置页的**纯逻辑基类**（不再包含任何原生 View 构建器）。
 *
 * 仅保留：Xposed service 连接与重连补写、远程偏好读写、桌面图标隐藏/恢复、入口自愈，
 * 以及一个供 Compose 页面观察 service 状态的 [refreshSignal]。
 *
 * 所有 UI 已迁移到 Compose（见 [com.mars.mimarketpurify.ui.components]），
 * 原生构建器（addSwitchRow / addNavRow / groupCard / ColorPickerView 等）已删除，
 * 避免与主页 Compose 形成两套 UI 语汇的重复。
 */
abstract class SettingsBaseActivity : ComponentActivity(), ServiceStateListener {

    protected var service: XposedService? = null

    /** service 未连接期间的待写入布尔值，连接后补写。 */
    private val pendingWrites = mutableMapOf<String, Boolean>()

    /** service 未连接期间的待写入整型值，连接后补写。 */
    private val pendingIntWrites = mutableMapOf<String, Int>()

    /**
     * Compose 页面观察此信号以重新读取偏好。
     * [onServiceStateChanged] 在补写完待写入项后自增，触发依赖它的 [androidx.compose.runtime.remember] 重新取数。
     */
    val refreshSignal = mutableStateOf(0)

    protected val launcherAlias: ComponentName by lazy {
        ComponentName(this, "$packageName.LauncherAlias")
    }

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
                val prefs = service.getRemotePreferences(PREFS_GROUP)
                pendingWrites.forEach { (k, v) -> prefs?.edit()?.putBoolean(k, v)?.apply() }
                pendingWrites.clear()
            }
            if (service != null && pendingIntWrites.isNotEmpty()) {
                val prefs = service.getRemotePreferences(PREFS_GROUP)
                pendingIntWrites.forEach { (k, v) -> prefs?.edit()?.putInt(k, v)?.apply() }
                pendingIntWrites.clear()
            }
            refreshAll()
        }
    }

    /** 重连 / 外部改动后统一刷新入口；Compose 页面通过 [refreshSignal] 重新取数。 */
    protected open fun refreshAll() {
        onRefresh()
        refreshSignal.value++
    }

    /** 子类可重写：在刷新时重新同步本地 UI 状态。默认空实现（偏好由 [refreshSignal] 驱动）。 */
    protected open fun onRefresh() {}

    // ==================== 远程偏好 ====================

    internal fun readLocal(key: String, def: Boolean): Boolean {
        return service?.getRemotePreferences(PREFS_GROUP)?.getBoolean(key, def) ?: def
    }

    internal fun writeRemote(key: String, value: Boolean) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) {
            pendingWrites[key] = value
            return
        }
        runCatching {
            prefs.edit()?.putBoolean(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun readLocalTabs(): Set<String> {
        val raw = service?.getRemotePreferences(PREFS_GROUP)
            ?.getString(Settings.KEY_TAB_KEEP, Settings.DEFAULT_TAB_KEEP)
            ?: Settings.DEFAULT_TAB_KEEP
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    internal fun readLocalInt(key: String, def: Int): Int =
        service?.getRemotePreferences(PREFS_GROUP)?.getInt(key, def) ?: def

    internal fun writeRemoteInt(key: String, value: Int) {
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

    internal fun writeRemoteString(key: String, value: String) {
        val prefs = service?.getRemotePreferences(PREFS_GROUP)
        if (prefs == null) return
        runCatching {
            prefs.edit()?.putString(key, value)?.apply()
        }.onFailure {
            Toast.makeText(this, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 模块自身 ====================

    internal fun isLauncherIconHidden(): Boolean {
        return runCatching {
            packageManager.getComponentEnabledSetting(launcherAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(false)
    }

    internal fun applyHideIcon(hide: Boolean) {
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
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
