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
import androidx.compose.ui.res.stringResource
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
import com.eduvark.app.ui.theme.EduVarkTheme
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

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
        title = stringResource(R.string.vark_screen_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        ScrollColumn(modifier = Modifier.padding(padding), spacing = dimen(R.dimen.space_0)) {
            Eyebrow(stringResource(R.string.vark_step_eyebrow))
            Spacer(Modifier.height(dimen(R.dimen.space_10)))
            Text(text = stringResource(R.string.vark_headline), style = EduTheme.type.display)
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(
                text = stringResource(R.string.vark_intro),
                style = EduTheme.type.body,
            )
            Spacer(Modifier.height(dimen(R.dimen.space_22)))

            VarkType.entries.forEach { vark ->
                VarkOptionCard(
                    vark = vark,
                    selected = selected == vark,
                    enabled = !state.isLoading,
                    onSelect = { selected = vark },
                )
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
            }

            val error = state.error
            if (error != null) {
                NoticeCard(text = error, tone = TintTone.Danger)
                Spacer(Modifier.height(dimen(R.dimen.space_12)))
            }

            NoticeCard(text = stringResource(R.string.vark_locked_notice))

            Spacer(Modifier.height(dimen(R.dimen.space_20)))
            val picked = selected
            PrimaryButton(
                text = if (picked != null) {
                    stringResource(R.string.vark_continue_with, picked.label)
                } else {
                    stringResource(R.string.vark_select_first)
                },
                onClick = { picked?.let(viewModel::select) },
                enabled = picked != null,
                loading = state.isLoading,
                showArrow = picked != null,
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
    val shape = RoundedCornerShape(dimen(R.dimen.radius_16))
    val contentColor = if (selected) EduTheme.colors.onBrand else EduTheme.colors.ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) EduTheme.colors.lime else EduTheme.colors.surface,
                shape,
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = dimen(R.dimen.space_18), vertical = dimen(R.dimen.space_16)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = vark.letter(),
            style = EduTheme.type.cardTitle.copy(
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.width(dimen(R.dimen.size_26)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = vark.displayLabel(),
                    style = EduTheme.type.cardTitle.copy(color = contentColor),
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = EduIcons.Check,
                        contentDescription = null,
                        tint = EduTheme.colors.onBrand,
                        modifier = Modifier.size(dimen(R.dimen.size_18)),
                    )
                }
            }
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            Text(
                text = vark.description(),
                style = EduTheme.type.bodySmall.copy(color = if (selected) EduTheme.colors.onBrand else EduTheme.colors.muted),
            )
        }
    }
}

internal fun VarkType.letter(): String = when (this) {
    VarkType.VISUAL -> "V"
    VarkType.AURAL -> "A"
    VarkType.READ_WRITE -> "R"
    VarkType.KINESTHETIC -> "K"
}

@Composable
internal fun VarkType.displayLabel(): String = when (this) {
    VarkType.AURAL -> stringResource(R.string.vark_label_aural)
    else -> label
}

@Composable
internal fun VarkType.description(): String = when (this) {
    VarkType.VISUAL -> stringResource(R.string.vark_desc_visual)
    VarkType.AURAL -> stringResource(R.string.vark_desc_aural)
    VarkType.READ_WRITE -> stringResource(R.string.vark_desc_read_write)
    VarkType.KINESTHETIC -> stringResource(R.string.vark_desc_kinesthetic)
}

@Preview(showBackground = true)
@Composable
private fun VarkSelectionScreenPreview() {
    EduVarkTheme {
        VarkSelectionScreen(onSelected = {})
    }
}
