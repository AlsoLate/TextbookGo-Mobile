package com.textbookgo.mobile.testing

import com.google.common.truth.Truth.assertThat
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.DownloadState
import com.textbookgo.mobile.core.model.CatalogEntry
import com.textbookgo.mobile.core.model.CatalogFilter
import com.textbookgo.mobile.core.model.Tag
import com.textbookgo.mobile.core.repository.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakeRepositoriesTest {

    private val entries = listOf(
        entry("g7-math", "数学 七年级上册", "七年级", "数学"),
        entry("g7-chinese", "语文 七年级上册", "七年级", "语文"),
        entry("g8-math", "数学 八年级上册", "八年级", "数学"),
    )

    private fun entry(id: String, title: String, grade: String, subject: String) = CatalogEntry(
        id = id,
        title = title,
        tags = listOf(
            Tag(id = "grade-$grade", name = grade, dimensionId = CatalogRules.GRADE_DIMENSION),
            Tag(id = "subject-$subject", name = subject, dimensionId = CatalogRules.SUBJECT_DIMENSION),
        ),
    )

    @Test
    fun `fake catalog filters by grade subject and query`() = runTest {
        val repository = FakeCatalogRepository(entries)

        val books = repository.books(CatalogFilter("七年级", "数学", "上册")).first()

        assertThat(books.map { it.id }).containsExactly("g7-math")
    }

    @Test
    fun `enqueue deduplicates active books`() = runTest {
        val repository = FakeDownloadRepository()

        val first = repository.enqueue(setOf("a", "b"))
        val second = repository.enqueue(setOf("b", "c"))

        assertThat(first.accepted).isEqualTo(2)
        assertThat(second.accepted).isEqualTo(1)
        assertThat(second.deduplicated).isEqualTo(1)
        assertThat(repository.tasks().first().map { it.bookId }).containsExactly("a", "b", "c")
    }

    @Test
    fun `pause and resume change task state`() = runTest {
        val repository = FakeDownloadRepository()
        val batch = repository.enqueue(setOf("a"))
        val task = repository.tasks(batch.batchId).first().single()

        repository.pause(task.taskId)
        assertThat(repository.tasks(batch.batchId).first().single().state)
            .isEqualTo(DownloadState.PAUSED)

        repository.resume(task.taskId)
        assertThat(repository.tasks(batch.batchId).first().single().state)
            .isEqualTo(DownloadState.QUEUED)
    }

    @Test
    fun `batch filter only returns tasks from that batch`() = runTest {
        val repository = FakeDownloadRepository()
        val first = repository.enqueue(setOf("a"))
        val second = repository.enqueue(setOf("b"))

        assertThat(repository.tasks(first.batchId).first().map { it.bookId }).containsExactly("a")
        assertThat(repository.tasks(second.batchId).first().map { it.bookId }).containsExactly("b")
    }

    @Test
    fun `fake preferences persist theme and remember flag`() = runTest {
        val repository = FakePreferencesRepository()

        repository.setTheme(AppTheme.NIGHT)
        repository.setRememberSession(false)

        assertThat(repository.preferences.first().theme).isEqualTo(AppTheme.NIGHT)
        assertThat(repository.preferences.first().rememberSession).isFalse()
    }

    @Test
    fun `fake auth exposes validated session after success`() = runTest {
        val repository = FakeAuthRepository(initialState = com.textbookgo.mobile.core.session.SessionState.SignedOut)

        val result = repository.validate()

        assertThat(result).isEqualTo(com.textbookgo.mobile.core.repository.AuthResult.Success)
        assertThat(repository.session.first()).isEqualTo(com.textbookgo.mobile.core.session.SessionState.Validated)
    }
}
