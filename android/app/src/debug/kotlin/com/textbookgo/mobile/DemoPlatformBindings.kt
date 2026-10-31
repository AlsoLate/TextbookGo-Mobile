package com.textbookgo.mobile

import android.content.Context
import com.textbookgo.mobile.core.repository.AuthRepository
import com.textbookgo.mobile.core.repository.AuthResult
import com.textbookgo.mobile.core.repository.CatalogRepository
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.model.Tag
import com.textbookgo.mobile.core.catalog.CatalogRefreshResult
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.session.SessionState
import com.textbookgo.mobile.data.download.ContentSource
import com.textbookgo.mobile.data.download.ContentSpec
import com.textbookgo.mobile.data.download.Transfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Debug 登录：立即成功，便于 UI 与下载链路开发。 */
class DemoAuthRepository : AuthRepository {
    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    override suspend fun validate(): AuthResult {
        _session.value = SessionState.Validating
        _session.value = SessionState.Validated
        return AuthResult.Success
    }

    override suspend fun signIn(token: String): AuthResult {
        _session.value = SessionState.Validating
        _session.value = SessionState.Validated
        return AuthResult.Success
    }

    override fun expire(reason: String) {
        _session.value = SessionState.Expired(reason)
    }

    override suspend fun setRememberSession(enabled: Boolean) = Unit

    override suspend fun logout() {
        _session.value = SessionState.SignedOut
    }
}

/** Debug 目录：固定 6 本示例教材，覆盖七/八/九年级与多学科。 */
class DemoCatalogRepository(
    private val shelf: ShelfRepository,
) : CatalogRepository {

    private val entries: List<CatalogEntry> = listOf(
        demoEntry("demo-7-math", "数学 七年级上册", "七年级", "数学", "人教版", "上册"),
        demoEntry(
            "demo-7-math-2022",
            "义务教育教科书·数学 七年级下册（根据2022年版课程标准修订）",
            "七年级",
            "数学",
            "人教版",
            "下册",
        ),
        demoEntry("demo-7-chinese", "语文 七年级上册", "七年级", "语文", "统编版", "上册"),
        demoEntry("demo-7-english", "英语 七年级上册", "七年级", "英语", "人教版", "上册"),
        demoEntry("demo-8-math", "数学 八年级上册", "八年级", "数学", "人教版", "上册"),
        demoEntry("demo-8-physics", "物理 八年级上册", "八年级", "物理", "人教版", "上册"),
        demoEntry("demo-9-math", "数学 九年级上册", "九年级", "数学", "人教版", "上册"),
    )

    private val _grades = MutableStateFlow(CatalogRules.grades(entries))
    override val grades: StateFlow<List<String>> = _grades.asStateFlow()

    private val _status = MutableStateFlow<CatalogStatus>(CatalogStatus.Loaded(entries.size))
    override val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    override fun books(filter: CatalogFilter): Flow<List<Book>> =
        combine(
            MutableStateFlow(entries),
            shelf.shelf(),
        ) { current, shelfBooks ->
            CatalogRules.books(
                entries = current,
                grade = filter.grade,
                subject = filter.subject,
                downloadedIds = shelfBooks.map { it.id }.toSet(),
                version = filter.version,
            ).filter { CatalogRules.titleMatches(it.title, filter.query) }
        }

    override fun subjects(grade: String): Flow<List<String>> =
        kotlinx.coroutines.flow.flowOf(CatalogRules.subjects(entries, grade))

    override fun versions(grade: String, subject: String): Flow<List<String>> =
        kotlinx.coroutines.flow.flowOf(CatalogRules.versions(entries, grade, subject))

    override suspend fun findBook(id: String): Book? =
        entries.firstOrNull { it.id == id }?.let {
            Book(
                id = it.id,
                title = CatalogRules.displayTitle(it.title),
                edition2022 = CatalogRules.is2022Edition(it.title),
            )
        }

    override suspend fun refresh(force: Boolean): CatalogRefreshResult =
        CatalogRefreshResult.Complete(loadedShards = 1, bookCount = entries.size)

    private fun demoEntry(
        id: String,
        title: String,
        grade: String,
        subject: String,
        version: String,
        volume: String,
    ) = CatalogEntry(
        id = id,
        title = title,
        tags = listOf(
            Tag("grade-$grade", grade, CatalogRules.GRADE_DIMENSION),
            Tag("subject-$subject", subject, CatalogRules.SUBJECT_DIMENSION),
            Tag("version-$version", version, CatalogRules.VERSION_DIMENSION),
            Tag("volume-$volume", volume, CatalogRules.VOLUME_DIMENSION),
        ),
    )
}

/** Debug 内容源：按 bookId 生成一份本地 PDF 并返回其路径。 */
class DemoContentSource(private val context: Context) : ContentSource {
    override suspend fun resolve(bookId: String): ContentSpec? {
        val file = File(context.cacheDir, "demo-$bookId.pdf")
        if (!file.exists() || file.length() == 0L) {
            withContext(Dispatchers.IO) {
                createDemoPdf(file, bookId)
            }
        }
        return ContentSpec(
            title = "",
            urls = emptyList(),
            size = file.length(),
            assetName = file.absolutePath,
        )
    }

    private fun createDemoPdf(file: File, bookId: String) {
        val document = android.graphics.pdf.PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(
                    android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, index + 1).create(),
                )
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(28, 27, 26)
                    textSize = 28f
                    isAntiAlias = true
                }
                canvas.drawText("TextbookGo", 72f, 120f, paint)
                paint.textSize = 18f
                canvas.drawText("示例教材 · $bookId · 第 ${index + 1} 页", 72f, 170f, paint)
                paint.textSize = 14f
                canvas.drawText("此文件仅用于开发演示，不代表真实教材内容。", 72f, 220f, paint)
                document.finishPage(page)
            }
            FileOutputStream(file).use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}

/** Debug 传输：从本地文件分块复制，模拟进度与取消。 */
class DemoTransfer : Transfer {
    override suspend fun download(
        spec: ContentSpec,
        target: File,
        onProgress: (Long, Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val source = File(spec.assetName ?: error("demo asset missing"))
        val total = source.length()
        target.parentFile?.mkdirs()
        FileOutputStream(target, false).use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var read = 0L
                var last = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count
                    if (System.currentTimeMillis() - last >= 80) {
                        onProgress(read, total)
                        last = System.currentTimeMillis()
                    }
                    delay(20)
                }
                output.flush()
                onProgress(read, total)
            }
        }
    }
}

class DebugPlatformBindings(
    context: Context,
    shelf: ShelfRepository,
) : PlatformBindings {
    private val appContext = context.applicationContext
    override val auth: AuthRepository = DemoAuthRepository()
    override val catalog: CatalogRepository = DemoCatalogRepository(shelf)
    override val content: ContentSource = DemoContentSource(appContext)
    override val transfer: Transfer = DemoTransfer()
}
