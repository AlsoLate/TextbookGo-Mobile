package com.textbookgo.mobile.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.core.graphics.createBitmap
import java.io.Closeable
import java.io.File

/**
 * 一本已下载教材的 PDF 句柄。
 *
 * PdfRenderer 不是线程安全的，也不支持并发打开页面，因此约定：
 * 打开、读取页面尺寸、渲染统一在阅读器的单线程调度器上执行。
 */
class PdfBookDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    /** 每页的「高 / 宽」，渲染完成前先用它占位，滚动时页面高度不会跳动。 */
    private val aspectRatios: FloatArray = FloatArray(pageCount).also { ratios ->
        for (index in 0 until pageCount) {
            renderer.openPage(index).use { page ->
                ratios[index] = if (page.width > 0) {
                    page.height.toFloat() / page.width.toFloat()
                } else {
                    A4_ASPECT
                }
            }
        }
    }

    fun aspectRatio(index: Int): Float = aspectRatios.getOrElse(index) { A4_ASPECT }

    /** 必须在单线程渲染调度器上调用。 */
    fun render(index: Int, widthPx: Int): Bitmap {
        val width = widthPx.coerceIn(1, MAX_RENDER_WIDTH_PX)
        renderer.openPage(index).use { page ->
            val height = (width * page.height.toFloat() / page.width.toFloat())
                .toInt()
                .coerceAtLeast(1)
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer 不铺白底：透明区域在深色背景上会变成黑块。
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        const val A4_ASPECT = 1.414f
        private const val MAX_RENDER_WIDTH_PX = 2400

        fun open(file: File): PdfBookDocument {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfBookDocument(descriptor, PdfRenderer(descriptor))
            } catch (error: Throwable) {
                runCatching { descriptor.close() }
                throw error
            }
        }
    }
}

/**
 * 页面位图缓存：按字节数淘汰，翻书时不必反复重渲染，也不会让大页撑爆内存。
 * 渲染宽度略大于屏幕宽度，保证 1.5 倍以内缩放依然清晰。
 */
class PdfPageCache(
    private val document: PdfBookDocument,
    private val renderWidthPx: Int,
    maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    private val cache = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    fun get(index: Int): Bitmap? = cache.get(index)

    /** 必须在渲染调度器上调用。 */
    fun getOrRender(index: Int): Bitmap? {
        cache.get(index)?.let { return it }
        val bitmap = runCatching { document.render(index, renderWidthPx) }.getOrNull() ?: return null
        cache.put(index, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()

    private companion object {
        /** 1080p 屏幕下约 3 页 A4，低端机也不至于触发 OOM。 */
        const val DEFAULT_MAX_BYTES = 40 * 1024 * 1024
    }
}
