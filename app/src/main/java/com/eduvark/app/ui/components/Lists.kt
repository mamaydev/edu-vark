package com.eduvark.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

/** Base card for list rows (topic, user, submission). */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EduCard(
        modifier = modifier.fillMaxWidth(),
        radius = 18.dp,
        padding = 18.dp,
        onClick = onClick,
        content = content,
    )
}

/** Eyebrow used inside list cards, e.g. "TOPIK 03  /  IMPLEMENTASI BASIS DATA". */
@Composable
fun CardEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = EduText.Meta.copy(
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.7.sp,
            fontWeight = FontWeight.Bold,
            color = EduColors.Muted,
        ),
        modifier = modifier,
    )
}

/** Title with the small trailing arrow used across the designs. */
@Composable
fun ArrowTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = EduColors.Ink,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = EduText.CardTitle.copy(color = color))
        Spacer(Modifier.size(6.dp))
        Icon(
            imageVector = EduIcons.ArrowRight,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Topic row used on the student topic list and the student dashboard.
 */
@Composable
fun TopicCard(
    orderLabel: String,
    title: String,
    meta: String,
    status: String,
    statusTone: TintTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ListCard(modifier = modifier, onClick = onClick) {
        CardEyebrow(orderLabel)
        Spacer(Modifier.height(8.dp))
        ArrowTitle(title)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = meta, style = EduText.BodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = status,
            style = EduText.BodySmall.copy(
                color = statusTone.content(),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Account row used by the admin lists and admin dashboard. */
@Composable
fun UserCard(
    name: String,
    meta: String,
    status: String,
    statusTone: TintTone,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ListCard(modifier = modifier, onClick = onClick) {
        ArrowTitle(name)
        Spacer(Modifier.height(6.dp))
        Text(text = meta, style = EduText.BodySmall)
        Spacer(Modifier.height(12.dp))
        TintBar(text = status, tone = statusTone)
    }
}

/** Simple key/value chip row used in summaries. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = EduColors.Ink,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = EduText.BodySmall, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = EduText.BodySmall.copy(
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/** Thin progress bar used by the analytics screen. */
@Composable
fun MetricBar(
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val safe = fraction.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(EduColors.Border),
    ) {
        if (safe > 0f) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

/** Inline "label • value" meta line with a leading dot separator. */
@Composable
fun DotMeta(parts: List<String>, modifier: Modifier = Modifier) {
    Text(
        text = parts.filter { it.isNotBlank() }.joinToString("  •  "),
        style = EduText.BodySmall,
        modifier = modifier,
    )
}

/** Section card used for long-form content (theory, answers, comments). */
@Composable
fun ContentCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    eyebrow: String? = null,
) {
    EduCard(modifier = modifier) {
        if (eyebrow != null) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(8.dp))
        }
        Text(text = title, style = EduText.CardTitle)
        if (meta != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = meta, style = EduText.BodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Text(text = body, style = EduText.Body)
    }
}

/** Rounded tinted panel used for inline notices with a heading. */
@Composable
fun TonePanel(
    title: String?,
    body: String,
    modifier: Modifier = Modifier,
    tone: TintTone = TintTone.Amber,
    bullets: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tone.tint(), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = EduText.CardTitle.copy(color = tone.content(), fontSize = 17.sp),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (bullets.isEmpty()) {
            Text(text = body, style = EduText.BodySmall.copy(color = tone.content()))
        } else {
            bullets.forEach { bullet ->
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(text = "•", style = EduText.BodySmall.copy(color = tone.content()))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = bullet,
                        style = EduText.BodySmall.copy(color = tone.content()),
                    )
                }
            }
        }
    }
}

/** Pill-shaped inline chip (design: "Visual aktif • Tidak dapat diganti"). */
@Composable
fun InlineChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: TintTone = TintTone.Primary,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.tint(), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = EduText.Chip.copy(color = tone.content(), fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Small circular letter badge used by VARK pickers. */
@Composable
fun LetterBadge(letter: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (highlighted) EduColors.Surface else EduColors.PrimaryTint,
                RoundedCornerShape(9.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = letter,
            style = EduText.CardTitle.copy(
                color = EduColors.Primary,
                fontSize = 15.sp,
            ),
        )
    }
}

/** Row wrapper that turns any content into a tappable list item. */
@Composable
fun ClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
