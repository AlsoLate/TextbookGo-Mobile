package com.textbookgo.mobile.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.download.DownloadTask
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.ui.components.BookCover
import com.textbookgo.mobile.ui.theme.LocalAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ShelfScreen(
    books: List<ShelfBook>,
    tasks: List<DownloadTask>,
    storagePath: String,
    onDelete: (ShelfBook) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val context = LocalContext.current
    val accents = LocalAccents.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfBook?>(null) }
    var pendingExport by remember { mutableStateOf<ShelfBook?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val book = pendingExport
        pendingExport = null
        if (uri != null && book != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            File(storagePath, book.filename).inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: error("无法写入目标文件")
                    }
                }
                Toast.makeText(
                    context,
                    if (result.isSuccess) "已导出《${book.title}》" else "导出失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val pendingTasks = tasks.pendingTasks()

    Column(Modifier.fillMaxSize()) {
        // 下载任务直接放在书架顶部：进度、失败原因和操作都可见，不再藏在顶栏弹层里。
        if (pendingTasks.isNotEmpty()) {
            DownloadTasksPanel(
                tasks = pendingTasks,
                onPause = onPause,
                onResume = onResume,
                onRetry = onRetry,
                onCancel = onCancel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        if (books.isEmpty()) {
            EmptyShelf(Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("shelf_grid"),
            ) {
                items(books, key = { it.id }) { book ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Box {
                                BookCover(
                                    id = book.id,
                                    coverUrl = book.coverUrl,
                                    modifier = Modifier.fillMaxWidth(),
                                    pdfFile = File(storagePath, book.filename),
                                )
                                if (book.edition2022 || CatalogRules.is2022Edition(book.title)) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = accents.goldContainer,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp),
                                    ) {
                                        Text(
                                            text = "2022年",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accents.gold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                ) {
                                    IconButton(
                                        onClick = { menuFor = book.id },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "更多",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = menuFor == book.id,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("打开") },
                                        onClick = {
                                            menuFor = null
                                            PdfActions.open(context, File(storagePath, book.filename))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("分享") },
                                        onClick = {
                                            menuFor = null
                                            PdfActions.share(context, File(storagePath, book.filename))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("导出") },
                                        onClick = {
                                            menuFor = null
                                            pendingExport = book
                                            exportLauncher.launch(book.filename)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuFor = null
                                            pendingDelete = book
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = CatalogRules.displayTitle(book.title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("shelf_title_${book.id}"),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = accents.successContainer,
                                ) {
                                    Text(
                                        text = formatSize(book.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.success,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = formatDate(book.downloadedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除教材") },
            text = { Text("将删除《${book.title}》及其应用内文件，已导出的副本不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(book)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun EmptyShelf(modifier: Modifier = Modifier) {
    val accents = LocalAccents.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("shelf_empty"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(accents.gradeContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("架", style = MaterialTheme.typography.headlineSmall, color = accents.grade)
        }
        Text("书架还是空的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "下载完成的教材会出现在这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
