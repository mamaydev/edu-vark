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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.core.text.P5TextRules
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialOpenEvent
import com.eduvark.app.domain.model.MaterialOpenFailure
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.presentation.student.materials.materialOpenFailureText
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
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TonePanel
import com.eduvark.app.ui.format.formatDateTime
import com.eduvark.app.ui.format.formatBytes
import com.eduvark.app.ui.format.formatDeadline
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

@Composable
fun StudentCaseScreen(
    onBack: () -> Unit,
    onOpenTopics: () -> Unit,
    onSessionExpired: () -> Unit,
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

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onSessionExpired()
        }
    }

    // FR-015: FILE diunduh lewat alamat tetap terautentikasi; LINK memakai tujuan
    // yang baru diperiksa izinnya. Tidak ada URL storage langsung di klien.
    LaunchedEffect(state.openEvent) {
        val event = state.openEvent ?: return@LaunchedEffect
        try {
            when (event) {
                is MaterialOpenEvent.OpenFile -> context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(event.contentUri), event.mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )

                is MaterialOpenEvent.OpenExternal -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(event.url)),
                )
            }
        } catch (error: ActivityNotFoundException) {
            viewModel.onOpenDispatchFailed()
        } finally {
            viewModel.onOpenEventConsumed()
        }
    }

    val openMaterial: (Material) -> Unit = viewModel::openMaterial
    val status = caseStudy?.status
    val screenTitle = when (status) {
        SubmissionStatus.MENUNGGU_PENILAIAN, SubmissionStatus.TIDAK_MENGUMPULKAN ->
            stringResource(R.string.st_case_title_status)

        SubmissionStatus.SUDAH_DINILAI -> stringResource(R.string.st_case_title_result)
        else -> if (confirming) {
            stringResource(R.string.st_case_title_confirm)
        } else {
            stringResource(R.string.st_case_title_default)
        }
    }

    EduScreen(
        title = screenTitle,
        onBack = { if (confirming) confirming = false else onBack() },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            state.error != null -> Column(Modifier.padding(padding).padding(dimen(R.dimen.space_24))) {
                ErrorBlock(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            caseStudy == null -> Column(Modifier.padding(padding).padding(dimen(R.dimen.space_24))) {
                NoticeCard(
                    text = stringResource(R.string.st_case_not_available),
                    tone = TintTone.Amber,
                )
            }

            status == SubmissionStatus.SUDAH_DINILAI -> GradedCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                topicOrder = state.topicOrder,
                onOpenHome = onOpenHome,
                modifier = Modifier.padding(padding),
            )

            status == SubmissionStatus.MENUNGGU_PENILAIAN -> WaitingCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                topicOrder = state.topicOrder,
                onOpenHome = onOpenHome,
                modifier = Modifier.padding(padding),
            )

            status == SubmissionStatus.TIDAK_MENGUMPULKAN -> ClosedCase(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                topicOrder = state.topicOrder,
                onOpenTopics = onOpenTopics,
                modifier = Modifier.padding(padding),
            )

            confirming -> ConfirmSubmission(
                caseStudy = caseStudy,
                topicTitle = state.topicTitle.orEmpty(),
                topicOrder = state.topicOrder,
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
                topicOrder = state.topicOrder,
                materials = state.materials,
                materialsFailed = state.materialsFailed,
                openFailure = state.openFailure,
                onReloadMaterials = viewModel::reloadMaterials,
                onOpenMaterial = openMaterial,
                answer = answer,
                // Jangan blokir diam-diam: biarkan pengguna melihat counter
                // melewati batas, validasi tetap di VM + API (C1-04).
                onAnswerChange = { answer = it },
                submitError = state.submitError,
                onNext = { confirming = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun topicLabel(order: Int?): String = stringResource(
    R.string.st_case_topic_label,
    order?.let { it.toString().padStart(2, '0') } ?: "—",
)

@Composable
private fun AnswerEditor(
    caseStudy: CaseStudy,
    topicTitle: String,
    topicOrder: Int?,
    materials: List<Material>,
    materialsFailed: Boolean,
    openFailure: MaterialOpenFailure?,
    onReloadMaterials: () -> Unit,
    onOpenMaterial: (Material) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
    submitError: String?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicFallback = stringResource(R.string.st_case_topic_default)
    val answerLength = P5TextRules.answerLength(answer)
    val answerTooLong = answerLength > P5TextRules.MAX_ANSWER_CODE_POINTS
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(text = topicLabel(topicOrder), uppercase = true)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        Text(text = topicTitle.ifBlank { topicFallback }, style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        DeadlinePanel(caseStudy)
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        if (openFailure != null) {
            NoticeCard(
                title = stringResource(R.string.p2_material_open_error_title),
                text = stringResource(materialOpenFailureText(openFailure)),
                tone = TintTone.Danger,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_16)))
        }

        when {
            // C1-03: error muat materi != materi belum tersedia.
            materialsFailed -> {
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.st_case_supporting_title),
                        style = EduTheme.type.cardTitle,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_6)))
                    NoticeCard(
                        text = stringResource(R.string.p5_case_materials_error),
                        tone = TintTone.Danger,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_8)))
                    TextLink(
                        text = stringResource(R.string.p5_case_materials_retry),
                        onClick = onReloadMaterials,
                        color = EduTheme.colors.lavenderDim,
                    )
                }
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
            }

            // FR-016: materi pendukung kosong tetap menampilkan studi kasus,
            // dengan pesan persis sesuai SRS.
            materials.isEmpty() -> {
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.st_case_supporting_title),
                        style = EduTheme.type.cardTitle,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_6)))
                    Text(
                        text = stringResource(R.string.p5_case_supporting_empty),
                        style = EduTheme.type.body,
                    )
                }
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
            }

            else -> {
                SupportingMaterials(materials = materials, onOpen = onOpenMaterial)
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
            }
        }

        EduCard(modifier = Modifier.fillMaxWidth()) {
            if (!caseStudy.theory.isNullOrBlank()) {
                Text(text = stringResource(R.string.st_case_theory), style = EduTheme.type.cardTitle)
                Spacer(Modifier.height(dimen(R.dimen.space_6)))
                Text(text = caseStudy.theory, style = EduTheme.type.body)
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
            }
            Text(text = stringResource(R.string.st_case_instructions), style = EduTheme.type.cardTitle)
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(text = caseStudy.instructions, style = EduTheme.type.body)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.st_case_answer_title), style = EduTheme.type.cardTitle)
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(text = stringResource(R.string.st_case_answer_meta), style = EduTheme.type.meta)
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            EduField(
                value = answer,
                onValueChange = onAnswerChange,
                placeholder = stringResource(R.string.st_case_answer_placeholder),
                singleLine = false,
                minHeight = dimen(R.dimen.dimen_150),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_case_answer_counter, answerLength),
                style = EduTheme.type.meta,
            )
            if (answerTooLong) {
                Spacer(Modifier.height(dimen(R.dimen.space_6)))
                Text(
                    text = stringResource(R.string.p5_case_answer_too_long),
                    style = EduTheme.type.meta.copy(color = EduTheme.colors.dangerMuted),
                )
            }
        }

        if (submitError != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_14)))
            NoticeCard(text = submitError, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.st_case_no_draft_notice),
            tone = TintTone.Primary,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = stringResource(R.string.st_case_submit),
            onClick = onNext,
            enabled = answerLength in 1..P5TextRules.MAX_ANSWER_CODE_POINTS,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(text = stringResource(R.string.st_case_answer_hint), style = EduTheme.type.bodySmall)
    }
}

