package com.mars.mimarketpurify.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 应用商店重启工具。
 *
 * 旧实现仅调用 [ActivityManager.killBackgroundProcesses]，该方法**只能杀后台进程**，
 * 当应用商店处于前台时不起作用，导致“重启”形同虚设。
 *
 * 这里优先通过 root 调用 `am force-stop` 真正强杀（含前台进程）；
 * 无 root 或执行失败时回退到 [ActivityManager.killBackgroundProcesses]。
 *
 * 强杀在后台线程执行，避免 `su` 授权弹窗阻塞 UI 线程；
 * 随后切回主线程重新拉起商店。
 */
object MarketRestarter {

    private const val PKG = "com.xiaomi.market"

    /**
     * 强制停止应用商店。
     *
     * 优先通过 root 真正强杀（含前台进程）。采用**交互式 su**（向 su 进程写入命令后退出），
     * 该写法对 Magisk / LSPosed 下 `su` 不支持 `-c` 参数的环境同样兼容；
     * 若一次失败再尝试 `su -c` 与 `am kill` 两种形态兜底。
     *
     * 全部 root 途径均失败时，回退到 [ActivityManager.killBackgroundProcesses]
     * （仅能杀后台进程，前台无效——此时其实无法真正强杀，但已尽力）。
     *
     * @return true 表示已用 root 成功触发强杀；false 表示回退到 killBackgroundProcesses。
     */
    fun forceStop(context: Context): Boolean {
        if (rootForceStop()) return true
        // 回退：仅能杀后台进程，前台无效
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(PKG)
        }
        return false
    }

    /** 用 root 强杀，依次尝试多种 su 调用形态，任一成功即返回 true。 */
    private fun rootForceStop(): Boolean {
        // 1) 交互式 su（兼容性最好，适配 Magisk / LSPosed）
        if (runSuInteractive("am force-stop $PKG")) return true
        // 2) su -c 形态
        if (runSu(arrayOf("su", "-c", "am force-stop $PKG"))) return true
        // 3) am kill 兜底（通知进程自杀，前台也能生效）
        if (runSuInteractive("am kill $PKG")) return true
        return false
    }

    /** 交互式 su：启动 su 后把命令写入其标准输入并退出。 */
    private fun runSuInteractive(cmd: String): Boolean = runCatching {
        val p = Runtime.getRuntime().exec("su")
        p.outputStream.use { os ->
            os.write("$cmd\n".toByteArray())
            os.write("exit\n".toByteArray())
            os.flush()
        }
        // 消费流，避免子进程因管道写满而卡住
        p.inputStream.readBytes()
        p.errorStream.readBytes()
        p.waitFor() == 0
    }.getOrDefault(false)

    /** 直接以参数数组执行 su 命令。 */
    private fun runSu(cmd: Array<String>): Boolean = runCatching {
        val p = Runtime.getRuntime().exec(cmd)
        p.inputStream.readBytes()
        p.errorStream.readBytes()
        p.waitFor() == 0
    }.getOrDefault(false)

    /** 强制停止并重新拉起应用商店：先强杀，再（主线程）拉起。 */
    fun restart(context: Context) {
        val appCtx = context.applicationContext
        Thread {
            val rooted = forceStop(appCtx)
            // 等强杀真正生效后再拉起，避免刚启动的进程被立刻清掉
            runCatching { Thread.sleep(if (rooted) 500L else 200L) }
            Handler(Looper.getMainLooper()).post {
                val launch = appCtx.packageManager.getLaunchIntentForPackage(PKG)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    appCtx.startActivity(launch)
                    Toast.makeText(
                        appCtx,
                        if (rooted) "已强制停止并重启应用商店" else "未授予 root，已尝试重启应用商店",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(appCtx, "未找到应用商店", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
