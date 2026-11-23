package com.eduvark.app.presentation.student.topics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduPullToRefresh
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TopicCard
import com.eduvark.app.ui.theme.EduVarkTheme
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

@Composable
fun StudentTopicsScreen(
    onBack: () -> Unit,
    onOpenTopic: (topicId: String, isKinesthetic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: StudentTopicsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val activeVark = state.activeVark
    val kinesthetic = activeVark == VarkType.KINESTHETIC
    val varkLabel = activeVark?.label ?: stringResource(R.string.home_vark_acronym)
    val filtered = state.topics.filter { it.title.contains(query, ignoreCase = true) }

    EduScreen(
        title = stringResource(R.string.st_topic_screen_title),
        onBack = onBack,
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        val error = state.error
        when {
            state.isLoading -> com.eduvark.app.ui.components.LoadingBlock(Modifier.padding(padding))

            error != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                com.eduvark.app.ui.components.ErrorBlock(
                    message = error,
                    onRetry = viewModel::load,
                )
            }

            state.topics.isEmpty() -> EmptyTopics(
                activeVark = activeVark,
                onReload = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> EduPullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(padding),
            ) {
                ScrollColumn(spacing = dimen(R.dimen.space_0)) {
                if (state.refreshError != null) {
                    com.eduvark.app.ui.components.NoticeCard(
                        text = state.refreshError.orEmpty(),
                        tone = com.eduvark.app.ui.components.TintTone.Danger,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_16)))
                }
                Eyebrow(stringResource(R.string.st_topic_class_name))
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(text = stringResource(R.string.st_topic_display), style = EduTheme.type.display)
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(
                    text = stringResource(R.string.st_topic_summary, state.topics.size),
                    style = EduTheme.type.body,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                TintBar(
                    text = if (activeVark != null) {
                        stringResource(R.string.st_topic_vark_locked, varkLabel)
                    } else {
                        stringResource(R.string.st_topic_vark_unset)
                    },
                    tone = TintTone.Primary,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_20)))
                FieldLabel(stringResource(R.string.st_topic_search_label))
                EduField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.st_topic_search_placeholder),
                    trailing = {
                        Icon(
                            imageVector = EduIcons.Search,
                            contentDescription = null,
                            tint = EduTheme.colors.muted,
                            modifier = Modifier.size(dimen(R.dimen.size_18)),
                        )
                    },
                )

                Spacer(Modifier.height(dimen(R.dimen.space_18)))
                if (filtered.isEmpty()) {
                    NoticeCard(
                        text = stringResource(R.string.st_topic_no_match, query),
                        tone = TintTone.Neutral,
                    )
                } else {
                    filtered.forEach { topic ->
                        TopicCard(
                            orderLabel = stringResource(
                                R.string.st_topic_order_label,
                                topic.sortOrder.toString().padStart(2, '0'),
                            ),
                            title = topic.title,
                            meta = topic.description?.takeIf { it.isNotBlank() }
                                ?: if (kinesthetic) {
                                    stringResource(R.string.st_topic_meta_kinesthetic)
                                } else {
                                    stringResource(R.string.st_topic_meta_format, varkLabel)
                                },
                            status = when {
                                kinesthetic && topic.materialAvailable ->
                                    stringResource(R.string.st_topic_status_open_case)

                                kinesthetic -> stringResource(R.string.st_topic_status_case_unavailable)
                                topic.materialAvailable ->
                                    stringResource(R.string.st_topic_status_available, varkLabel)

                                else -> stringResource(R.string.st_topic_status_unavailable)
                            },
                            statusTone = if (topic.materialAvailable) {
                                TintTone.Teal
                            } else {
                                TintTone.Neutral
                            },
                            onClick = { onOpenTopic(topic.id, kinesthetic) },
                        )
                        Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun EmptyTopics(
    activeVark: VarkType?,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        Eyebrow(stringResource(R.string.st_topic_class_name))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.st_topic_empty_display), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(
            text = stringResource(R.string.st_topic_empty_message),
            style = EduTheme.type.body,
        )

        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimen(R.dimen.space_8)),
        ) {
            com.eduvark.app.ui.components.EduCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = EduTheme.colors.purpleTint,
            ) {
                Text(
                    text = stringResource(R.string.st_topic_empty_steps),
                    style = EduTheme.type.numeral.copy(color = EduTheme.colors.lavender, fontSize = dimenSp(R.dimen.text_size_30)),
                )
                Spacer(Modifier.height(dimen(R.dimen.space_10)))
                Text(text = stringResource(R.string.st_topic_empty_panel_title), style = EduTheme.type.cardTitle)
                Spacer(Modifier.height(dimen(R.dimen.space_6)))
                Text(
                    text = stringResource(R.string.st_topic_empty_panel_body),
                    style = EduTheme.type.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        TintBar(
            text = if (activeVark != null) {
                stringResource(R.string.st_topic_empty_vark_locked, activeVark.label)
            } else {
                stringResource(R.string.st_topic_vark_unset)
            },
        )
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        PrimaryButton(text = stringResource(R.string.st_topic_reload), onClick = onReload)
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentTopicsPreview() {
    EduVarkTheme {
        StudentTopicsScreen(onBack = {}, onOpenTopic = { _, _ -> })
    }
}
