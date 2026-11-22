package com.eduvark.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * The EduVARK designs only define a light appearance (docs/Desain), so the app
 * is rendered light-only to keep every screen identical to its mockup.
 */
private val EduColorScheme = lightColorScheme(
    primary = EduColors.Primary,
    onPrimary = Color.White,
    primaryContainer = EduColors.PrimaryTint,
    onPrimaryContainer = EduColors.Primary,
    secondary = EduColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = EduColors.TealTint,
    onSecondaryContainer = EduColors.Teal,
    tertiary = EduColors.Amber,
    onTertiary = Color.White,
    tertiaryContainer = EduColors.AmberTint,
    onTertiaryContainer = EduColors.Amber,
    error = EduColors.Danger,
    onError = Color.White,
    errorContainer = EduColors.DangerTint,
    onErrorContainer = EduColors.Danger,
    background = EduColors.Page,
    onBackground = EduColors.Ink,
    surface = EduColors.Surface,
    onSurface = EduColors.Ink,
    surfaceVariant = EduColors.PrimaryTint,
    onSurfaceVariant = EduColors.Muted,
    surfaceContainer = EduColors.Surface,
    surfaceContainerHigh = EduColors.Surface,
    outline = EduColors.Border,
    outlineVariant = EduColors.Border,
    scrim = Color(0x66162947),
)

private val EduShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EduVarkTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = EduColorScheme,
        typography = Typography,
        shapes = EduShapes,
        content = content,
    )
}
