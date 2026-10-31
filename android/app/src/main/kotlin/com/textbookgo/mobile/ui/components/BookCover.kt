package com.textbookgo.mobile.ui.components

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import coil.compose.AsyncImage
import com.textbookgo.mobile.core.design.CoverPalette
import com.textbookgo.mobile.ui.theme.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 教材封面：优先平台封面图；无图时用 PDF 首页缩略图；再失败露出确定性几何拼贴。
 * 书架传入 [pdfFile]，下载页不传，保持同一视觉语言。
 */
@Composable
fun BookCover(
    id: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    pdfFile: File? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(148f / 200f)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .testTag("cover_$id"),
    ) {
        GeometricCover(id)
        when {
            !coverUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            pdfFile != null -> {
                val thumbnail = produceState<ImageBitmap?>(
                    initialValue = null,
                    key1 = id,
                    key2 = pdfFile.path,
                    key3 = pdfFile.lastModified(),
                ) {
                    value = withContext(Dispatchers.IO) { renderFirstPage(pdfFile) }
                }
                thumbnail.value?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** 渲染 PDF 首页为缩略图；文件损坏或加密时返回 null，由几何封面兜底。 */
private fun renderFirstPage(file: File): ImageBitmap? {
    if (!file.exists() || file.length() == 0L) return null
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return@use null
                val page = renderer.openPage(0)
                try {
                    val width = 480
                    val height = (width * page.height.toFloat() / page.width)
                        .toInt()
                        .coerceAtLeast(1)
                    val bitmap = createBitmap(width, height)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                } finally {
                    page.close()
                }
            }
        }
    }.getOrNull()
}

@Composable
private fun GeometricCover(id: String) {
    val variant = CoverPalette.variant(id)
    Canvas(Modifier.fillMaxSize()) {
        fun block(x: Float, y: Float, w: Float, h: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(x * size.width, y * size.height),
                size = Size(w * size.width, h * size.height),
            )
        }

        when (variant) {
            0 -> {
                block(0f, 0f, 0.55f, 0.42f, CoverColors.sage)
                block(0.52f, 0.54f, 0.48f, 0.46f, CoverColors.linen)
                block(0.08f, 0.68f, 0.34f, 0.05f, CoverColors.ink)
            }

            1 -> {
                block(0f, 0.08f, 0.40f, 0.56f, CoverColors.terracotta)
                block(0.44f, 0f, 0.56f, 0.36f, CoverColors.linen)
                block(0.46f, 0.30f, 0.05f, 0.52f, CoverColors.ink)
            }

            2 -> {
                block(0f, 0f, 0.62f, 0.34f, CoverColors.basalt)
                block(0.56f, 0.54f, 0.44f, 0.46f, CoverColors.sage)
                block(0.12f, 0.79f, 0.40f, 0.05f, CoverColors.ink)
            }

            3 -> {
                block(0f, 0f, 0.44f, 0.62f, CoverColors.oatmeal)
                block(0.62f, 0.58f, 0.38f, 0.42f, CoverColors.terracotta)
                block(0.75f, 0f, 0.05f, 0.30f, CoverColors.ink)
            }

            4 -> {
                block(0f, 0f, 0.58f, 0.52f, CoverColors.linen)
                block(0.54f, 0.34f, 0.46f, 0.44f, CoverColors.sage)
                block(0.08f, 0.06f, 0.26f, 0.05f, CoverColors.ink)
            }

            else -> {
                block(0f, 0f, 0.52f, 0.40f, CoverColors.terracotta)
                block(0f, 0.70f, 0.64f, 0.30f, CoverColors.basalt)
                block(0.77f, 0.84f, 0.11f, 0.08f, CoverColors.ink)
            }
        }
    }
}
