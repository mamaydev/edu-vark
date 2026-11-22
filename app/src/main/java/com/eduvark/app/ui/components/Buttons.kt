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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

private val ButtonShape = RoundedCornerShape(28.dp)

/** Solid blue pill button. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    showArrow: Boolean = false,
    height: Dp = 56.dp,
) {
    val active = enabled && !loading
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ButtonShape)
            .background(if (active) EduColors.Primary else EduColors.Primary.copy(alpha = 0.45f))
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(text = text, style = EduText.Button)
            if (showArrow && !loading) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = EduIcons.ArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** White pill button with a hairline border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    textColor: Color = EduColors.Ink,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ButtonShape)
            .background(EduColors.Surface)
            .border(1.dp, EduColors.Border, ButtonShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = EduText.Button.copy(color = textColor))
    }
}

/** Destructive filled pill button. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ButtonShape)
            .background(if (enabled) EduColors.Danger else EduColors.Danger.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = EduText.Button)
    }
}

/** White pill placed inside a blue hero card. */
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
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(EduColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, style = EduText.Button.copy(color = EduColors.Ink))
            if (showArrow) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = EduIcons.ArrowRight,
                    contentDescription = null,
                    tint = EduColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Inline teal link with an arrow, as used inside cards. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = EduColors.Teal,
    icon: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = EduText.Link.copy(color = color))
        if (icon) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = EduIcons.ArrowRight,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Tappable white row used by lists (name + chevron). */
@Composable
fun RowChevron(
    modifier: Modifier = Modifier,
    tint: Color = EduColors.MutedSoft,
) {
    Icon(
        imageVector = EduIcons.ChevronRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(18.dp),
    )
}