@Composable
private fun SupportingMaterials(
    materials: List<Material>,
    onOpen: (Material) -> Unit,
) {
    EduCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.st_case_supporting_title), style = EduTheme.type.cardTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_4)))
        Text(
            text = stringResource(R.string.st_case_supporting_body),
            style = EduTheme.type.meta,
        )
        materials.forEach { material ->
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EduTheme.colors.purpleTint, RoundedCornerShape(dimen(R.dimen.radius_12)))
                    .clickable { onOpen(material) }
                    .padding(horizontal = dimen(R.dimen.radius_14), vertical = dimen(R.dimen.radius_12)),
            ) {
                ArrowTitle(text = material.title, color = EduTheme.colors.lavender)
                Spacer(Modifier.height(dimen(R.dimen.space_4)))
                Text(
                    text = listOfNotNull(
                        material.kind.label,
                        material.mime,
                        formatBytes(material.sizeBytes),
                    ).joinToString(" • "),
                    style = EduTheme.type.meta,
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
            .background(EduTheme.colors.amberTint, RoundedCornerShape(dimen(R.dimen.radius_16)))
            .padding(horizontal = dimen(R.dimen.radius_16), vertical = dimen(R.dimen.radius_14)),
    ) {
        Text(
            text = stringResource(
                R.string.st_case_deadline,
                formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib,
            ),
            style = EduTheme.type.bodySmall.copy(
                color = EduTheme.colors.amber,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_4)))
        Text(
            text = stringResource(R.string.st_case_deadline_note),
            style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.amber),
        )
    }
}

