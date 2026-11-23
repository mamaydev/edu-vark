package com.eduvark.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.eduvark.app.R

/**
 * Dark palette of the redesigned EduVARK mockups in `docs/Desain/redesain`
 * (artboards "Tema gelap · 01…40").
 *
 * The literal colour values live in `res/values/colors.xml`; this class only
 * exposes them with semantic names. Screens read the palette through
 * [EduTheme.colors] so Kotlin code never hardcodes a colour.
 */
data class EduPalette(
    // surfaces
    val page: Color = Color.Unspecified,
    val surface: Color = Color.Unspecified,
    // brand
    val lavender: Color = Color.Unspecified,
    val lavenderDim: Color = Color.Unspecified,
    val lime: Color = Color.Unspecified,
    val onBrand: Color = Color.Unspecified,
    // text
    val ink: Color = Color.Unspecified,
    val muted: Color = Color.Unspecified,
    val mutedSoft: Color = Color.Unspecified,
    // tints
    val purpleTint: Color = Color.Unspecified,
    val successTint: Color = Color.Unspecified,
    val amberTint: Color = Color.Unspecified,
    val dangerTint: Color = Color.Unspecified,
    // semantic hues
    val success: Color = Color.Unspecified,
    val amber: Color = Color.Unspecified,
    val danger: Color = Color.Unspecified,
    val dangerMuted: Color = Color.Unspecified,
    val purple: Color = Color.Unspecified,
    // lines
    val border: Color = Color.Unspecified,
    val fieldBorder: Color = Color.Unspecified,
    // panels
    val mediaNavy: Color = Color.Unspecified,
)

/**
 * Palette provided by [EduVarkTheme]. The default is intentionally empty:
 * a screen rendered outside the theme must not silently keep stale colours.
 */
val LocalEduPalette = staticCompositionLocalOf { EduPalette() }

/** Reads the palette from `res/values/colors.xml` inside a composition. */
@Composable
@ReadOnlyComposable
fun eduPalette(): EduPalette = EduPalette(
    page = colorResource(R.color.eduvark_page),
    surface = colorResource(R.color.eduvark_surface),
    lavender = colorResource(R.color.eduvark_lavender),
    lavenderDim = colorResource(R.color.eduvark_lavender_dim),
    lime = colorResource(R.color.eduvark_lime),
    onBrand = colorResource(R.color.eduvark_on_brand),
    ink = colorResource(R.color.eduvark_ink),
    muted = colorResource(R.color.eduvark_muted),
    mutedSoft = colorResource(R.color.eduvark_muted_soft),
    purpleTint = colorResource(R.color.eduvark_purple_tint),
    successTint = colorResource(R.color.eduvark_success_tint),
    amberTint = colorResource(R.color.eduvark_amber_tint),
    dangerTint = colorResource(R.color.eduvark_danger_tint),
    success = colorResource(R.color.eduvark_success),
    amber = colorResource(R.color.eduvark_amber),
    danger = colorResource(R.color.eduvark_danger),
    dangerMuted = colorResource(R.color.eduvark_danger_muted),
    purple = colorResource(R.color.eduvark_purple),
    border = colorResource(R.color.eduvark_border),
    fieldBorder = colorResource(R.color.eduvark_field_border),
    mediaNavy = colorResource(R.color.eduvark_media_navy),
)
