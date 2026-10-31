package com.textbookgo.mobile.core.session

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class SessionTokenParserTest {

    private val token = "eyJhbGciOiJIUzI1NiJ9.${"x".repeat(40)}.signature"

    @Test
    fun `parses nested value payload used by the platform`() {
        val inner = Json.encodeToString(JsonPrimitive("""{"access_token":"$token"}"""))
        val outer = """{"value":$inner}"""
        assertThat(SessionTokenParser.parseAccessToken(outer)).isEqualTo(token)
    }

    @Test
    fun `parses flat access_token payload`() {
        assertThat(SessionTokenParser.parseAccessToken("""{"access_token":"$token","expires":1}"""))
            .isEqualTo(token)
    }

    @Test
    fun `rejects unknown or malformed storage values`() {
        assertThat(SessionTokenParser.parseAccessToken(null)).isNull()
        assertThat(SessionTokenParser.parseAccessToken("")).isNull()
        assertThat(SessionTokenParser.parseAccessToken("not-json")).isNull()
        assertThat(SessionTokenParser.parseAccessToken("""{"value":"not-json"}""")).isNull()
        assertThat(SessionTokenParser.parseAccessToken("""{"access_token":""}""")).isNull()
    }

    @Test
    fun `extracts token from a storage dump with double json encoding`() {
        val inner = Json.encodeToString(JsonPrimitive("""{"access_token":"$token"}"""))
        val storage = buildJsonObject {
            put("unrelated", JsonPrimitive("1"))
            put("ND_UC_AUTH_42", JsonPrimitive("""{"value":$inner}"""))
        }
        val dump = Json.encodeToString(JsonPrimitive(storage.toString()))

        assertThat(SessionTokenParser.extractFromStorageDump(dump)).isEqualTo(token)
    }

    @Test
    fun `storage dump without auth keys returns null`() {
        val storage = buildJsonObject { put("other", JsonPrimitive("x")) }
        val dump = Json.encodeToString(JsonPrimitive(storage.toString()))
        assertThat(SessionTokenParser.extractFromStorageDump(dump)).isNull()
        assertThat(SessionTokenParser.extractFromStorageDump("{}")).isNull()
        assertThat(SessionTokenParser.extractFromStorageDump("null")).isNull()
    }

    @Test
    fun `sanitize trims quotes and bearer prefix`() {
        assertThat(SessionTokenParser.sanitize("\"$token\"")).isEqualTo(token)
        assertThat(SessionTokenParser.sanitize("Bearer $token")).isEqualTo(token)
        assertThat(SessionTokenParser.sanitize("short")).isNull()
    }

    @Test
    fun `plausible tokens are printable ascii without whitespace`() {
        assertThat(SessionTokenParser.isPlausible(token)).isTrue()
        assertThat(SessionTokenParser.isPlausible("has space $token")).isFalse()
        assertThat(SessionTokenParser.isPlausible(null)).isFalse()
    }
}
