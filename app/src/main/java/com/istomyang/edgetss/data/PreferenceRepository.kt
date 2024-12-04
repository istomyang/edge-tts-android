package com.istomyang.edgetss.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferenceRepository(
    private val localDS: PreferenceLocalDataSource
) {
    companion object {
        private const val SPEAKER_ACTIVE_ID = "preference.active-speaker.id"
        private const val SPEAKER_SELECTED_ID = "preference.selected-speaker.id"

        fun create(context: Context): PreferenceRepository {
            val dataStore = context.dateStorePreference
            return PreferenceRepository(PreferenceLocalDataSource(dataStore))
        }
    }

    var activeSpeakerId: Flow<Int?> = localDS.getInt(SPEAKER_ACTIVE_ID)
    suspend fun setActiveSpeakerId(id: Int?) = localDS.setInt(SPEAKER_ACTIVE_ID, id)

    var selectedSpeakerIds: Flow<List<Int>> = localDS.getList(SPEAKER_SELECTED_ID)
    suspend fun setSelectedSpeakerIds(ids: List<Int>) = localDS.setList(SPEAKER_SELECTED_ID, ids)
    suspend fun addSelectedSpeakerId(id: Int) {
        val ids = localDS.getList(SPEAKER_SELECTED_ID).first().toMutableList()
        if (ids.contains(id)) {
            ids.remove(id)
        } else {
            ids.add(id)
        }
        localDS.setList(SPEAKER_SELECTED_ID, ids)
    }
}

val Context.dateStorePreference by preferencesDataStore("preference")

class PreferenceLocalDataSource(private val dataStore: DataStore<Preferences>) {
    fun getString(key: String): Flow<String?> {
        val k = stringPreferencesKey(key)
        return dataStore.data.map { it[k] }
    }

    suspend fun setString(key: String, value: String) {
        val k = stringPreferencesKey(key)
        dataStore.edit { settings ->
            settings[k] = value
        }
    }

    fun getInt(key: String): Flow<Int?> {
        val k = intPreferencesKey(key)
        return dataStore.data.map { it[k] }
    }

    suspend fun setInt(key: String, value: Int?) {
        val k = intPreferencesKey(key)
        dataStore.edit { settings ->
            if (value == null) {
                settings.remove(k)
            } else {
                settings[k] = value
            }
        }
    }

    fun getList(key: String): Flow<List<Int>> {
        val k = stringPreferencesKey(key)
        return dataStore.data.map { it0 -> it0[k]?.split(",")?.map { it.toInt() } ?: emptyList() }
    }

    suspend fun setList(key: String, value: List<Int>) {
        val k = stringPreferencesKey(key)
        dataStore.edit { settings ->
            settings[k] = value.joinToString(",")
        }
    }
}