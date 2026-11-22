package com.eduvark.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.BuildConfig
import com.eduvark.app.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ServerSettingsUiState(
    val currentUrl: String = BuildConfig.API_BASE_URL,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerSettingsUiState())
    val state: StateFlow<ServerSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val override = settingsStore.baseUrl.first()
            _state.update { it.copy(currentUrl = override ?: BuildConfig.API_BASE_URL) }
        }
    }

    fun save(input: String) {
        val parsed = input.trim().toHttpUrlOrNull()
        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https")) {
            _state.update {
                it.copy(error = "URL harus http(s), contoh http://192.168.1.10:3000/api/v1/")
            }
            return
        }
        val normalized = parsed.newBuilder()
            .encodedPath("/api/v1/")
            .build()
            .toString()
        viewModelScope.launch {
            settingsStore.saveBaseUrl(normalized)
            _state.update { it.copy(currentUrl = normalized, error = null, saved = true) }
        }
    }

    fun reset() {
        viewModelScope.launch {
            settingsStore.clearBaseUrl()
            _state.update {
                it.copy(currentUrl = BuildConfig.API_BASE_URL, error = null, saved = true)
            }
        }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false) }
    }
}
