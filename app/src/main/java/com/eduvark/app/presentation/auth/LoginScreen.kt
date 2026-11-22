package com.eduvark.app.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.eduvark.app.BuildConfig
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduText
import com.eduvark.app.ui.theme.EduVarkTheme

@Composable
fun LoginScreen(
    onLoginSuccess: (mustChangePassword: Boolean, varkRequired: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenServerSettings: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.success) {
        state.success?.let { success ->
            viewModel.consumeSuccess()
            onLoginSuccess(success.mustChangePassword, success.varkRequired)
        }
    }

    EduScreen(title = "EduVARK", brand = true, modifier = modifier) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
            Spacer(Modifier.height(4.dp))
            HeroCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "EDUVARK",
                    style = EduText.Eyebrow.copy(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "Satu kelas.\nBanyak cara\nmemahami.", style = EduText.HeroTitle)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Implementasi Basis Data\nSemester Ganjil 2026/2027",
                    style = EduText.BodySmall.copy(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                    ),
                )
            }

            Spacer(Modifier.height(28.dp))
            Eyebrow("Selamat datang")
            Spacer(Modifier.height(10.dp))
            Text(text = "Masuk untuk belajar", style = EduText.Display)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Gunakan akun yang diberikan oleh admin.",
                style = EduText.Body,
            )

            Spacer(Modifier.height(24.dp))
            Column {
                FieldLabel("Identitas login")
                EduField(
                    value = loginId,
                    onValueChange = { loginId = it },
                    placeholder = "NIM / NIP",
                    enabled = !state.isLoading,
                    keyboardType = KeyboardType.Text,
                )
            }

            Spacer(Modifier.height(16.dp))
            Column {
                FieldLabel("Kata sandi")
                EduField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
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
                text = if (state.isLoading) "Memproses…" else "Masuk",
                onClick = { viewModel.submit(loginId, password) },
                loading = state.isLoading,
                showArrow = !state.isLoading,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Akun mahasiswa, dosen, dan admin menggunakan halaman masuk yang sama.",
                style = EduText.BodySmall,
            )

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(14.dp))
                TextLink(
                    text = "Pengaturan server (debug)",
                    onClick = onOpenServerSettings,
                    icon = false,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    EduVarkTheme {
        LoginScreen(onLoginSuccess = { _, _ -> })
    }
}
