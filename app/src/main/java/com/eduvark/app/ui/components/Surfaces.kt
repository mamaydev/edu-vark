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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

/** Visual tone for tinted bars, notices and status pills. */
enum class TintTone {
    Primary,
    Teal,
    Amber,
    Danger,
    Neutral,
}

fun TintTone.tint(): Color = when (this) {
    TintTone.Primary -> EduColors.PrimaryTint
    TintTone.Teal -> EduColors.TealTint
    TintTone.Amber -> EduColors.AmberTint
    TintTone.Danger -> EduColors.DangerTint
    TintTone.Neutral -> EduColors.Border.copy(alpha = 0.45f)
}

fun TintTone.content(): Color = when (this) {
    TintTone.Primary -> EduColors.Primary
    TintTone.Teal -> EduColors.Teal
    TintTone.Amber -> EduColors.Amber
    TintTone.Danger -> EduColors.Danger
    TintTone.Neutral -> EduColors.Muted
}

/** White rounded container used for almost every block in the designs. */
@Composable
fun EduCard(
    modifier: Modifier = Modifier,
    containerColor: Color = EduColors.Surface,
    radius: Dp = 20.dp,
    padding: Dp = 20.dp,
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

/** Blue card used for the primary summary block of a screen. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EduCard(
        modifier = modifier,
        containerColor = EduColors.Primary,
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
    color: Color = EduColors.Teal,
    align: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        style = EduText.Eyebrow.copy(color = color),
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone.tint(), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (uppercase) text.uppercase() else text,
            style = EduText.Chip.copy(
                color = tone.content(),
                letterSpacing = if (uppercase) 0.8.sp else 0.2.sp,
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
            .clip(RoundedCornerShape(8.dp))
            .background(tone.tint(), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = EduText.Chip.copy(color = tone.content()))
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
            .clip(RoundedCornerShape(16.dp))
            .background(tone.tint(), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = EduText.BodyStrong.copy(color = tone.content()),
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = text,
            style = EduText.BodySmall.copy(color = tone.content()),
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
        Text(text = title, style = EduText.Section, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextLink(text = actionLabel, onClick = onAction)
        }
    }
}

/** White tile with a big number and a caption (dashboard stats). */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    EduCard(modifier = modifier, radius = 18.dp, padding = 18.dp) {
        Text(text = value, style = EduText.Numeral.copy(color = EduColors.Primary))
        Spacer(Modifier.height(6.dp))
        Text(text = label, style = EduText.BodySmall)
    }
}

/** Big success check block used after saving a feedback. */
@Composable
fun SuccessPanel(
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(EduColors.TealTint, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = EduIcons.Check,
            contentDescription = null,
            tint = EduColors.Teal,
            modifier = Modifier.size(42.dp),
        )
    }
}

/** Thin divider that matches the top/bottom bar hairlines. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(EduColors.Border),
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
            .clip(RoundedCornerShape(20.dp))
            .background(tone.tint(), RoundedCornerShape(20.dp))
            .padding(22.dp),
    ) {
        Text(text = title, style = EduText.CardTitle)
        Spacer(Modifier.height(6.dp))
        Text(text = message, style = EduText.BodySmall)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Full-screen centered loader. */
@Composable
fun LoadingBlock(
    modifier: Modifier = Modifier,
    message: String = "Memuat…",
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(
                color = EduColors.Primary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(text = message, style = EduText.BodySmall)
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
        NoticeCard(text = message, tone = TintTone.Danger, title = "Tidak dapat dimuat")
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Coba lagi", onClick = onRetry)
        }
    }
}