@Composable
private fun ConfirmSubmission(
    caseStudy: CaseStudy,
    topicTitle: String,
    topicOrder: Int?,
    answer: String,
    isSubmitting: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicFallback = stringResource(R.string.st_case_topic_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.st_case_confirm_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.st_case_confirm_title), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(text = stringResource(R.string.st_case_confirm_body), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_20)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow(topicLabel(topicOrder))
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(text = topicTitle.ifBlank { topicFallback }, style = EduTheme.type.headline)
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.st_case_confirm_ready, P5TextRules.answerLength(answer)),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.lime, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(
                    R.string.st_case_deadline,
                    formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib,
                ),
                style = EduTheme.type.meta,
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        TonePanel(
            title = stringResource(R.string.st_case_confirm_final_title),
            body = "",
            tone = TintTone.Amber,
            bullets = listOf(
                stringResource(R.string.st_case_confirm_bullet_1),
                stringResource(R.string.st_case_confirm_bullet_2),
                stringResource(R.string.st_case_confirm_bullet_3),
                stringResource(R.string.st_case_confirm_bullet_4),
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_14)))
            NoticeCard(text = error, tone = TintTone.Danger)
        }

        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(
            text = stringResource(R.string.st_case_confirm_submit),
            onClick = onConfirm,
            loading = isSubmitting,
            enabled = !isSubmitting,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        SecondaryButton(text = stringResource(R.string.st_case_confirm_cancel), onClick = onCancel)
        Spacer(Modifier.height(dimen(R.dimen.space_14)))
        Text(
            text = stringResource(R.string.st_case_confirm_footnote),
            style = EduTheme.type.bodySmall,
        )
    }
}

@Composable
private fun WaitingCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    topicOrder: Int?,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAnswer by rememberSaveable { mutableStateOf(false) }
    val topicFallback = stringResource(R.string.st_case_topic_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        SuccessPanel()
        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        Eyebrow(stringResource(R.string.st_case_waiting_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.st_case_waiting_title), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(text = stringResource(R.string.st_case_waiting_body), style = EduTheme.type.body)
        Spacer(Modifier.height(dimen(R.dimen.space_20)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow(topicLabel(topicOrder))
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(text = topicTitle.ifBlank { topicFallback }, style = EduTheme.type.headline)
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.st_case_waiting_status),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.lime, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(
                    R.string.st_case_deadline,
                    formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib,
                ),
                style = EduTheme.type.meta,
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.st_case_waiting_submitted), style = EduTheme.type.meta)
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = formatDateTime(caseStudy.submittedAt) ?: caseStudy.submittedAt.orEmpty(),
                style = EduTheme.type.bodyStrong,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.st_case_waiting_answer_meta, P5TextRules.answerLength(caseStudy.answer.orEmpty())),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.lime),
            )
            if (!caseStudy.answer.isNullOrBlank()) {
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                TextLink(
                    text = stringResource(R.string.st_case_waiting_view),
                    onClick = { showAnswer = !showAnswer },
                    color = EduTheme.colors.lime,
                )
                if (showAnswer) {
                    Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    Text(text = caseStudy.answer.orEmpty(), style = EduTheme.type.body)
                }
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.st_case_waiting_notice),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(text = stringResource(R.string.st_case_back_home), onClick = onOpenHome)
    }
}

