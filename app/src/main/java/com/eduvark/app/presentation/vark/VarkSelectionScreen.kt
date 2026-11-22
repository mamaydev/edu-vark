package com.eduvark.app.presentation.vark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.ui.components.EduIcons
import com.eduvark.app.ui.components.EduScreen
import com.eduvark.app.ui.components.Eyebrow
import com.eduvark.app.ui.components.NoticeCard
import com.eduvark.app.ui.components.PrimaryButton
import com.eduvark.app.ui.components.ScrollColumn
import com.eduvark.app.ui.components.TintTone
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText
import com.eduvark.app.ui.theme.EduVarkTheme

@Composable
fun VarkSelectionScreen(
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: VarkSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<VarkType?>(null) }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onSelected()
        }
    }

    EduScreen(
        title = "Format belajarmu",
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = 0.dp) {
            Eyebrow("Langkah 1 dari 1")
            Spacer(Modifier.height(10.dp))
            Text(text = "Belajar dengan\ncaramu hari ini.", style = EduText.Display)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pilih satu format untuk sesi ini. VARK adalah preferensi akses, bukan diagnosis gaya belajar.",
                style = EduText.Body,
            )
            Spacer(Modifier.height(22.dp))

            VarkType.entries.forEach { vark ->
                VarkOptionCard(
                    vark = vark,
                    selected = selected == vark,
                    enabled = !state.isLoading,
                    onSelect = { selected = vark },
                )
                Spacer(Modifier.height(12.dp))
            }

            val error = state.error
            if (error != null) {
                NoticeCard(text = error, tone = TintTone.Danger)
                Spacer(Modifier.height(12.dp))
            }

            NoticeCard(text = "Pilihan terkunci sampai kamu keluar. Login kembali untuk memilih format lain.")

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = if (selected != null) {
                    "Lanjut dengan ${selected?.label}"
                } else {
                    "Pilih satu format dulu"
                },
                onClick = { selected?.let(viewModel::select) },
                enabled = selected != null,
                loading = state.isLoading,
                showArrow = selected != null,
            )
        }
    }
}

@Composable
private fun VarkOptionCard(
    vark: VarkType,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) EduColors.PrimaryTint else EduColors.Surface,
                shape,
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = vark.letter(),
            style = EduText.CardTitle.copy(
                color = EduColors.Primary,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.width(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vark.displayLabel(),
                    style = EduText.CardTitle.copy(
                        color = if (selected) EduColors.Primary else EduColors.Ink,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = EduIcons.Check,
                        contentDescription = null,
                        tint = EduColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = vark.description(), style = EduText.BodySmall)
        }
    }
}

internal fun VarkType.letter(): String = when (this) {
    VarkType.VISUAL -> "V"
    VarkType.AURAL -> "A"
    VarkType.READ_WRITE -> "R"
    VarkType.KINESTHETIC -> "K"
}

internal fun VarkType.displayLabel(): String = when (this) {
    VarkType.AURAL -> "Aural (Audio)"
    else -> label
}

internal fun VarkType.description(): String = when (this) {
    VarkType.VISUAL -> "Video dan diagram untuk melihat konsep."
    VarkType.AURAL -> "Dengarkan penjelasan dan rekaman."
    VarkType.READ_WRITE -> "Baca modul dan rangkum pemahaman."
    VarkType.KINESTHETIC -> "Terapkan konsep melalui studi kasus."
}

@Preview(showBackground = true)
@Composable
private fun VarkSelectionScreenPreview() {
    EduVarkTheme {
        VarkSelectionScreen(onSelected = {})
    }
}
