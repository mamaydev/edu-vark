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
import androidx.compose.ui.res.stringResource
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
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

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

    val scoreRangeError = stringResource(R.string.lec_assess_error_score_range)
    val screenTitle = when {
        locked -> stringResource(R.string.lec_assess_title_locked)
        confirming -> stringResource(R.string.lec_assess_title_confirm)
        else -> stringResource(R.string.lec_assess_title_form)
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
                    .padding(dimen(R.dimen.space_24)),
            ) {
                ErrorBlock(message = error, onRetry = viewModel::load)
            }

            detail == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                EmptyBlock(
                    title = stringResource(R.string.lec_assess_unavailable_title),
                    message = stringResource(R.string.lec_assess_unavailable_message),
                    actionLabel = stringResource(R.string.lec_assess_action_reload),
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
                        validationError = scoreRangeError
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
                        validationError = scoreRangeError
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

    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(R.string.lec_assess_form_tintbar),
            tone = TintTone.Amber,
            uppercase = true,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Text(text = detail.studentName, style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.lec_assess_form_subtitle), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.lec_assess_answer_title),
                style = EduTheme.type.cardTitle,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.lec_assess_submitted_at, submitted),
                style = EduTheme.type.bodySmall,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(
                text = detail.answerText,
                style = EduTheme.type.body,
                maxLines = if (answerExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.answerText.length > 220 || answerExpanded) {
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                TextLink(
                    text = stringResource(
                        if (answerExpanded) {
                            R.string.lec_assess_answer_collapse
                        } else {
                            R.string.lec_assess_answer_expand
                        },
                    ),
                    onClick = onToggleAnswer,
                    color = EduTheme.colors.lavenderDim,
                )
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        FieldLabel(stringResource(R.string.lec_assess_score_label))
        EduField(
            value = scoreText,
            onValueChange = onScoreChange,
            placeholder = stringResource(R.string.lec_assess_score_placeholder),
            keyboardType = KeyboardType.Number,
            enabled = !isSaving,
            error = validationError != null,
        )
        if (validationError != null) {
            FieldHelper(text = validationError, isError = true)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        FieldLabel(stringResource(R.string.lec_assess_comment_label))
        EduField(
            value = comment,
            onValueChange = onCommentChange,
            placeholder = stringResource(R.string.lec_assess_comment_placeholder),
            singleLine = false,
            minHeight = dimen(R.dimen.dimen_120),
            enabled = !isSaving,
        )
        FieldHelper(text = stringResource(R.string.lec_assess_comment_counter, comment.length))

        if (saveError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = saveError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.lec_assess_once_notice),
            tone = TintTone.Amber,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = stringResource(R.string.lec_assess_review_button),
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
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_assess_confirm_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_assess_confirm_title),
            style = EduTheme.type.displaySmall,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = detail.studentName, style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow(stringResource(R.string.lec_assess_objective_label))
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.lec_assess_confirm_score_out_of, score),
                style = EduTheme.type.numeralLarge.copy(color = EduTheme.colors.lavender),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(
                text = if (comment.isBlank()) {
                    stringResource(R.string.lec_assess_no_comment)
                } else {
                    comment
                },
                style = EduTheme.type.body,
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        TonePanel(
            title = stringResource(R.string.lec_assess_confirm_panel_title),
            body = "",
            tone = TintTone.Amber,
            bullets = listOf(
                stringResource(R.string.lec_assess_confirm_bullet_1),
                stringResource(R.string.lec_assess_confirm_bullet_2),
                stringResource(R.string.lec_assess_confirm_bullet_3),
                stringResource(R.string.lec_assess_confirm_bullet_4),
            ),
        )

        if (saveError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            NoticeCard(text = saveError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = stringResource(R.string.lec_assess_confirm_primary),
            enabled = !isSaving,
            loading = isSaving,
            onClick = onConfirm,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        SecondaryButton(
            text = stringResource(R.string.lec_assess_confirm_secondary),
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
    val score = detail.score?.toString() ?: stringResource(R.string.lec_assess_no_value)
    val assessed = detail.assessedAt?.let { formatDeadline(it) } ?: detail.assessedAt

    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(R.string.lec_assess_locked_tintbar),
            tone = TintTone.Teal,
            uppercase = true,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Eyebrow(stringResource(R.string.lec_assess_locked_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_assess_locked_headline),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = detail.studentName, style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Eyebrow(
                text = stringResource(R.string.lec_assess_objective_label),
                color = EduTheme.colors.onBrand.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.lec_assess_locked_score_out_of, score),
                style = EduTheme.type.numeralLarge.copy(color = EduTheme.colors.onBrand),
            )
            val savedComment = detail.comment
            if (!savedComment.isNullOrBlank()) {
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                Text(
                    text = savedComment,
                    style = EduTheme.type.bodySmall.copy(
                        color = EduTheme.colors.onBrand.copy(alpha = 0.9f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.lec_assess_locked_case_title),
                style = EduTheme.type.cardTitle,
            )
            if (assessed != null) {
                Spacer(Modifier.height(dimen(R.dimen.space_6)))
                Text(
                    text = stringResource(R.string.lec_assess_assessed_at, assessed),
                    style = EduTheme.type.bodySmall,
                )
            }
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.lec_assess_result_visible),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.success),
            )
        }

        if (answerOpen) {
            Spacer(Modifier.height(dimen(R.dimen.space_16)))
            val submittedLabel = formatDeadline(detail.submittedAt)
            ContentCard(
                title = stringResource(R.string.lec_assess_answer_title),
                body = detail.answerText,
                meta = if (submittedLabel == null) {
                    null
                } else {
                    stringResource(R.string.lec_assess_submitted_at, submittedLabel)
                },
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.lec_assess_locked_notice),
            tone = TintTone.Primary,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        SecondaryButton(
            text = stringResource(
                if (answerOpen) {
                    R.string.lec_assess_locked_toggle_close
                } else {
                    R.string.lec_assess_locked_toggle_open
                },
            ),
            onClick = onToggleAnswer,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        PrimaryButton(
            text = stringResource(R.string.lec_assess_back_button),
            onClick = onBack,
        )
    }
}
