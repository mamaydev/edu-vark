package com.eduvark.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.AdminBottomBar
import com.eduvark.app.ui.components.AdminTab
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.LecturerBottomBar
import com.eduvark.app.ui.components.LecturerTab
import com.eduvark.app.ui.components.StudentBottomBar
import com.eduvark.app.ui.components.StudentTab
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LightButton
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SectionHeader
import com.eduvark.app.ui.components.StatCard
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TopicCard
import com.eduvark.app.ui.components.UserCard
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenStudentTopics: () -> Unit,
    onOpenLecturerTopics: () -> Unit,
    onOpenAccount: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGrading: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onCreateTopic: () -> Unit = {},
    onCreateUser: () -> Unit = {},
    onOpenAdminAccounts: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLoggedOut()
        }
    }

    val role = state.user?.role
    EduScreen(
        modifier = modifier,
        title = when (role) {
            UserRole.STUDENT -> "Beranda"
            UserRole.LECTURER -> "Beranda dosen"
            UserRole.ADMIN -> "Beranda admin"
            null -> "Beranda"
        },
        topBarAction = {
            if (state.user != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TopBarIcon(
                        icon = EduIcons.Person,
                        description = "Akun & sesi",
                        onClick = onOpenAccount,
                    )
                    TopBarIcon(
                        icon = EduIcons.Logout,
                        description = "Keluar dari akun",
                        onClick = { confirmLogout = true },
                    )
                }
            }
        },
        bottomBar = {
            when (role) {
                UserRole.STUDENT -> StudentBottomBar(StudentTab.BERANDA) { tab ->
                    when (tab) {
                        StudentTab.BERANDA -> Unit
                        StudentTab.TOPIK -> onOpenStudentTopics()
                        StudentTab.AKUN -> onOpenAccount()
                    }
                }

                UserRole.LECTURER -> LecturerBottomBar(LecturerTab.BERANDA) { tab ->
                    when (tab) {
                        LecturerTab.BERANDA -> Unit
                        LecturerTab.TOPIK -> onOpenLecturerTopics()
                        LecturerTab.PENILAIAN -> onOpenGrading()
                        LecturerTab.ANALITIK -> onOpenAnalytics()
                        LecturerTab.AKUN -> onOpenAccount()
                    }
                }

                UserRole.ADMIN -> AdminBottomBar(AdminTab.BERANDA) { tab ->
                    when (tab) {
                        AdminTab.BERANDA -> Unit
                        AdminTab.AKUN -> onOpenAdminAccounts()
                        AdminTab.PROFIL -> onOpenAccount()
                    }
                }

                null -> Unit
            }
        },
    ) { padding ->
        val user = state.user
        when {
            state.isLoading && user == null -> LoadingBlock(Modifier.padding(padding))

            user == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                ErrorBlock(
                    message = state.error ?: "Sesi tidak ditemukan.",
                    onRetry = viewModel::load,
                )
            }

            else -> ScrollColumn(modifier = Modifier.padding(padding)) {
                when (user.role) {
                    UserRole.STUDENT -> StudentHome(state, onOpenStudentTopics)
                    UserRole.LECTURER -> LecturerHome(
                        state = state,
                        onOpenTopics = onOpenLecturerTopics,
                        onOpenAnalytics = onOpenAnalytics,
                        onCreateTopic = onCreateTopic,
                    )

                    UserRole.ADMIN -> AdminHome(
                        state = state,
                        onCreateUser = onCreateUser,
                        onOpenAccounts = onOpenAdminAccounts,
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = EduColors.Surface,
            title = { Text(text = "Keluar dari akun?", style = EduText.Headline) },
            text = {
                Text(
                    text = "Sesi belajar akan diakhiri dan pilihan VARK aktif dihapus. " +
                        "Kamu perlu masuk kembali untuk melanjutkan.",
                    style = EduText.Body,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout()
                    },
                ) {
                    Text(
                        text = "Ya, keluar",
                        style = EduText.Button.copy(color = EduColors.Danger),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(text = "Batal", style = EduText.Button.copy(color = EduColors.Muted))
                }
            },
        )
    }
}

@Composable
private fun StudentHome(state: HomeUiState, onOpenTopics: () -> Unit) {
    val user = state.user ?: return
    val firstName = displayFirstName(user.fullName, "kamu")
    val vark = user.activeVark
    val kinesthetic = vark == VarkType.KINESTHETIC

    Eyebrow("Selamat ${greeting()}, $firstName")
    Text(
        text = if (kinesthetic) "Saatnya mencoba secara langsung." else "Siap memahami hal baru?",
        style = EduText.Display,
    )

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "IBD • SEMESTER GANJIL 2026/2027",
            style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.72f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (kinesthetic) "Belajar melalui studi kasus." else "Implementasi Basis Data",
            style = EduText.HeroTitle,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${vark?.label ?: "VARK belum dipilih"} • Terkunci selama sesi",
            style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
        )
        if (kinesthetic) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Jawaban teks. Satu kiriman final.",
                style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.75f)),
            )
        }
        Spacer(Modifier.height(18.dp))
        LightButton(
            text = if (kinesthetic) "Buka studi kasus" else "Lanjut belajar",
            onClick = onOpenTopics,
        )
    }

    if (!kinesthetic) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                value = state.publishedTopics.size.toString().padStart(2, '0'),
                label = "Topik terbit",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = state.readyTopics.size.toString().padStart(2, '0'),
                label = "Materi siap",
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionHeader(
        title = if (kinesthetic) "Studi kasus untukmu" else "Lanjutkan topik",
        actionLabel = "Semua",
        onAction = onOpenTopics,
    )

    val preview = state.publishedTopics.take(2)
    if (preview.isEmpty() && !state.isLoading) {
        EduCard {
            Text(text = "Belum ada topik terbit.", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(text = "Topik akan muncul setelah dosen menerbitkannya.", style = EduText.BodySmall)
        }
    } else {
        preview.forEachIndexed { index, topic ->
            TopicPreviewCard(
                index = index,
                topic = topic,
                kinesthetic = kinesthetic,
                vark = vark,
                onClick = onOpenTopics,
            )
        }
    }

    if (kinesthetic) {
        NoticeCard(
            text = "Kinesthetic menggunakan penilaian dosen, bukan slider feedback pemahaman.",
        )
    }
}

