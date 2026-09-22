package com.cy.codex.protocol.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.Json as JsonFormat

/**
 * JSON codec for the app-server wire.
 *
 * The tree, parser and printer are kotlinx.serialization's [JsonElement]; the decoder is a real
 * RFC 8259 implementation, so surrogate pairs, `\u` escapes and number literals keep their exact
 * text (the hand-written parser turned every number into a [Double] and rounded anything past
 * 2^53). This object only pins the format and gives the wire code one import.
 *
 * Transport contract: one [parse] per incoming message and one [write] per outgoing message. The
 * Rust side mirrors it with `serde_json` typed deserialization, so a message never crosses more
 * than one encode/decode pair per direction.
 */
object Json {
    fun parse(text: String): JsonElement = JsonFormat.Default.parseToJsonElement(text)

    fun parseOrNull(text: String): JsonElement? = try {
        parse(text)
    } catch (_: SerializationException) {
        null
    }

    fun write(value: JsonElement): String = JsonFormat.Default.encodeToString(JsonElement.serializer(), value)
}

/** Decode a value the caller already knows is an object; a miss is a protocol error, not `null`. */
internal fun JsonElement.objectValue(): JsonObject = this as? JsonObject
    ?: error("Expected JSON object")

/** The string content of a primitive, or `null` for numbers, booleans, null and containers. */
internal fun JsonElement.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.text(key: String): String? = this[key]?.stringOrNull()

internal fun JsonObject.required(key: String): String = text(key) ?: error("Missing field: $key")

// Numeric and boolean readers reject string primitives, matching the strict schema types: a field
// that must be a number is not silently coerced from `"42"`.
internal fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()

internal fun JsonObject.strings(key: String): List<String> = array(key).mapNotNull { it.stringOrNull() }

/** Request/response ids arrive as either strings or numbers; render both to their id text. */
internal fun JsonElement.wireText(): String = stringOrNull() ?: Json.write(this)
