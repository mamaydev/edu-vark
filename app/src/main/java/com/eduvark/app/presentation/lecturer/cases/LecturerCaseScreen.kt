package com.eduvark.app.presentation.lecturer.cases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.EduDropdown
import com.eduvark.app.ui.components.EduField
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.FieldHelper
import com.eduvark.app.ui.components.FieldLabel
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.format.formatDeadline
import com.eduvark.app.ui.theme.EduText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val JAKARTA: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
private val INDONESIAN: Locale = Locale.forLanguageTag("id-ID")

private data class DateOption(val key: String, val label: String)
private data class TimeOption(val key: String, val label: String)

/**
 * Atur studi kasus (mockup 23).
 *
 * Deadline is picked with a date and a time dropdown; the pair is converted from the
 * display timezone (UTC+07:00) to the ISO UTC string the API expects.
 */
@Composable
fun LecturerCaseScreen(
    onBack: () -> Unit,
    onOpenSubmissions: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMaterials: (String) -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: LecturerCaseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caseStudy = state.caseStudy
    val kinestheticMaterials = state.materials.filter { it.vark == VarkType.KINESTHETIC }
    val deadlineIso = caseStudy?.deadlineAt
    val locked = (caseStudy?.submissionCount ?: 0) > 0

    var theory by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var dateKey by rememberSaveable { mutableStateOf(defaultDateKey()) }
    var timeKey by rememberSaveable { mutableStateOf("23:59") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val dateOptions = remember(deadlineIso) { buildDateOptions(count = 90, savedIso = deadlineIso) }
    // Includes the current selection so a saved 23.59 deadline stays visible in the menu.
    val timeOptions = remember(timeKey) { buildTimeOptions(savedKey = timeKey) }

    LaunchedEffect(state.caseStudy?.id, deadlineIso) {
        val study = state.caseStudy ?: return@LaunchedEffect
        theory = study.theory.orEmpty()
        instructions = study.instructions
        val parts = deadlineParts(study.deadlineAt)
        if (parts != null) {
            dateKey = parts.first
            timeKey = parts.second
        }
    }

    val dateLabel = dateOptions.firstOrNull { it.key == dateKey }?.label ?: "Pilih tanggal"
    val timeLabel = timeOptions.firstOrNull { it.key == timeKey }?.label ?: "Pilih waktu"

    EduScreen(
        title = "Studi kasus",
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
                Text(text = "Rancang studi kasus", style = EduText.Display)
                Spacer(Modifier.height(8.dp))
                state.topicTitle?.let { title ->
                    Text(text = "Topik: $title", style = EduText.BodyStrong)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = if (caseStudy == null) {
                        "Topik ini belum punya studi kasus. Simpan rancangan pertama."
                    } else {
                        "Atur penjelasan teori, instruksi, dan tenggat pengumpulan."
                    },
                    style = EduText.Body,
                )

                Spacer(Modifier.height(12.dp))
                NoticeCard(
                    text = if (kinestheticMaterials.isEmpty()) {
                        "Belum ada materi Kinesthetic. Unggah PDF pendukung agar mahasiswa " +
                            "bisa membacanya sebelum menjawab."
                    } else {
                        "${kinestheticMaterials.size} materi Kinesthetic siap dibaca mahasiswa."
                    },
                    tone = if (kinestheticMaterials.isEmpty()) {
                        TintTone.Neutral
                    } else {
                        TintTone.Teal
                    },
                )
                Spacer(Modifier.height(8.dp))
                TextLink(
                    text = "Kelola materi Kinesthetic",
                    onClick = { onOpenMaterials(state.topicId) },
                )

                caseStudy?.let { study ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextLink(
                            text = "Lihat pengumpulan",
                            onClick = { onOpenSubmissions(study.id) },
                        )
                        study.submissionCount?.let { count ->
                            Text(
                                text = "  •  $count kiriman",
                                style = EduText.BodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                FieldLabel("Penjelasan teori")
                EduField(
                    value = theory,
                    onValueChange = { theory = it },
                    placeholder = "Ringkas teori yang mendasari studi kasus",
                    singleLine = false,
                    minHeight = 96.dp,
                    enabled = !state.isSaving,
                )
                Spacer(Modifier.height(16.dp))

                FieldLabel("Instruksi studi kasus")
                EduField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = "Uraikan langkah pengerjaan yang diminta",
                    singleLine = false,
                    minHeight = 120.dp,
                    enabled = !state.isSaving,
                )
                Spacer(Modifier.height(16.dp))

                FieldLabel("Tanggal deadline")
                EduDropdown(
                    value = dateLabel,
                    options = dateOptions.map { it.label },
                    onSelect = { selected ->
                        dateOptions.firstOrNull { it.label == selected }?.let {
                            dateKey = it.key
                            localError = null
                        }
                    },
                    enabled = !locked && !state.isSaving,
                )
                Spacer(Modifier.height(16.dp))

                FieldLabel("Waktu deadline")
                EduDropdown(
                    value = timeLabel,
                    options = timeOptions.map { it.label },
                    onSelect = { selected ->
                        timeOptions.firstOrNull { it.label == selected }?.let {
                            timeKey = it.key
                            localError = null
                        }
                    },
                    enabled = !locked && !state.isSaving,
                )
                Spacer(Modifier.height(16.dp))

                FieldLabel("Zona waktu tampilan")
                EduField(
                    value = "UTC+07:00",
                    onValueChange = {},
                    enabled = false,
                )
                FieldHelper(text = "Contoh rancangan; zona waktu final diperiksa.")

                if (deadlineIso != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tenggat tersimpan: " +
                            (formatDeadline(deadlineIso) ?: deadlineIso),
                        style = EduText.BodySmall,
                    )
                }

                if (locked) {
                    Spacer(Modifier.height(14.dp))
                    NoticeCard(
                        text = "Tenggat terkunci karena sudah ada pengumpulan. Instruksi " +
                            "masih dapat diubah.",
                        tone = TintTone.Amber,
                    )
                }

                Spacer(Modifier.height(16.dp))
                NoticeCard(
                    text = "Jawaban teks 1–10.000 karakter, satu kali kirim, tanpa draft. " +
                        "Validasi tenggat memakai waktu server.",
                    tone = TintTone.Primary,
                )

                val message = localError ?: state.saveError
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    NoticeCard(text = message, tone = TintTone.Danger)
                }

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    text = "Simpan studi kasus",
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                    onClick = {
                        val iso = combineDeadline(dateKey, timeKey)
                        if (iso == null) {
                            localError = "Tanggal dan waktu deadline wajib dipilih."
                        } else {
                            localError = null
                            viewModel.save(theory, instructions, iso)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun defaultDateKey(): String {
    val calendar = Calendar.getInstance(JAKARTA)
    calendar.add(Calendar.DAY_OF_YEAR, 7)
    return dateKeyOf(calendar)
}

private fun dateKeyOf(calendar: Calendar): String {
    val year = calendar.get(Calendar.YEAR).toString().padStart(4, '0')
    val month = (calendar.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = calendar.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "$year-$month-$day"
}

private fun timeKeyOf(calendar: Calendar): String {
    val hour = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$hour:$minute"
}

private fun timeLabelOf(key: String): String = key.replace(':', '.')

private fun dateLabelOf(calendar: Calendar): String {
    val formatter = SimpleDateFormat("d MMMM yyyy", INDONESIAN)
    formatter.timeZone = JAKARTA
    return formatter.format(calendar.time)
}

private fun buildDateOptions(count: Int, savedIso: String?): List<DateOption> {
    val start = Calendar.getInstance(JAKARTA).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val options = mutableListOf<DateOption>()
    for (offset in 0 until count) {
        val calendar = start.clone() as Calendar
        calendar.add(Calendar.DAY_OF_YEAR, offset)
        options += DateOption(dateKeyOf(calendar), dateLabelOf(calendar))
    }
    val savedDate = parseIsoDate(savedIso)
    if (savedDate != null) {
        val saved = Calendar.getInstance(JAKARTA).apply { time = savedDate }
        val key = dateKeyOf(saved)
        if (options.none { it.key == key }) {
            options.add(0, DateOption(key, dateLabelOf(saved)))
        }
    }
    return options
}

private fun buildTimeOptions(savedKey: String?): List<TimeOption> {
    val options = mutableListOf<TimeOption>()
    for (hour in 0..23) {
        for (minute in 0..45 step 15) {
            val key = hour.toString().padStart(2, '0') + ":" +
                minute.toString().padStart(2, '0')
            options += TimeOption(key, timeLabelOf(key))
        }
    }
    if (savedKey != null && options.none { it.key == savedKey }) {
        options += TimeOption(savedKey, timeLabelOf(savedKey))
    }
    return options
}

private fun deadlineParts(iso: String?): Pair<String, String>? {
    val date = parseIsoDate(iso) ?: return null
    val calendar = Calendar.getInstance(JAKARTA).apply { time = date }
    return dateKeyOf(calendar) to timeKeyOf(calendar)
}

private fun combineDeadline(dateKey: String, timeKey: String): String? {
    val dateParts = dateKey.split("-")
    val timeParts = timeKey.split(":")
    if (dateParts.size != 3 || timeParts.size != 2) return null
    val year = dateParts[0].toIntOrNull() ?: return null
    val month = dateParts[1].toIntOrNull() ?: return null
    val day = dateParts[2].toIntOrNull() ?: return null
    val hour = timeParts[0].toIntOrNull() ?: return null
    val minute = timeParts[1].toIntOrNull() ?: return null

    val calendar = Calendar.getInstance(JAKARTA)
    calendar.clear()
    calendar.set(year, month - 1, day, hour, minute, 0)

    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(calendar.time)
}

private fun parseIsoDate(iso: String?): Date? {
    if (iso.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in patterns) {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        val result = runCatching { formatter.parse(iso) }.getOrNull()
        if (result != null) return result
    }
    return null
}
