package com.textbookgo.mobile.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textbookgo.mobile.core.catalog.CatalogRules
import com.textbookgo.mobile.core.model.ShelfBook
import com.textbookgo.mobile.core.repository.AppTheme
import com.textbookgo.mobile.data.reader.ReaderProgressStore
import com.textbookgo.mobile.ui.PdfActions
import com.textbookgo.mobile.ui.theme.LocalAccents
import com.textbookgo.mobile.ui.theme.TextbookGoTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.2f
private const val PAGE_HORIZONTAL_PADDING_DP = 12f
private const val MAX_PAGE_WIDTH_DP = 760f

/** 页面位图相对屏幕宽度的渲染倍率；越大越清晰，内存占用按平方增长。 */
private const val RENDER_SCALE = 1.25f

/**
 * 应用内 PDF 阅读器：书架里的教材直接在这里翻，
 * 不再跳去别的应用。滚动、缩放、跳页、夜读都保持在应用自己的主题里。
 */
@Composable
fun PdfReaderScreen(
    book: ShelfBook,
    file: File,
    progress: ReaderProgressStore,
    theme: AppTheme,
    onClose: () -> Unit,
) {
    var load by remember(book.id) { mutableStateOf<ReaderLoad>(ReaderLoad.Loading) }
    var nightMode by rememberSaveable { mutableStateOf(progress.nightMode()) }
    // 渲染和关闭都排到同一个单线程调度器上，避免渲染中途 close 掉 PdfRenderer。
    val renderDispatcher = remember { Dispatchers.IO.limitedParallelism(1) }
    val closeScope = remember(renderDispatcher) { CoroutineScope(SupervisorJob() + renderDispatcher) }

    LaunchedEffect(book.id, file.path) {
        load = ReaderLoad.Loading
        load = withContext(Dispatchers.IO) {
            runCatching { PdfBookDocument.open(file) }.fold(
                onSuccess = { ReaderLoad.Ready(it) },
                onFailure = { ReaderLoad.Failed(it) },
            )
        }
    }

    DisposableEffect(load) {
        val document = (load as? ReaderLoad.Ready)?.document
        onDispose {
            if (document != null) closeScope.launch { document.close() }
        }
    }

    // 看教材时屏幕常亮，退出阅读器立即恢复系统行为。
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(onBack = onClose)

    // 夜读时整页连顶栏、菜单一起切成暗色，只反相纸面会显得很刺眼。
    TextbookGoTheme(theme = if (nightMode) AppTheme.NIGHT else theme) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("pdf_reader"),
        ) {
            when (val current = load) {
                ReaderLoad.Loading -> ReaderLoading(book)
                is ReaderLoad.Failed -> ReaderError(book, file, current.error, onClose)
                is ReaderLoad.Ready -> ReaderReady(
                    book = book,
                    file = file,
                    document = current.document,
                    progress = progress,
                    renderDispatcher = renderDispatcher,
                    nightMode = nightMode,
                    onNightModeChange = { enabled ->
                        nightMode = enabled
                        progress.setNightMode(enabled)
                    },
                    onClose = onClose,
                )
            }
        }
    }
}

private sealed interface ReaderLoad {
    data object Loading : ReaderLoad
    data class Ready(val document: PdfBookDocument) : ReaderLoad
    data class Failed(val error: Throwable) : ReaderLoad
}

@Composable
private fun ReaderLoading(book: ShelfBook) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = LocalAccents.current.selection,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在打开《${CatalogRules.displayTitle(book.title)}》",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderError(
    book: ShelfBook,
    file: File,
    error: Throwable,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "无法打开这本教材",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = describeError(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { PdfActions.open(context, file) }) {
            Text("用其他应用打开")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClose) {
            Text("返回书架")
        }
    }
}

