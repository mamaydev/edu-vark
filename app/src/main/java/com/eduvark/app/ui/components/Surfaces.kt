package com.eduvark.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

/** Visual tone for tinted bars, notices and status pills. */
enum class TintTone {
    Primary,
    Teal,
    Amber,
    Danger,
    Neutral,
}

@Composable
fun TintTone.tint(): Color = when (this) {
    TintTone.Primary -> EduTheme.colors.purpleTint
    TintTone.Teal -> EduTheme.colors.successTint
    TintTone.Amber -> EduTheme.colors.amberTint
    TintTone.Danger -> EduTheme.colors.dangerTint
    TintTone.Neutral -> EduTheme.colors.surface
}

@Composable
fun TintTone.content(): Color = when (this) {
    TintTone.Primary -> EduTheme.colors.lavender
    TintTone.Teal -> EduTheme.colors.success
    TintTone.Amber -> EduTheme.colors.amber
    TintTone.Danger -> EduTheme.colors.dangerMuted
    TintTone.Neutral -> EduTheme.colors.muted
}

/** Dark rounded container used for almost every block in the designs. */
@Composable
fun EduCard(
    modifier: Modifier = Modifier,
    containerColor: Color = EduTheme.colors.surface,
    radius: Dp = dimen(R.dimen.dimen_20),
    padding: Dp = dimen(R.dimen.dimen_20),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .then(clickModifier)
            .padding(padding),
        content = content,
    )
}

/** Lavender brand card used for the primary summary block of a screen. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    radius: Dp = dimen(R.dimen.dimen_20),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EduCard(
        modifier = modifier,
        containerColor = EduTheme.colors.lavender,
        radius = radius,
        onClick = onClick,
        content = content,
    )
}

/** All-caps label placed above headlines. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EduTheme.colors.lime,
    align: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        style = EduTheme.type.eyebrow.copy(color = color),
        modifier = modifier,
        textAlign = align,
    )
}

/** Full-width tinted bar (chips such as "TOPIK 03 • VISUAL" or "Aktif"). */
@Composable
fun TintBar(
    text: String,
    modifier: Modifier = Modifier,
    tone: TintTone = TintTone.Primary,
    uppercase: Boolean = false,
    background: Color? = null,
    contentColor: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimen(R.dimen.radius_10)))
            .background(background ?: tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_10)))
            .padding(horizontal = dimen(R.dimen.radius_12), vertical = dimen(R.dimen.radius_8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (uppercase) text.uppercase() else text,
            style = EduTheme.type.chip.copy(
                color = contentColor ?: tone.content(),
                letterSpacing = if (uppercase) dimenSp(R.dimen.text_tracking_0_8) else dimenSp(R.dimen.text_tracking_0_2),
            ),
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.Center,
        )
    }
}

/** Small inline pill (used for statuses inside list rows). */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: TintTone = TintTone.Teal,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimen(R.dimen.radius_8)))
            .background(tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_8)))
            .padding(horizontal = dimen(R.dimen.radius_12), vertical = dimen(R.dimen.radius_7)),
    ) {
        Text(text = text, style = EduTheme.type.chip.copy(color = tone.content()))
    }
}

/** Tinted information / warning block. */
@Composable
fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: TintTone = TintTone.Primary,
    title: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimen(R.dimen.radius_16)))
            .background(tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_16)))
            .padding(horizontal = dimen(R.dimen.radius_16), vertical = dimen(R.dimen.radius_14)),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = EduTheme.type.bodyStrong.copy(color = tone.content()),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
        }
        Text(
            text = text,
            style = EduTheme.type.bodySmall.copy(color = tone.content()),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = EduTheme.type.section, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextLink(text = actionLabel, onClick = onAction)
        }
    }
}

/** Surface tile with a big number and a caption (dashboard stats). */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    EduCard(modifier = modifier, radius = dimen(R.dimen.dimen_18), padding = dimen(R.dimen.dimen_18)) {
        Text(text = value, style = EduTheme.type.numeral.copy(color = EduTheme.colors.ink))
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(text = label, style = EduTheme.type.bodySmall)
    }
}

/** Big success check block used after saving a feedback. */
@Composable
fun SuccessPanel(
    modifier: Modifier = Modifier,
    height: Dp = dimen(R.dimen.dimen_110),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(dimen(R.dimen.radius_20)))
            .background(EduTheme.colors.successTint, RoundedCornerShape(dimen(R.dimen.radius_20))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = EduIcons.Check,
            contentDescription = null,
            tint = EduTheme.colors.success,
            modifier = Modifier.size(dimen(R.dimen.size_42)),
        )
    }
}

/** Thin divider that matches the top/bottom bar hairlines. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dimen(R.dimen.size_1))
            .background(EduTheme.colors.border),
    )
}

/** Centered empty/placeholder block with optional action. */
@Composable
fun EmptyBlock(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tone: TintTone = TintTone.Primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimen(R.dimen.radius_20)))
            .background(tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_20)))
            .padding(dimen(R.dimen.radius_22)),
    ) {
        Text(text = title, style = EduTheme.type.cardTitle)
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(text = message, style = EduTheme.type.bodySmall)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_14)))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Full-screen centered loader. */
@Composable
fun LoadingBlock(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val text = message ?: stringResource(R.string.common_loading)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = EduTheme.colors.lime,
                modifier = Modifier.size(dimen(R.dimen.size_28)),
                strokeWidth = dimen(R.dimen.size_2_5),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            Text(text = text, style = EduTheme.type.bodySmall)
        }
    }
}

/** Error card with retry action. */
@Composable
fun ErrorBlock(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        NoticeCard(
            text = message,
            tone = TintTone.Danger,
            title = stringResource(R.string.common_load_error_title),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_12)))
            SecondaryButton(text = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}
