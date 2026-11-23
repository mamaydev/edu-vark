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
import androidx.compose.ui.res.stringResource
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

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

    val defaultDateLabel = stringResource(R.string.lec_case_default_date)
    val defaultTimeLabel = stringResource(R.string.lec_case_default_time)
    val deadlineRequiredError = stringResource(R.string.lec_case_deadline_error)
    val dateLabel = dateOptions.firstOrNull { it.key == dateKey }?.label ?: defaultDateLabel
    val timeLabel = timeOptions.firstOrNull { it.key == timeKey }?.label ?: defaultTimeLabel

    EduScreen(
        title = stringResource(R.string.lec_case_screen_title),
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
                    text = stringResource(R.string.lec_case_chip),
                    tone = TintTone.Primary,
                    uppercase = true,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                Text(text = stringResource(R.string.lec_case_headline), style = EduTheme.type.display)
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                state.topicTitle?.let { title ->
                    Text(text = stringResource(R.string.lec_case_topic, title), style = EduTheme.type.bodyStrong)
                    Spacer(Modifier.height(dimen(R.dimen.space_6)))
                }
                Text(
                    text = if (caseStudy == null) {
                        stringResource(R.string.lec_case_empty_body)
                    } else {
                        stringResource(R.string.lec_case_body)
                    },
                    style = EduTheme.type.body,
                )

                Spacer(Modifier.height(dimen(R.dimen.space_12)))
                NoticeCard(
                    text = if (kinestheticMaterials.isEmpty()) {
                        stringResource(R.string.lec_case_no_material_notice)
                    } else {
                        stringResource(
                            R.string.lec_case_material_ready_notice,
                            kinestheticMaterials.size,
                        )
                    },
                    tone = if (kinestheticMaterials.isEmpty()) {
                        TintTone.Neutral
                    } else {
                        TintTone.Teal
                    },
                )
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                TextLink(
                    text = stringResource(R.string.lec_case_link_materials),
                    onClick = { onOpenMaterials(state.topicId) },
                )

                caseStudy?.let { study ->
                    Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextLink(
                            text = stringResource(R.string.lec_case_link_submissions),
                            onClick = { onOpenSubmissions(study.id) },
                        )
                        study.submissionCount?.let { count ->
                            Text(
                                text = stringResource(
                                    R.string.lec_case_submission_count,
                                    count,
                                ),
                                style = EduTheme.type.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(dimen(R.dimen.space_18)))
                FieldLabel(stringResource(R.string.lec_case_theory_label))
                EduField(
                    value = theory,
                    onValueChange = { theory = it },
                    placeholder = stringResource(R.string.lec_case_theory_placeholder),
                    singleLine = false,
                    minHeight = dimen(R.dimen.dimen_96),
                    enabled = !state.isSaving,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_16)))

                FieldLabel(stringResource(R.string.lec_case_instructions_label))
                EduField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = stringResource(R.string.lec_case_instructions_placeholder),
                    singleLine = false,
                    minHeight = dimen(R.dimen.dimen_120),
                    enabled = !state.isSaving,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_16)))

                FieldLabel(stringResource(R.string.lec_case_date_label))
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
                Spacer(Modifier.height(dimen(R.dimen.space_16)))

                FieldLabel(stringResource(R.string.lec_case_time_label))
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
                Spacer(Modifier.height(dimen(R.dimen.space_16)))

                FieldLabel(stringResource(R.string.lec_case_timezone_label))
                EduField(
                    value = stringResource(R.string.lec_case_timezone_value),
                    onValueChange = {},
                    enabled = false,
                )
                FieldHelper(text = stringResource(R.string.lec_case_timezone_helper))

                if (deadlineIso != null) {
                    Spacer(Modifier.height(dimen(R.dimen.space_8)))
                    Text(
                        text = stringResource(
                            R.string.lec_case_saved_deadline,
                            formatDeadline(deadlineIso) ?: deadlineIso,
                        ),
                        style = EduTheme.type.bodySmall,
                    )
                }

                if (locked) {
                    Spacer(Modifier.height(dimen(R.dimen.space_14)))
                    NoticeCard(
                        text = stringResource(R.string.lec_case_locked_notice),
                        tone = TintTone.Amber,
                    )
                }

                Spacer(Modifier.height(dimen(R.dimen.space_16)))
                NoticeCard(
                    text = stringResource(R.string.lec_case_rules_notice),
                    tone = TintTone.Primary,
                )

                val message = localError ?: state.saveError
                if (message != null) {
                    Spacer(Modifier.height(dimen(R.dimen.space_12)))
                    NoticeCard(text = message, tone = TintTone.Danger)
                }

                Spacer(Modifier.height(dimen(R.dimen.space_20)))
                PrimaryButton(
                    text = stringResource(R.string.lec_case_save),
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                    onClick = {
                        val iso = combineDeadline(dateKey, timeKey)
                        if (iso == null) {
                            localError = deadlineRequiredError
                        } else {
                            localError = null
                            viewModel.save(theory, instructions, iso)
                        }
                    },
                )
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
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
