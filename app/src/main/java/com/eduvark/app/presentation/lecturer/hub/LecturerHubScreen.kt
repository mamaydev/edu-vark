package com.eduvark.app.presentation.lecturer.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.ui.components.EduPullToRefresh
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TopicCard
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

/** Which flow the shared lecturer hub is showing. */
enum class LecturerHubMode { GRADING, ANALYTICS }

@Composable
fun LecturerHubScreen(
    mode: LecturerHubMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    onOpenSubmissions: (String) -> Unit = {},
    onOpenAnalytics: (String) -> Unit = {},
    onOpenCase: (String) -> Unit = {},
    viewModel: LecturerHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.topics.isEmpty() && !state.isLoading) viewModel.load()
    }

    val title = when (mode) {
        LecturerHubMode.GRADING -> stringResource(R.string.lec_hub_title_grading)
        LecturerHubMode.ANALYTICS -> stringResource(R.string.lec_hub_title_analytics)
    }
    val headline = when (mode) {
        LecturerHubMode.GRADING -> stringResource(R.string.lec_hub_headline_grading)
        LecturerHubMode.ANALYTICS -> stringResource(R.string.lec_hub_headline_analytics)
    }
    val body = when (mode) {
        LecturerHubMode.GRADING -> stringResource(R.string.lec_hub_body_grading)
        LecturerHubMode.ANALYTICS -> stringResource(R.string.lec_hub_body_analytics)
    }
    val courseName = stringResource(R.string.lec_hub_eyebrow)

    EduScreen(title = title, onBack = onBack, modifier = modifier, bottomBar = bottomBar) { padding ->
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            state.error != null -> Column(Modifier.padding(padding).padding(dimen(R.dimen.space_24))) {
                ErrorBlock(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            else -> EduPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding),
            ) {
                ScrollColumn(spacing = dimen(R.dimen.space_0)) {
                if (state.refreshError != null) {
                    NoticeCard(text = state.refreshError.orEmpty(), tone = TintTone.Danger)
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                }
                Eyebrow(courseName)
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(text = headline, style = EduTheme.type.display)
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                Text(text = body, style = EduTheme.type.body)
                Spacer(Modifier.height(dimen(R.dimen.space_18)))

                if (state.topics.isEmpty()) {
                    EmptyBlock(
                        title = stringResource(R.string.lec_hub_empty_title),
                        message = stringResource(R.string.lec_hub_empty_body),
                        actionLabel = stringResource(R.string.lec_hub_empty_action),
                        onAction = viewModel::load,
                    )
                } else {
                    state.topics.forEach { item ->
                        val hasCase = item.caseId != null
                        val card = when (mode) {
                            LecturerHubMode.GRADING -> {
                                val count = item.submissionCount
                                when {
                                    !hasCase -> stringResource(R.string.lec_hub_status_create_case) to TintTone.Neutral
                                    count != null && count > 0 -> stringResource(
                                        R.string.lec_hub_status_submissions,
                                        count,
                                    ) to TintTone.Teal
                                    else -> stringResource(R.string.lec_hub_status_no_submissions) to TintTone.Neutral
                                }
                            }

                            LecturerHubMode.ANALYTICS ->
                                stringResource(R.string.lec_hub_status_analytics) to TintTone.Teal
                        }
                        TopicCard(
                            orderLabel = stringResource(
                                R.string.lec_hub_order,
                                item.topic.sortOrder.toString().padStart(2, '0'),
                                courseName,
                            ),
                            title = item.topic.title,
                            meta = when (mode) {
                                LecturerHubMode.GRADING ->
                                    if (hasCase) {
                                        stringResource(R.string.lec_hub_meta_case)
                                    } else {
                                        stringResource(R.string.lec_hub_meta_no_case)
                                    }

                                LecturerHubMode.ANALYTICS ->
                                    stringResource(R.string.lec_hub_meta_analytics)
                            },
                            status = card.first,
                            statusTone = card.second,
                            onClick = {
                                when {
                                    mode == LecturerHubMode.ANALYTICS ->
                                        onOpenAnalytics(item.topic.id)

                                    item.caseId != null -> onOpenSubmissions(item.caseId)
                                    else -> onOpenCase(item.topic.id)
                                }
                            },
                        )
                        Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    }
                }
                }
            }
        }
    }
}
