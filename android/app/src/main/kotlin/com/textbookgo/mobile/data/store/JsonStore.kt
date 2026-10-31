package com.textbookgo.mobile.data.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 原子 JSON 文件存储：先写同目录临时文件，再 rename 覆盖。
 * 数据量小、结构简单；后续切换 Room 时保留接口不变。
 */
class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: T,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): T = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext default
        runCatching {
            json.decodeFromString(serializer, file.readText())
        }.getOrDefault(default)
    }

    suspend fun save(value: T) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(serializer, value))

        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }
}
