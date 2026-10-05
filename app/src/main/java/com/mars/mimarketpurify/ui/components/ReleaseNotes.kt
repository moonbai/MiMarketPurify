package com.mars.mimarketpurify.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
// 与本模块其它 Compose 页面保持一致：Text 统一取自 material3（MiuixTheme 下同样呈现 MiuiX 观感）。
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mars.mimarketpurify.MiuiX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.res.stringResource
import com.mars.mimarketpurify.R

/**
 * GitHub Release 说明的轻量渲染（Markdown + 内联 HTML 混排）。
 *
 * GitHub 的 release body 常见两种写法混在一起：
 *  - Markdown：`### 更新说明:`、`1. 列表`、`[文本](链接)`、`**加粗**`、`` `代码` ``；
 *  - 内联 HTML：`<img width="2160" height="986" src="https://github.com/user-attachments/assets/xxx" />`。
 *
 * 旧实现把整段 body 当成一个纯文本 [androidx.compose.material3.Text] 直接显示，于是
 * `<img .../>` 会原样暴露成「代码」。这里做**最小解析**：把 body 切成「文本块 / 图片块」两类，
 * 文本块只做轻量 Markdown / HTML 清理，图片块交给 [RemoteImage] 用自带的
 * [HttpURLConnection] 下载并显示——**不引入任何第三方图片加载库**（Coil 等），
 * 与本仓库「最小依赖」的约定一致。
 */
sealed interface ReleaseNoteBlock {
    /** 一段文本（可能多行，行首 `#` 由渲染层识别为标题） */
    data class Text(val content: String) : ReleaseNoteBlock

    /** 一张图片；[width] / [height] 来自 `<img>` 的声明尺寸，可能为空 */
    data class Image(val url: String, val width: Int? = null, val height: Int? = null) : ReleaseNoteBlock
}

// ── 解析用的正则（均可在多行 body 上安全匹配，`<img>` 标签可能跨行）──
private val IMG_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val IMG_SRC = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val IMG_WIDTH = Regex("""\bwidth\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
private val IMG_HEIGHT = Regex("""\bheight\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)
/** Markdown 图片语法 `![alt](url)`，含可选的 `"title"` */
private val MD_IMAGE = Regex("""!\[[^\]]*]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)""")
/** 行内链接 `[文本](链接)`，统一降级为可读的「文本」 */
private val MD_LINK = Regex("""\[([^\]]*)]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)""")
/** 行内代码 `` `x` `` → 内容 */
private val MD_INLINE_CODE = Regex("`([^`]+)`")
/** 强调标记（仅处理成对出现的，避免误删正文里的单个 `*` / `_`） */
private val MD_EMPHASIS = Regex("""\*\*|__|~~""")
/** GitHub 自动链接 `<https://...>` → 裸链接，先于 HTML 标签清理处理，避免被整段删掉 */
private val MD_AUTOLINK = Regex("""<(https?://[^>\s]+)>""")
/** 其余 HTML 标签（`<details>` / `<summary>` / `<p>` / `<a>` …）一律去掉，只留文字 */
private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
private val HTML_BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val MANY_BLANK_LINES = Regex("""\n{3,}""")
/** 标题行前缀（`#` ~ `######`），渲染时加粗放大 */
private val HEADING_MARKER = Regex("""^#{1,6}\s*""")

/**
 * 把 release body 解析成 [ReleaseNoteBlock] 列表：图片（Markdown 或 HTML）抽成独立块，
 * 其余相邻内容归并为文本块。解析失败（正则异常等）时整体退化为一个文本块，绝不让弹窗崩掉。
 */
internal fun parseReleaseNotes(raw: String): List<ReleaseNoteBlock> {
    if (raw.isBlank()) return emptyList()
    return runCatching { doParse(raw) }.getOrElse { listOf(ReleaseNoteBlock.Text(raw.trim())) }
}

private fun doParse(raw: String): List<ReleaseNoteBlock> {
    // `<br>` 视作换行，便于后续按行渲染
    val text = raw.replace(HTML_BR, "\n")

    // 收集所有图片 token（记录其在原文中的区间），最后按出现顺序切分
    val images = mutableListOf<ImageToken>()
    IMG_TAG.findAll(text).forEach { match ->
        val value = match.value
        val url = IMG_SRC.find(value)?.groupValues?.get(1)
        if (!url.isNullOrBlank()) {
            images += ImageToken(
                start = match.range.first,
                end = match.range.last + 1,
                url = url,
                width = IMG_WIDTH.find(value)?.groupValues?.get(1)?.toIntOrNull(),
                height = IMG_HEIGHT.find(value)?.groupValues?.get(1)?.toIntOrNull(),
            )
        }
    }
    MD_IMAGE.findAll(text).forEach { match ->
        images += ImageToken(
            start = match.range.first,
            end = match.range.last + 1,
            url = match.groupValues[1],
        )
    }
    images.sortBy { it.start }

    val blocks = mutableListOf<ReleaseNoteBlock>()
    var cursor = 0
    for (image in images) {
        if (image.start < cursor) continue // 区间重叠，跳过（理论上不会出现）
        if (image.start > cursor) {
            cleanText(text.substring(cursor, image.start))?.let { blocks += ReleaseNoteBlock.Text(it) }
        }
        blocks += ReleaseNoteBlock.Image(image.url, image.width, image.height)
        cursor = image.end
    }
    if (cursor < text.length) {
        cleanText(text.substring(cursor))?.let { blocks += ReleaseNoteBlock.Text(it) }
    }
    return blocks
}

