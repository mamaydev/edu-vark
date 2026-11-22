package com.eduvark.app.presentation.lecturer.assessments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.ui.components.ArrowTitle
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.ListCard
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.format.formatDeadline
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

private const val FILTER_ALL = "ALL"
private const val FILTER_WAITING = "WAITING"
private const val FILTER_GRADED = "GRADED"
private const val FILTER_MISSING = "MISSING"

private const val LABEL_ALL = "Semua status"
private const val LABEL_WAITING = "Menunggu penilaian"
private const val LABEL_GRADED = "Sudah dinilai"
private const val LABEL_MISSING = "Tidak mengumpulkan"

/**
 * Daftar pengumpulan studi kasus (mockup 25).
 *
 * The blue hero card derives its numbers from the submission rows: total students,
 * submitted, waiting, graded and not submitted.
 */
@Composable
fun LecturerSubmissionsScreen(
    onBack: () -> Unit,
    onOpenSubmission: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerSubmissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    // Reload on entry and whenever the lecturer returns from grading a submission.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val rows = state.submissions
    val total = rows.size
    val submitted = rows.count { it.submissionId != null }
    val waiting = rows.count { it.status == SubmissionStatus.MENUNGGU_PENILAIAN }
    val graded = rows.count { it.status == SubmissionStatus.SUDAH_DINILAI }
    val notSubmitted = rows.count {
        it.submissionId == null ||
            it.status == SubmissionStatus.TIDAK_MENGUMPULKAN ||
            it.status == SubmissionStatus.BELUM_MENGUMPULKAN
    }
    val visible = rows.filter { row ->
        when (filter) {
            FILTER_WAITING -> row.status == SubmissionStatus.MENUNGGU_PENILAIAN
            FILTER_GRADED -> row.status == SubmissionStatus.SUDAH_DINILAI
            FILTER_MISSING -> row.submissionId == null ||
                row.status == SubmissionStatus.TIDAK_MENGUMPULKAN ||
                row.status == SubmissionStatus.BELUM_MENGUMPULKAN

            else -> true
        }
    }

    EduScreen(
        title = "Pengumpulan",
        onBack = onBack,
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

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                TintBar(text = "KINESTHETIC • STUDI KASUS", tone = TintTone.Primary, uppercase = true)
                Spacer(Modifier.height(16.dp))
                Text(text = "Pengumpulan\nstudi kasus", style = EduText.Display)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Pilih mahasiswa untuk melihat jawaban dan memberi nilai.",
                    style = EduText.Body,
                )

                if (rows.isEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    EmptyBlock(
                        title = "Belum ada mahasiswa",
                        message = "Belum ada mahasiswa Kinesthetic pada kelas ini.",
                        actionLabel = "Muat ulang",
                        onAction = viewModel::load,
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                    HeroCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "$submitted / $total",
                            style = EduText.NumeralLarge.copy(color = EduColors.Surface),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Terkumpul • $waiting menunggu penilaian",
                            style = EduText.BodySmall.copy(
                                color = EduColors.Surface.copy(alpha = 0.9f),
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "$graded sudah dinilai • $notSubmitted tidak mengumpulkan",
                            style = EduText.BodySmall.copy(
                                color = EduColors.Surface.copy(alpha = 0.75f),
                            ),
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    FieldLabel("Filter status")
                    EduDropdown(
                        value = filterLabel(filter),
                        options = listOf(
                            LABEL_ALL,
                            LABEL_WAITING,
                            LABEL_GRADED,
                            LABEL_MISSING,
                        ),
                        onSelect = { selected -> filter = filterKey(selected) },
                    )
                    Spacer(Modifier.height(16.dp))

                    if (visible.isEmpty()) {
                        NoticeCard(
                            text = "Tidak ada mahasiswa dengan status ini.",
                            tone = TintTone.Neutral,
                        )
                    } else {
                        visible.forEach { row ->
                            SubmissionCard(
                                row = row,
                                onClick = { row.submissionId?.let(onOpenSubmission) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ketuk mahasiswa untuk membuka jawaban dan penilaian.",
                        style = EduText.BodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmissionCard(row: CaseSubmissionSummary, onClick: () -> Unit) {
    val tone = when (row.status) {
        SubmissionStatus.SUDAH_DINILAI -> TintTone.Teal
        SubmissionStatus.MENUNGGU_PENILAIAN -> TintTone.Amber
        else -> TintTone.Danger
    }
    val statusText = when (row.status) {
        SubmissionStatus.SUDAH_DINILAI ->
            row.score?.let { "Sudah dinilai • $it/100" } ?: "Sudah dinilai"

        SubmissionStatus.MENUNGGU_PENILAIAN -> "Menunggu penilaian"
        SubmissionStatus.TIDAK_MENGUMPULKAN,
        SubmissionStatus.BELUM_MENGUMPULKAN,
        -> "Tidak Mengumpulkan"
    }
    val meta = when {
        row.status == SubmissionStatus.SUDAH_DINILAI -> "Nilai dan komentar terkunci"
        row.submittedAt != null -> formatDeadline(row.submittedAt) ?: row.submittedAt.orEmpty()
        row.status == SubmissionStatus.MENUNGGU_PENILAIAN ->
            "Jawaban terkirim • menunggu penilaian"

        else -> "Tidak ada pengiriman • bukan nilai 0"
    }

    ListCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (row.submissionId != null) onClick else null,
    ) {
        ArrowTitle(text = row.fullName)
        Spacer(Modifier.height(6.dp))
        Text(text = row.loginId, style = EduText.BodySmall)
        Spacer(Modifier.height(12.dp))
        TintBar(text = statusText, tone = tone)
        Spacer(Modifier.height(10.dp))
        Text(text = meta, style = EduText.BodySmall)
    }
}

private fun filterLabel(key: String): String = when (key) {
    FILTER_WAITING -> LABEL_WAITING
    FILTER_GRADED -> LABEL_GRADED
    FILTER_MISSING -> LABEL_MISSING
    else -> LABEL_ALL
}

private fun filterKey(label: String): String = when (label) {
    LABEL_WAITING -> FILTER_WAITING
    LABEL_GRADED -> FILTER_GRADED
    LABEL_MISSING -> FILTER_MISSING
    else -> FILTER_ALL
}
