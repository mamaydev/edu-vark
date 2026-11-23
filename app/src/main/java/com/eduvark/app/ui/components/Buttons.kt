package com.eduvark.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
@ReadOnlyComposable
private fun buttonShape() = RoundedCornerShape(dimen(R.dimen.radius_28))

/** Solid lime pill button (primary action). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    showArrow: Boolean = false,
    height: Dp = dimen(R.dimen.dimen_56),
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(buttonShape())
            .background(if (active) EduTheme.colors.lime else EduTheme.colors.lime.copy(alpha = 0.4f))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = EduTheme.colors.onBrand,
                    strokeWidth = dimen(R.dimen.dimen_2),
                    modifier = Modifier.size(dimen(R.dimen.size_18)),
                )
                Spacer(Modifier.width(dimen(R.dimen.space_10)))
            }
            Text(text = text, style = EduTheme.type.button)
            if (showArrow && !loading) {
                Spacer(Modifier.width(dimen(R.dimen.space_8)))
                Icon(
                    imageVector = EduIcons.ArrowRight,
                    contentDescription = null,
                    tint = EduTheme.colors.onBrand,
                    modifier = Modifier.size(dimen(R.dimen.size_18)),
                )
            }
        }
    }
}

/** Outlined (lavender-dim) pill button for secondary actions. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = dimen(R.dimen.dimen_56),
    textColor: Color = EduTheme.colors.ink,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(buttonShape())
            .border(dimen(R.dimen.size_1), EduTheme.colors.lavenderDim, buttonShape())
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = EduTheme.type.button.copy(color = textColor))
    }
}

/** Destructive filled pill button. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = dimen(R.dimen.dimen_56),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(buttonShape())
            .background(if (enabled) EduTheme.colors.danger else EduTheme.colors.danger.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = EduTheme.type.button.copy(color = Color.White))
    }
}

/** Ghost pill placed on a lavender hero card: dark ink, no fill. */
@Composable
fun LightButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimen(R.dimen.size_48))
            .clip(RoundedCornerShape(dimen(R.dimen.radius_14)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = EduTheme.type.button.copy(color = EduTheme.colors.onBrand))
            if (showArrow) {
                Spacer(Modifier.width(dimen(R.dimen.space_8)))
                Icon(
                    imageVector = EduIcons.ArrowRight,
                    contentDescription = null,
                    tint = EduTheme.colors.onBrand,
                    modifier = Modifier.size(dimen(R.dimen.size_16)),
                )
            }
        }
    }
}

/** Inline lavender-dim link with an arrow, as used inside cards. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = EduTheme.colors.lavenderDim,
    icon: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimen(R.dimen.radius_6)))
            .clickable(onClick = onClick)
            .padding(vertical = dimen(R.dimen.radius_2)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = EduTheme.type.link.copy(color = color))
        if (icon) {
            Spacer(Modifier.width(dimen(R.dimen.space_4)))
            Icon(
                imageVector = EduIcons.ArrowRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(dimen(R.dimen.size_16)),
            )
        }
    }
}

/** Tappable row used by lists (name + chevron). */
@Composable
fun RowChevron(
    modifier: Modifier = Modifier,
    tint: Color = EduTheme.colors.mutedSoft,
) {
    Icon(
        imageVector = EduIcons.ChevronRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(dimen(R.dimen.size_18)),
    )
}
