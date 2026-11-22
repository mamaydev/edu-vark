package com.eduvark.app.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduText

@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var url by remember(state.currentUrl) { mutableStateOf(state.currentUrl) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    EduScreen(title = "Pengaturan server", onBack = onBack, modifier = modifier) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
            Eyebrow("Build debug")
            Spacer(Modifier.height(10.dp))
            Text(text = "Arahkan app ke\nbackend lokal.", style = EduText.Display)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Untuk HP fisik, isi IP LAN komputer yang menjalankan backend.",
                style = EduText.Body,
            )

            Spacer(Modifier.height(22.dp))
            Column {
                FieldLabel("Base URL backend")
                EduField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "http://<IP-Mac>:3000/api/v1/",
                    keyboardType = KeyboardType.Uri,
                )
                FieldHelper("Contoh: http://192.168.1.10:3000/api/v1/ atau https://pam.sequein.my.id/api/v1/")
            }

            Spacer(Modifier.height(12.dp))
            Text(text = "Tersimpan: ${state.currentUrl}", style = EduText.BodySmall)

            val error = state.error
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                NoticeCard(text = error, tone = TintTone.Danger)
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Simpan", onClick = { viewModel.save(url) })
            Spacer(Modifier.height(12.dp))
            TextLink(text = "Reset ke default", onClick = viewModel::reset, icon = false)
        }
    }
}
