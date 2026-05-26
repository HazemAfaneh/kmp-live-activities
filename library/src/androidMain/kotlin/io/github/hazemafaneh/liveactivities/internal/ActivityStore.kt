package io.github.hazemafaneh.liveactivities.internal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer

private val Context.liveActivityStore by preferencesDataStore(name = "kmp_live_activities")
private val recordsKey = stringPreferencesKey("records")
private val recordsSerializer = ListSerializer(PersistedActivity.serializer())

/** A DataStore-backed cache of tracked activities, so they survive process death. */
internal class ActivityStore(private val context: Context) {

    suspend fun save(records: List<PersistedActivity>) {
        val encoded = ActivityCodec.json.encodeToString(recordsSerializer, records)
        context.liveActivityStore.edit { prefs -> prefs[recordsKey] = encoded }
    }

    suspend fun load(): List<PersistedActivity> {
        val raw = context.liveActivityStore.data.first()[recordsKey] ?: return emptyList()
        return runCatching { ActivityCodec.json.decodeFromString(recordsSerializer, raw) }
            .getOrDefault(emptyList())
    }
}
