package com.textbookgo.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textbookgo.mobile.core.model.Book
import com.textbookgo.mobile.core.repository.CatalogStatus
import com.textbookgo.mobile.core.session.SessionState
import com.textbookgo.mobile.ui.components.AccentFilterChip
import com.textbookgo.mobile.ui.components.BookCover
import com.textbookgo.mobile.ui.components.FilterLabel
import com.textbookgo.mobile.ui.theme.LocalAccents

@Composable
fun DownloadScreen(
    state: DownloadUiState,
    catalogStatus: CatalogStatus,
    isDemo: Boolean,
    gradeListState: LazyListState,
    subjectListState: LazyListState,
    versionListState: LazyListState,
    onLogin: () -> Unit,
    onRetryCatalog: () -> Unit,
    onSelectGrade: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectVersion: (String) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
) {
    val accents = LocalAccents.current
    val needsLogin = state.session != SessionState.Validated
    val hasCatalog = catalogStatus is CatalogStatus.Loaded || state.books.isNotEmpty()

    if (needsLogin && (isDemo || !hasCatalog)) {
        LoginPanel(state, onLogin)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val failure = catalogStatus as? CatalogStatus.Failed
        if (failure != null) {
            CatalogBanner(failure.message, onRetryCatalog)
        }

        if (state.grades.isNotEmpty()) {
            FilterRow(
                label = "年级",
                accent = accents.grade,
                onAccent = accents.onAccent,
                listState = gradeListState,
            ) {
                items(state.grades) { grade ->
                    AccentFilterChip(
                        label = grade,
                        selected = grade == state.grade,
                        accent = accents.grade,
                        onAccent = accents.onAccent,
                        onClick = { onSelectGrade(grade) },
                    )
                }
            }
        }
        if (state.subjects.isNotEmpty()) {
            FilterRow(
                label = "学科",
                accent = accents.subject,
                onAccent = accents.onAccent,
                listState = subjectListState,
            ) {
                items(state.subjects) { subject ->
                    AccentFilterChip(
                        label = subject,
                        selected = subject == state.subject,
                        accent = accents.subject,
                        onAccent = accents.onAccent,
                        onClick = { onSelectSubject(subject) },
                    )
                }
            }
        }
        if (state.versions.isNotEmpty()) {
            FilterRow(
                label = "版本",
                accent = accents.version,
                onAccent = accents.onAccent,
                listState = versionListState,
                topPadding = 4.dp,
            ) {
                item(key = "__all_versions__") {
                    AccentFilterChip(
                        label = "全部",
                        selected = state.version.isBlank(),
                        accent = accents.version,
                        onAccent = accents.onAccent,
                        onClick = { onSelectVersion("") },
                        modifier = Modifier.testTag("version_all"),
                    )
                }
                items(state.versions, key = { "v_$it" }) { version ->
                    AccentFilterChip(
                        label = version,
                        selected = version == state.version,
                        accent = accents.version,
                        onAccent = accents.onAccent,
                        onClick = { onSelectVersion(version) },
                    )
                }
            }
        }

        if (state.busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = accents.version,
                trackColor = accents.versionContainer,
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                catalogStatus is CatalogStatus.Loading && state.books.isEmpty() -> {
                    CenterPanel {
                        CircularProgressIndicator(color = accents.grade)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "正在加载教材目录…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                catalogStatus is CatalogStatus.Failed && state.books.isEmpty() -> {
                    CenterPanel {
                        Text(
                            text = catalogStatus.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onRetryCatalog,
                            border = BorderStroke(1.dp, accents.grade),
                            modifier = Modifier.testTag("retry_catalog"),
                        ) {
                            Text("重试", color = accents.grade)
                        }
                    }
                }

                state.books.isEmpty() -> {
                    CenterPanel {
                        Text(
                            text = "该分类暂无教材",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                selected = book.id in state.selected,
                                onClick = { onToggle(book.id) },
                            )
                        }
                    }
                }
            }

            if (needsLogin) {
                LoginMask(onLogin)
            }
        }

        BottomActionBar(
            count = state.selected.size,
            enabled = !state.busy && !needsLogin && state.books.isNotEmpty(),
            onClear = onClear,
            onDownload = onDownload,
        )
    }
}

