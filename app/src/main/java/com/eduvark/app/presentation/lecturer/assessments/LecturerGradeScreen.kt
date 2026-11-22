package com.eduvark.app.presentation.lecturer.assessments

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.SubmissionDetail
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.ui.components.CardEyebrow
import com.eduvark.app.ui.components.ContentCard
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.format.formatDeadline
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

/**
 * Beri penilaian studi kasus (mockups 26, 27 and 28).
 *
 * The screen has an assessment form, an in-screen final confirmation, and the locked
 * result view once the submission has been graded.
 */
@Composable
fun LecturerGradeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerGradeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    val locked = detail?.status == SubmissionStatus.SUDAH_DINILAI

    var scoreText by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var answerExpanded by rememberSaveable { mutableStateOf(false) }
    var lockedAnswerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(locked) {
        if (locked) confirming = false
    }

    val screenTitle = when {
        locked -> "Detail penilaian"
        confirming -> "Simpan penilaian"
        else -> "Penilaian jawaban"
    }

    EduScreen(
        title = screenTitle,
        onBack = { if (confirming) confirming = false else onBack() },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        val error = state.error
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            error != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                ErrorBlock(message = error, onRetry = viewModel::load)
            }

            detail == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                EmptyBlock(
                    title = "Penilaian tidak tersedia",
                    message = "Jawaban mahasiswa tidak dapat dimuat.",
                    actionLabel = "Muat ulang",
                    onAction = viewModel::load,
                )
            }

            locked -> LockedGrade(
                detail = detail,
                answerOpen = lockedAnswerOpen,
                onToggleAnswer = { lockedAnswerOpen = !lockedAnswerOpen },
                onBack = onBack,
                modifier = Modifier.padding(padding),
            )

            confirming -> ConfirmGrade(
                detail = detail,
                score = scoreText.toIntOrNull() ?: 0,
                comment = comment,
                isSaving = state.isSaving,
                saveError = state.saveError,
                onConfirm = {
                    val score = scoreText.toIntOrNull()
                    if (score == null || score !in 0..100) {
                        validationError = "Nilai harus 0–100."
                        confirming = false
                    } else {
                        viewModel.save(score, comment)
                    }
                },
                onCancel = { confirming = false },
                modifier = Modifier.padding(padding),
            )

            else -> GradeForm(
                detail = detail,
                scoreText = scoreText,
                onScoreChange = { value ->
                    scoreText = value.filter { it.isDigit() }.take(3)
                    validationError = null
                },
                comment = comment,
                onCommentChange = { comment = it.take(1000) },
                answerExpanded = answerExpanded,
                onToggleAnswer = { answerExpanded = !answerExpanded },
                validationError = validationError,
                saveError = state.saveError,
                isSaving = state.isSaving,
                onReview = {
                    val score = scoreText.toIntOrNull()
                    if (score == null || score !in 0..100) {
                        validationError = "Nilai harus 0–100."
                    } else {
                        validationError = null
                        confirming = true
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun GradeForm(
    detail: SubmissionDetail,
    scoreText: String,
    onScoreChange: (String) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    answerExpanded: Boolean,
    onToggleAnswer: () -> Unit,
    validationError: String?,
    saveError: String?,
    isSaving: Boolean,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submitted = formatDeadline(detail.submittedAt) ?: detail.submittedAt

    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "BELUM DINILAI", tone = TintTone.Amber, uppercase = true)
        Spacer(Modifier.height(16.dp))
        Text(text = detail.studentName, style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(text = "Jawaban studi kasus Kinesthetic", style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Jawaban final", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(text = "Dikirim $submitted", style = EduText.BodySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = detail.answerText,
                style = EduText.Body,
                maxLines = if (answerExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.answerText.length > 220 || answerExpanded) {
                Spacer(Modifier.height(10.dp))
                TextLink(
                    text = if (answerExpanded) "Ringkas jawaban" else "Baca jawaban lengkap",
                    onClick = onToggleAnswer,
                    color = EduColors.Primary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        FieldLabel("Nilai (0–100)")
        EduField(
            value = scoreText,
            onValueChange = onScoreChange,
            placeholder = "0–100",
            keyboardType = KeyboardType.Number,
            enabled = !isSaving,
            error = validationError != null,
        )
        if (validationError != null) {
            FieldHelper(text = validationError, isError = true)
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Komentar penilaian")
        EduField(
            value = comment,
            onValueChange = onCommentChange,
            placeholder = "Tuliskan umpan balik untuk mahasiswa…",
            singleLine = false,
            minHeight = 120.dp,
            enabled = !isSaving,
        )
        FieldHelper(text = "${comment.length} / 1.000 karakter")

        if (saveError != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = saveError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Penilaian hanya sekali. Setelah disimpan, nilai dan komentar tidak " +
                "dapat diubah.",
            tone = TintTone.Amber,
        )

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Tinjau penilaian final",
            enabled = !isSaving,
            loading = isSaving,
            showArrow = true,
            onClick = onReview,
        )
    }
}

@Composable
private fun ConfirmGrade(
    detail: SubmissionDetail,
    score: Int,
    comment: String,
    isSaving: Boolean,
    saveError: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Konfirmasi final")
        Spacer(Modifier.height(10.dp))
        Text(text = "Simpan nilai\nsecara permanen?", style = EduText.DisplaySmall)
        Spacer(Modifier.height(10.dp))
        Text(text = detail.studentName, style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow("Nilai objektif Kinesthetic")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$score / 100",
                style = EduText.NumeralLarge.copy(color = EduColors.Primary),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = comment.ifBlank { "Tanpa komentar." },
                style = EduText.Body,
            )
        }

        Spacer(Modifier.height(16.dp))
        TonePanel(
            title = "Pastikan sudah benar",
            body = "",
            tone = TintTone.Amber,
            bullets = listOf(
                "Nilai dan komentar tidak bisa diedit atau dihapus.",
                "Hasil langsung terlihat oleh mahasiswa.",
                "Tidak ada tahap publikasi tambahan.",
                "Jawaban mahasiswa tetap utuh.",
            ),
        )

        if (saveError != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(text = saveError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Ya, simpan penilaian final",
            enabled = !isSaving,
            loading = isSaving,
            onClick = onConfirm,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "Kembali periksa",
            enabled = !isSaving,
            onClick = onCancel,
        )
    }
}

@Composable
private fun LockedGrade(
    detail: SubmissionDetail,
    answerOpen: Boolean,
    onToggleAnswer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val score = detail.score?.toString() ?: "—"
    val assessed = detail.assessedAt?.let { formatDeadline(it) } ?: detail.assessedAt

    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(text = "SUDAH DINILAI • TERKUNCI", tone = TintTone.Teal, uppercase = true)
        Spacer(Modifier.height(16.dp))
        Eyebrow("Penilaian final")
        Spacer(Modifier.height(10.dp))
        Text(text = "Nilai tersimpan.", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(text = detail.studentName, style = EduText.Body)
        Spacer(Modifier.height(18.dp))

        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Eyebrow(
                text = "Nilai objektif Kinesthetic",
                color = EduColors.Surface.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$score / 100",
                style = EduText.NumeralLarge.copy(color = EduColors.Surface),
            )
            val savedComment = detail.comment
            if (!savedComment.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = savedComment,
                    style = EduText.BodySmall.copy(
                        color = EduColors.Surface.copy(alpha = 0.9f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Studi kasus Kinesthetic", style = EduText.CardTitle)
            if (assessed != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = "Dinilai $assessed", style = EduText.BodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Hasil sudah terlihat oleh mahasiswa.",
                style = EduText.BodySmall.copy(color = EduColors.Teal),
            )
        }

        if (answerOpen) {
            Spacer(Modifier.height(16.dp))
            ContentCard(
                title = "Jawaban final",
                body = detail.answerText,
                meta = formatDeadline(detail.submittedAt)?.let { "Dikirim $it" },
            )
        }

        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Nilai dan komentar final tidak dapat diubah atau dihapus. Jawaban " +
                "mahasiswa tetap terkunci.",
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(20.dp))
        SecondaryButton(
            text = if (answerOpen) "Tutup jawaban final" else "Lihat jawaban final",
            onClick = onToggleAnswer,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Kembali ke pengumpulan",
            onClick = onBack,
        )
    }
}
