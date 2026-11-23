package com.eduvark.app.presentation.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.BuildConfig
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.ui.components.AdminBottomBar
import com.eduvark.app.ui.components.AdminTab
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LecturerBottomBar
import com.eduvark.app.ui.components.LecturerTab
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.StudentBottomBar
import com.eduvark.app.ui.components.StudentTab
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduText
import com.eduvark.app.ui.theme.EduVarkTheme

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenStudentTopics: () -> Unit,
    onOpenLecturerTopics: () -> Unit,
    onOpenGrading: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenAdminAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePassword: () -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLoggedOut()
        }
    }

    EduScreen(
        title = "Akun & sesi",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            when (state.user?.role) {
                UserRole.STUDENT -> StudentBottomBar(StudentTab.AKUN) { tab ->
                    when (tab) {
                        StudentTab.BERANDA -> onOpenHome()
                        StudentTab.TOPIK -> onOpenStudentTopics()
                        StudentTab.AKUN -> Unit
                    }
                }

                UserRole.LECTURER -> LecturerBottomBar(LecturerTab.AKUN) { tab ->
                    when (tab) {
                        LecturerTab.BERANDA -> onOpenHome()
                        LecturerTab.TOPIK -> onOpenLecturerTopics()
                        LecturerTab.PENILAIAN -> onOpenGrading()
                        LecturerTab.ANALITIK -> onOpenAnalytics()
                        LecturerTab.AKUN -> Unit
                    }
                }

                UserRole.ADMIN -> AdminBottomBar(AdminTab.PROFIL) { tab ->
                    when (tab) {
                        AdminTab.BERANDA -> onOpenHome()
                        AdminTab.AKUN -> onOpenAdminAccounts()
                        AdminTab.PROFIL -> Unit
                    }
                }

                null -> Unit
            }
        },
    ) { padding ->
        val user = state.user
        when {
            state.isLoading && user == null -> LoadingBlock(Modifier.padding(padding))

            user == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                ErrorBlock(
                    message = state.error ?: "Akun tidak ditemukan.",
                    onRetry = viewModel::load,
                )
            }

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                Eyebrow(user.role.label)
                Spacer(Modifier.height(10.dp))
                Text(text = accountHeadline(user.role), style = EduText.Display)
                Spacer(Modifier.height(10.dp))
                Text(text = accountSubtitle(user.role), style = EduText.Body)
                Spacer(Modifier.height(22.dp))

                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = user.fullName, style = EduText.Headline)
                    Spacer(Modifier.height(6.dp))
                    Text(text = user.loginId, style = EduText.BodySmall)
                    Spacer(Modifier.height(12.dp))
                    TintBar(
                        text = "${user.role.label} • Aktif",
                        tone = TintTone.Teal,
                        uppercase = true,
                    )
                }

                Spacer(Modifier.height(16.dp))
                HeroCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (user.role == UserRole.STUDENT) "FORMAT SESI AKTIF" else "HAK AKSES",
                        style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.72f)),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = sessionTitle(user),
                        style = EduText.Headline.copy(color = Color.White, fontSize = 24.sp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = sessionBody(user),
                        style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
                    )
                }

                Spacer(Modifier.height(16.dp))
                NoticeCard(text = accountNotice(user.role))

                Spacer(Modifier.height(20.dp))
                SecondaryButton(text = "Ubah password", onClick = onOpenChangePassword)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Keluar dari akun", onClick = viewModel::logout)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Keluar akan mengakhiri sesi dan pilihan VARK aktif.",
                    style = EduText.BodySmall,
                )

                if (BuildConfig.DEBUG) {
                    Spacer(Modifier.height(16.dp))
                    TextLink(
                        text = "Pengaturan server (debug)",
                        onClick = onOpenServerSettings,
                        icon = false,
                    )
                }
            }
        }
    }
}

private fun accountHeadline(role: UserRole): String = when (role) {
    UserRole.STUDENT -> "Akun belajarmu"
    UserRole.LECTURER -> "Akun mengajarmu"
    UserRole.ADMIN -> "Akun admin"
}

private fun accountSubtitle(role: UserRole): String = when (role) {
    UserRole.STUDENT -> "Implementasi Basis Data · Semester Ganjil 2026/2027"
    UserRole.LECTURER -> "Dosen pengampu IBD · Semester Ganjil 2026/2027"
    UserRole.ADMIN -> "Ruang administrasi EduVARK"
}

private fun sessionTitle(user: UserProfile): String = when (user.role) {
    UserRole.STUDENT -> "${user.activeVark?.label ?: "VARK"} • Terkunci"
    UserRole.LECTURER -> "Dosen • Tanpa VARK"
    UserRole.ADMIN -> "Admin • Manajemen akun"
}

private fun sessionBody(user: UserProfile): String = when (user.role) {
    UserRole.STUDENT ->
        "Untuk mengganti format, keluar lalu masuk kembali. Feedback kategori lain tetap tersimpan."

    UserRole.LECTURER ->
        "Staf tidak memilih format belajar. VARK hanya berlaku untuk sesi mahasiswa."

    UserRole.ADMIN ->
        "Admin hanya membuat, melihat, memperbarui, dan menonaktifkan akun."
}

private fun accountNotice(role: UserRole): String = when (role) {
    UserRole.STUDENT -> "Ruang belajar: satu kelas IBD untuk satu semester."
    UserRole.LECTURER -> "Kelas yang diampu: satu kelas IBD dalam satu semester."
    UserRole.ADMIN -> "Materi, feedback, dan penilaian tidak tersedia untuk role admin."
}

@Preview(showBackground = true)
@Composable
private fun AccountScreenPreview() {
    EduVarkTheme {
        AccountScreen(
            onBack = {},
            onLoggedOut = {},
            onOpenHome = {},
            onOpenStudentTopics = {},
            onOpenLecturerTopics = {},
            onOpenGrading = {},
            onOpenAnalytics = {},
            onOpenAdminAccounts = {},
        )
    }
}
