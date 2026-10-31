package com.textbookgo.mobile.data.store

import com.textbookgo.mobile.core.model.ShelfAvailability
import com.textbookgo.mobile.core.model.ShelfBook
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ShelfBookSnapshot(
    val id: String,
    val title: String,
    val filename: String,
    val size: Long,
    val downloadedAt: String,
    val coverUrl: String? = null,
    val availability: String = ShelfAvailability.AVAILABLE.name,
)

@Serializable
data class ShelfSnapshot(val books: List<ShelfBookSnapshot> = emptyList())

class ShelfStore(file: File) {

    private val store = JsonStore(file, ShelfSnapshot.serializer(), ShelfSnapshot())

    suspend fun load(): List<ShelfBook> = store.load().books.map { snapshot ->
        ShelfBook(
            id = snapshot.id,
            title = snapshot.title,
            filename = snapshot.filename,
            size = snapshot.size,
            downloadedAt = snapshot.downloadedAt,
            coverUrl = snapshot.coverUrl,
            availability = runCatching { ShelfAvailability.valueOf(snapshot.availability) }
                .getOrDefault(ShelfAvailability.AVAILABLE),
        )
    }

    suspend fun save(books: List<ShelfBook>) {
        store.save(
            ShelfSnapshot(
                books = books.map { book ->
                    ShelfBookSnapshot(
                        id = book.id,
                        title = book.title,
                        filename = book.filename,
                        size = book.size,
                        downloadedAt = book.downloadedAt,
                        coverUrl = book.coverUrl,
                        availability = book.availability.name,
                    )
                },
            ),
        )
    }
}
