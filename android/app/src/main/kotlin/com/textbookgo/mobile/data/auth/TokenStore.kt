package com.textbookgo.mobile.data.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * 授权凭据的本地存储。
 *
 * 只放在应用私有目录（不进入备份，Manifest 已关闭 allowBackup），写入采用
 * “临时文件 + 重命名”，并尽量收紧文件权限。若设备已 root，本存储不构成
 * 强保护；后续可替换为 Android Keystore 加密实现而不影响上层契约。
 */
class TokenStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun read(): String? {
        if (!file.exists()) return null
        val document = runCatching {
            json.decodeFromString<TokenDocument>(file.readText())
        }.getOrNull() ?: return null
        return document.token.takeIf { it.isNotBlank() }
    }

    fun write(token: String) {
        val parent = file.parentFile ?: return
        parent.mkdirs()
        val temporary = File(parent, "${file.name}.tmp")
        temporary.writeText(
            json.encodeToString(TokenDocument(token = token, savedAt = Instant.now().toString())),
        )
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    fun clear() {
        file.delete()
        file.parentFile?.let { File(it, "${file.name}.tmp").delete() }
    }
}

@Serializable
private data class TokenDocument(
    val token: String = "",
    val savedAt: String = "",
)
