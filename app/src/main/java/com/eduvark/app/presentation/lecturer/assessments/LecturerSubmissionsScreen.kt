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
import androidx.compose.ui.res.stringResource
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
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

private const val FILTER_ALL = "ALL"
private const val FILTER_WAITING = "WAITING"
private const val FILTER_GRADED = "GRADED"
private const val FILTER_MISSING = "MISSING"

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
        title = stringResource(R.string.lec_assess_submissions_title),
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
                    .padding(dimen(R.dimen.space_24)),
            ) {
                ErrorBlock(message = error, onRetry = viewModel::load)
            }

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                TintBar(
                    text = stringResource(R.string.lec_assess_submissions_tintbar),
                    tone = TintTone.Primary,
                    uppercase = true,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                Text(
                    text = stringResource(R.string.lec_assess_submissions_headline),
                    style = EduTheme.type.display,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.lec_assess_submissions_body),
                    style = EduTheme.type.body,
                )

                if (rows.isEmpty()) {
                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    EmptyBlock(
                        title = stringResource(R.string.lec_assess_submissions_empty_title),
                        message = stringResource(R.string.lec_assess_submissions_empty_message),
                        actionLabel = stringResource(R.string.lec_assess_action_reload),
                        onAction = viewModel::load,
                    )
                } else {
                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    HeroCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.lec_assess_hero_ratio, submitted, total),
                            style = EduTheme.type.numeralLarge.copy(color = EduTheme.colors.onBrand),
                        )
                        Spacer(Modifier.height(dimen(R.dimen.space_10)))
                        Text(
                            text = stringResource(R.string.lec_assess_hero_collected, waiting),
                            style = EduTheme.type.bodySmall.copy(
                                color = EduTheme.colors.onBrand.copy(alpha = 0.9f),
                            ),
                        )
                        Spacer(Modifier.height(dimen(R.dimen.space_6)))
                        Text(
                            text = stringResource(
                                R.string.lec_assess_hero_graded,
                                graded,
                                notSubmitted,
                            ),
                            style = EduTheme.type.bodySmall.copy(
                                color = EduTheme.colors.onBrand.copy(alpha = 0.75f),
                            ),
                        )
                    }

                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    FieldLabel(stringResource(R.string.lec_assess_filter_label))
                    val filterOptions = listOf(
                        FILTER_ALL to stringResource(R.string.lec_assess_filter_all),
                        FILTER_WAITING to stringResource(R.string.lec_assess_filter_waiting),
                        FILTER_GRADED to stringResource(R.string.lec_assess_filter_graded),
                        FILTER_MISSING to stringResource(R.string.lec_assess_filter_missing),
                    )
                    EduDropdown(
                        value = filterOptions.firstOrNull { it.first == filter }?.second.orEmpty(),
                        options = filterOptions.map { it.second },
                        onSelect = { selected ->
                            filter = filterOptions
                                .firstOrNull { it.second == selected }
                                ?.first
                                ?: FILTER_ALL
                        },
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))

                    if (visible.isEmpty()) {
                        NoticeCard(
                            text = stringResource(R.string.lec_assess_filter_empty_notice),
                            tone = TintTone.Neutral,
                        )
                    } else {
                        for (row in visible) {
                            SubmissionCard(
                                row = row,
                                onClick = { row.submissionId?.let(onOpenSubmission) },
                            )
                            Spacer(Modifier.height(dimen(R.dimen.space_12)))
                        }
                    }

                    Spacer(Modifier.height(dimen(R.dimen.space_4)))
                    Text(
                        text = stringResource(R.string.lec_assess_list_footer),
                        style = EduTheme.type.bodySmall,
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
        SubmissionStatus.SUDAH_DINILAI -> {
            val score = row.score
            if (score == null) {
                stringResource(R.string.lec_assess_status_graded)
            } else {
                stringResource(R.string.lec_assess_status_graded_scored, score)
            }
        }

        SubmissionStatus.MENUNGGU_PENILAIAN ->
            stringResource(R.string.lec_assess_status_waiting)

        SubmissionStatus.TIDAK_MENGUMPULKAN,
        SubmissionStatus.BELUM_MENGUMPULKAN,
        -> stringResource(R.string.lec_assess_status_missing)
    }
    val meta = when {
        row.status == SubmissionStatus.SUDAH_DINILAI ->
            stringResource(R.string.lec_assess_meta_locked)

        row.submittedAt != null -> formatDeadline(row.submittedAt) ?: row.submittedAt.orEmpty()
        row.status == SubmissionStatus.MENUNGGU_PENILAIAN ->
            stringResource(R.string.lec_assess_meta_waiting)

        else -> stringResource(R.string.lec_assess_meta_no_submission)
    }

    ListCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (row.submissionId != null) onClick else null,
    ) {
        ArrowTitle(text = row.fullName)
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(text = row.loginId, style = EduTheme.type.bodySmall)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        TintBar(text = statusText, tone = tone)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = meta, style = EduTheme.type.bodySmall)
    }
}