@Composable
private fun FilterRow(
    label: String,
    accent: Color,
    onAccent: Color,
    listState: LazyListState,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp,
    content: LazyListScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterLabel(
            text = label,
            color = accent,
            modifier = Modifier.padding(start = 12.dp),
        )
        // 关闭 Material 的 48dp 最小触控区，筛选项保持紧凑的一行，给书卡留出高度。
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LoginMask(onLogin: () -> Unit) {
    val accents = LocalAccents.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, accents.selection.copy(alpha = 0.35f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(accents.selectionContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = accents.selection,
                    )
                }
                Text("登录后可选择教材", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "未登录时只能浏览教材目录，无法加入下载队列。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onLogin,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accents.selection,
                        contentColor = accents.onAccent,
                    ),
                    modifier = Modifier.testTag("mask_login_button"),
                ) {
                    Text("立即登录")
                }
            }
        }
    }
}

@Composable
private fun CenterPanel(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun CatalogBanner(message: String, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text("重试", color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun LoginPanel(state: DownloadUiState, onLogin: () -> Unit) {
    val accents = LocalAccents.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        accents.gradeContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BookStackMark()
            Spacer(Modifier.height(28.dp))
            Text(
                "TextbookGo",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "国家智慧教育平台教材下载",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))

            val failure = state.session as? SessionState.ValidationFailed
            if (failure != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = failure.reason,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Button(
                onClick = onLogin,
                enabled = !state.busy,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.selection,
                    contentColor = accents.onAccent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("login_button"),
            ) {
                Text(
                    if (state.busy) "正在打开登录页…" else "登录并加载教材",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "账号密码只在平台页面输入，应用不保存。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 三本叠放的书，作为登录页的品牌图形，复用封面拼贴色。 */
@Composable
private fun BookStackMark() {
    Row(verticalAlignment = Alignment.Bottom) {
        StackBook(color = Color(0xFFA7B0A0), rotate = -10f, height = 76.dp)
        Spacer(Modifier.width(4.dp))
        StackBook(color = Color(0xFFC09A86), rotate = -3f, height = 88.dp)
        Spacer(Modifier.width(4.dp))
        StackBook(color = Color(0xFFD4C9B0), rotate = 6f, height = 80.dp)
    }
}

@Composable
private fun StackBook(color: Color, rotate: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .width(52.dp)
            .height(height)
            .rotate(rotate)
            .background(color, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0xFF2A2926).copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun BookCard(book: Book, selected: Boolean, onClick: () -> Unit) {
    val accents = LocalAccents.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("book_${book.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                accents.selectionContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                accents.selection
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 2.dp,
            pressedElevation = 4.dp,
            focusedElevation = 4.dp,
        ),
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                BookCover(book.id, book.coverUrl, Modifier.fillMaxWidth())
                if (book.edition2022) {
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
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = accents.selection,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Text(
                            "✓",
                            color = accents.onAccent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = when {
                        selected -> "已选"
                        book.downloaded -> "已下载"
                        else -> "可选"
                    },
                    container = when {
                        selected -> accents.selection
                        book.downloaded -> accents.success
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    content = when {
                        selected -> accents.onAccent
                        book.downloaded -> accents.onAccent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (book.version.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accents.versionContainer,
                    ) {
                        Text(
                            text = book.version,
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.version,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun BottomActionBar(
    count: Int,
    enabled: Boolean,
    onClear: () -> Unit,
    onDownload: () -> Unit,
) {
    val accents = LocalAccents.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    append("已选 ")
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = if (count > 0) accents.selection else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                    ) {
                        append("$count")
                    }
                    append(" 本")
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear, enabled = count > 0 && enabled) {
                Text("清空")
            }
            Button(
                onClick = onDownload,
                enabled = count > 0 && enabled,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accents.selection,
                    contentColor = accents.onAccent,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("download_button"),
            ) {
                Text(
                    if (count > 0) "开始下载（$count）" else "开始下载",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
