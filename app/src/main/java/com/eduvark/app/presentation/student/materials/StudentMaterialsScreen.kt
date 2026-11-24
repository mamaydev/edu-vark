package com.eduvark.app.presentation.student.materials

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
import com.eduvark.app.domain.model.MaterialOpenEvent
import com.eduvark.app.domain.model.MaterialOpenFailure
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.ArrowTitle
import com.eduvark.app.ui.components.CardEyebrow
import com.eduvark.app.ui.components.EduCard
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.HeroCard
import com.eduvark.app.ui.components.LoadingBlock
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.SecondaryButton
import com.eduvark.app.ui.components.SectionHeader
import com.eduvark.app.ui.components.ErrorBlock
import com.eduvark.app.ui.components.SuccessPanel
import com.eduvark.app.ui.components.TextLink
import com.eduvark.app.ui.components.TintBar
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.format.formatBytes
import com.eduvark.app.ui.format.formatDateTime
import com.eduvark.app.ui.format.formatShortDate
import kotlin.math.roundToInt
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

@Composable
fun StudentMaterialsScreen(
    onBack: () -> Unit,
    onOpenTopics: () -> Unit,
    onSessionExpired: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: StudentMaterialsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            viewModel.onSessionExpiredHandled()
            onSessionExpired()
        }
    }

    // FR-015: berkas dibuka dari hasil unduhan terautentikasi; tautan eksternal
    // dibuka di browser tanpa header Authorization.
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

    var draft by remember { mutableStateOf<Float?>(null) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSavingFeedback, state.feedback, state.feedbackError) {
        if (pendingSave && !state.isSavingFeedback) {
            if (state.feedbackError == null && state.feedback != null) {
                saved = true
            }
            pendingSave = false
        }
    }

    val vark = state.activeVark
    val materials = state.materials
    val primary = materials.firstOrNull { it.kind == MaterialKind.FILE } ?: materials.firstOrNull()
    val title = when {
        materials.isEmpty() -> stringResource(R.string.st_material_title_missing)
        vark == null -> stringResource(R.string.st_material_title_default)
        else -> stringResource(R.string.st_material_title_with_vark, varkTitle(vark))
    }

    val open: (Material) -> Unit = viewModel::openMaterial

    EduScreen(
        title = title,
        onBack = { if (saved) saved = false else onBack() },
        modifier = modifier,
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading -> LoadingBlock(Modifier.padding(padding))

            state.error != null -> Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(dimen(R.dimen.space_24)),
            ) {
                ErrorBlock(message = state.error.orEmpty(), onRetry = viewModel::load)
            }

            saved -> FeedbackSaved(
                topicTitle = state.topic?.title.orEmpty(),
                value = state.feedback ?: (draft?.roundToInt() ?: 0),
                vark = vark,
                updatedAt = state.feedbackUpdatedAt,
                onBackToMaterial = { saved = false },
                onOpenTopics = onOpenTopics,
                modifier = Modifier.padding(padding),
            )

            materials.isEmpty() -> MissingMaterial(
                topicTitle = state.topic?.title.orEmpty(),
                order = state.topic?.sortOrder ?: 0,
                vark = vark,
                onOpenTopics = onOpenTopics,
                modifier = Modifier.padding(padding),
            )

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
                state.openFailure?.let { failure ->
                    NoticeCard(
                        title = stringResource(R.string.p2_material_open_error_title),
                        text = stringResource(materialOpenFailureText(failure)),
                        tone = TintTone.Danger,
                    )
                    Spacer(Modifier.height(dimen(R.dimen.space_12)))
                }
                TintBar(
                    text = stringResource(
                        R.string.st_material_topic_bar,
                        (state.topic?.sortOrder ?: 0).toString().padStart(2, '0'),
                        vark?.label ?: stringResource(R.string.st_material_vark_default),
                    ),
                    uppercase = true,
                )
                Spacer(Modifier.height(dimen(R.dimen.space_18)))
                Text(text = state.topic?.title.orEmpty(), style = EduTheme.type.display)
                Spacer(Modifier.height(dimen(R.dimen.space_18)))

                if (primary != null) {
                    when (vark) {
                        VarkType.AURAL -> AudioPanel(primary, open)
                        VarkType.READ_WRITE -> ReadPanel(primary, state.topic?.description, open)
                        else -> VideoPanel(primary, state.topic?.description, open)
                    }
                }

                Spacer(Modifier.height(dimen(R.dimen.space_22)))
                SectionHeader(title = sectionTitle(vark))
                Spacer(Modifier.height(dimen(R.dimen.space_8)))
                Text(
                    text = state.topic?.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.st_material_description_default),
                    style = EduTheme.type.body,
                )

                val others = materials.filter { it.id != primary?.id }
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(dimen(R.dimen.space_18)))
                    others.forEach { material ->
                        AttachmentCard(material = material, onOpen = { open(material) })
                        Spacer(Modifier.height(dimen(R.dimen.space_10)))
                    }
                }

                if (vark != null && vark != VarkType.KINESTHETIC) {
                    Spacer(Modifier.height(dimen(R.dimen.space_14)))
                    FeedbackCard(
                        value = draft ?: (state.feedback ?: 0).toFloat(),
                        storedValue = state.feedback,
                        isSaving = state.isSavingFeedback,
                        error = state.feedbackError,
                        onValueChange = { draft = it },
                        onSave = {
                            pendingSave = true
                            viewModel.saveFeedback((draft ?: (state.feedback ?: 0).toFloat()).roundToInt())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun varkTitle(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> stringResource(R.string.st_material_vark_visual)
    VarkType.AURAL -> stringResource(R.string.st_material_vark_aural)
    VarkType.READ_WRITE -> stringResource(R.string.st_material_vark_read_write)
    VarkType.KINESTHETIC -> stringResource(R.string.st_material_vark_kinesthetic)
}

@Composable
private fun sectionTitle(vark: VarkType?): String = when (vark) {
    VarkType.AURAL -> stringResource(R.string.st_material_section_audio)
    VarkType.READ_WRITE -> stringResource(R.string.st_material_section_read)
    else -> stringResource(R.string.st_material_section_default)
}

@Composable
private fun VideoPanel(
    material: Material,
    description: String?,
    onOpen: (Material) -> Unit,
) {
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduTheme.colors.mediaNavy,
        onClick = { onOpen(material) },
    ) {
        Text(
            text = stringResource(R.string.st_material_video_eyebrow),
            style = EduTheme.type.eyebrow.copy(color = Color.White.copy(alpha = 0.65f)),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = material.title,
            style = EduTheme.type.heroTitle.copy(fontSize = dimenSp(R.dimen.text_size_24), lineHeight = dimenSp(R.dimen.text_size_30), color = Color.White),
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = description,
                style = EduTheme.type.bodySmall.copy(color = Color.White.copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(dimen(R.dimen.size_26))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = EduIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(dimen(R.dimen.size_12)),
                )
            }
            Spacer(Modifier.width(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_material_video_play),
                style = EduTheme.type.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatBytes(material.sizeBytes) ?: stringResource(R.string.st_material_video_meta_default),
                style = EduTheme.type.meta.copy(color = Color.White.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.width(dimen(R.dimen.space_8)))
            Icon(
                imageVector = EduIcons.Expand,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(dimen(R.dimen.size_16)),
            )
        }
    }
}

@Composable
private fun AudioPanel(
    material: Material,
    onOpen: (Material) -> Unit,
) {
    val bars = listOf(
        10, 18, 26, 14, 30, 22, 12, 26, 34, 18, 10, 24, 30, 16, 10, 22, 28, 14, 8, 20, 26, 12, 8,
        16, 22, 10, 18, 24, 12, 8,
    )
    val metaFallback = stringResource(R.string.st_material_audio_meta_default)
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduTheme.colors.lavender,
        onClick = { onOpen(material) },
    ) {
        Text(
            text = material.vark.label.uppercase(),
            style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.65f)),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = material.title,
            style = EduTheme.type.heroTitle.copy(fontSize = dimenSp(R.dimen.text_size_23), lineHeight = dimenSp(R.dimen.text_size_29)),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimen(R.dimen.space_40)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimen(R.dimen.space_3)),
        ) {
            bars.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(dimen(R.dimen.size_3))
                        .height(height.dp)
                        .clip(RoundedCornerShape(dimen(R.dimen.radius_2)))
                        .background(
                            if (index % 3 == 0) {
                                EduTheme.colors.onBrand.copy(alpha = 0.55f)
                            } else {
                                EduTheme.colors.onBrand
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(dimen(R.dimen.space_14)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = EduIcons.Play,
                contentDescription = null,
                tint = EduTheme.colors.onBrand,
                modifier = Modifier.size(dimen(R.dimen.size_16)),
            )
            Spacer(Modifier.width(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_material_audio_play),
                style = EduTheme.type.bodySmall.copy(
                    color = EduTheme.colors.onBrand,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = listOfNotNull(
                    material.mime,
                    formatBytes(material.sizeBytes),
                ).joinToString(" • ").ifBlank { metaFallback },
                style = EduTheme.type.meta.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun ReadPanel(
    material: Material,
    description: String?,
    onOpen: (Material) -> Unit,
) {
    EduCard(modifier = Modifier.fillMaxWidth()) {
        CardEyebrow(stringResource(R.string.st_material_read_eyebrow, material.kind.label))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = material.title, style = EduTheme.type.headline)
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(text = description, style = EduTheme.type.body)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        TextLink(
            text = stringResource(R.string.st_material_read_link),
            onClick = { onOpen(material) },
        )
        val meta = listOfNotNull(
            material.mime,
            formatBytes(material.sizeBytes),
            formatShortDate(material.uploadedAt),
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = meta, style = EduTheme.type.meta)
        }
    }
}

@Composable
private fun AttachmentCard(material: Material, onOpen: () -> Unit) {
    EduCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        ArrowTitle(text = material.title)
        val meta = listOfNotNull(
            material.kind.label,
            formatBytes(material.sizeBytes),
            formatShortDate(material.uploadedAt),
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(text = meta, style = EduTheme.type.meta)
        }
    }
}

@Composable
private fun FeedbackCard(
    value: Float,
    storedValue: Int?,
    isSaving: Boolean,
    error: String?,
    onValueChange: (Float) -> Unit,
    onSave: () -> Unit,
) {
    EduCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.st_material_feedback_title), style = EduTheme.type.cardTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(text = stringResource(R.string.st_material_feedback_subtitle), style = EduTheme.type.bodySmall)
        Spacer(Modifier.height(dimen(R.dimen.space_14)))
        Text(
            text = stringResource(R.string.st_material_percent, value.roundToInt()),
            style = EduTheme.type.numeral.copy(
                color = EduTheme.colors.lavender,
                fontSize = dimenSp(R.dimen.text_size_44),
                lineHeight = dimenSp(R.dimen.text_size_48),
            ),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            steps = 99,
            enabled = !isSaving,
            colors = SliderDefaults.colors(
                thumbColor = EduTheme.colors.lime,
                activeTrackColor = EduTheme.colors.lavender,
                inactiveTrackColor = EduTheme.colors.border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.st_material_feedback_min), style = EduTheme.type.meta)
            Text(text = stringResource(R.string.st_material_feedback_max), style = EduTheme.type.meta)
        }
        if (storedValue != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_material_feedback_stored, storedValue),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.success),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            NoticeCard(text = error, tone = TintTone.Danger)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        PrimaryButton(
            text = stringResource(R.string.st_material_feedback_save),
            onClick = onSave,
            loading = isSaving,
            enabled = !isSaving,
        )
    }
}

