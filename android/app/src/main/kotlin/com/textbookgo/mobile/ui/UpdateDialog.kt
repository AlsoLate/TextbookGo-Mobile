package com.textbookgo.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.textbookgo.mobile.BuildConfig
import com.textbookgo.mobile.core.update.UpdateManifest

/** 新版本提示：强制更新时不可取消，只留“立即更新”。 */
@Composable
fun UpdateDialog(
    manifest: UpdateManifest,
    mandatory: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!mandatory) onLater() },
        title = {
            Column {
                Text("发现新版本 ${manifest.versionName}")
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "当前版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (manifest.size > 0 || manifest.publishedAt.isNotBlank()) {
                    Text(
                        text = buildString {
                            if (manifest.size > 0) append("安装包 ${formatSize(manifest.size)}")
                            if (manifest.publishedAt.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(manifest.publishedAt.take(10))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = manifest.changelog.ifBlank { "建议在 Wi-Fi 下更新。" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (mandatory) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "当前版本已不再支持，必须更新后才能继续使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("立即更新")
            }
        },
        dismissButton = if (mandatory) {
            null
        } else {
            {
                Row {
                    TextButton(onClick = onSkipVersion) { Text("跳过此版本") }
                    TextButton(onClick = onLater) { Text("稍后") }
                }
            }
        },
    )
}
