package com.eduvark.app.presentation.student.cases

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.ui.components.ArrowTitle
import com.eduvark.app.ui.components.CardEyebrow
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.SuccessPanel
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.format.formatDateTime
import com.eduvark.app.ui.format.formatBytes
import com.eduvark.app.ui.format.formatDeadline
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

@Composable
fun StudentCaseScreen(
    onBack: () -> Unit,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHome: () -> Unit = onOpenTopics,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: StudentCaseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caseStudy = state.caseStudy
    var answer by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val openMaterial: (Material) -> Unit = { material ->
        val target = material.signedUrl ?: material.url
        if (!target.isNullOrBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            } catch (_: ActivityNotFoundException) {
                // No PDF/file viewer installed; the link stays visible for retry.
            }
        }
    }
    val status = caseStudy?.status
    val screenTitle = when (status) {
        SubmissionStatus.MENUNGGU_PENILAIAN, SubmissionStatus.TIDAK_MENGUMPULKAN ->
            "Status pengumpulan"

        SubmissionStatus.SUDAH_DINILAI -> "Hasil penilaian"
        else -> if (confirming) "Kirim jawaban" else "Studi kasus"
    }

    EduScreen(
        title = screenTitle,
        onBack = { if (confirming) confirming = false else onBack() },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            state.error != null -> Column(Modifier.padding(padding).padding(24.dp)) {
                ErrorBlock(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            caseStudy == null -> Column(Modifier.padding(padding).padding(24.dp)) {
                NoticeCard(
                    text = "Studi kasus untuk topik ini belum tersedia.",
                    tone = TintTone.Amber,
                )
            }

            status == SubmissionStatus.SUDAH_DINILAI -> GradedCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                onOpenHome = onOpenHome,
                modifier = Modifier.padding(padding),
            )

            status == SubmissionStatus.MENUNGGU_PENILAIAN -> WaitingCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                onOpenHome = onOpenHome,
                modifier = Modifier.padding(padding),
            )

            status == SubmissionStatus.TIDAK_MENGUMPULKAN -> ClosedCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                onOpenTopics = onOpenTopics,
                modifier = Modifier.padding(padding),
            )

            confirming -> ConfirmSubmission(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                answer = answer,
                isSubmitting = state.isSubmitting,
                error = state.submitError,
                onConfirm = { viewModel.submit(answer) },
                onCancel = { confirming = false },
                modifier = Modifier.padding(padding),
            )

            else -> AnswerEditor(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                materials = state.materials,
                onOpenMaterial = openMaterial,
                answer = answer,
                onAnswerChange = { if (it.length <= 10000) answer = it },
                submitError = state.submitError,
                onNext = { confirming = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AnswerEditor(
    caseStudy: CaseStudy,
    topicTitle: String,
    materials: List<Material>,
    onOpenMaterial: (Material) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
    submitError: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "Topik • Kinesthetic", uppercase = true)
        Spacer(Modifier.height(18.dp))
        Text(text = topicTitle.ifBlank { "Studi kasus" }, style = EduText.Display)
        Spacer(Modifier.height(16.dp))
        DeadlinePanel(caseStudy)
        Spacer(Modifier.height(16.dp))

        if (materials.isNotEmpty()) {
            SupportingMaterials(materials = materials, onOpen = onOpenMaterial)
            Spacer(Modifier.height(16.dp))
        }

        EduCard(modifier = Modifier.fillMaxWidth()) {
            if (!caseStudy.theory.isNullOrBlank()) {
                Text(text = "Teori singkat", style = EduText.CardTitle)
                Spacer(Modifier.height(6.dp))
                Text(text = caseStudy.theory, style = EduText.Body)
                Spacer(Modifier.height(16.dp))
            }
            Text(text = "Instruksi studi kasus", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(text = caseStudy.instructions, style = EduText.Body)
        }

        Spacer(Modifier.height(16.dp))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Jawabanku", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(text = "Teks biasa • Tanpa unggah file", style = EduText.Meta)
            Spacer(Modifier.height(12.dp))
            EduField(
                value = answer,
                onValueChange = onAnswerChange,
                placeholder = "Tulis jawabanmu di sini…",
                singleLine = false,
                minHeight = 150.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${answer.length} / 10.000 karakter",
                style = EduText.Meta,
            )
        }

        if (submitError != null) {
            Spacer(Modifier.height(14.dp))
            NoticeCard(text = submitError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Tidak ada draft atau simpan otomatis. Jawaban yang belum dikirim dapat hilang " +
                "jika halaman ditutup.",
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Kirim jawaban final",
            onClick = onNext,
            enabled = answer.trim().isNotEmpty(),
        )
        Spacer(Modifier.height(12.dp))
        Text(text = "1–10.000 karakter • Hanya satu kali kirim", style = EduText.BodySmall)
    }
}

@Composable
private fun SupportingMaterials(
    materials: List<Material>,
    onOpen: (Material) -> Unit,
) {
    EduCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Materi pendukung Kinesthetic", style = EduText.CardTitle)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Baca materi dari dosen sebelum menjawab studi kasus.",
            style = EduText.Meta,
        )
        materials.forEach { material ->
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EduColors.PrimaryTint, RoundedCornerShape(12.dp))
                    .clickable { onOpen(material) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                ArrowTitle(text = material.title, color = EduColors.Primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        material.kind.label,
                        material.mime,
                        formatBytes(material.sizeBytes),
                    ).joinToString(" • "),
                    style = EduText.Meta,
                )
            }
        }
    }
}

@Composable
private fun DeadlinePanel(caseStudy: CaseStudy) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EduColors.AmberTint, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Tenggat: " + (formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib),
            style = EduText.BodySmall.copy(
                color = EduColors.Amber,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Acuan penerimaan: waktu server.",
            style = EduText.BodySmall.copy(color = EduColors.Amber),
        )
    }
}

