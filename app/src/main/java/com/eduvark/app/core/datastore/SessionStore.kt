package com.eduvark.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.VarkType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "eduvark_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.sessionDataStore

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val userId: Flow<String?> = dataStore.data.map { it[KEY_USER_ID] }
    val fullName: Flow<String?> = dataStore.data.map { it[KEY_FULL_NAME] }
    val role: Flow<String?> = dataStore.data.map { it[KEY_ROLE] }
    val activeVark: Flow<String?> = dataStore.data.map { it[KEY_ACTIVE_VARK] }
    val mustChangePassword: Flow<Boolean> = dataStore.data.map { it[KEY_MUST_CHANGE] ?: false }

    suspend fun saveSession(session: AuthSession) {
        dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = session.accessToken
            preferences[KEY_REFRESH_TOKEN] = session.refreshToken
            preferences[KEY_USER_ID] = session.user.id
            preferences[KEY_FULL_NAME] = session.user.fullName
            preferences[KEY_ROLE] = session.user.role.wireValue
            preferences[KEY_MUST_CHANGE] = session.user.mustChangePassword
            session.activeVark?.let { preferences[KEY_ACTIVE_VARK] = it.wireValue }
                ?: preferences.remove(KEY_ACTIVE_VARK)
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = accessToken
            preferences[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun updateUser(user: UserProfile) {
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = user.id
            preferences[KEY_FULL_NAME] = user.fullName
            preferences[KEY_ROLE] = user.role.wireValue
            preferences[KEY_MUST_CHANGE] = user.mustChangePassword
            user.activeVark?.let { preferences[KEY_ACTIVE_VARK] = it.wireValue }
                ?: preferences.remove(KEY_ACTIVE_VARK)
        }
    }

    suspend fun updateActiveVark(vark: VarkType) {
        dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_VARK] = vark.wireValue
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_FULL_NAME = stringPreferencesKey("full_name")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_ACTIVE_VARK = stringPreferencesKey("active_vark")
        val KEY_MUST_CHANGE = booleanPreferencesKey("must_change_password")
    }
}