@Composable
private fun LecturerHome(
    state: HomeUiState,
    onOpenTopics: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onCreateTopic: () -> Unit,
) {
    val user = state.user ?: return
    val firstName = displayFirstName(user.fullName, "Pak/Bu")

    Eyebrow("Selamat ${greeting()}, $firstName")
    Text(text = "Bantu setiap cara belajar bertumbuh.", style = EduText.Display)

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "KELAS YANG DIAMPU",
            style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.72f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(text = "Implementasi Basis Data", style = EduText.HeroTitle)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Semester Ganjil 2026/2027 • ${state.publishedTopics.size} topik terbit",
            style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            value = state.publishedTopics.size.toString().padStart(2, '0'),
            label = "Topik terbit",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = state.draftTopics.size.toString().padStart(2, '0'),
            label = "Draf",
            modifier = Modifier.weight(1f),
        )
    }

    SectionHeader(title = "Prioritas hari ini")

    EduCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTopics) {
        Text(text = "Kelola topik dan materi", style = EduText.CardTitle)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Susun urutan topik, unggah materi per kategori, dan atur studi kasus.",
            style = EduText.BodySmall,
        )
        Spacer(Modifier.height(12.dp))
        TextLink(text = "Buka daftar topik", onClick = onOpenTopics)
    }

    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduColors.TealTint,
        onClick = onOpenAnalytics,
    ) {
        Text(
            text = "Pantau pemahaman kelas",
            style = EduText.CardTitle.copy(color = EduColors.Teal),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Feedback V/A/R dan nilai Kinesthetic ditampilkan terpisah.",
            style = EduText.BodySmall.copy(color = EduColors.Teal),
        )
        Spacer(Modifier.height(12.dp))
        TextLink(text = "Buka analitik", onClick = onOpenAnalytics, color = EduColors.Teal)
    }

    PrimaryButton(text = "+ Buat topik baru", onClick = onCreateTopic)
}

