package com.eduvark.app.presentation.lecturer.analytics

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
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import java.util.Locale
import kotlin.math.roundToInt

private val FEEDBACK_CATEGORIES = listOf(
    VarkType.VISUAL,
    VarkType.AURAL,
    VarkType.READ_WRITE,
)

private const val FILTER_ALL = "ALL"

private data class CategoryStat(
    val category: VarkType,
    val respondents: Int,
    val mean: Double?,
)

/**
 * Analitik pemahaman kelas (mockups 29, 30 and 31).
 *
 * Per-category means are derived from the student feedback rows in the state, so the
 * three V/A/R bars stay independent of the Kinesthetic score card.
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
    var categoryFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val students = state.allStudents
    val stats = FEEDBACK_CATEGORIES.map { category ->
        val values = students.mapNotNull { it.valueFor(category) }
        CategoryStat(
            category = category,
            respondents = values.size,
            mean = if (values.isEmpty()) null else values.average(),
        )
    }
    val hasFeedback = stats.any { it.respondents > 0 }
    val kinesthetic = state.analytics?.kinesthetic

    EduScreen(
        title = if (feedbackOpen) "Feedback mahasiswa" else "Analitik kelas",
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
                    .padding(24.dp),
            ) {
                ErrorBlock(message = error, onRetry = { viewModel.load() })
            }

            state.analytics == null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            ) {
                EmptyBlock(
                    title = "Analitik belum tersedia",
                    message = "Data pemahaman kelas tidak dapat dimuat.",
                    actionLabel = "Muat ulang",
                    onAction = { viewModel.load() },
                )
            }

            feedbackOpen -> FeedbackList(
                students = students,
                categoryFilter = categoryFilter,
                onCategoryChange = { categoryFilter = it },
                modifier = Modifier.padding(padding),
            )

            !hasFeedback -> EmptyAnalytics(
                kinestheticMean = kinesthetic?.meanScore,
                assessedCount = kinesthetic?.assessedCount ?: 0,
                onPickTopic = onBack,
                modifier = Modifier.padding(padding),
            )

            else -> AnalyticsMain(
                stats = stats,
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
    stats: List<CategoryStat>,
    kinestheticMean: Double?,
    assessedCount: Int,
    onOpenFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Implementasi Basis Data")
        Spacer(Modifier.height(10.dp))
        Text(text = "Dengarkan data.\nPahami kelas.", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Feedback subjektif dan nilai objektif dipisahkan.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(18.dp))

        FieldLabel("Topik")
        EduDropdown(
            value = "Topik saat ini",
            options = listOf("Topik saat ini"),
            onSelect = {},
            enabled = false,
        )
        Spacer(Modifier.height(16.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Feedback pemahaman", style = EduText.Section)
            Spacer(Modifier.height(4.dp))
            Eyebrow("Subjektif • V/A/R saja")
            Spacer(Modifier.height(14.dp))

            stats.forEach { stat ->
                val color = categoryColor(stat.category)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subjectLabel(stat.category),
                        style = EduText.Label,
                        modifier = Modifier.width(78.dp),
                    )
                    Text(
                        text = stat.mean?.let { "${it.roundToInt()}%" } ?: "—",
                        style = EduText.BodySmall.copy(
                            color = EduColors.Ink,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "•  n = ${stat.respondents}",
                        style = EduText.BodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                MetricBar(
                    fraction = ((stat.mean ?: 0.0) / 100.0).toFloat(),
                    color = color,
                )
                Spacer(Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "0%",
                    style = EduText.BodySmall.copy(color = EduColors.MutedSoft),
                )
                Text(
                    text = "100%",
                    style = EduText.BodySmall.copy(color = EduColors.MutedSoft),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Rerata aritmetika feedback terbaru per mahasiswa–topik–kategori. " +
                    "Belum merespons tidak dihitung sebagai 0%.",
                style = EduText.BodySmall,
            )
            Spacer(Modifier.height(12.dp))
            TextLink(text = "Lihat feedback mahasiswa", onClick = onOpenFeedback)
        }

        Spacer(Modifier.height(16.dp))
        KinestheticCard(
            title = "Kinesthetic",
            mean = kinestheticMean,
            assessedCount = assessedCount,
            compact = false,
        )
    }
}

@Composable
private fun EmptyAnalytics(
    kinestheticMean: Double?,
    assessedCount: Int,
    onPickTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Analitik kelas")
        Spacer(Modifier.height(10.dp))
        Text(text = "Belum ada data\nuntuk ditampilkan.", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Grafik akan muncul setelah mahasiswa mengirim feedback.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(18.dp))

        FieldLabel("Topik")
        EduDropdown(
            value = "Topik saat ini",
            options = listOf("Topik saat ini"),
            onSelect = {},
            enabled = false,
        )
        Spacer(Modifier.height(16.dp))

        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduColors.PrimaryTint,
        ) {
            Text(
                text = "— %",
                style = EduText.Numeral.copy(color = EduColors.Primary),
            )
            Spacer(Modifier.height(10.dp))
            Text(text = "0 responden", style = EduText.CardTitle)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tidak ada feedback V/A/R pada topik ini.\n" +
                    "Belum merespons tidak dihitung sebagai 0%.",
                style = EduText.BodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))
        KinestheticCard(
            title = "Nilai Kinesthetic",
            mean = kinestheticMean,
            assessedCount = assessedCount,
            compact = true,
        )

        Spacer(Modifier.height(18.dp))
        SecondaryButton(text = "Pilih topik lain", onClick = onPickTopic)
    }
}

@Composable
private fun KinestheticCard(
    title: String,
    mean: Double?,
    assessedCount: Int,
    compact: Boolean,
) {
    val score = mean?.let { String.format(Locale.forLanguageTag("id-ID"), "%.1f", it) } ?: "—"
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduColors.TealTint,
    ) {
        if (compact) {
            Text(
                text = title,
                style = EduText.CardTitle.copy(color = EduColors.Teal),
            )
        } else {
            Eyebrow(text = "$title • Objektif", color = EduColors.Teal)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$score / 100",
            style = EduText.Numeral.copy(color = EduColors.Teal),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (assessedCount == 0) {
                "Belum ada kiriman yang dinilai."
            } else {
                "Rerata $assessedCount kiriman yang sudah dinilai."
            },
            style = EduText.BodySmall,
        )
        if (!compact) {
            Text(
                text = "Tidak digabungkan dengan feedback V/A/R.",
                style = EduText.BodySmall,
            )
        }
    }
}

@Composable
private fun FeedbackList(
    students: List<StudentFeedback>,
    categoryFilter: String,
    onCategoryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = VarkType.fromWire(categoryFilter)
    val visible = if (selected == null) students else students.filter { it.vark == selected }
    val respondents = visible.count { student ->
        val value = if (selected == null) {
            student.valueFor(student.vark)
        } else {
            student.valueFor(selected)
        }
        value != null
    }

    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        Eyebrow("Feedback subjektif")
        Spacer(Modifier.height(10.dp))
        Text(text = "Lihat setiap suara.", style = EduText.Display)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${students.size} mahasiswa pada topik ini",
            style = EduText.Body,
        )
        Spacer(Modifier.height(18.dp))

        FieldLabel("Kategori asal feedback")
        EduDropdown(
            value = categoryFilterLabel(selected),
            options = listOf(
                "Semua kategori",
                subjectLabel(VarkType.VISUAL),
                subjectLabel(VarkType.AURAL),
                subjectLabel(VarkType.READ_WRITE),
            ),
            onSelect = { label -> onCategoryChange(categoryFilterKey(label)) },
        )
        Spacer(Modifier.height(12.dp))
        NoticeCard(
            text = "Filter memakai kategori saat feedback dibuat, bukan pilihan login " +
                "terakhir.",
            tone = TintTone.Primary,
        )
        Spacer(Modifier.height(16.dp))

        EduCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$respondents responden",
                style = EduText.Numeral.copy(color = EduColors.Primary),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Satu feedback terbaru per mahasiswa pada topik dan kategori ini.",
                style = EduText.BodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            NoticeCard(
                text = "Belum ada mahasiswa untuk kategori ini.",
                tone = TintTone.Neutral,
            )
        } else {
            visible.forEach { student ->
                val value = if (selected == null) {
                    student.valueFor(student.vark)
                } else {
                    student.valueFor(selected)
                }
                EduCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = student.fullName, style = EduText.CardTitle)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${student.loginId} • ${subjectLabel(student.vark)}",
                        style = EduText.BodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (value == null) {
                        Text(
                            text = "Belum merespons",
                            style = EduText.Headline.copy(color = EduColors.Muted),
                        )
                    } else {
                        Text(
                            text = "$value%",
                            style = EduText.Numeral.copy(color = EduColors.Primary),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "0% adalah feedback valid. Belum merespons bukan 0%.",
            style = EduText.BodySmall,
        )
    }
}

private fun subjectLabel(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> "Visual"
    VarkType.AURAL -> "Aural"
    VarkType.READ_WRITE -> "Read/Write"
    VarkType.KINESTHETIC -> "Kinesthetic"
}

private fun categoryColor(vark: VarkType) = when (vark) {
    VarkType.VISUAL -> EduColors.Primary
    VarkType.AURAL -> EduColors.Teal
    VarkType.READ_WRITE -> EduColors.Purple
    VarkType.KINESTHETIC -> EduColors.Teal
}

private fun categoryFilterLabel(selected: VarkType?): String =
    selected?.let { subjectLabel(it) } ?: "Semua kategori"

private fun categoryFilterKey(label: String): String = when (label) {
    subjectLabel(VarkType.VISUAL) -> VarkType.VISUAL.wireValue
    subjectLabel(VarkType.AURAL) -> VarkType.AURAL.wireValue
    subjectLabel(VarkType.READ_WRITE) -> VarkType.READ_WRITE.wireValue
    else -> FILTER_ALL
}