@Composable
private fun GradedCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    topicOrder: Int?,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAnswer by rememberSaveable { mutableStateOf(false) }
    val topicFallback = stringResource(R.string.st_case_topic_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(R.string.st_case_graded_tintbar),
            tone = TintTone.Teal,
            uppercase = true,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        Text(text = stringResource(R.string.st_case_graded_title), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.st_case_graded_eyebrow),
                style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.st_case_graded_score, caseStudy.score ?: 0),
                style = EduTheme.type.numeralLarge.copy(fontSize = dimenSp(R.dimen.text_size_46), lineHeight = dimenSp(R.dimen.text_size_52)),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(
                    R.string.st_case_graded_meta,
                    topicTitle.ifBlank { topicFallback },
                ),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.92f)),
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = caseStudy.gradedBy?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.st_case_graded_by_default),
                style = EduTheme.type.cardTitle,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = caseStudy.comment?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.st_case_graded_comment_default),
                style = EduTheme.type.body,
            )
            val gradedAt = formatDeadline(caseStudy.gradedAt)
            if (gradedAt != null) {
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.st_case_graded_at, gradedAt),
                    style = EduTheme.type.meta,
                )
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_14)))
        Text(
            text = stringResource(R.string.st_case_graded_note),
            style = EduTheme.type.bodySmall,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        if (!caseStudy.answer.isNullOrBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            TextLink(
                text = stringResource(R.string.st_case_graded_view),
                onClick = { showAnswer = !showAnswer },
                color = EduTheme.colors.lime,
            )
            if (showAnswer) {
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = caseStudy.answer.orEmpty(), style = EduTheme.type.body)
                }
            }
        }
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(text = stringResource(R.string.st_case_back_home), onClick = onOpenHome)
    }
}

@Composable
private fun ClosedCase(
    caseStudy: CaseStudy,
    topicTitle: String,
    topicOrder: Int?,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicFallback = stringResource(R.string.st_case_topic_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(R.string.st_case_closed_tintbar),
            tone = TintTone.Danger,
            uppercase = true,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Eyebrow(stringResource(R.string.st_case_closed_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.st_case_closed_title), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(
            text = stringResource(R.string.st_case_closed_body),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            CardEyebrow(topicLabel(topicOrder))
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(text = topicTitle.ifBlank { topicFallback }, style = EduTheme.type.headline)
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.st_case_closed_status),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.lime, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(
                    R.string.st_case_deadline,
                    formatDeadline(caseStudy.deadlineAt) ?: caseStudy.deadlineWib,
                ),
                style = EduTheme.type.meta,
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EduTheme.colors.amberTint, RoundedCornerShape(dimen(R.dimen.radius_18)))
                .padding(dimen(R.dimen.radius_18)),
        ) {
            Text(
                text = stringResource(R.string.st_case_closed_score),
                style = EduTheme.type.numeral.copy(
                    color = EduTheme.colors.amber,
                    fontSize = dimenSp(R.dimen.text_size_38),
                    lineHeight = dimenSp(R.dimen.text_size_44),
                ),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_case_closed_score_note),
                style = EduTheme.type.bodyStrong.copy(color = EduTheme.colors.amber, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.st_case_closed_score_body),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.amber),
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.st_case_closed_notice),
            tone = TintTone.Danger,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(text = stringResource(R.string.st_case_closed_back), onClick = onOpenTopics)
    }
}
