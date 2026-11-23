package com.eduvark.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eduvark.app.R
import com.eduvark.app.ui.theme.dimenSp

/**
 * Plus Jakarta Sans is the typeface used by the UI designs (docs/Desain).
 * Bundled as static weights so every API level renders identically.
 */
val EduFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/**
 * Named text styles of the redesigned screens.
 *
 * Sizes, line heights, tracking and colours are resolved from resources
 * (`res/values/dimens.xml` + `res/values/colors.xml`) so no screen hardcodes a
 * typography value. Screens read the styles through [EduTheme.type].
 */
data class EduTypeStyles(
    val display: TextStyle = TextStyle.Default,
    val displaySmall: TextStyle = TextStyle.Default,
    val heroTitle: TextStyle = TextStyle.Default,
    val numeral: TextStyle = TextStyle.Default,
    val numeralLarge: TextStyle = TextStyle.Default,
    val headline: TextStyle = TextStyle.Default,
    val section: TextStyle = TextStyle.Default,
    val cardTitle: TextStyle = TextStyle.Default,
    val body: TextStyle = TextStyle.Default,
    val bodyStrong: TextStyle = TextStyle.Default,
    val bodySmall: TextStyle = TextStyle.Default,
    val meta: TextStyle = TextStyle.Default,
    val label: TextStyle = TextStyle.Default,
    val eyebrow: TextStyle = TextStyle.Default,
    val button: TextStyle = TextStyle.Default,
    val chip: TextStyle = TextStyle.Default,
    val navLabel: TextStyle = TextStyle.Default,
    val link: TextStyle = TextStyle.Default,
)

val LocalEduType = staticCompositionLocalOf { EduTypeStyles() }

/** Builds the named text styles from the palette and the dimens resources. */
@Composable
@ReadOnlyComposable
fun eduTypeStyles(palette: EduPalette): EduTypeStyles = EduTypeStyles(
    display = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_30),
        lineHeight = dimenSp(R.dimen.text_line_37),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_6),
        color = palette.ink,
    ),
    displaySmall = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_26),
        lineHeight = dimenSp(R.dimen.text_line_33),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_4),
        color = palette.ink,
    ),
    heroTitle = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_26),
        lineHeight = dimenSp(R.dimen.text_line_33),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_4),
        color = palette.onBrand,
    ),
    numeral = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_34),
        lineHeight = dimenSp(R.dimen.text_line_40),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_8),
        color = palette.ink,
    ),
    numeralLarge = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_44),
        lineHeight = dimenSp(R.dimen.text_line_50),
        letterSpacing = dimenSp(R.dimen.text_tracking_n1),
        color = palette.onBrand,
    ),
    headline = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_22),
        lineHeight = dimenSp(R.dimen.text_line_28),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_3),
        color = palette.ink,
    ),
    section = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_19),
        lineHeight = dimenSp(R.dimen.text_line_25),
        letterSpacing = dimenSp(R.dimen.text_tracking_n0_2),
        color = palette.ink,
    ),
    cardTitle = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_17),
        lineHeight = dimenSp(R.dimen.text_line_23),
        color = palette.ink,
    ),
    body = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = dimenSp(R.dimen.text_size_15),
        lineHeight = dimenSp(R.dimen.text_line_23),
        color = palette.muted,
    ),
    bodyStrong = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = dimenSp(R.dimen.text_size_15),
        lineHeight = dimenSp(R.dimen.text_line_23),
        color = palette.ink,
    ),
    bodySmall = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = dimenSp(R.dimen.text_size_13),
        lineHeight = dimenSp(R.dimen.text_line_20),
        color = palette.muted,
    ),
    meta = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = dimenSp(R.dimen.text_size_13),
        lineHeight = dimenSp(R.dimen.text_line_19),
        color = palette.muted,
    ),
    label = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = dimenSp(R.dimen.text_size_13),
        lineHeight = dimenSp(R.dimen.text_line_18),
        color = palette.ink,
    ),
    eyebrow = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = dimenSp(R.dimen.text_size_11),
        lineHeight = dimenSp(R.dimen.text_line_14),
        letterSpacing = dimenSp(R.dimen.text_tracking_1_2),
        color = palette.lime,
    ),
    button = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_15),
        lineHeight = dimenSp(R.dimen.text_line_20),
        color = palette.onBrand,
    ),
    chip = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_12),
        lineHeight = dimenSp(R.dimen.text_line_16),
        letterSpacing = dimenSp(R.dimen.text_tracking_0_2),
    ),
    navLabel = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = dimenSp(R.dimen.text_size_11),
        lineHeight = dimenSp(R.dimen.text_line_14),
    ),
    link = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = dimenSp(R.dimen.text_size_13),
        lineHeight = dimenSp(R.dimen.text_line_19),
        color = palette.lavenderDim,
    ),
)

/** Material typography mapped onto the design scale. */
fun eduTypography(type: EduTypeStyles): Typography = Typography(
    displayLarge = type.display,
    displayMedium = type.display,
    displaySmall = type.displaySmall,
    headlineLarge = type.display,
    headlineMedium = type.displaySmall,
    headlineSmall = type.headline,
    titleLarge = type.headline,
    titleMedium = type.section,
    titleSmall = type.cardTitle,
    bodyLarge = type.body,
    bodyMedium = type.bodySmall,
    bodySmall = type.bodySmall,
    labelLarge = type.button,
    labelMedium = type.label,
    labelSmall = type.eyebrow,
)
