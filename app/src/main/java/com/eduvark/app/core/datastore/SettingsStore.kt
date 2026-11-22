package com.eduvark.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "eduvark_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val baseUrl: Flow<String?> = dataStore.data.map { it[KEY_BASE_URL] }

    suspend fun saveBaseUrl(url: String) {
        dataStore.edit { it[KEY_BASE_URL] = url }
    }

    suspend fun clearBaseUrl() {
        dataStore.edit { it.remove(KEY_BASE_URL) }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("api_base_url_override")
    }
}
