package com.textbookgo.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.textbookgo.mobile.core.repository.AppPreferences
import com.textbookgo.mobile.core.repository.AppTheme
import com.textbookgo.mobile.core.session.SessionState
import com.textbookgo.mobile.ui.theme.LocalAccents

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    session: SessionState,
    storagePath: String,
    isDemo: Boolean,
    updateChecking: Boolean,
    onThemeChange: (AppTheme) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onRelogin: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLogout: () -> Unit,
) {
    val accents = LocalAccents.current
    var showLicense by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    if (showLicense) {
        LicenseDialog(onDismiss = { showLicense = false })
    }
    if (showContact) {
        ContactDialog(onDismiss = { showContact = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isDemo) {
            Surface(
                color = accents.goldContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "示例模式：使用内置示例教材，不连接真实平台。",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.gold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .testTag("demo_banner"),
                )
            }
        }

        SettingsCard(title = "外观", accent = accents.version) {
            Text("暗色模式", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            ThemeModeSelector(
                selected = preferences.theme,
                accent = accents.version,
                onAccent = accents.onAccent,
                onSelect = onThemeChange,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "默认跟随系统，也可以固定为浅色或深色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(title = "登录", accent = accents.grade) {
            SettingRow(
                title = "记住登录状态",
                checked = preferences.rememberSession,
                onCheckedChange = onRememberChange,
            )
            Spacer(Modifier.height(6.dp))
            SessionBadge(session)
            if (!isDemo) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "凭据仅保存在应用私有目录，卸载应用即删除；关闭“记住登录状态”后不会落盘。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (session != SessionState.Validated) {
                    Button(
                        onClick = onRelogin,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accents.selection,
                            contentColor = accents.onAccent,
                        ),
                        modifier = Modifier.testTag("settings_login_button"),
                    ) {
                        Text(if (isDemo) "进入示例模式" else "登录平台")
                    }
                }
                OutlinedButton(
                    onClick = onLogout,
                    enabled = session == SessionState.Validated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("logout_button"),
                ) {
                    Text("退出登录")
                }
            }
        }

        SettingsCard(title = "教材目录", accent = accents.subject) {
            Text(
                text = "目录缓存在本机，12 小时内启动不再重复下载；平台新增教材后可手动更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRefreshCatalog,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, accents.subject.copy(alpha = 0.55f)),
                modifier = Modifier.testTag("refresh_catalog_button"),
            ) {
                Text("立即更新目录", color = accents.subject)
            }
        }

        SettingsCard(title = "存储", accent = accents.gold) {
            Text(
                text = "教材保存在应用内：$storagePath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "卸载应用会删除未导出的教材，导出的副本不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(title = "关于", accent = accents.version) {
            Text(
                text = "TextbookGo-Mobile ${com.textbookgo.mobile.BuildConfig.VERSION_NAME} · " +
                    com.textbookgo.mobile.BuildConfig.BUILD_TYPE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "作者：Yizhixx",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = accents.version,
            )
            Text(
                text = "原作者：${Attribution.ORIGINAL_AUTHOR} · ${Attribution.LICENSE_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accents.versionContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "本项目是 ${Attribution.ORIGINAL_AUTHOR}/${Attribution.ORIGINAL_PROJECT} " +
                        "的 Android 原生移植，界面与交互按 Android 习惯重做，遵循 MIT 开源协议。",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.version,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "教材版权归原平台及出版社所有，仅供个人学习使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { showContact = true }) {
                    Text("联系作者", color = accents.version)
                }
                TextButton(onClick = { showLicense = true }) {
                    Text("MIT 许可", color = accents.version)
                }
            }
            Row {
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(Attribution.GITEE_URL) }
                    },
                ) {
                    Text("Gitee 仓库", color = accents.version)
                }
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(Attribution.GITHUB_URL) }
                    },
                ) {
                    Text("GitHub 仓库", color = accents.version)
                }
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(Attribution.PROJECT_URL) }
                    },
                ) {
                    Text("上游项目", color = accents.version)
                }
            }
            TextButton(
                onClick = onCheckUpdate,
                enabled = !updateChecking,
            ) {
                Text(
                    text = if (updateChecking) "正在检查更新…" else "检查更新",
                    color = accents.version,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: AppTheme,
    accent: Color,
    onAccent: Color,
    onSelect: (AppTheme) -> Unit,
) {
    val options = listOf(
        AppTheme.SYSTEM to "跟随系统",
        AppTheme.DAY to "浅色",
        AppTheme.NIGHT to "深色",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (mode, label) ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .testTag("theme_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Attribution.LICENSE_NAME) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = Attribution.LICENSE_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun SessionBadge(session: SessionState) {
    val accents = LocalAccents.current
    val (text, container, content) = when (session) {
        SessionState.Validated ->
            Triple("已登录", accents.successContainer, accents.success)
        SessionState.SignedOut ->
            Triple("未登录", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        SessionState.Authorizing ->
            Triple("授权中", accents.gradeContainer, accents.grade)
        SessionState.Validating ->
            Triple("校验中", accents.gradeContainer, accents.grade)
        is SessionState.Expired ->
            Triple("登录已过期", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        is SessionState.ValidationFailed ->
            Triple(session.reason, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(shape = RoundedCornerShape(999.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accents = LocalAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accents.onAccent,
                checkedTrackColor = accents.selection,
                checkedBorderColor = accents.selection,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}
