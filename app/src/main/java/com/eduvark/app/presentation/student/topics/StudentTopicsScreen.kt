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
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import com.eduvark.app.ui.theme.EduVarkTheme

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
    val filtered = state.topics.filter { it.title.contains(query, ignoreCase = true) }

    EduScreen(
        title = "Topik pembelajaran",
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
                    .padding(24.dp),
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
                ScrollColumn(spacing = 0.dp) {
                if (state.refreshError != null) {
                    com.eduvark.app.ui.components.NoticeCard(
                        text = state.refreshError.orEmpty(),
                        tone = com.eduvark.app.ui.components.TintTone.Danger,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Eyebrow("Implementasi Basis Data")
                Spacer(Modifier.height(10.dp))
                Text(text = "Ruang belajarmu", style = EduText.Display)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${state.topics.size} topik terbit · urutan ditentukan dosen",
                    style = EduText.Body,
                )
                Spacer(Modifier.height(16.dp))
                TintBar(
                    text = if (activeVark != null) {
                        "${activeVark.label} aktif • Tidak dapat diganti"
                    } else {
                        "Format belajar belum dipilih"
                    },
                    tone = TintTone.Primary,
                )

                Spacer(Modifier.height(20.dp))
                FieldLabel("Cari topik")
                EduField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Cari judul topik…",
                    trailing = {
                        Icon(
                            imageVector = EduIcons.Search,
                            contentDescription = null,
                            tint = EduColors.Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                Spacer(Modifier.height(18.dp))
                if (filtered.isEmpty()) {
                    NoticeCard(
                        text = "Tidak ada topik yang cocok dengan \"$query\".",
                        tone = TintTone.Neutral,
                    )
                } else {
                    filtered.forEach { topic ->
                        TopicCard(
                            orderLabel = "Topik ${topic.sortOrder.toString().padStart(2, '0')}  /  " +
                                "Implementasi Basis Data",
                            title = topic.title,
                            meta = topic.description?.takeIf { it.isNotBlank() }
                                ?: if (kinesthetic) {
                                    "Jawaban teks • Satu kiriman final"
                                } else {
                                    "Format ${activeVark?.label ?: "VARK"}"
                                },
                            status = when {
                                kinesthetic && topic.materialAvailable -> "Buka studi kasus"
                                kinesthetic -> "Studi kasus belum tersedia"
                                topic.materialAvailable ->
                                    "Tersedia • ${activeVark?.label ?: "VARK"}"

                                else -> "Belum tersedia"
                            },
                            statusTone = if (topic.materialAvailable) {
                                TintTone.Teal
                            } else {
                                TintTone.Neutral
                            },
                            onClick = { onOpenTopic(topic.id, kinesthetic) },
                        )
                        Spacer(Modifier.height(12.dp))
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
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Implementasi Basis Data")
        Spacer(Modifier.height(10.dp))
        Text(text = "Ruang belajar\nsegera dimulai.", style = EduText.Display)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Belum ada topik yang diterbitkan dosen untuk kelas ini.",
            style = EduText.Body,
        )

        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            com.eduvark.app.ui.components.EduCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = EduColors.PrimaryTint,
            ) {
                Text(
                    text = "01 → 02 → 03",
                    style = EduText.Numeral.copy(color = EduColors.Primary, fontSize = 30.sp),
                )
                Spacer(Modifier.height(10.dp))
                Text(text = "Topik akan muncul di sini", style = EduText.CardTitle)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Materi ditampilkan sesuai format aktif dan urutan yang diatur dosen.",
                    style = EduText.BodySmall,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TintBar(
            text = if (activeVark != null) {
                "${activeVark.label} aktif • Sesi terkunci"
            } else {
                "Format belajar belum dipilih"
            },
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = "Muat ulang", onClick = onReload)
    }
}

@Preview(showBackground = true)
@Composable
private fun StudentTopicsPreview() {
    EduVarkTheme {
        StudentTopicsScreen(onBack = {}, onOpenTopic = { _, _ -> })
    }
}