@Composable
private fun ConfirmSubmission(
    caseStudy: CaseStudy,
    topicTitle: String,
    answer: String,
    isSubmitting: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Konfirmasi pengiriman")
        Spacer(Modifier.height(10.dp))
        Text(text = "Sudah yakin dengan\njawabanmu?", style = EduText.Display)
        Spacer(Modifier.height(12.dp))
        Text(text = "Baca kembali sebelum mengirim.", style = EduText.Body)
        Spacer(Modifier.height(20.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow("Topik • Kinesthetic")
            Spacer(Modifier.height(8.dp))
            Text(text = topicTitle.ifBlank { "Studi kasus" }, style = EduText.Headline)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Siap dikirim • ${answer.trim().length} karakter",
                style = EduText.BodySmall.copy(color = EduColors.Teal, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tenggat: " + (formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib),
                style = EduText.Meta,
            )
        }

        Spacer(Modifier.height(16.dp))
        TonePanel(
            title = "Pengiriman bersifat final",
            body = "",
            tone = TintTone.Amber,
            bullets = listOf(
                "Hanya satu jawaban per studi kasus.",
                "Jawaban tidak dapat diubah atau dikirim ulang.",
                "Tidak ada penyimpanan draft.",
                "Server memeriksa tenggat saat Kirim diproses.",
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            NoticeCard(text = error, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Ya, kirim jawaban final",
            onClick = onConfirm,
            loading = isSubmitting,
            enabled = !isSubmitting,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = "Kembali periksa jawaban", onClick = onCancel)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Kiriman diterima jika waktu server ≤ tenggat. Setelah tenggat, jawaban tidak " +
                "tersimpan.",
            style = EduText.BodySmall,
        )
    }
}

@Composable
private fun WaitingCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        SuccessPanel()
        Spacer(Modifier.height(22.dp))
        Eyebrow("Berhasil dikirim")
        Spacer(Modifier.height(10.dp))
        Text(text = "Jawabanmu\nsudah diterima.", style = EduText.Display)
        Spacer(Modifier.height(12.dp))
        Text(text = "Dosen akan menilai jawaban finalmu.", style = EduText.Body)
        Spacer(Modifier.height(20.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow("Topik • Kinesthetic")
            Spacer(Modifier.height(8.dp))
            Text(text = topicTitle.ifBlank { "Studi kasus" }, style = EduText.Headline)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Menunggu penilaian",
                style = EduText.BodySmall.copy(color = EduColors.Teal, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tenggat: " + (formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib),
                style = EduText.Meta,
            )
        }

        Spacer(Modifier.height(16.dp))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Dikirim pada", style = EduText.Meta)
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatDateTime(caseStudy.submittedAt) ?: caseStudy.submittedAt.orEmpty(),
                style = EduText.BodyStrong,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Jawaban final • ${caseStudy.submissionCount ?: 1} kiriman",
                style = EduText.BodySmall.copy(color = EduColors.Teal),
            )
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Jawaban terkunci. Tidak ada opsi revisi atau pengiriman ulang sebelum " +
                "maupun sesudah dinilai.",
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Kembali ke beranda", onClick = onOpenHome)
    }
}

@Composable
private fun GradedCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "Sudah dinilai • Final", tone = TintTone.Teal, uppercase = true)
        Spacer(Modifier.height(18.dp))
        Text(text = "Hasil kerja kerasmu.", style = EduText.Display)
        Spacer(Modifier.height(18.dp))

        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "NILAI STUDI KASUS",
                style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${caseStudy.score ?: 0} / 100",
                style = EduText.NumeralLarge.copy(fontSize = 46.sp, lineHeight = 52.sp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${topicTitle.ifBlank { "Studi kasus" }} • Kinesthetic",
                style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.92f)),
            )
        }

        Spacer(Modifier.height(16.dp))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Komentar dosen", style = EduText.CardTitle)
            Spacer(Modifier.height(8.dp))
            Text(
                text = caseStudy.comment?.takeIf { it.isNotBlank() }
                    ?: "Dosen tidak menambahkan komentar.",
                style = EduText.Body,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Penilaian langsung terlihat setelah disimpan dosen dan tidak dapat diubah.",
            style = EduText.BodySmall,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Kembali ke beranda", onClick = onOpenHome)
    }
}

@Composable
private fun ClosedCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "Tenggat berakhir", tone = TintTone.Danger, uppercase = true)
        Spacer(Modifier.height(16.dp))
        Eyebrow("Status pengumpulan")
        Spacer(Modifier.height(10.dp))
        Text(text = "Tidak\nMengumpulkan", style = EduText.Display)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Kamu belum mengirim jawaban hingga tenggat berakhir.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(20.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow("Topik • Kinesthetic")
            Spacer(Modifier.height(8.dp))
            Text(text = topicTitle.ifBlank { "Studi kasus" }, style = EduText.Headline)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tidak ada pengiriman",
                style = EduText.BodySmall.copy(color = EduColors.Teal, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tenggat: " + (formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib),
                style = EduText.Meta,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EduColors.AmberTint, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "— / 100",
                style = EduText.Numeral.copy(
                    color = EduColors.Amber,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Bukan nilai 0",
                style = EduText.BodyStrong.copy(color = EduColors.Amber, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tidak ada jawaban atau nilai yang dibuat otomatis untuk status ini.",
                style = EduText.BodySmall.copy(color = EduColors.Amber),
            )
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Pengumpulan ditutup. Jawaban yang diterima server setelah tenggat akan ditolak.",
            tone = TintTone.Danger,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Kembali ke daftar topik", onClick = onOpenTopics)
    }
}
