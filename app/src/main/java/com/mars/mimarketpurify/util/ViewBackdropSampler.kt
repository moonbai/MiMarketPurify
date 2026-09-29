package com.mars.mimarketpurify.util

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 采样浮层下方区域生成毛玻璃底图：PixelCopy 优先（硬件合成更准），失败时回退 [View.draw]
 * 软件绘制。可在离屏绘制前临时隐藏注入的 Compose 浮层自身（[excludedView]），避免递归采样。
 * 已去除 HyperModifier 中仅服务于米家（Mi Home）卡片的专用分支。
 * 移植自 AritxOnly/HyperModifier 的 ViewBackdropSampler。
 */

@Composable
internal fun ViewBackdropLayer(
    snapshot: ViewBackdropSnapshot?,
    backdrop: LayerBackdrop,
) {
    if (snapshot == null) return
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(snapshot.alignmentOffsetXPx, snapshot.alignmentOffsetYPx)
            }
            .graphicsLayer(alpha = BACKDROP_SOURCE_ALPHA)
            .requiredSize(
                width = (snapshot.sourceWidthPx / density.density).dp,
                height = (snapshot.sourceHeightPx / density.density).dp,
            )
            .layerBackdrop(backdrop),
    ) {
        Image(
            bitmap = snapshot.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal data class ViewBackdropBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal data class ViewBackdropSnapshot(
    val bitmap: Bitmap,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val alignmentOffsetXPx: Int,
    val alignmentOffsetYPx: Int,
    val generation: Long,
)

internal class ViewBackdropSampler(
    private val source: View,
    private val excludedView: View? = null,
    private val pixelCopyWindow: Window? = null,
    private val usePixelCopySampling: () -> Boolean = { false },
    private val minimumCaptureIntervalMs: Long = 0L,
    private val allowSoftwareFallback: Boolean = true,
    private val matchDisplayRefreshRate: Boolean = false,
    private val sampleOnSourceFrame: Boolean = false,
    private val pixelCopyRetryDelayMs: Long = PIXEL_COPY_RETRY_DELAY_MS,
    private val onSnapshotChanged: (ViewBackdropSnapshot?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var bounds: ViewBackdropBounds? = null
    private var captureScheduled = false
    private var lastCaptureStartedAt = Long.MIN_VALUE
    private var keepCapturingUntil = Long.MIN_VALUE
    private var generation = 0L
    private var lastPublishedBitmap: Bitmap? = null
    private var lastPublishedSourceWidthPx = 0
    private var lastPublishedSourceHeightPx = 0
    private var lastPublishedOffsetXPx = 0
    private var lastPublishedOffsetYPx = 0
    private var pendingPixelCopyBitmap: Bitmap? = null
    private var pixelCopyInFlight = false
    private var pixelCopyRefreshPending = false
    private var pixelCopyFallbackUntil = Long.MIN_VALUE
    private var samplingContextGeneration = 0L
    private var disposed = false
    private var active = true
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        if (!active) return@OnScrollChangedListener
        if (usePixelCopySampling()) {
            keepCapturingUntil = maxOf(
                keepCapturingUntil,
                SystemClock.uptimeMillis() + PIXEL_COPY_SCROLL_SETTLE_MS,
            )
        }
        requestCapture()
    }
    private val captureRunnable = Runnable {
        captureScheduled = false
        capture()
    }

    init {
        if (source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
        }
    }

    fun setNavigationBounds(value: ViewBackdropBounds) {
        if (bounds == value) return
        bounds = value
        requestCaptureBurst(INITIAL_CAPTURE_BURST_MS)
    }

    fun onTouchEvent(event: MotionEvent) {
        if (!active) return
        val now = SystemClock.uptimeMillis()
        keepCapturingUntil = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> now + ACTIVE_CAPTURE_GRACE_MS
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> now + FLING_CAPTURE_MS
            else -> keepCapturingUntil
        }
        requestCapture()
    }

    fun onFrame() {
        if (active && (sampleOnSourceFrame || SystemClock.uptimeMillis() < keepCapturingUntil)) {
            requestCapture()
        }
    }

    /** 当隐藏的导航 chrome 位于广告、弹窗或另一个 Activity 之下时，不应持续复制其下方帧。 */
    fun setActive(value: Boolean) {
        if (active == value || disposed) return
        active = value
        samplingContextGeneration += 1
        if (value) {
            requestCaptureBurst()
        } else {
            handler.removeCallbacks(captureRunnable)
            captureScheduled = false
            pixelCopyRefreshPending = false
            keepCapturingUntil = Long.MIN_VALUE
        }
    }

    fun requestCapture() {
        if (disposed || !active || bounds == null || !source.isAttachedToWindow) return
        if (pixelCopyInFlight) {
            // PixelCopy 不可取消。记录可见内容在请求飞行期间已前进，待其完成时立即抓取最新帧。
            pixelCopyRefreshPending = true
            return
        }
        if (captureScheduled) return
        val elapsed = if (lastCaptureStartedAt == Long.MIN_VALUE) Long.MAX_VALUE
        else SystemClock.uptimeMillis() - lastCaptureStartedAt
        val captureInterval = maxOf(
            minimumCaptureIntervalMs,
            if (matchDisplayRefreshRate) {
                backdropCaptureCadenceMs(source.display?.refreshRate ?: Float.NaN)
            } else if (usePixelCopySampling()) {
                PIXEL_COPY_CAPTURE_INTERVAL_MS
            } else {
                CAPTURE_INTERVAL_MS
            },
        )
        captureScheduled = true
        handler.postDelayed(captureRunnable, (captureInterval - elapsed).coerceAtLeast(0L))
    }

    fun requestCaptureBurst(durationMs: Long = PAGE_CHANGE_CAPTURE_BURST_MS) {
        if (disposed || !active) return
        keepCapturingUntil = maxOf(
            keepCapturingUntil,
            SystemClock.uptimeMillis() + durationMs.coerceAtLeast(0L),
        )
        requestCapture()
    }

    fun invalidateSamplingContext() {
        samplingContextGeneration += 1
        requestCapture()
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacks(captureRunnable)
        captureScheduled = false
        if (source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.removeOnScrollChangedListener(scrollChangedListener)
        }
        // 已发布的 Bitmap 仍可能被 Compose 记录的绘制命令引用，此处只丢弃自身状态，
        // 其生命周期交由渲染器与 GC 处理。
        lastPublishedBitmap = null
        onSnapshotChanged(null)
    }

    private fun capture() {
        if (disposed || !active) return
        val target = bounds ?: return
        if (!source.isAttachedToWindow || source.width <= 0 || source.height <= 0) {
            retryInitialCapture()
            return
        }
        val sourceLocation = IntArray(2)
        source.getLocationInWindow(sourceLocation)
        val bleed = ceil(source.resources.displayMetrics.density * SAMPLE_BLEED_DP).toInt()
        val sourceRect = Rect(
            target.left - sourceLocation[0] - bleed,
            target.top - sourceLocation[1] - bleed,
            target.left - sourceLocation[0] + target.width + bleed,
            target.top - sourceLocation[1] + target.height + bleed,
        )
        if (!sourceRect.intersect(0, 0, source.width, source.height) || sourceRect.isEmpty) {
            retryInitialCapture()
            return
        }
        val targetLeftInSource = target.left - sourceLocation[0]
        val targetTopInSource = target.top - sourceLocation[1]
        val targetOffsetX = targetLeftInSource - sourceRect.left
        val targetOffsetY = targetTopInSource - sourceRect.top
        val alignmentOffsetX = (
            (sourceRect.width() - target.width) / 2f - targetOffsetX
            ).roundToInt()
        val alignmentOffsetY = (
            (sourceRect.height() - target.height) / 2f - targetOffsetY
            ).roundToInt()

        val now = SystemClock.uptimeMillis()
        if (pixelCopyWindow != null && usePixelCopySampling() &&
            now >= pixelCopyFallbackUntil &&
            requestPixelCopy(
                window = pixelCopyWindow,
                sourceRect = sourceRect,
                sourceLocation = sourceLocation,
                sampleWidth = sourceRect.width(),
                sampleHeight = sourceRect.height(),
                alignmentOffsetXPx = alignmentOffsetX,
                alignmentOffsetYPx = alignmentOffsetY,
            )
        ) return

        if (!allowSoftwareFallback) {
            // 保留上一帧采样（或毛玻璃配方的兜底着色），而非在 PixelCopy 瞬时失败后同步重绘复杂 App。
            lastCaptureStartedAt = now
            if (now < keepCapturingUntil) requestCapture()
            return
        }

        lastCaptureStartedAt = now
        val bitmap = obtainBuffer(
            max(1, (sourceRect.width() * SAMPLE_SCALE).roundToInt()),
            max(1, (sourceRect.height() * SAMPLE_SCALE).roundToInt()),
        )
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.scale(SAMPLE_SCALE, SAMPLE_SCALE)
        canvas.translate(-sourceRect.left.toFloat(), -sourceRect.top.toFloat())
        // 离屏绘制前临时隐藏自身 Compose 浮层，使底图包含 App 原生层而不递归采样自己。
        val excludedAlpha = excludedView?.alpha
        try {
            if (excludedAlpha != null) excludedView.alpha = 0f
            source.draw(canvas)
        } catch (_: Throwable) {
            bitmap.recycle() // 从未发布给 Compose。
            retryInitialCapture()
            return
        } finally {
            if (excludedAlpha != null) excludedView.alpha = excludedAlpha
        }

        publishIfChanged(
            bitmap = bitmap,
            sourceWidthPx = sourceRect.width(),
            sourceHeightPx = sourceRect.height(),
            alignmentOffsetXPx = alignmentOffsetX,
            alignmentOffsetYPx = alignmentOffsetY,
        )
    }

    private fun requestPixelCopy(
        window: Window,
        sourceRect: Rect,
        sourceLocation: IntArray,
        sampleWidth: Int,
        sampleHeight: Int,
        alignmentOffsetXPx: Int,
        alignmentOffsetYPx: Int,
    ): Boolean {
        val windowRoot = window.decorView
        if (windowRoot.width <= 0 || windowRoot.height <= 0) return false
        val requestRect = Rect(sourceRect).apply {
            offset(sourceLocation[0], sourceLocation[1])
        }
        if (!requestRect.intersect(0, 0, windowRoot.width, windowRoot.height) ||
            requestRect.width() != sampleWidth || requestRect.height() != sampleHeight
        ) return false

        val bitmap = obtainBuffer(
            max(1, (sampleWidth * PIXEL_COPY_SAMPLE_SCALE).roundToInt()),
            max(1, (sampleHeight * PIXEL_COPY_SAMPLE_SCALE).roundToInt()),
        )
        val requestGeneration = samplingContextGeneration
        pixelCopyRefreshPending = false
        pixelCopyInFlight = true
        pendingPixelCopyBitmap = bitmap
        lastCaptureStartedAt = SystemClock.uptimeMillis()
        return runCatching {
            PixelCopy.request(window, requestRect, bitmap, { result ->
                pixelCopyInFlight = false
                pendingPixelCopyBitmap = null
                val refreshLatestFrame = pixelCopyRefreshPending
                pixelCopyRefreshPending = false
                if (disposed) {
                    bitmap.recycle()
                    return@request
                }
                if (!active) {
                    bitmap.recycle() // 此结果未发布。
                    return@request
                }
                if (requestGeneration != samplingContextGeneration || !usePixelCopySampling()) {
                    bitmap.recycle() // 有更新的生成已取代本次拷贝。
                    requestCapture()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    publishIfChanged(
                        bitmap = bitmap,
                        sourceWidthPx = sampleWidth,
                        sourceHeightPx = sampleHeight,
                        alignmentOffsetXPx = alignmentOffsetXPx,
                        alignmentOffsetYPx = alignmentOffsetYPx,
                    )
                    if (refreshLatestFrame) requestCapture()
                } else {
                    bitmap.recycle() // 失败拷贝不可显示。
                    // 短暂回退到 View.draw()，避免硬件采样瞬断导致毛玻璃留空；下一轮突发重试硬件采样。
                    pixelCopyFallbackUntil = SystemClock.uptimeMillis() +
                        pixelCopyRetryDelayMs
                    requestCapture()
                }
            }, handler)
        }.onFailure {
            pixelCopyInFlight = false
            pendingPixelCopyBitmap = null
            pixelCopyRefreshPending = false
            pixelCopyFallbackUntil = SystemClock.uptimeMillis() + pixelCopyRetryDelayMs
            bitmap.recycle() // PixelCopy 从未接收此目标。
        }.isSuccess
    }

    private fun publishIfChanged(
        bitmap: Bitmap,
        sourceWidthPx: Int,
        sourceHeightPx: Int,
        alignmentOffsetXPx: Int,
        alignmentOffsetYPx: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        val unchanged = lastPublishedSourceWidthPx == sourceWidthPx &&
            lastPublishedSourceHeightPx == sourceHeightPx &&
            lastPublishedOffsetXPx == alignmentOffsetXPx &&
            lastPublishedOffsetYPx == alignmentOffsetYPx &&
            lastPublishedBitmap?.sameAs(bitmap) == true
        if (unchanged) {
            bitmap.recycle() // 从未交给 Compose。
            if (now < keepCapturingUntil) requestCapture()
            return
        }
        if (generation == 0L) {
            // LayerBackdrop 在相邻 Compose 帧上注册生产者与消费者。一段启动期突发保证在无需
            // 等待用户输入的情况下产生第二帧采样。
            keepCapturingUntil = maxOf(keepCapturingUntil, now + INITIAL_CAPTURE_BURST_MS)
        }
        lastPublishedBitmap = bitmap
        lastPublishedSourceWidthPx = sourceWidthPx
        lastPublishedSourceHeightPx = sourceHeightPx
        lastPublishedOffsetXPx = alignmentOffsetXPx
        lastPublishedOffsetYPx = alignmentOffsetYPx
        generation += 1
        onSnapshotChanged(
            ViewBackdropSnapshot(
                bitmap = bitmap,
                sourceWidthPx = sourceWidthPx,
                sourceHeightPx = sourceHeightPx,
                alignmentOffsetXPx = alignmentOffsetXPx,
                alignmentOffsetYPx = alignmentOffsetYPx,
                generation = generation,
            ),
        )
        if (now < keepCapturingUntil) requestCapture()
    }

    private fun retryInitialCapture() {
        if (disposed || !active || generation != 0L || captureScheduled) return
        captureScheduled = true
        handler.postDelayed(captureRunnable, maxOf(CAPTURE_INTERVAL_MS, minimumCaptureIntervalMs))
    }

    private fun obtainBuffer(width: Int, height: Int): Bitmap =
        // 交给 Compose 的快照从采样器视角看是不可变的，此处复用或回收都不安全：
        // 先前的 RenderNode 可能在下一帧绘制它。
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** 显示刷新率跟随采样（不让其它目标适配器被迫改变节奏）。内联自 HyperModifier 的 BackdropCaptureCadence。 */
    private fun backdropCaptureCadenceMs(refreshRate: Float): Long {
        val validRate = if (refreshRate.isFinite() && refreshRate > 0f) refreshRate else 60f
        return max(4L, (1_000f / min(validRate, 120f)).toLong())
    }

    private companion object {
        const val SAMPLE_BLEED_DP = 32f
        const val SAMPLE_SCALE = 0.18f
        const val PIXEL_COPY_SAMPLE_SCALE = 0.14f
        const val PIXEL_COPY_CAPTURE_INTERVAL_MS = 16L
        const val PIXEL_COPY_SCROLL_SETTLE_MS = 240L
        const val PIXEL_COPY_RETRY_DELAY_MS = 1_000L
        const val CAPTURE_INTERVAL_MS = 16L
        const val INITIAL_CAPTURE_BURST_MS = 350L
        const val PAGE_CHANGE_CAPTURE_BURST_MS = 500L
        const val ACTIVE_CAPTURE_GRACE_MS = 100L
        // 实际 fling 动作由 OnScrollChangedListener 跟进；此处仅为某些晚一帧派发滚动回调的
        // 自定义容器兜底。
        const val FLING_CAPTURE_MS = 160L
    }
}

private const val BACKDROP_SOURCE_ALPHA = 0.001f
