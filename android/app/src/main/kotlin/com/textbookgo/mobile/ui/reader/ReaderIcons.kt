package com.textbookgo.mobile.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 阅读器自带的几个小图标：Material 核心图标集里没有月亮、网格、页码和全屏，
 * 用 24dp 的矢量路径补上，保证和系统图标一致的可着色能力。
 */
internal object ReaderIcons {

    val Moon: ImageVector by lazy {
        icon("ReaderMoon") {
            fill {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                curveToRelative(0f, 4.97f, 4.03f, 9f, 9f, 9f)
                curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveToRelative(-0.44f, -0.06f, -0.9f, -0.1f, -1.36f, -0.1f)
                close()
            }
        }
    }

    /** 页码跳转：一个 “#”。 */
    val PageNumber: ImageVector by lazy {
        icon("ReaderPageNumber") {
            stroke {
                moveTo(9f, 4f)
                lineTo(7.5f, 20f)
                moveTo(16.5f, 4f)
                lineTo(15f, 20f)
                moveTo(4.5f, 9f)
                lineTo(19.5f, 9f)
                moveTo(3.5f, 15f)
                lineTo(18.5f, 15f)
            }
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.fill(block: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathBuilder = block,
        )
    }

    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }
}