private data class ImageToken(
    val start: Int,
    val end: Int,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * 文本块的轻量清理：链接降级为文字、去掉行内代码反引号与强调标记、剥离剩余 HTML 标签。
 * **保留行首 `#` 标记**，交由渲染层识别为标题，避免把「### 更新说明」显示成裸符号。
 * 清理后为空时返回 null（该块被丢弃）。
 */
private fun cleanText(chunk: String): String? {
    var s = chunk
    s = MD_AUTOLINK.replace(s) { it.groupValues[1] }
    s = MD_LINK.replace(s) { it.groupValues[1] }
    s = MD_INLINE_CODE.replace(s) { it.groupValues[1] }
    s = MD_EMPHASIS.replace(s, "")
    s = HTML_TAG.replace(s, "")
    s = MANY_BLANK_LINES.replace(s, "\n\n")
    s = s.trim()
    return s.ifBlank { null }
}

/**
 * 渲染 GitHub Release 说明：文本块按行输出（`#` 行加粗放大），图片块直接显示远程图片。
 * 图片点击可跳转查看原图（由 [onImageClick] 决定，通常走浏览器打开）。
 */
@Composable
fun ReleaseNotesView(
    notes: String,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
) {
    val blocks = remember(notes) { parseReleaseNotes(notes) }
    if (blocks.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ReleaseNoteBlock.Text -> {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    NoteTextBlock(block.content)
                }
                is ReleaseNoteBlock.Image -> {
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    RemoteImage(
                        url = block.url,
                        declaredWidth = block.width,
                        declaredHeight = block.height,
                        modifier = Modifier.clickable { onImageClick(block.url) },
                    )
                }
            }
        }
    }
}

/** 文本块：逐行渲染，识别 `#` 标题行，其余作为正文。 */
@Composable
private fun NoteTextBlock(content: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        content.split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()
            if (trimmed.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                return@forEach
            }
            val marker = HEADING_MARKER.find(trimmed)
            if (marker != null) {
                Text(
                    text = trimmed.substring(marker.value.length),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            } else {
                Text(
                    text = line,
                    fontSize = 13.5.sp,
                    color = colors.onSurfaceSecondary,
                    // 行距与仓库其它说明文字保持同一节奏（MiuiX.LINE_SPACING = 1.45）
                    lineHeight = (13.5 * MiuiX.LINE_SPACING).sp,
                )
            }
        }
    }
}

/**
 * 远程图片：进入组合时异步下载并缓存（[ReleaseImageCache]），下载中显示占位、失败显示提示。
 * 已知声明宽高时先按比例占位（避免加载完成时弹窗高度跳动），未知时用固定占位高度。
 */
@Composable
private fun RemoteImage(
    url: String,
    declaredWidth: Int?,
    declaredHeight: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    // 命中缓存则首帧即显示，避免弹窗二次打开时闪一下占位。
    var bitmap by remember(url) { mutableStateOf(ReleaseImageCache.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap == null) {
            val loaded = loadBitmap(url)
            if (loaded != null) bitmap = loaded else failed = true
        }
    }

    val loaded = bitmap
    val ratio = when {
        loaded != null && loaded.height > 0 -> loaded.width.toFloat() / loaded.height.toFloat()
        declaredWidth != null && declaredHeight != null && declaredWidth > 0 && declaredHeight > 0 ->
            declaredWidth.toFloat() / declaredHeight.toFloat()
        else -> null
    }

    Box(
        // clip / background 放在最前，调用方传入的 clickable（水波纹）才会被圆角裁剪。
        modifier = Modifier
            .fillMaxWidth()
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.height(160.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(colors.dividerLine.copy(alpha = 0.35f))
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loaded != null -> Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            failed -> Text(
                text = stringResource(R.string.img_load_fail),
                fontSize = 12.sp,
                color = colors.onSurfaceSecondary,
            )
            else -> Text(
                text = stringResource(R.string.img_loading),
                fontSize = 12.sp,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

/** 图片内存缓存：按字节计上限 8 MB，避免反复打开更新弹窗时重复下载。 */
private object ReleaseImageCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(url: String): Bitmap? = cache.get(url)

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

/** 单张图片解码的最大宽度（px）：超过时按 2 的幂采样，防止大截图直接吃满内存。 */
private const val MAX_IMAGE_WIDTH = 1600

/**
 * 后台下载并解码图片：先读字节再按需采样解码（两次解码，省连接）。
 * 任意环节失败均返回 null，由上层显示「图片加载失败」，不影响弹窗其它内容。
 */
private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    ReleaseImageCache.get(url)?.let { return@withContext it }
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MiMarketPurify-UpdateChecker")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            val bytes = connection.inputStream.use { it.readBytes() }
            decodeSampled(bytes)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()?.also { ReleaseImageCache.put(url, it) }
}

/** 先把字节流按原始尺寸读一遍，据此计算 [BitmapFactory.Options.inSampleSize] 再真正解码。 */
private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > MAX_IMAGE_WIDTH) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
