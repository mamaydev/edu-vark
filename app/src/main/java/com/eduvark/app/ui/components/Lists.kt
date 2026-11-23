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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen
import com.eduvark.app.ui.theme.dimenSp

/** Base card for list rows (topic, user, submission). */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EduCard(
        modifier = modifier.fillMaxWidth(),
        radius = dimen(R.dimen.dimen_18),
        padding = dimen(R.dimen.dimen_18),
        onClick = onClick,
        content = content,
    )
}

/** Eyebrow used inside list cards, e.g. "TOPIK 03  /  IMPLEMENTASI BASIS DATA". */
@Composable
fun CardEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = EduTheme.type.meta.copy(
            fontSize = dimenSp(R.dimen.text_size_10),
            lineHeight = dimenSp(R.dimen.text_size_14),
            letterSpacing = dimenSp(R.dimen.text_size_0_7),
            fontWeight = FontWeight.Bold,
            color = EduTheme.colors.muted,
        ),
        modifier = modifier,
    )
}

/** Title with the small trailing arrow used across the designs. */
@Composable
fun ArrowTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = EduTheme.colors.ink,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = EduTheme.type.cardTitle.copy(color = color))
        Spacer(Modifier.size(dimen(R.dimen.space_6)))
        Icon(
            imageVector = EduIcons.ArrowRight,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(dimen(R.dimen.size_16)),
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
        Spacer(Modifier.height(dimen(R.dimen.space_8)))
        ArrowTitle(title)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(dimen(R.dimen.space_6)))
            Text(text = meta, style = EduTheme.type.bodySmall)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(
            text = status,
            style = EduTheme.type.bodySmall.copy(
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
        Spacer(Modifier.height(dimen(R.dimen.space_6)))
        Text(text = meta, style = EduTheme.type.bodySmall)
        Spacer(Modifier.height(dimen(R.dimen.space_12)))
        TintBar(text = status, tone = statusTone)
    }
}

/** Simple key/value chip row used in summaries. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = EduTheme.colors.ink,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = EduTheme.type.bodySmall, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = EduTheme.type.bodySmall.copy(
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
    height: androidx.compose.ui.unit.Dp = dimen(R.dimen.dimen_8),
) {
    val safe = fraction.coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(dimen(R.dimen.radius_999)))
            .background(EduTheme.colors.border),
    ) {
        if (safe > 0f) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .height(height)
                    .clip(RoundedCornerShape(dimen(R.dimen.radius_999)))
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
        style = EduTheme.type.bodySmall,
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
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
        }
        Text(text = title, style = EduTheme.type.cardTitle)
        if (meta != null) {
            Spacer(Modifier.height(dimen(R.dimen.space_4)))
            Text(text = meta, style = EduTheme.type.bodySmall)
        }
        Spacer(Modifier.height(dimen(R.dimen.space_10)))
        Text(text = body, style = EduTheme.type.body)
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
            .clip(RoundedCornerShape(dimen(R.dimen.radius_18)))
            .background(tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_18)))
            .padding(dimen(R.dimen.radius_18)),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = EduTheme.type.cardTitle.copy(color = tone.content(), fontSize = dimenSp(R.dimen.text_size_17)),
            )
            Spacer(Modifier.height(dimen(R.dimen.space_8)))
        }
        if (bullets.isEmpty()) {
            Text(text = body, style = EduTheme.type.bodySmall.copy(color = tone.content()))
        } else {
            bullets.forEach { bullet ->
                Row(modifier = Modifier.padding(bottom = dimen(R.dimen.space_4))) {
                    Text(
                        text = stringResource(R.string.common_bullet),
                        style = EduTheme.type.bodySmall.copy(color = tone.content()),
                    )
                    Spacer(Modifier.size(dimen(R.dimen.space_8)))
                    Text(
                        text = bullet,
                        style = EduTheme.type.bodySmall.copy(color = tone.content()),
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
            .clip(RoundedCornerShape(dimen(R.dimen.radius_999)))
            .background(tone.tint(), RoundedCornerShape(dimen(R.dimen.radius_999)))
            .padding(horizontal = dimen(R.dimen.radius_14), vertical = dimen(R.dimen.radius_8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = EduTheme.type.chip.copy(color = tone.content(), fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Small circular letter badge used by VARK pickers. */
@Composable
fun LetterBadge(letter: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(dimen(R.dimen.size_28))
            .clip(RoundedCornerShape(dimen(R.dimen.radius_9)))
            .background(
                if (highlighted) EduTheme.colors.lavender else EduTheme.colors.purpleTint,
                RoundedCornerShape(dimen(R.dimen.radius_9)),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = letter,
            style = EduTheme.type.cardTitle.copy(
                color = if (highlighted) EduTheme.colors.onBrand else EduTheme.colors.lavender,
                fontSize = dimenSp(R.dimen.text_size_15),
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
            .clip(RoundedCornerShape(dimen(R.dimen.radius_12)))
            .clickable(onClick = onClick)
            .padding(vertical = dimen(R.dimen.radius_6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
