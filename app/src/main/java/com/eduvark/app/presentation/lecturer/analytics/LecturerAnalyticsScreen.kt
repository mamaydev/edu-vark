package com.eduvark.app.presentation.lecturer.analytics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.StudentFeedback
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.MetricBar
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintTone
import java.util.Locale
import kotlin.math.roundToInt
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

/**
 * Analitik pemahaman kelas (mockups 29, 30 and 31).
 *
 * Feed per kategori dihitung dari nilai feedback tersimpan pada kategori asal
 * masing-masing, bukan dari satu label VARK mahasiswa. Nilai 0% tetap dihitung
 * sebagai respons; mahasiswa tanpa feedback (`null`) ditampilkan "belum
 * merespons". Metrik Kinesthetic berasal dari endpoint yang sama tetapi
 * ditampilkan terpisah dari feedback subjektif V/A/R.
 */
@Composable
fun LecturerAnalyticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }

    val stats = state.categoryStats
    val hasFeedback = state.hasFeedback
    val kinesthetic = state.analytics?.kinesthetic

    EduScreen(
        title = if (feedbackOpen) {
            stringResource(R.string.lec_analytics_screen_feedback_title)
        } else {
            stringResource(R.string.lec_analytics_screen_title)
        },
        onBack = { if (feedbackOpen) feedbackOpen = false else onBack() },
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
                ErrorBlock(message = error, onRetry = { viewModel.load() })
            }

            state.analytics == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                EmptyBlock(
                    title = stringResource(R.string.lec_analytics_empty_unavailable_title),
                    message = stringResource(R.string.lec_analytics_empty_unavailable_message),
                    actionLabel = stringResource(R.string.lec_analytics_action_reload),
                    onAction = { viewModel.load() },
                )
            }

            feedbackOpen -> FeedbackList(
                students = state.visibleStudents,
                totalStudents = state.totalStudentCount,
                selectedCategory = state.selectedCategory,
                respondentCount = state.respondentCount,
                aggregation = state.aggregationDefinition,
                onCategoryChange = viewModel::selectCategory,
                modifier = Modifier.padding(padding),
            )

            !hasFeedback -> EmptyAnalytics(
                respondentCount = state.respondentCount,
                aggregation = state.aggregationDefinition,
                kinestheticMean = kinesthetic?.meanScore,
                assessedCount = kinesthetic?.assessedCount ?: 0,
                onPickTopic = onBack,
                modifier = Modifier.padding(padding),
            )

            else -> AnalyticsMain(
                stats = stats,
                respondentCount = state.respondentCount,
                aggregation = state.aggregationDefinition,
                kinestheticMean = kinesthetic?.meanScore,
                assessedCount = kinesthetic?.assessedCount ?: 0,
                onOpenFeedback = { feedbackOpen = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AnalyticsMain(
    stats: List<LecturerCategoryStat>,
    respondentCount: Int,
    aggregation: String?,
    kinestheticMean: Double?,
    assessedCount: Int,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_analytics_intro_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_intro_title),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_intro_body),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        FieldLabel(stringResource(R.string.lec_analytics_topic_label))
        EduDropdown(
            value = stringResource(R.string.lec_analytics_current_topic),
            options = listOf(stringResource(R.string.lec_analytics_current_topic)),
            onSelect = {},
            enabled = false,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.lec_analytics_feedback_understanding_title),
                style = EduTheme.type.section,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            Eyebrow(stringResource(R.string.lec_analytics_feedback_understanding_eyebrow))
            Spacer(Modifier.height(dimen(R.dimen.space_14)))

            for (stat in stats) {
                val color = categoryColor(stat.category)
                val mean = stat.mean
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(subjectLabel(stat.category)),
                        style = EduTheme.type.label,
                        modifier = Modifier.width(dimen(R.dimen.size_78)),
                    )
                    Text(
                        text = if (mean == null) {
                            stringResource(R.string.lec_analytics_no_value)
                        } else {
                            stringResource(R.string.lec_analytics_percent, mean.roundToInt())
                        },
                        style = EduTheme.type.bodySmall.copy(
                            color = EduTheme.colors.ink,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.width(dimen(R.dimen.space_8)))
                    Text(
                        text = stringResource(R.string.lec_analytics_sample_size, stat.respondents),
                        style = EduTheme.type.bodySmall,
                    )
                }
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                MetricBar(
                    fraction = ((stat.mean ?: 0.0) / 100.0).toFloat(),
                    color = color,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_14)))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.lec_analytics_axis_zero),
                    style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.mutedSoft),
                )
                Text(
                    text = stringResource(R.string.lec_analytics_axis_full),
                    style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.mutedSoft),
                )
            }
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.p4_analytics_respondent_count, respondentCount),
                style = EduTheme.type.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
            if (aggregation != null) {
                Spacer(Modifier.height(dimen(R.dimen.space_6)))
                Text(
                    text = stringResource(
                        R.string.p4_analytics_aggregation_definition,
                        aggregation,
                    ),
                    style = EduTheme.type.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.p4_analytics_zero_vs_null_note),
                style = EduTheme.type.bodySmall,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            TextLink(
                text = stringResource(R.string.lec_analytics_feedback_link),
                onClick = onOpenFeedback,
            )
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        KinestheticCard(
            title = stringResource(R.string.lec_analytics_kinesthetic_title),
            mean = kinestheticMean,
            assessedCount = assessedCount,
            compact = false,
        )
    }
}

