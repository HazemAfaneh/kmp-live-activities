package io.github.hazemafaneh.liveactivities.internal

import kotlinx.serialization.Serializable

/**
 * On-disk representation of a tracked Live Activity, used to restore activities after the
 * hosting process is killed and recreated.
 */
@Serializable
internal data class PersistedActivity(
    val id: String,
    val notificationId: Int,
    val attributesClass: String,
    val attributesJson: String,
    val stateClass: String,
    val stateJson: String,
    val status: String,
    val channelId: String,
    val smallIconResName: String,
    val pushType: String,
    val staleAfterMillis: Long? = null,
)
