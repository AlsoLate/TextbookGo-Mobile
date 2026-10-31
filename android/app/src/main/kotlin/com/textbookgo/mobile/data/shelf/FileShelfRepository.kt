package com.textbookgo.mobile.data.shelf

import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.repository.DeleteResult
import com.textbookgo.mobile.core.repository.ShelfRepository
import com.textbookgo.mobile.data.store.ShelfStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** 书架以 JSON 为事实源，PDF 存放在 [booksDir] 下。 */
class FileShelfRepository(
    private val store: ShelfStore,
    private val booksDir: File,
) : ShelfRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _shelf = MutableStateFlow<List<ShelfBook>>(emptyList())
    private val loaded = scope.launch { _shelf.value = store.load() }

    override fun shelf(): Flow<List<ShelfBook>> = _shelf.asStateFlow()

    override suspend fun awaitLoaded() {
        loaded.join()
    }

    override suspend fun add(book: ShelfBook) {
        loaded.join()
        _shelf.update { current -> current.filterNot { it.id == book.id } + book }
        store.save(_shelf.value)
    }

    override suspend fun delete(bookId: String): DeleteResult {
        loaded.join()
        val target = _shelf.value.firstOrNull { it.id == bookId }
        if (target != null) {
            File(booksDir, target.filename).delete()
        }
        File(booksDir, "$bookId.part").delete()
        File(booksDir, "$bookId.part.meta").delete()
        _shelf.update { current -> current.filterNot { it.id == bookId } }
        store.save(_shelf.value)
        return DeleteResult.Deleted
    }
}
