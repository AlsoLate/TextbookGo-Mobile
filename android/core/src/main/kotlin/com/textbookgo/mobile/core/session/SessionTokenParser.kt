package com.textbookgo.mobile.core.session

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 从平台网页存储中提取 `access_token`。
 *
 * 旧版 Go 客户端在 Chrome 里读取 `localStorage` 的 `ND_UC_AUTH*` 键：
 * 外层 JSON 的 `value` 字段又是一段 JSON 字符串，真正的 token 在其中。
 * 安卓端在应用内 WebView 完成同样的读取，因此解析逻辑与平台解耦，
 * 可脱离 WebView 单测。
 */
object SessionTokenParser {

    const val KEY_PREFIX: String = "ND_UC_AUTH"

    /** token 为可打印 ASCII，不含空白；平台真实值通常在 30–2000 字符之间。 */
    private val PLAUSIBLE = Regex("^[\\x21-\\x7E]{16,4096}$")

    fun isPlausible(token: String?): Boolean =
        token != null && PLAUSIBLE.matches(token)

    /** 解析 `ND_UC_AUTH*` 中的原始值；兼容直接含 access_token 的扁平结构。 */
    fun parseAccessToken(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
        directToken(root)?.let { return it }
        val nested = root.string("value") ?: return null
        val inner = runCatching { Json.parseToJsonElement(nested) }.getOrNull() as? JsonObject ?: return null
        return directToken(inner)
    }

    /** 从键值表中挑出第一个可用的 `ND_UC_AUTH*` token。 */
    fun extractFromLocalStorage(entries: Map<String, String?>): String? {
        val candidates = entries.entries
            .filter { it.key.startsWith(KEY_PREFIX) }
            .sortedBy { it.key }
        for (candidate in candidates) {
            parseAccessToken(candidate.value)?.let { return it }
        }
        return null
    }

    /**
     * 解析 WebView 注入脚本的返回值。
     *
     * 脚本返回 `JSON.stringify({key: value})`，而 `evaluateJavascript` 又会把
     * 该字符串再包一层 JSON 字符串，因此这里需要解两层。
     */
    fun extractFromStorageDump(dump: String?): String? {
        if (dump.isNullOrBlank() || dump == "null") return null
        val outer = runCatching { Json.parseToJsonElement(dump).jsonPrimitive.contentOrNull }
            .getOrNull()
            ?: return null
        val map = runCatching {
            Json.parseToJsonElement(outer).jsonObject.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.contentOrNull
            }
        }.getOrNull() ?: return null
        return extractFromLocalStorage(map)
    }

    /** 用户手工粘贴时可能带引号、空白或 `Bearer` 前缀；统一清洗。 */
    fun sanitize(token: String?): String? {
        val trimmed = token?.trim()?.trim('"', '\'', ' ', '\n', '\r', '\t') ?: return null
        val withoutBearer = trimmed
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
        return withoutBearer.takeIf { isPlausible(it) }
    }

    private fun directToken(root: JsonObject): String? =
        sanitize(root.string("access_token"))

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