@Composable
private fun AdminHome(
    state: HomeUiState,
    onCreateUser: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val summary = state.adminSummary

    Eyebrow("Ruang administrasi")
    Text(text = "Akun tertata.\nBelajar tetap terjaga.", style = EduText.Display)

    HeroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "EDUVARK • KELAS IBD",
            style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.72f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${summary?.total ?: 0} akun",
            style = EduText.NumeralLarge.copy(color = Color.White),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${summary?.students ?: 0} mahasiswa • ${summary?.lecturers ?: 0} dosen • " +
                "${summary?.admins ?: 0} admin",
            style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.9f)),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            value = (summary?.active ?: 0).toString().padStart(2, '0'),
            label = "Akun aktif",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = (summary?.inactive ?: 0).toString().padStart(2, '0'),
            label = "Nonaktif",
            modifier = Modifier.weight(1f),
        )
    }

    PrimaryButton(text = "+ Buat akun baru", onClick = onCreateUser)

    SectionHeader(
        title = "Kelola pengguna",
        actionLabel = "Semua",
        onAction = onOpenAccounts,
    )

    if (state.adminUsers.isEmpty() && !state.isLoading) {
        EduCard {
            Text(text = "Belum ada akun terdaftar.", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(text = "Tambahkan akun mahasiswa atau dosen untuk memulai.", style = EduText.BodySmall)
        }
    } else {
        state.adminUsers.take(2).forEach { account ->
            UserCard(
                name = account.fullName.ifBlank { account.loginId },
                meta = "${account.loginId} • ${account.role.label}",
                status = account.status.label,
                statusTone = if (account.status.wireValue == "ACTIVE") TintTone.Teal else TintTone.Danger,
                onClick = onOpenAccounts,
            )
        }
    }

    NoticeCard(
        text = "Hak admin terbatas pada akun. Materi, feedback, dan penilaian tidak tersedia untuk role ini.",
    )
}

@Composable
private fun TopicPreviewCard(
    index: Int,
    topic: Topic,
    kinesthetic: Boolean,
    vark: VarkType?,
    onClick: () -> Unit,
) {
    val order = (index + 1).coerceAtLeast(topic.sortOrder)
    TopicCard(
        orderLabel = "Topik ${order.toString().padStart(2, '0')}  /  Implementasi Basis Data",
        title = topic.title,
        meta = topic.description?.takeIf { it.isNotBlank() }
            ?: if (kinesthetic) "Jawaban teks • Satu kiriman final" else "Format ${vark?.label ?: "VARK"}",
        status = when {
            kinesthetic -> "Buka studi kasus"
            topic.materialAvailable -> "Tersedia • ${vark?.label ?: "VARK"}"
            else -> "Belum tersedia"
        },
        statusTone = when {
            kinesthetic -> TintTone.Teal
            topic.materialAvailable -> TintTone.Teal
            else -> TintTone.Neutral
        },
        onClick = onClick,
    )
}

@Composable
private fun TopBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = EduColors.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** First name for greetings, skipping academic titles such as "Dr." or "Prof.". */
private fun displayFirstName(fullName: String, fallback: String): String {
    val titles = setOf("dr.", "dr", "prof.", "prof", "drs.", "drs", "ir.", "ir", "h.", "hj.")
    val name = fullName.trim().split(" ")
        .firstOrNull { it.isNotBlank() && it.lowercase() !in titles }
        ?.trimEnd(',')
    return name?.takeIf { it.isNotBlank() } ?: fallback
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 11 -> "pagi"
        hour < 15 -> "siang"
        hour < 19 -> "sore"
        else -> "malam"
    }
}