@Composable
private fun EmptyAnalytics(
    respondentCount: Int,
    aggregation: String?,
    kinestheticMean: Double?,
    assessedCount: Int,
    onPickTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_analytics_screen_title))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_empty_title),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_empty_body),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        FieldLabel(stringResource(R.string.lec_analytics_topic_label))
        EduDropdown(
            value = stringResource(R.string.lec_analytics_current_topic),
            options = listOf(stringResource(R.string.lec_analytics_current_topic)),
            onSelect = {},
            enabled = false,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduTheme.colors.purpleTint,
        ) {
            Text(
                text = stringResource(R.string.lec_analytics_no_percent),
                style = EduTheme.type.numeral.copy(color = EduTheme.colors.lavender),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = stringResource(R.string.lec_analytics_respondents_count, respondentCount),
                style = EduTheme.type.cardTitle,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(
                text = stringResource(R.string.lec_analytics_no_feedback_message),
                style = EduTheme.type.bodySmall,
            )
            if (aggregation != null) {
                Text(
                    text = stringResource(
                        R.string.p4_analytics_aggregation_definition,
                        aggregation,
                    ),
                    style = EduTheme.type.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        KinestheticCard(
            title = stringResource(R.string.lec_analytics_kinesthetic_score_title),
            mean = kinestheticMean,
            assessedCount = assessedCount,
            compact = true,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        SecondaryButton(
            text = stringResource(R.string.lec_analytics_pick_other_topic),
            onClick = onPickTopic,
        )
    }
}

@Composable
private fun KinestheticCard(
    title: String,
    mean: Double?,
    assessedCount: Int,
    compact: Boolean,
) {
    val formattedScore = mean?.let { String.format(Locale.forLanguageTag("id-ID"), "%.1f", it) }
    val score = formattedScore ?: stringResource(R.string.lec_analytics_no_value)
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduTheme.colors.successTint,
    ) {
        if (compact) {
            Text(
                text = title,
                style = EduTheme.type.cardTitle.copy(color = EduTheme.colors.lime),
            )
        } else {
            Eyebrow(
                text = stringResource(R.string.lec_analytics_objective_eyebrow, title),
                color = EduTheme.colors.lime,
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Text(
            text = stringResource(R.string.lec_analytics_score_out_of, score),
            style = EduTheme.type.numeral.copy(color = EduTheme.colors.lime),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Text(
            text = if (assessedCount == 0) {
                stringResource(R.string.lec_analytics_no_assessed)
            } else {
                stringResource(R.string.lec_analytics_assessed_mean, assessedCount)
            },
            style = EduTheme.type.bodySmall,
        )
        Text(
            text = stringResource(R.string.p4_analytics_kinesthetic_final_count, assessedCount),
            style = EduTheme.type.bodySmall,
        )
        if (!compact) {
            Text(
                text = stringResource(R.string.lec_analytics_not_combined),
                style = EduTheme.type.bodySmall,
            )
        }
    }
}

@Composable
private fun FeedbackList(
    students: List<StudentFeedback>,
    totalStudents: Int,
    selectedCategory: VarkType?,
    respondentCount: Int,
    aggregation: String?,
    onCategoryChange: (VarkType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.lec_analytics_feedback_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_feedback_title),
            style = EduTheme.type.display,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = stringResource(R.string.lec_analytics_students_count, totalStudents),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))

        FieldLabel(stringResource(R.string.lec_analytics_category_label))
        val allCategoryLabel = stringResource(R.string.lec_analytics_filter_all)
        val visualLabel = stringResource(R.string.lec_analytics_vark_visual)
        val auralLabel = stringResource(R.string.lec_analytics_vark_aural)
        val readWriteLabel = stringResource(R.string.lec_analytics_vark_read_write)
        EduDropdown(
            value = if (selectedCategory == null) {
                allCategoryLabel
            } else {
                stringResource(subjectLabel(selectedCategory))
            },
            options = listOf(allCategoryLabel, visualLabel, auralLabel, readWriteLabel),
            onSelect = { label ->
                onCategoryChange(
                    when (label) {
                        visualLabel -> VarkType.VISUAL
                        auralLabel -> VarkType.AURAL
                        readWriteLabel -> VarkType.READ_WRITE
                        else -> null
                    },
                )
            },
        )
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        NoticeCard(
            text = stringResource(R.string.lec_analytics_category_notice),
            tone = TintTone.Primary,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.lec_analytics_respondents_count, respondentCount),
                style = EduTheme.type.numeral.copy(color = EduTheme.colors.lavender),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.lec_analytics_respondent_note),
                style = EduTheme.type.bodySmall,
            )
            if (aggregation != null) {
                Text(
                    text = stringResource(
                        R.string.p4_analytics_aggregation_definition,
                        aggregation,
                    ),
                    style = EduTheme.type.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.p4_analytics_zero_vs_null_note),
                style = EduTheme.type.bodySmall,
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))

        if (students.isEmpty()) {
            NoticeCard(
                text = stringResource(R.string.lec_analytics_no_students_notice),
                tone = TintTone.Neutral,
            )
        } else {
            for (student in students) {
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = student.fullName, style = EduTheme.type.cardTitle)
                    Spacer(Modifier.height(dimen(R.dimen.space_6)))
                    val originLabels = mutableListOf<String>()
                    for (category in student.originCategories) {
                        originLabels += stringResource(subjectLabel(category))
                    }
                    val originLabel = if (originLabels.isEmpty()) {
                        stringResource(R.string.p4_analytics_no_origin_categories)
                    } else {
                        originLabels.joinToString(", ")
                    }
                    Text(
                        text = stringResource(
                            R.string.lec_analytics_student_meta,
                            student.loginId,
                            originLabel,
                        ),
                        style = EduTheme.type.bodySmall,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    if (selectedCategory == null) {
                        if (student.originCategories.isEmpty()) {
                            Text(
                                text = stringResource(R.string.lec_analytics_no_respond),
                                style = EduTheme.type.headline.copy(color = EduTheme.colors.muted),
                            )
                        } else {
                            for (category in student.originCategories) {
                                val value = student.valueFor(category)
                                Text(
                                    text = if (value == null) {
                                        stringResource(
                                            R.string.p4_analytics_origin_value_empty,
                                            stringResource(subjectLabel(category)),
                                        )
                                    } else {
                                        stringResource(
                                            R.string.p4_analytics_origin_value,
                                            stringResource(subjectLabel(category)),
                                            value,
                                        )
                                    },
                                    style = EduTheme.type.numeral.copy(
                                        color = EduTheme.colors.lavender,
                                    ),
                                )
                            }
                        }
                    } else {
                        val value = student.valueFor(selectedCategory)
                        if (value == null) {
                            Text(
                                text = stringResource(R.string.lec_analytics_no_respond),
                                style = EduTheme.type.headline.copy(color = EduTheme.colors.muted),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.lec_analytics_percent, value),
                                style = EduTheme.type.numeral.copy(
                                    color = EduTheme.colors.lavender,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_4)))
        Text(
            text = stringResource(R.string.lec_analytics_footer_note),
            style = EduTheme.type.bodySmall,
        )
    }
}

@StringRes
private fun subjectLabel(vark: VarkType): Int = when (vark) {
    VarkType.VISUAL -> R.string.lec_analytics_vark_visual
    VarkType.AURAL -> R.string.lec_analytics_vark_aural
    VarkType.READ_WRITE -> R.string.lec_analytics_vark_read_write
    VarkType.KINESTHETIC -> R.string.lec_analytics_vark_kinesthetic
}

@Composable
private fun categoryColor(vark: VarkType) = when (vark) {
    VarkType.VISUAL -> EduTheme.colors.lavender
    VarkType.AURAL -> EduTheme.colors.lime
    VarkType.READ_WRITE -> EduTheme.colors.danger
    VarkType.KINESTHETIC -> EduTheme.colors.purple
}