private fun describeError(error: Throwable): String = when {
    error is FileNotFoundException -> "教材文件不存在，可能已被系统清理，请重新下载。"
    error is IOException && error.message.orEmpty().contains("password", ignoreCase = true) ->
        "这本 PDF 有密码保护，应用内暂时无法打开。"
    error is OutOfMemoryError -> "内存不足，无法渲染这个页面。"
    else -> "文件可能已损坏，或不是标准的 PDF。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderReady(
    book: ShelfBook,
    file: File,
    document: PdfBookDocument,
    progress: ReaderProgressStore,
    renderDispatcher: CoroutineDispatcher,
    nightMode: Boolean,
    onNightModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val accents = LocalAccents.current
    val scope = rememberCoroutineScope()
    val pageCount = document.pageCount

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    var jumpInput by rememberSaveable { mutableStateOf("") }

    val initialPage = remember(book.id) {
        progress.page(book.id).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    // 以“占屏幕面积最大的页”为当前页：滚到最后几页时，最后页往往只能露出下半屏，
    // 但用户看到的就是它；直接取 firstVisibleItemIndex 会错误地停在上一页。
    val currentPage by remember(pageCount) {
        derivedStateOf {
            val info = listState.layoutInfo
            var index = listState.firstVisibleItemIndex
            var best = -1
            info.visibleItemsInfo.forEach { item ->
                val overlap = minOf(item.offset + item.size, info.viewportEndOffset) -
                    maxOf(item.offset, info.viewportStartOffset)
                if (overlap > best) {
                    best = overlap
                    index = item.index
                }
            }
            index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }
    val zoom = remember { PageZoomState() }

    // 放大状态下返回键先复位，再按一次才回书架。
    BackHandler(enabled = zoom.active) { zoom.reset() }

    var sliderValue by remember { mutableFloatStateOf(initialPage.toFloat()) }
    var sliderDragging by remember { mutableStateOf(false) }

    fun goTo(page: Int) {
        if (pageCount <= 0) return
        val target = page.coerceIn(0, pageCount - 1)
        zoom.reset()
        scope.launch { listState.scrollToItem(target) }
    }

    LaunchedEffect(pageCount, book.id) {
        progress.savePage(book.id, initialPage, pageCount)
    }

    // 停在某页超过半秒就记进度，避免快速滑动时频繁写盘。
    LaunchedEffect(listState, book.id, pageCount) {
        snapshotFlow { currentPage }
            .distinctUntilChanged()
            .collect { page ->
                sliderValue = page.toFloat()
                delay(500)
                progress.savePage(book.id, page, pageCount)
            }
    }

    DisposableEffect(book.id) {
        onDispose { progress.savePage(book.id, currentPage, pageCount) }
    }

    LaunchedEffect(currentPage, pageCount) {
        if (!sliderDragging) sliderValue = currentPage.toFloat()
    }

    if (pageCount <= 0) {
        ReaderError(book, file, IOException("empty pdf"), onClose)
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (chromeVisible) {
                ReaderTopBar(
                    title = CatalogRules.displayTitle(book.title),
                    nightMode = nightMode,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onClose = onClose,
                    onJump = {
                        menuOpen = false
                        jumpInput = (currentPage + 1).toString()
                        showJumpDialog = true
                    },
                    onToggleNight = {
                        menuOpen = false
                        onNightModeChange(!nightMode)
                    },
                    onExternalOpen = {
                        menuOpen = false
                        PdfActions.open(context, file)
                    },
                    onShare = {
                        menuOpen = false
                        PdfActions.share(context, file)
                    },
                )
            }
        },
        bottomBar = {
            if (chromeVisible) {
                ReaderBottomBar(
                    currentPage = currentPage,
                    pageCount = pageCount,
                    sliderValue = sliderValue,
                    onSliderChange = {
                        sliderDragging = true
                        sliderValue = it
                    },
                    onSliderChangeFinished = {
                        sliderDragging = false
                        goTo(sliderValue.roundToInt())
                    },
                    onPrevious = { goTo(currentPage - 1) },
                    onNext = { goTo(currentPage + 1) },
                    onJump = {
                        jumpInput = (currentPage + 1).toString()
                        showJumpDialog = true
                    },
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val sidePaddingPx = with(density) { PAGE_HORIZONTAL_PADDING_DP.dp.toPx() }
            val pageWidthPx = minOf(
                constraints.maxWidth - sidePaddingPx * 2,
                with(density) { MAX_PAGE_WIDTH_DP.dp.toPx() },
            ).coerceAtLeast(1f)
            val contentSidePx = ((constraints.maxWidth - pageWidthPx) / 2f).coerceAtLeast(0f)
            // 渲染宽度略大于屏幕宽度：等比缩放后文字更实，放大到 1.25 倍左右仍清晰。
            val renderWidthPx = (pageWidthPx * RENDER_SCALE).toInt().coerceAtLeast(1)
            val cache = remember(document, renderWidthPx) { PdfPageCache(document, renderWidthPx) }
            // 退出阅读器立刻丢掉位图引用，别把几十 MB 的页面留在内存里等 GC。
            DisposableEffect(cache) {
                onDispose { cache.clear() }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = with(density) { contentSidePx.toDp() },
                    end = with(density) { contentSidePx.toDp() },
                    top = 14.dp,
                    bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = !zoom.active,
            ) {
                items(count = pageCount, key = { it }) { index ->
                    ReaderPage(
                        index = index,
                        pageWidthPx = pageWidthPx,
                        aspect = document.aspectRatio(index),
                        cache = cache,
                        renderDispatcher = renderDispatcher,
                        zoom = zoom,
                        nightMode = nightMode,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onTransform = { centroid, zoomFactor, pan ->
                            zoom.apply(
                                index = index,
                                centroid = centroid,
                                zoomFactor = zoomFactor,
                                pan = pan,
                                pageWidth = pageWidthPx,
                                pageHeight = pageWidthPx * document.aspectRatio(index),
                            )
                        },
                        onDoubleTap = { centroid ->
                            if (zoom.page == index && zoom.scale > 1.001f) {
                                zoom.reset()
                            } else {
                                zoom.zoomTo(
                                    index = index,
                                    centroid = centroid,
                                    scale = DOUBLE_TAP_ZOOM,
                                    pageWidth = pageWidthPx,
                                    pageHeight = pageWidthPx * document.aspectRatio(index),
                                )
                            }
                        },
                    )
                }
            }

            // 预取下一页，翻过去时少一点空白等待。
            LaunchedEffect(currentPage) {
                delay(350)
                withContext(renderDispatcher) {
                    cache.getOrRender((currentPage + 1).coerceAtMost(pageCount - 1))
                }
            }

            if (zoom.active) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { zoom.reset() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f×", zoom.scale),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accents.selection,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "复位",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("跳转到页面") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { value ->
                        jumpInput = value.filter(Char::isDigit).take(6)
                    },
                    singleLine = true,
                    label = { Text("页码 1 - $pageCount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        jumpInput.toIntOrNull()?.let { goTo(it - 1) }
                        showJumpDialog = false
                        jumpInput = ""
                    },
                ) {
                    Text("跳转")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJumpDialog = false
                        jumpInput = ""
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    nightMode: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onJump: () -> Unit,
    onToggleNight: () -> Unit,
    onExternalOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回书架",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (nightMode) "夜读模式" else "阅读中",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nightMode) {
                        LocalAccents.current.gold
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("跳转到页面") },
                        onClick = onJump,
                    )
                    DropdownMenuItem(
                        text = { Text(if (nightMode) "关闭夜读" else "开启夜读") },
                        onClick = onToggleNight,
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("用其他应用打开") },
                        onClick = onExternalOpen,
                    )
                    DropdownMenuItem(
                        text = { Text("分享") },
                        onClick = onShare,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
) {
    val accents = LocalAccents.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = currentPage > 0) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "上一页",
                    )
                }
                if (pageCount > 1) {
                    Slider(
                        value = sliderValue.coerceIn(0f, (pageCount - 1).toFloat()),
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accents.selection,
                            activeTrackColor = accents.selection,
                            inactiveTrackColor = accents.selectionContainer,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onNext, enabled = currentPage < pageCount - 1) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "下一页",
                    )
                }
            }
            TextButton(
                onClick = onJump,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = "第 ${currentPage + 1} / $pageCount 页",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReaderPage(
    index: Int,
    pageWidthPx: Float,
    aspect: Float,
    cache: PdfPageCache,
    renderDispatcher: CoroutineDispatcher,
    zoom: PageZoomState,
    nightMode: Boolean,
    onToggleChrome: () -> Unit,
    onTransform: (centroid: Offset, zoomFactor: Float, pan: Offset) -> Unit,
    onDoubleTap: (centroid: Offset) -> Unit,
) {
    val density = LocalDensity.current
    val pageHeightPx = pageWidthPx * aspect
    val widthDp = with(density) { pageWidthPx.toDp() }
    val heightDp = with(density) { pageHeightPx.toDp() }

    var bitmap by remember(index) { mutableStateOf(cache.get(index)) }
    LaunchedEffect(index) {
        if (bitmap == null) {
            bitmap = withContext(renderDispatcher) { cache.getOrRender(index) }
        }
    }

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .clipToBounds()
            .background(if (nightMode) Color.Black else Color.White)
            .testTag("reader_page_$index")
            .pointerInput(index, pageWidthPx, pageHeightPx) {
                detectReaderGesture(
                    currentScale = { if (zoom.page == index) zoom.scale else 1f },
                    onTransform = onTransform,
                )
            }
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = onDoubleTap,
                )
            },
    ) {
        val image = bitmap?.asImageBitmap()
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "第 ${index + 1} 页",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = if (zoom.page == index) zoom.scale else 1f
                        scaleX = scale
                        scaleY = scale
                        translationX = if (scale > 1f) zoom.offset.x else 0f
                        translationY = if (scale > 1f) zoom.offset.y else 0f
                    },
                contentScale = ContentScale.FillBounds,
                colorFilter = if (nightMode) NightColorFilter else null,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            // 还没渲染好时先铺一张白纸，避免滚动时闪黑块。
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFC9C3B6),
                )
            }
        }
    }
}

