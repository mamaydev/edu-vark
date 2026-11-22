package com.eduvark.app.presentation.student.materials

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
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
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import kotlin.math.roundToInt

@Composable
fun StudentMaterialsScreen(
    onBack: () -> Unit,
    onOpenTopics: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    viewModel: StudentMaterialsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
        materials.isEmpty() -> "Materi belum tersedia"
        vark == null -> "Materi"
        else -> "Materi " + varkTitle(vark)
    }

    val open: (Material) -> Unit = { material ->
        val url = material.signedUrl ?: material.url
        if (!url.isNullOrBlank()) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

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
                    .padding(24.dp),
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

            else -> ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
                TintBar(
                    text = "Topik ${(state.topic?.sortOrder ?: 0).toString().padStart(2, '0')} • " +
                        (vark?.label ?: "VARK"),
                    uppercase = true,
                )
                Spacer(Modifier.height(18.dp))
                Text(text = state.topic?.title.orEmpty(), style = EduText.Display)
                Spacer(Modifier.height(18.dp))

                if (primary != null) {
                    when (vark) {
                        VarkType.AURAL -> AudioPanel(primary, open)
                        VarkType.READ_WRITE -> ReadPanel(primary, state.topic?.description, open)
                        else -> VideoPanel(primary, state.topic?.description, open)
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionHeader(title = sectionTitle(vark))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.topic?.description?.takeIf { it.isNotBlank() }
                        ?: "Materi untuk topik ini tersedia pada format sesimu.",
                    style = EduText.Body,
                )

                val others = materials.filter { it.id != primary?.id }
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    others.forEach { material ->
                        AttachmentCard(material = material, onOpen = { open(material) })
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (vark != null && vark != VarkType.KINESTHETIC) {
                    Spacer(Modifier.height(14.dp))
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

private fun varkTitle(vark: VarkType): String = when (vark) {
    VarkType.VISUAL -> "Visual"
    VarkType.AURAL -> "Aural"
    VarkType.READ_WRITE -> "Read/Write"
    VarkType.KINESTHETIC -> "Kinesthetic"
}

private fun sectionTitle(vark: VarkType?): String = when (vark) {
    VarkType.AURAL -> "Tentang rekaman ini"
    VarkType.READ_WRITE -> "Isi modul"
    else -> "Tentang materi ini"
}

@Composable
private fun VideoPanel(
    material: Material,
    description: String?,
    onOpen: (Material) -> Unit,
) {
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduColors.MediaNavy,
        onClick = { onOpen(material) },
    ) {
        Text(
            text = "VIDEO PEMBELAJARAN",
            style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.65f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = material.title,
            style = EduText.HeroTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
        )
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.85f)),
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = EduIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Putar video",
                style = EduText.BodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatBytes(material.sizeBytes) ?: "Video",
                style = EduText.Meta.copy(color = Color.White.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = EduIcons.Expand,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
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
    EduCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = EduColors.Primary,
        onClick = { onOpen(material) },
    ) {
        Text(
            text = material.vark.label.uppercase(),
            style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.65f)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = material.title,
            style = EduText.HeroTitle.copy(fontSize = 23.sp, lineHeight = 29.sp),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            bars.forEachIndexed { index, height ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index % 3 == 0) Color.White.copy(alpha = 0.55f) else Color.White,
                        ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = EduIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Putar audio",
                style = EduText.BodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = listOfNotNull(
                    material.mime,
                    formatBytes(material.sizeBytes),
                ).joinToString(" • ").ifBlank { "Audio" },
                style = EduText.Meta.copy(color = Color.White.copy(alpha = 0.75f)),
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
        CardEyebrow("Modul • ${material.kind.label}")
        Spacer(Modifier.height(10.dp))
        Text(text = material.title, style = EduText.Headline)
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = description, style = EduText.Body)
        }
        Spacer(Modifier.height(16.dp))
        TextLink(
            text = "Lanjut membaca modul",
            onClick = { onOpen(material) },
        )
        val meta = listOfNotNull(
            material.mime,
            formatBytes(material.sizeBytes),
            formatShortDate(material.uploadedAt),
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(text = meta, style = EduText.Meta)
        }
    }
}