@Composable
private fun FeedbackSaved(
    topicTitle: String,
    value: Int,
    vark: VarkType?,
    updatedAt: String?,
    onBackToMaterial: () -> Unit,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicFallback = stringResource(R.string.st_material_topic_default)
    val varkFallback = stringResource(R.string.st_material_vark_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        SuccessPanel()
        Spacer(Modifier.height(dimen(R.dimen.space_22)))
        Eyebrow(stringResource(R.string.st_material_saved_eyebrow))
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = stringResource(R.string.st_material_saved_title), style = EduTheme.type.display)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        Text(
            text = stringResource(R.string.st_material_saved_body),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = topicTitle.ifBlank { topicFallback }.uppercase(),
                style = EduTheme.type.eyebrow.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_material_percent, value),
                style = EduTheme.type.numeralLarge.copy(fontSize = dimenSp(R.dimen.text_size_52), lineHeight = dimenSp(R.dimen.text_size_58)),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
            Text(
                text = stringResource(R.string.st_material_saved_meta, vark?.label ?: varkFallback),
                style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.92f)),
            )
            val stamp = formatDateTime(updatedAt)
            if (stamp != null) {
                Spacer(Modifier.height(dimen(R.dimen.space_4)))
                Text(
                    text = stamp,
                    style = EduTheme.type.meta.copy(color = EduTheme.colors.onBrand.copy(alpha = 0.7f)),
                )
            }
        }
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(R.string.st_material_saved_notice),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(text = stringResource(R.string.st_material_saved_back), onClick = onBackToMaterial)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        SecondaryButton(text = stringResource(R.string.st_material_saved_topics), onClick = onOpenTopics)
    }
}

