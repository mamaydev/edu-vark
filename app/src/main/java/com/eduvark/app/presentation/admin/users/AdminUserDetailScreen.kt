package com.eduvark.app.presentation.admin.users

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
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.DangerButton
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

@Composable
fun AdminUserDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: AdminUserDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val user = state.user
    var fullName by rememberSaveable { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.STUDENT) }
    var confirmingDeactivate by rememberSaveable { mutableStateOf(false) }
    var enrollmentConfirmOpen by rememberSaveable { mutableStateOf(false) }
    var resetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(user?.id) {
        user?.let {
            fullName = it.fullName
            role = it.role
        }
    }

    val inactive = user?.status == AdminAccountStatus.INACTIVE
    val confirming = confirmingDeactivate && user != null && !inactive
    val roleLabels = UserRole.entries.map { it.label }
    val statusLabels = AdminAccountStatus.entries.map { it.label }

    EduScreen(
        title = when {
            inactive -> "Status akun"
            confirming -> "Nonaktifkan akun"
            else -> "Detail akun"
        },
        onBack = {
            if (confirming) {
                confirmingDeactivate = false
            } else {
                onBack()
            }
        },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading && user == null -> {
                LoadingBlock(Modifier.padding(padding))
            }

            user == null -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                ) {
                    ErrorBlock(
                        message = state.error ?: "Akun tidak ditemukan.",
                        onRetry = { viewModel.load() },
                    )
                }
            }

            inactive -> {
                InactiveAccountState(
                    onBack = onBack,
                    modifier = Modifier.padding(padding),
                )
            }

            confirming -> {
                ConfirmDeactivateState(
                    user = user,
                    isSaving = state.isSaving,
                    error = state.actionError,
                    onCancel = { confirmingDeactivate = false },
                    onConfirm = viewModel::deactivate,
                    modifier = Modifier.padding(padding),
                )
            }

            else -> {
                ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                    TintBar(
                        text = "${user.role.label} • ${user.status.label}".uppercase(),
                        tone = if (user.status == AdminAccountStatus.ACTIVE) {
                            TintTone.Teal
                        } else {
                            TintTone.Danger
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(text = user.fullName, style = EduText.Display)
                    Spacer(Modifier.height(10.dp))
                    Text(text = "Identitas dan akses akun", style = EduText.Body)

                    Spacer(Modifier.height(22.dp))
                    FieldLabel("Nama lengkap")
                    EduField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = "Nama lengkap",
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(16.dp))
                    FieldLabel("Identitas login unik")
                    EduField(
                        value = user.loginId,
                        onValueChange = { },
                        enabled = false,
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

                    Spacer(Modifier.height(16.dp))
                    FieldLabel("Status")
                    EduDropdown(
                        value = user.status.label,
                        options = statusLabels,
                        onSelect = { label ->
                            if (label == AdminAccountStatus.INACTIVE.label) {
                                confirmingDeactivate = true
                            }
                        },
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(18.dp))
                    NoticeCard(
                        text = "Perubahan akun tidak mengubah materi, feedback, jawaban, " +
                            "atau nilai akademik.",
                        tone = TintTone.Primary,
                    )

                    val loadError = state.error
                    if (loadError != null) {
                        Spacer(Modifier.height(16.dp))
                        NoticeCard(text = loadError, tone = TintTone.Danger)
                    }
                    val actionMessage = state.actionMessage
                    if (actionMessage != null) {
                        Spacer(Modifier.height(16.dp))
                        NoticeCard(text = actionMessage, tone = TintTone.Teal)
                    }
                    val actionError = state.actionError
                    if (actionError != null) {
                        Spacer(Modifier.height(16.dp))
                        NoticeCard(text = actionError, tone = TintTone.Danger)
                    }

                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        text = "Simpan perubahan",
                        onClick = { viewModel.updateProfile(fullName, role) },
                        enabled = !state.isSaving,
                        loading = state.isSaving,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Nonaktifkan akun",
                        onClick = { confirmingDeactivate = true },
                        enabled = !state.isSaving,
                    )

                    Spacer(Modifier.height(18.dp))
                    if (resetOpen) {
                        ResetPasswordPanel(
                            enabled = !state.isSaving,
                            onSave = { temporaryPassword ->
                                resetOpen = false
                                viewModel.resetPassword(temporaryPassword)
                            },
                            onCancel = { resetOpen = false },
                        )
                    } else {
                        TextLink(
                            text = "Reset kata sandi sementara",
                            onClick = {
                                resetOpen = true
                                enrollmentConfirmOpen = false
                            },
                            icon = false,
                        )
                    }

                    if (user.role == UserRole.STUDENT && user.enrollmentId != null) {
                        val enrollmentActive = user.enrollmentStatus == EnrollmentStatus.ACTIVE
                        Spacer(Modifier.height(10.dp))
                        if (enrollmentConfirmOpen) {
                            TonePanel(
                                title = if (enrollmentActive) {
                                    "Keluarkan dari kelas?"
                                } else {
                                    "Aktifkan akses kelas?"
                                },
                                body = if (enrollmentActive) {
                                    "Akses kelas dicabut; histori pengerjaan dan nilai tetap tersimpan."
                                } else {
                                    "Mahasiswa kembali mendapat akses kelas dan materinya."
                                },
                                tone = if (enrollmentActive) TintTone.Amber else TintTone.Primary,
                            )
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton(
                                text = "Batal",
                                onClick = { enrollmentConfirmOpen = false },
                                enabled = !state.isSaving,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (enrollmentActive) {
                                DangerButton(
                                    text = "Keluarkan dari kelas",
                                    onClick = {
                                        enrollmentConfirmOpen = false
                                        viewModel.setEnrollment(false)
                                    },
                                    enabled = !state.isSaving,
                                )
                            } else {
                                PrimaryButton(
                                    text = "Aktifkan akses kelas",
                                    onClick = {
                                        enrollmentConfirmOpen = false
                                        viewModel.setEnrollment(true)
                                    },
                                    enabled = !state.isSaving,
                                )
                            }
                        } else {
                            TextLink(
                                text = if (enrollmentActive) {
                                    "Keluarkan dari kelas"
                                } else {
                                    "Aktifkan kembali akses kelas"
                                },
                                onClick = {
                                    enrollmentConfirmOpen = true
                                    resetOpen = false
                                },
                                color = if (enrollmentActive) EduColors.Danger else EduColors.Teal,
                                icon = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeactivateState(
    user: AdminUser,
    isSaving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Konfirmasi admin")
        Spacer(Modifier.height(10.dp))
        Text(text = "Nonaktifkan akun ini?", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Akun nonaktif tidak dapat memulai login baru.",
            style = EduText.Body,
        )

        Spacer(Modifier.height(22.dp))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduColors.DangerTint,
            padding = 20.dp,
        ) {
            Text(
                text = user.fullName,
                style = EduText.Headline.copy(color = EduColors.Danger),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = user.loginId,
                style = EduText.BodySmall.copy(color = EduColors.Muted),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${user.role.label} • Saat ini ${user.status.label.lowercase()}",
                style = EduText.BodySmall.copy(color = EduColors.Danger),
            )
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Ini bukan penghapusan permanen akun. Kebijakan retensi dan " +
                "nasib sesi yang sedang aktif masih perlu dikonfirmasi.",
            tone = TintTone.Amber,
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            NoticeCard(text = error, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "Batal",
            onClick = onCancel,
            enabled = !isSaving,
        )
        Spacer(Modifier.height(12.dp))
        DangerButton(
            text = if (isSaving) "Menonaktifkan…" else "Ya, nonaktifkan akun",
            onClick = onConfirm,
            enabled = !isSaving,
        )
    }
}

@Composable
private fun InactiveAccountState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "LOGIN DITOLAK", tone = TintTone.Danger)
        Spacer(Modifier.height(18.dp))
        Eyebrow("Akun nonaktif")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Akses akunmu sedang dinonaktifkan.",
            style = EduText.Display,
        )
        Spacer(Modifier.height(10.dp))
        Text(text = "Sesi belajar belum dapat dimulai.", style = EduText.Body)

        Spacer(Modifier.height(22.dp))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduColors.DangerTint,
            padding = 20.dp,
        ) {
            Text(
                text = "Akun nonaktif",
                style = EduText.Headline.copy(color = EduColors.Danger),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Hubungi administrator kelas untuk memeriksa status akun " +
                    "dan mendapatkan bantuan.",
                style = EduText.BodySmall.copy(color = EduColors.Danger),
            )
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Hanya akun aktif yang dapat masuk ke EduVARK.",
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(22.dp))
        PrimaryButton(text = "Kembali ke halaman masuk", onClick = onBack)
    }
}

@Composable
private fun ResetPasswordPanel(
    enabled: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val save: () -> Unit = {
        error = when {
            password.length < 6 -> "Kata sandi minimal 6 karakter."
            password != confirmation -> "Konfirmasi kata sandi tidak sama."
            else -> null
        }
        if (error == null) onSave(password)
    }

    EduCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 18.dp,
    ) {
        Text(text = "Reset kata sandi", style = EduText.Section)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pengguna wajib mengganti kata sandi sementara ini saat masuk berikutnya.",
            style = EduText.BodySmall,
        )

        Spacer(Modifier.height(16.dp))
        FieldLabel("Kata sandi sementara")
        EduField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            placeholder = "Minimal 6 karakter",
            enabled = enabled,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )

        Spacer(Modifier.height(12.dp))
        FieldLabel("Ulangi kata sandi sementara")
        EduField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                error = null
            },
            placeholder = "Ulangi kata sandi",
            enabled = enabled,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )

        val errorText = error
        if (errorText != null) {
            FieldHelper(text = errorText, isError = true)
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Simpan kata sandi sementara",
            onClick = save,
            enabled = enabled,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Batal",
            onClick = onCancel,
            enabled = enabled,
        )
    }
}