/** 把纸面反相：白底变深底、黑字变浅字。 */
private val NightColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** 只允许一个页面处于放大状态，复位时一起清掉位移。 */
@Stable
private class PageZoomState {
    var page by mutableIntStateOf(-1)
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    val active: Boolean
        get() = page >= 0 && scale > 1.001f

    fun reset() {
        page = -1
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomTo(index: Int, centroid: Offset, scale: Float, pageWidth: Float, pageHeight: Float) {
        val target = scale.coerceIn(1f, MAX_ZOOM)
        val center = Offset(pageWidth / 2f, pageHeight / 2f)
        page = index
        this.scale = target
        offset = clampOffset(
            offset = (centroid - center) * (1f - target),
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            scale = target,
        )
    }

    /**
     * 手势缩放：以双指中心为锚点，保证手指下面的内容不跑；
     * [pan] 是同一帧的双指位移。
     */
    fun apply(
        index: Int,
        centroid: Offset,
        zoomFactor: Float,
        pan: Offset,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        if (page != index) {
            page = index
            scale = 1f
            offset = Offset.Zero
        }
        val oldScale = scale
        val newScale = (oldScale * zoomFactor).coerceIn(1f, MAX_ZOOM)
        if (newScale <= 1.001f) {
            reset()
            return
        }
        val center = Offset(pageWidth / 2f, pageHeight / 2f)
        val ratio = newScale / oldScale
        val anchored = offset * ratio + (centroid - center) * (1f - ratio)
        scale = newScale
        offset = clampOffset(anchored + pan, pageWidth, pageHeight, newScale)
    }
}

private fun clampOffset(offset: Offset, pageWidth: Float, pageHeight: Float, scale: Float): Offset {
    val maxX = (scale - 1f) * pageWidth / 2f
    val maxY = (scale - 1f) * pageHeight / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

/**
 * 阅读器手势：
 * - 单指在未放大时完全不拦截，交给 LazyColumn 滚动；
 * - 双指捏合触发缩放；
 * - 放大后单指拖动平移页面。
 */
private suspend fun PointerInputScope.detectReaderGesture(
    currentScale: () -> Float,
    onTransform: (centroid: Offset, zoomFactor: Float, pan: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastCentroid = down.position
        var lastSpan = 0f
        var lastCount = 1
        // 捏合阈值从双指落下的位置开始累计，慢速捏合也能触发。
        var slopCentroid = down.position
        var slopSpan = 0f
        var pastSlop = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            val centroid = if (pressed.size == 1) {
                pressed[0].position
            } else {
                pressed.fold(Offset.Zero) { acc, change -> acc + change.position } /
                    pressed.size.toFloat()
            }
            val span = if (pressed.size >= 2) maxSpan(pressed) else 0f
            if (pressed.size != lastCount) {
                // 手指数量变化时中心点会跳，跳过这一帧，避免画面瞬移。
                lastCentroid = centroid
                lastSpan = span
                lastCount = pressed.size
                if (pressed.size >= 2) {
                    slopCentroid = centroid
                    slopSpan = span
                    pastSlop = false
                }
                continue
            }
            if (!pastSlop) {
                val slop = viewConfiguration.touchSlop
                val spanDelta = if (span > 0f && slopSpan > 0f) abs(span - slopSpan) else 0f
                val moved = (centroid - slopCentroid).getDistance()
                pastSlop = (span > 0f && spanDelta > slop) ||
                    (currentScale() > 1.001f && moved > slop)
            }
            if (pastSlop) {
                val zoomFactor = if (span > 0f && lastSpan > 0f) {
                    (span / lastSpan).coerceIn(0.5f, 2f)
                } else {
                    1f
                }
                val pan = centroid - lastCentroid
                if (zoomFactor != 1f || pan != Offset.Zero) {
                    onTransform(centroid, zoomFactor, pan)
                    event.changes.forEach { change ->
                        if (change.pressed) change.consume()
                    }
                }
            }
            lastCentroid = centroid
            lastSpan = span
        }
    }
}

private fun maxSpan(points: List<PointerInputChange>): Float {
    var max = 0f
    for (i in points.indices) {
        for (j in i + 1 until points.size) {
            val distance = (points[i].position - points[j].position).getDistance()
            if (distance > max) max = distance
        }
    }
    return max
}