@Composable
private fun MissingMaterial(
    topicTitle: String,
    order: Int,
    vark: VarkType?,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topicFallback = stringResource(R.string.st_material_topic_default)
    val varkFallback = stringResource(R.string.st_material_vark_default)
    val categoryFallback = stringResource(R.string.st_material_category_default)
    val formatFallback = stringResource(R.string.st_material_format_default)
    ScrollColumn(modifier = modifier, spacing = dimen(R.dimen.dimen_0)) {
        TintBar(
            text = stringResource(
                R.string.st_material_topic_bar,
                order.toString().padStart(2, '0'),
                vark?.label ?: varkFallback,
            ),
            uppercase = true,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduTheme.colors.purpleTint,
        ) {
            Text(
                text = order.toString().padStart(2, '0'),
                style = EduTheme.type.numeral.copy(
                    color = EduTheme.colors.lavender,
                    fontSize = dimenSp(R.dimen.text_size_56),
                    lineHeight = dimenSp(R.dimen.text_size_60),
                ),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(
                text = topicTitle.ifBlank { topicFallback },
                style = EduTheme.type.displaySmall.copy(color = EduTheme.colors.ink),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(
                text = stringResource(R.string.st_material_missing_message),
                style = EduTheme.type.body.copy(
                    color = EduTheme.colors.lavender,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Spacer(Modifier.height(dimen(R.dimen.space_18)))
        Text(
            text = stringResource(
                R.string.st_material_missing_body,
                vark?.label ?: categoryFallback,
            ),
            style = EduTheme.type.body,
        )
        Spacer(Modifier.height(dimen(R.dimen.space_16)))
        NoticeCard(
            text = stringResource(
                R.string.st_material_missing_notice,
                vark?.label ?: formatFallback,
            ),
        )
        Spacer(Modifier.height(dimen(R.dimen.space_20)))
        PrimaryButton(text = stringResource(R.string.st_material_missing_back), onClick = onOpenTopics)
    }
}