@Composable
private fun AttachmentCard(material: Material, onOpen: () -> Unit) {
    EduCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        ArrowTitle(text = material.title, color = EduColors.Primary)
        val meta = listOfNotNull(
            material.kind.label,
            formatBytes(material.sizeBytes),
            formatShortDate(material.uploadedAt),
        ).joinToString(" • ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = meta, style = EduText.Meta)
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
        Text(text = "Seberapa paham kamu?", style = EduText.CardTitle)
        Spacer(Modifier.height(6.dp))
        Text(text = "Feedback pribadi, bukan nilai akademik.", style = EduText.BodySmall)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${value.roundToInt()}%",
            style = EduText.Numeral.copy(
                color = EduColors.Primary,
                fontSize = 44.sp,
                lineHeight = 48.sp,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            steps = 99,
            enabled = !isSaving,
            colors = SliderDefaults.colors(
                thumbColor = EduColors.Primary,
                activeTrackColor = EduColors.Primary,
                inactiveTrackColor = EduColors.Border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "0%", style = EduText.Meta)
            Text(text = "100%", style = EduText.Meta)
        }
        if (storedValue != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tersimpan: $storedValue%",
                style = EduText.BodySmall.copy(color = EduColors.Teal),
            )
        }
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            NoticeCard(text = error, tone = TintTone.Danger)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Simpan feedback",
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
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        SuccessPanel()
        Spacer(Modifier.height(22.dp))
        Eyebrow("Feedback tersimpan")
        Spacer(Modifier.height(10.dp))
        Text(text = "Pemahamannmu\nsudah dicatat.", style = EduText.Display)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Kamu bisa memperbarui feedback untuk topik dan format yang sama.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(20.dp))
        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = topicTitle.ifBlank { "Topik" }.uppercase(),
                style = EduText.Eyebrow.copy(color = Color.White.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$value%",
                style = EduText.NumeralLarge.copy(fontSize = 52.sp, lineHeight = 58.sp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${vark?.label ?: "VARK"} • Feedback terbaru",
                style = EduText.BodySmall.copy(color = Color.White.copy(alpha = 0.92f)),
            )
            val stamp = formatDateTime(updatedAt)
            if (stamp != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stamp,
                    style = EduText.Meta.copy(color = Color.White.copy(alpha = 0.7f)),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Nilai terbaru menggantikan feedback sebelumnya pada topik dan kategori ini. " +
                "Feedback kategori lain tetap tersimpan.",
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Kembali ke materi", onClick = onBackToMaterial)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = "Lihat topik lain", onClick = onOpenTopics)
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
    ScrollColumn(modifier = modifier, spacing = 0.dp) {
        TintBar(
            text = "Topik ${order.toString().padStart(2, '0')} • ${vark?.label ?: "VARK"}",
            uppercase = true,
        )
        Spacer(Modifier.height(16.dp))
        EduCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = EduColors.PrimaryTint,
        ) {
            Text(
                text = order.toString().padStart(2, '0'),
                style = EduText.Numeral.copy(
                    color = EduColors.Primary,
                    fontSize = 56.sp,
                    lineHeight = 60.sp,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = topicTitle.ifBlank { "Topik" },
                style = EduText.DisplaySmall.copy(color = EduColors.Ink),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "materi belum tersedia, harap hubungi dosen terlebih dahulu",
                style = EduText.Body.copy(color = EduColors.Primary, fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Topik sudah diterbitkan, tetapi materi untuk kategori " +
                "${vark?.label ?: "ini"} belum diunggah.",
            style = EduText.Body,
        )
        Spacer(Modifier.height(16.dp))
        NoticeCard(
            text = "Sesi kamu tetap ${vark?.label ?: "format ini"}. Format lain tidak dapat " +
                "diakses dalam sesi ini.",
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Kembali ke daftar topik", onClick = onOpenTopics)
    }
}
