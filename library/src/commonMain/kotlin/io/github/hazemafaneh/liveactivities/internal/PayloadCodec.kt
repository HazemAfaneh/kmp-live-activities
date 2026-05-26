package io.github.hazemafaneh.liveactivities.internal

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Serializes user-supplied `@Serializable` attributes and content states to JSON.
 *
 * Resolves the serializer reflectively from the value's class, so callers do not need a reified
 * type parameter. Works on every target.
 */
@OptIn(InternalSerializationApi::class)
internal object PayloadCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Suppress("UNCHECKED_CAST")
    fun serializerOf(value: Any): KSerializer<Any> =
        value::class.serializer() as KSerializer<Any>

    fun encode(value: Any): String = json.encodeToString(serializerOf(value), value)

    /** The UTF-8 byte size of [payload], used to enforce the iOS 4 KB payload limit. */
    fun byteSize(payload: String): Int = payload.encodeToByteArray().size
}
