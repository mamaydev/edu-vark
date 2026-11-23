package com.eduvark.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.eduvark.app.R

/**
 * Entry points used by the screens to read the design tokens provided by
 * [EduVarkTheme]:
 *
 * ```kotlin
 * Text("Halo", style = EduTheme.type.body, color = EduTheme.colors.ink)
 * ```
 */
object EduTheme {
    val colors: EduPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalEduPalette.current

    val type: EduTypeStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalEduType.current
}

/**
 * The redesign (docs/Desain/redesain) only defines a dark appearance, so the app
 * always renders dark and only flips the system bar icons to light.
 */
@Composable
fun eduColorScheme(colors: EduPalette): androidx.compose.material3.ColorScheme = darkColorScheme(
    primary = colors.lime,
    onPrimary = colors.onBrand,
    primaryContainer = colors.lavender,
    onPrimaryContainer = colors.onBrand,
    secondary = colors.lavender,
    onSecondary = colors.onBrand,
    secondaryContainer = colors.purpleTint,
    onSecondaryContainer = colors.lavender,
    tertiary = colors.amber,
    onTertiary = colors.onBrand,
    tertiaryContainer = colors.amberTint,
    onTertiaryContainer = colors.amber,
    error = colors.danger,
    onError = colorResource(R.color.white),
    errorContainer = colors.dangerTint,
    onErrorContainer = colors.dangerMuted,
    background = colors.page,
    onBackground = colors.ink,
    surface = colors.surface,
    onSurface = colors.ink,
    surfaceVariant = colors.surface,
    onSurfaceVariant = colors.muted,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.surface,
    outline = colors.fieldBorder,
    outlineVariant = colors.border,
)

@Composable
fun eduShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(dimen(R.dimen.radius_8)),
    small = RoundedCornerShape(dimen(R.dimen.radius_12)),
    medium = RoundedCornerShape(dimen(R.dimen.radius_16)),
    large = RoundedCornerShape(dimen(R.dimen.radius_20)),
    extraLarge = RoundedCornerShape(dimen(R.dimen.radius_28)),
)

@Composable
fun EduVarkTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val palette = eduPalette()
    val type = eduTypeStyles(palette)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    CompositionLocalProvider(
        LocalEduPalette provides palette,
        LocalEduType provides type,
    ) {
        MaterialTheme(
            colorScheme = eduColorScheme(palette),
            typography = eduTypography(type),
            shapes = eduShapes(),
            content = content,
        )
    }
}
