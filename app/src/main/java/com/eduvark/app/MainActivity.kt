package com.eduvark.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.eduvark.app.core.datastore.SettingsStore
import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.presentation.navigation.EduVarkApp
import com.eduvark.app.ui.theme.EduVarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var api: EduVarkApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDebugServerOverride()
        requestLocalNetworkPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            EduVarkTheme {
                EduVarkApp()
            }
        }
    }

    private fun applyDebugServerOverride() {
        if (!BuildConfig.DEBUG) return
        val override = intent?.getStringExtra("api_base_url")?.trim()
        lifecycleScope.launch {
            if (!override.isNullOrBlank()) {
                settingsStore.saveBaseUrl(override)
            }
            runCatching { api.health() }
                .onSuccess { response ->
                    Log.i("EduVARK", "Server probe OK: " + response.data?.status)
                }
                .onFailure { error ->
                    Log.e(
                        "EduVARK",
                        "Server probe failed: " + error.javaClass.simpleName + ": " + error.message,
                    )
                }
        }
    }

    private fun requestLocalNetworkPermissionIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 36) return
        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), LOCAL_NETWORK_REQUEST)
        }
    }

    private companion object {
        const val LOCAL_NETWORK_REQUEST = 1001
    }
}
