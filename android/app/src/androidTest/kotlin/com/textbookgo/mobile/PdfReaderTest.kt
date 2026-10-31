package com.textbookgo.mobile

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.data.reader.ReaderProgressStore
import com.textbookgo.mobile.ui.reader.PdfReaderScreen
import com.textbookgo.mobile.ui.theme.TextbookGoTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 应用内阅读器的交互验收：打开、翻页、缩放、跳页、夜读与进度恢复。
 * 直接组合 [PdfReaderScreen]，用测试内生成的 3 页 PDF，不依赖网络和下载流程。
 */
@RunWith(AndroidJUnit4::class)
class PdfReaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val open = mutableStateOf(true)
    private var closed = false
    private lateinit var file: File
    private lateinit var progress: ReaderProgressStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("textbookgo-reader", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        file = File(context.cacheDir, "reader-test.pdf").also { createPdf(it) }
        progress = ReaderProgressStore(context)
        closed = false
        open.value = true

        composeRule.setContent {
            TextbookGoTheme {
                if (open.value) {
                    PdfReaderScreen(
                        book = ShelfBook(
                            id = "reader-test",
                            title = "测试教材 七年级上册",
                            filename = file.name,
                            size = file.length(),
                            downloadedAt = "2026-10-30T09:12:00+08:00",
                        ),
                        file = file,
                        progress = progress,
                        theme = com.textbookgo.mobile.core.repository.AppTheme.SYSTEM,
                        onClose = {
                            closed = true
                            open.value = false
                        },
                    )
                } else {
                    Text("closed")
                }
            }
        }
        waitForReader()
    }

    private fun waitForReader() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("reader_page_0").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun readerOpensOnFirstPage() {
        composeRule.onNodeWithText("第 1 / 3 页").assertIsDisplayed()
        composeRule.onNodeWithText("测试教材 七年级上册").assertIsDisplayed()
    }

    @Test
    fun nextAndPreviousButtonsTurnPages() {
        composeRule.onNodeWithContentDescription("下一页").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("第 2 / 3 页").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("上一页").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("第 1 / 3 页").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tapTogglesReaderChrome() {
        composeRule.onNodeWithTag("reader_page_0").performTouchInput { click() }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("第 1 / 3 页").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("reader_page_0").performTouchInput { click() }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("第 1 / 3 页").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun doubleTapZoomsAndChipResets() {
        composeRule.onNodeWithTag("reader_page_0").performTouchInput { doubleClick() }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("复位").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("复位").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("复位").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun pinchZoomsCurrentPage() {
        composeRule.onNodeWithTag("reader_page_0").performTouchInput {
            pinch(
                start0 = center + Offset(-80f, 0f),
                end0 = center + Offset(-320f, 0f),
                start1 = center + Offset(80f, 0f),
                end1 = center + Offset(320f, 0f),
                durationMillis = 400,
            )
        }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("复位").fetchSemanticsNodes().isNotEmpty()
        }
        // 放大后双击复位，不应该只是把页码跳走。
        composeRule.onNodeWithTag("reader_page_0").performTouchInput { doubleClick() }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("复位").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun jumpDialogRestoresReadingPosition() {
        composeRule.onNodeWithText("第 1 / 3 页").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("跳转到页面").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("3")
        composeRule.onNodeWithText("跳转").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("第 3 / 3 页").fetchSemanticsNodes().isNotEmpty()
        }

        // 关闭再打开同一本教材，应该回到第 3 页。
        composeRule.runOnIdle { open.value = false }
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("closed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { open.value = true }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("reader_page_0").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("第 3 / 3 页").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("第 3 / 3 页").assertIsDisplayed()
    }

    @Test
    fun nightModeToggleUpdatesSubtitle() {
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("夜读").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("夜读").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("夜读模式").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun backButtonClosesReader() {
        composeRule.onNodeWithContentDescription("返回书架").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("closed").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(closed)
    }

    @Test
    fun externalOpenEntryExistsInMenu() {
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText("用其他应用打开").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("用其他应用打开").assertIsDisplayed()
    }

    private fun createPdf(target: File) {
        val document = PdfDocument()
        try {
            repeat(3) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, index + 1).create(),
                )
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val paint = android.graphics.Paint().apply {
                    color = Color.rgb(28, 27, 26)
                    textSize = 30f
                    isAntiAlias = true
                }
                canvas.drawText("TextbookGo 阅读器测试", 60f, 120f, paint)
                paint.textSize = 22f
                canvas.drawText("第 ${index + 1} 页", 60f, 180f, paint)
                paint.textSize = 16f
                canvas.drawText("用于验证滚动、缩放与进度恢复。", 60f, 230f, paint)
                document.finishPage(page)
            }
            FileOutputStream(target).use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}
