package com.eduvark.app.presentation.admin.users

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduRadioRow
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduText

@Composable
fun AdminCreateUserScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: AdminCreateUserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fullName by rememberSaveable { mutableStateOf("") }
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.STUDENT) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicateIdentity by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onCreated()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null) duplicateIdentity = isDuplicateIdentityError(error)
    }

    val roleLabels = UserRole.entries.map { it.label }
    val passwordsMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val submit: () -> Unit = {
        if (password != confirmPassword) {
            localError = "Konfirmasi kata sandi tidak sama."
        } else {
            localError = null
            viewModel.create(loginId, fullName, role, password)
        }
    }

    EduScreen(
        title = "Buat akun",
        onBack = onBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        if (duplicateIdentity) {
            ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                TintBar(text = "VALIDASI AKUN", tone = TintTone.Danger)
                Spacer(Modifier.height(18.dp))
                Eyebrow("Identitas sudah dipakai")
                Spacer(Modifier.height(10.dp))
                Text(text = "Gunakan identitas yang berbeda.", style = EduText.Display)
                Spacer(Modifier.height(10.dp))
                Text(text = "Akun baru belum disimpan.", style = EduText.Body)

                Spacer(Modifier.height(22.dp))
                FieldLabel("Nama lengkap")
                EduField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        localError = null
                    },
                    placeholder = "Nama lengkap",
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Identitas login unik")
                EduField(
                    value = loginId,
                    onValueChange = {
                        loginId = it
                        localError = null
                    },
                    placeholder = "NIM / NIP",
                    error = true,
                    enabled = !state.isSaving,
                )
                FieldHelper(
                    text = "Identitas login ini sudah digunakan oleh akun lain.",
                    isError = true,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Role")
                EduDropdown(
                    value = role.label,
                    options = roleLabels,
                    onSelect = { label ->
                        UserRole.entries.firstOrNull { it.label == label }?.let { role = it }
                    },
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(20.dp))
                NoticeCard(
                    text = "Periksa daftar akun jika pengguna sudah pernah didaftarkan. " +
                        "Hindari membuat akun ganda.",
                    tone = TintTone.Danger,
                )

                Spacer(Modifier.height(22.dp))
                PrimaryButton(
                    text = "Perbaiki & simpan",
                    onClick = submit,
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Kembali ke daftar akun",
                    onClick = onBack,
                    enabled = !state.isSaving,
                )
            }
        } else {
            ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                Eyebrow("Akun baru")
                Spacer(Modifier.height(10.dp))
                Text(text = "Siapkan akses pengguna.", style = EduText.Display)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Pastikan identitas login belum digunakan.",
                    style = EduText.Body,
                )

                Spacer(Modifier.height(22.dp))
                FieldLabel("Nama lengkap")
                EduField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        localError = null
                    },
                    placeholder = "Nama lengkap",
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Identitas login unik")
                EduField(
                    value = loginId,
                    onValueChange = {
                        loginId = it
                        localError = null
                    },
                    placeholder = "NIM / NIP",
                    enabled = !state.isSaving,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Kata sandi awal")
                EduField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    placeholder = "Minimal 6 karakter",
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Ulangi kata sandi awal")
                EduField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    placeholder = "Ulangi kata sandi",
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (passwordsMismatch) {
                    FieldHelper(
                        text = "Konfirmasi kata sandi tidak sama.",
                        isError = true,
                    )
                }

                Spacer(Modifier.height(18.dp))
                FieldLabel("Role akun")
                EduCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 16.dp,
                ) {
                    UserRole.entries.forEachIndexed { index, option ->
                        if (index > 0) Spacer(Modifier.height(2.dp))
                        EduRadioRow(
                            label = option.label,
                            selected = role == option,
                            onClick = {
                                if (!state.isSaving) role = option
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FieldHelper(text = roleHint(role))

                Spacer(Modifier.height(16.dp))
                FieldLabel("Status akun")
                EduDropdown(
                    value = AdminAccountStatus.ACTIVE.label,
                    options = listOf(AdminAccountStatus.ACTIVE.label),
                    onSelect = { },
                    enabled = false,
                )

                val errorText = localError ?: state.error
                if (errorText != null) {
                    Spacer(Modifier.height(18.dp))
                    NoticeCard(text = errorText, tone = TintTone.Danger)
                }

                Spacer(Modifier.height(22.dp))
                PrimaryButton(
                    text = "Simpan akun baru",
                    onClick = submit,
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Hanya tiga role yang dapat dipilih: Mahasiswa, Dosen, dan Admin.",
                    style = EduText.BodySmall,
                )
            }
        }
    }
}

/** Consequence of the selected role, mirrored from the backend role assignment. */
private fun roleHint(role: UserRole): String = when (role) {
    UserRole.STUDENT -> "Mahasiswa otomatis di-enroll ke kelas aktif."
    UserRole.LECTURER -> "Dosen otomatis mendapat teaching assignment kelas aktif."
    UserRole.ADMIN -> "Akun admin tidak mendapat enrollment atau teaching assignment."
}

/**
 * The backend answers a duplicate login with `LOGIN_ID_TAKEN` / "NIM/NIP sudah digunakan.";
 * the ViewModel surfaces only the message, so match on the wording it forwards.
 */
private fun isDuplicateIdentityError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val normalized = message.lowercase()
    return normalized.contains("sudah digunakan") ||
        normalized.contains("sudah terdaftar") ||
        normalized.contains("duplikat") ||
        normalized.contains("login_id_taken")
}
