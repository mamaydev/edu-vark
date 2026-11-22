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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.EmptyBlock
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.components.TopicCard
import com.eduvark.app.ui.theme.EduText

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
    viewModel: LecturerHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.topics.isEmpty() && !state.isLoading) viewModel.load()
    }

    val title = when (mode) {
        LecturerHubMode.GRADING -> "Penilaian"
        LecturerHubMode.ANALYTICS -> "Analitik"
    }
    val headline = when (mode) {
        LecturerHubMode.GRADING -> "Pilih topik untuk\ndinilai."
        LecturerHubMode.ANALYTICS -> "Pilih topik untuk\ndianalisis."
    }
    val body = when (mode) {
        LecturerHubMode.GRADING ->
            "Buka pengumpulan studi kasus pada topik yang dipilih, lalu beri nilai final."

        LecturerHubMode.ANALYTICS ->
            "Buka topik untuk melihat feedback pemahaman V/A/R dan nilai Kinesthetic."
    }

    EduScreen(title = title, onBack = onBack, modifier = modifier, bottomBar = bottomBar) { padding ->
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            state.error != null -> Column(Modifier.padding(padding).padding(24.dp)) {
                ErrorBlock(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                Eyebrow("Implementasi Basis Data")
                Spacer(Modifier.height(10.dp))
                Text(text = headline, style = EduText.Display)
                Spacer(Modifier.height(12.dp))
                Text(text = body, style = EduText.Body)
                Spacer(Modifier.height(18.dp))

                if (state.topics.isEmpty()) {
                    EmptyBlock(
                        title = "Belum ada topik",
                        message = "Topik yang diterbitkan dosen akan muncul di sini.",
                        actionLabel = "Muat ulang",
                        onAction = viewModel::load,
                    )
                } else {
                    state.topics.forEach { item ->
                        val hasCase = item.caseId != null
                        val card = when (mode) {
                            LecturerHubMode.GRADING -> {
                                val count = item.submissionCount
                                when {
                                    !hasCase -> "Belum ada studi kasus" to TintTone.Neutral
                                    count != null -> "$count pengumpulan" to TintTone.Teal
                                    else -> "Buka pengumpulan" to TintTone.Teal
                                }
                            }

                            LecturerHubMode.ANALYTICS ->
                                "Buka analitik" to TintTone.Teal
                        }
                        val enabled = mode == LecturerHubMode.ANALYTICS || hasCase
                        TopicCard(
                            orderLabel = "Topik ${item.topic.sortOrder.toString().padStart(2, '0')}  /  " +
                                "Implementasi Basis Data",
                            title = item.topic.title,
                            meta = when (mode) {
                                LecturerHubMode.GRADING ->
                                    if (hasCase) {
                                        "Studi kasus Kinesthetic • nilai final"
                                    } else {
                                        "Tambahkan studi kasus dari tab Topik"
                                    }

                                LecturerHubMode.ANALYTICS ->
                                    "Feedback V/A/R dan nilai Kinesthetic"
                            },
                            status = card.first,
                            statusTone = card.second,
                            onClick = if (!enabled) {
                                null
                            } else {
                                {
                                    when (mode) {
                                        LecturerHubMode.GRADING ->
                                            item.caseId?.let(onOpenSubmissions)

                                        LecturerHubMode.ANALYTICS -> onOpenAnalytics(item.topic.id)
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}
