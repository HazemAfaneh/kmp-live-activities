package io.github.hazemafaneh.liveactivities.internal

import io.github.hazemafaneh.liveactivities.LiveActivityAttributes
import io.github.hazemafaneh.liveactivities.LiveActivityContentState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Serializes user-supplied `@Serializable` attributes and content states to and from JSON.
 *
 * Decoding resolves the concrete type by its class name via JVM reflection, so this codec is
 * Android-only. Types that are not annotated with `@Serializable` cannot be persisted.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
internal object ActivityCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Suppress("UNCHECKED_CAST")
    fun encode(value: Any): String {
        val serializer = value::class.serializer() as KSerializer<Any>
        return json.encodeToString(serializer, value)
    }

    fun decodeAttributes(className: String, payload: String): LiveActivityAttributes =
        decode(className, payload) as LiveActivityAttributes

    fun decodeState(className: String, payload: String): LiveActivityContentState =
        decode(className, payload) as LiveActivityContentState

    private fun decode(className: String, payload: String): Any {
        val serializer = Class.forName(className).kotlin.serializer()
        return json.decodeFromString(serializer, payload)
    }
}
