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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduvark.app.ui.theme.EduColors
import com.eduvark.app.ui.theme.EduText

private val FieldShape = RoundedCornerShape(12.dp)

/** Small caption placed above an input, matching the designs. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = EduText.Label,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** White rounded input used for every form field in the redesign. */
@Composable
fun EduField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = EduText.Body.copy(color = EduColors.Ink),
    trailing: (@Composable () -> Unit)? = null,
) {
    val borderColor = if (error) EduColors.Danger else EduColors.Border
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(EduColors.Primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(FieldShape)
            .background(EduColors.Surface, FieldShape)
            .border(1.dp, borderColor, FieldShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (singleLine) 0.dp else 14.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = EduText.Body.copy(color = EduColors.Muted),
                        )
                    }
                    innerTextField()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(10.dp))
                    trailing()
                }
            }
        },
    )
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
                .heightIn(min = 52.dp)
                .clip(FieldShape)
                .background(EduColors.Surface, FieldShape)
                .border(1.dp, EduColors.Border, FieldShape)
                .clickable(enabled = enabled && options.isNotEmpty()) { expanded = true }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    style = EduText.Body.copy(
                        color = if (value.isBlank()) EduColors.Muted else EduColors.Ink,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = EduIcons.ChevronDown,
                    contentDescription = null,
                    tint = EduColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = EduColors.Surface,
            shape = RoundedCornerShape(12.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option, style = EduText.BodyStrong) },
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
            .heightIn(min = 52.dp)
            .clip(FieldShape)
            .background(EduColors.Surface, FieldShape)
            .border(1.dp, EduColors.Border, FieldShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.toString().padStart(2, '0'),
            style = EduText.BodyStrong,
            modifier = Modifier.weight(1f),
        )
        StepButton(label = "−", onClick = onDecrease)
        Spacer(Modifier.width(10.dp))
        StepButton(label = "+", onClick = onIncrease)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = EduText.CardTitle.copy(color = EduColors.Muted))
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = 1.6.dp,
                    color = if (selected) EduColors.Primary else EduColors.MutedSoft,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(EduColors.Primary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = EduText.BodyStrong.copy(
                color = if (selected) EduColors.Primary else EduColors.Ink,
            ),
        )
    }
}

/** Character counter / helper line below a field. */
@Composable
fun FieldHelper(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text = text,
        style = EduText.BodySmall.copy(
            color = if (isError) EduColors.Danger else EduColors.Muted,
        ),
        modifier = modifier.padding(top = 6.dp),
    )
}

/** Label + value pair for read-only summaries. */
@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = EduText.BodySmall)
        Text(text = value, style = EduText.BodySmall.copy(color = EduColors.Ink))
    }
}
