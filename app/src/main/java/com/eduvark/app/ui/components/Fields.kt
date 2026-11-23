package com.eduvark.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduvark.app.ui.theme.EduTheme
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimen

@Composable
@ReadOnlyComposable
private fun fieldShape() = RoundedCornerShape(dimen(R.dimen.radius_12))

/** Small caption placed above an input, matching the designs. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = EduTheme.type.label,
        modifier = modifier.padding(bottom = dimen(R.dimen.space_8)),
    )
}

/** Outlined, transparent input used for every form field in the redesign. */
@Composable
fun EduField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Dp = dimen(R.dimen.dimen_52),
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = EduTheme.type.body.copy(color = EduTheme.colors.ink),
    trailing: (@Composable () -> Unit)? = null,
) {
    val borderColor = if (error) EduTheme.colors.danger else EduTheme.colors.fieldBorder
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(EduTheme.colors.lime),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(fieldShape())
            .border(dimen(R.dimen.size_1), borderColor, fieldShape()),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(horizontal = dimen(R.dimen.space_16)),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (singleLine) dimen(R.dimen.space_0) else dimen(R.dimen.space_14)),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = EduTheme.type.body.copy(color = EduTheme.colors.muted),
                        )
                    }
                    innerTextField()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(dimen(R.dimen.space_10)))
                    trailing()
                }
            }
        },
    )
}

/**
 * Password input with an accessible show/hide control. The toggle only swaps
 * the visual transformation, so the underlying value, cursor, and focus are
 * preserved.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    error: Boolean = false,
    fieldLabel: String? = null,
    leadingLabel: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val label = leadingLabel ?: stringResource(R.string.common_password)
    Column(modifier = modifier.fillMaxWidth()) {
        if (fieldLabel != null) {
            FieldLabel(fieldLabel)
        }
        EduField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = enabled,
            error = error,
            keyboardType = KeyboardType.Password,
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) EduIcons.EyeOff else EduIcons.Eye,
                        contentDescription = if (visible) {
                            stringResource(R.string.common_hide_password, label)
                        } else {
                            stringResource(R.string.common_show_password, label)
                        },
                        tint = EduTheme.colors.muted,
                        modifier = Modifier.size(dimen(R.dimen.size_20)),
                    )
                }
            },
        )
    }
}

/** Dropdown built on the same visual as [EduField]. */
@Composable
fun EduDropdown(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimen(R.dimen.size_52))
                .clip(fieldShape())
                .border(dimen(R.dimen.size_1), EduTheme.colors.fieldBorder, fieldShape())
                .clickable(enabled = enabled && options.isNotEmpty()) { expanded = true }
                .padding(horizontal = dimen(R.dimen.space_16)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    style = EduTheme.type.body.copy(
                        color = if (value.isBlank()) EduTheme.colors.muted else EduTheme.colors.ink,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = EduIcons.ChevronDown,
                    contentDescription = null,
                    tint = EduTheme.colors.muted,
                    modifier = Modifier.size(dimen(R.dimen.size_18)),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = EduTheme.colors.surface,
            shape = RoundedCornerShape(dimen(R.dimen.radius_12)),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option, style = EduTheme.type.bodyStrong) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Compact number stepper used for the topic order field. */
@Composable
fun EduStepper(
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimen(R.dimen.size_52))
            .clip(fieldShape())
            .border(dimen(R.dimen.size_1), EduTheme.colors.fieldBorder, fieldShape())
            .padding(horizontal = dimen(R.dimen.space_16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.toString().padStart(2, '0'),
            style = EduTheme.type.bodyStrong,
            modifier = Modifier.weight(1f),
        )
        StepButton(label = stringResource(R.string.common_stepper_decrease), onClick = onDecrease)
        Spacer(Modifier.width(dimen(R.dimen.space_10)))
        StepButton(label = stringResource(R.string.common_stepper_increase), onClick = onIncrease)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(dimen(R.dimen.size_30))
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = EduTheme.type.cardTitle.copy(color = EduTheme.colors.muted))
    }
}

/** Radio row used by the account role picker. */
@Composable
fun EduRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimen(R.dimen.radius_8)))
            .clickable(onClick = onClick)
            .padding(vertical = dimen(R.dimen.radius_9)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimen(R.dimen.size_18))
                .clip(CircleShape)
                .border(
                    width = dimen(R.dimen.size_1_6),
                    color = if (selected) EduTheme.colors.lime else EduTheme.colors.mutedSoft,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(dimen(R.dimen.size_9))
                        .clip(CircleShape)
                        .background(EduTheme.colors.lime),
                )
            }
        }
        Spacer(Modifier.width(dimen(R.dimen.space_12)))
        Text(
            text = label,
            style = EduTheme.type.bodyStrong.copy(
                color = if (selected) EduTheme.colors.lime else EduTheme.colors.ink,
            ),
        )
    }
}

/** Character counter / helper line below a field. */
@Composable
fun FieldHelper(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text = text,
        style = EduTheme.type.bodySmall.copy(
            color = if (isError) EduTheme.colors.danger else EduTheme.colors.muted,
        ),
        modifier = modifier.padding(top = dimen(R.dimen.space_6)),
    )
}

/** Label + value pair for read-only summaries. */
@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = EduTheme.type.bodySmall)
        Text(text = value, style = EduTheme.type.bodySmall.copy(color = EduTheme.colors.ink))
    }
}
