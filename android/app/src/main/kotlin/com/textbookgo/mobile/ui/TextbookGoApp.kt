package com.textbookgo.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.textbookgo.mobile.ui.theme.LocalAccents

/** 一级导航，对应旧版侧栏的三个入口。 */
enum class AppDestination(val label: String) {
    DOWNLOAD("下载"),
    SHELF("书架"),
    SETTINGS("设置"),
}

private fun AppDestination.icon() = when (this) {
    AppDestination.DOWNLOAD -> Icons.Filled.KeyboardArrowDown
    AppDestination.SHELF -> Icons.Filled.List
    AppDestination.SETTINGS -> Icons.Filled.Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextbookGoApp(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val catalogStatus by vm.catalogStatus.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(AppDestination.DOWNLOAD) }
    val snackbar = remember { SnackbarHostState() }
    // 放在导航切换之外，切去书架/设置再回来时保留三行筛选的横向滚动位置。
    val gradeListState = rememberLazyListState()
    val subjectListState = rememberLazyListState()
    val versionListState = rememberLazyListState()

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun ensureNotificationPermission() {
        if (vm.isDemo || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(ui.message, ui.showLogin) {
        val message = ui.message ?: return@LaunchedEffect
        if (ui.showLogin) return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.consumeMessage()
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                UiEvent.NavigateToShelf -> destination = AppDestination.SHELF
                is UiEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }

    val accents = LocalAccents.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    androidx.compose.foundation.layout.Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(accents.selection, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "TEXTBOOKGO",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppDestination.entries.forEach { item ->
                    val selected = destination == item
                    NavigationBarItem(
                        selected = selected,
                        onClick = { destination = item },
                        icon = {
                            Icon(
                                imageVector = item.icon(),
                                contentDescription = item.label,
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accents.selection,
                            selectedTextColor = accents.selection,
                            indicatorColor = accents.selectionContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.testTag("tab_${item.name}"),
                    )
                }
            }
        },
        snackbarHost = {
            // 下载页底部有“已选/开始下载”操作条，Snackbar 上移避免遮挡按钮。
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 64.dp))
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destination) {
                AppDestination.DOWNLOAD -> DownloadScreen(
                    state = ui,
                    catalogStatus = catalogStatus,
                    isDemo = vm.isDemo,
                    gradeListState = gradeListState,
                    subjectListState = subjectListState,
                    versionListState = versionListState,
                    onLogin = vm::login,
                    onRetryCatalog = vm::retryCatalog,
                    onSelectGrade = vm::selectGrade,
                    onSelectSubject = vm::selectSubject,
                    onSelectVersion = vm::selectVersion,
                    onToggle = vm::toggleBook,
                    onClear = vm::clearSelection,
                    onDownload = {
                        ensureNotificationPermission()
                        vm.startDownload()
                    },
                )

                AppDestination.SHELF -> ShelfScreen(
                    books = shelf,
                    tasks = tasks,
                    storagePath = vm.storagePath,
                    onDelete = vm::deleteBook,
                    onPause = vm::pause,
                    onResume = vm::resume,
                    onRetry = vm::retry,
                    onCancel = vm::cancel,
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    preferences = preferences,
                    session = ui.session,
                    storagePath = vm.storagePath,
                    isDemo = vm.isDemo,
                    updateChecking = update.checking,
                    onThemeChange = vm::setTheme,
                    onRememberChange = vm::setRememberSession,
                    onRelogin = vm::login,
                    onRefreshCatalog = vm::refreshCatalogNow,
                    onCheckUpdate = { vm.checkForUpdates(force = true) },
                    onLogout = vm::logout,
                )
            }
        }
    }

    update.available?.let { manifest ->
        UpdateDialog(
            manifest = manifest,
            mandatory = update.mandatory,
            onUpdate = {
                runCatching { uriHandler.openUri(manifest.apkUrl) }
                vm.dismissUpdate()
            },
            onLater = vm::dismissUpdate,
            onSkipVersion = vm::skipUpdateVersion,
        )
    }

    if (ui.showLogin) {
        WebLoginDialog(
            busy = ui.busy,
            error = ui.message,
            onDismiss = vm::dismissLogin,
            onToken = vm::signIn,
        )
    }
}
