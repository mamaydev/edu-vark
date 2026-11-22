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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
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
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduText
import com.eduvark.app.ui.theme.EduVarkTheme

@Composable
fun ChangePasswordScreen(
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onSuccess()
        }
    }

    EduScreen(title = "Ganti password", onBack = onBack, modifier = modifier) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
            Eyebrow("Keamanan akun")
            Spacer(Modifier.height(10.dp))
            Text(text = "Ganti password\nsebelum lanjut.", style = EduText.Display)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Password sementara wajib diganti sebelum kamu memulai sesi belajar.",
                style = EduText.Body,
            )

            Spacer(Modifier.height(24.dp))
            Column {
                FieldLabel("Password saat ini")
                EduField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    placeholder = "Password saat ini",
                    enabled = !state.isLoading,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Column {
                FieldLabel("Password baru")
                EduField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "Password baru",
                    enabled = !state.isLoading,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                FieldHelper("Minimal 6 karakter.")
            }

            Spacer(Modifier.height(16.dp))
            Column {
                FieldLabel("Konfirmasi password baru")
                EduField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    placeholder = "Ulangi password baru",
                    enabled = !state.isLoading,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            val error = state.error
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                NoticeCard(text = error, tone = TintTone.Danger)
            }

            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = "Simpan password",
                onClick = { viewModel.submit(currentPassword, newPassword, confirmation) },
                loading = state.isLoading,
                enabled = !state.isLoading,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Gunakan kombinasi yang mudah kamu ingat namun sulit ditebak orang lain.",
                style = EduText.BodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChangePasswordScreenPreview() {
    EduVarkTheme {
        ChangePasswordScreen(onSuccess = {})
    }
}
