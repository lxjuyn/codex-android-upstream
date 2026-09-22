package com.cy.codex.protocol.protocol

import com.cy.codex.protocol.json
import com.cy.codex.protocol.obj
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JSON layer is the one piece of the protocol without a generated schema to be correct
 * against. These tests pin the transport behaviour the wire code relies on.
 */
class JsonTest {

    @Test
    fun parsesScalars() {
        assertEquals(JsonNull, Json.parse("null"))
        assertEquals(JsonPrimitive(true), Json.parse("true"))
        assertEquals(JsonPrimitive(false), Json.parse("false"))
        assertEquals(JsonPrimitive(42), Json.parse("42"))
        assertEquals(JsonPrimitive(-1.5), Json.parse("-1.5"))
        assertEquals(JsonPrimitive("hi"), Json.parse("\"hi\""))
    }

    @Test
    fun parsesNestedStructures() {
        val value = Json.parse(
            """{"method":"thread/read","params":{"threadId":"t1","items":[{"id":"i1"},null]}}""",
        )
        val obj = value as JsonObject
        assertEquals("thread/read", (obj["method"] as JsonPrimitive).content)
        val params = obj["params"] as JsonObject
        assertEquals("t1", (params["threadId"] as JsonPrimitive).content)
        val items = params["items"] as JsonArray
        assertEquals(2, items.size)
        assertEquals(JsonNull, items[1])
    }

    @Test
    fun parsesStringEscapesIncludingSurrogatePairs() {
        val value = Json.parse(
            """{"path":"a\/b\n\t\"q\"","unicode":"\u0041","emoji":"\uD83D\uDE00"}""",
        ) as JsonObject
        assertEquals("a/b\n\t\"q\"", (value["path"] as JsonPrimitive).content)
        assertEquals("A", (value["unicode"] as JsonPrimitive).content)
        assertEquals("\uD83D\uDE00", (value["emoji"] as JsonPrimitive).content)
    }

    @Test
    fun roundTripsThroughWrite() {
        val source = """{"a":[1,2,{"b":"x\ny"}],"c":true,"d":null}"""
        val parsed = Json.parse(source)
        assertEquals(source, Json.write(parsed))
    }

    @Test
    fun reportsMalformedInput() {
        assertFailsWith<SerializationException> { Json.parse("{") }
        assertFailsWith<SerializationException> { Json.parse("{\"a\":}") }
        assertFailsWith<SerializationException> { Json.parse("[1,2") }
        assertFailsWith<SerializationException> { Json.parse("\"unterminated") }
        assertFailsWith<SerializationException> { Json.parse("{} trailing") }
    }

    @Test
    fun unquotedTokensStayInert() {
        // kotlinx keeps an unquoted non-string token verbatim without validating it as a number or
        // literal. The typed readers treat it as absent, so a malformed token can never become a
        // string or a number in the UI.
        val token = Json.parse("tru") as JsonPrimitive
        assertNull(token.stringOrNull())
        assertNull(token.takeIf { !it.isString }?.longOrNull)
    }

    @Test
    fun parseOrNullSwallowsErrors() {
        assertNull(Json.parseOrNull("{"))
        assertEquals(JsonPrimitive(1), Json.parseOrNull("1"))
    }

    @Test
    fun numbersKeepTheirExactLiteral() {
        // The handwritten parser held every number as Double, so this id came back 9007199254740992
        // and writing a config value back corrupted any integer past 2^53.
        assertEquals("9007199254740993", Json.write(Json.parse("9007199254740993")))
        assertEquals(100000.0, (Json.parse("1e5") as JsonPrimitive).doubleOrNull)
        assertEquals("1", Json.write(json(1.0)))
        assertEquals("1.5", Json.write(json(1.5)))
    }

    @Test
    fun readersRejectMismatchedPrimitiveTypes() {
        val o = obj("number" to "42", "text" to 7, "flag" to "true")
        assertNull(o.long("number"))
        assertNull(o.text("text"))
        assertNull(o.bool("flag"))
    }

    @Test
    fun requestIdAcceptsNumbersAndStrings() {
        assertEquals("42", RequestId(42L).value)
        assertEquals("abc", RequestId("abc").value)
        assertEquals(42L, RequestId("42").asLongOrNull)
        assertNull(RequestId("abc").asLongOrNull)
        assertTrue(RequestId(7L) == RequestId("7"))
    }

    @Test
    fun jsonRpcErrorCarriesStandardCodes() {
        assertEquals(-32700, JsonRpcError.ParseError)
        assertEquals(-32603, JsonRpcError.InternalError)
        val response = JsonRpcResponse(id = RequestId(1L), error = JsonRpcError(-32601, "no such method"))
        assertTrue(response.isError)
    }
}
