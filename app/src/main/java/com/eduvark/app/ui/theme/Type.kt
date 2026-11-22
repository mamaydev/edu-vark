package com.eduvark.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eduvark.app.R

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

/** Named text styles for the redesigned screens. */
object EduText {
    /** Page headline, e.g. "Siap memahami hal baru?". */
    val Display = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.6).sp,
        color = EduColors.Ink,
    )

    /** Slightly smaller headline used on confirmation screens. */
    val DisplaySmall = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.4).sp,
        color = EduColors.Ink,
    )

    /** Headline inside a blue hero card. */
    val HeroTitle = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.4).sp,
        color = EduColors.Surface,
    )

    /** Big numerals (stats, scores). */
    val Numeral = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
        color = EduColors.Ink,
    )

    /** Very big numerals (feedback percentage, grade). */
    val NumeralLarge = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1).sp,
        color = EduColors.Surface,
    )

    /** Card title / large emphasis. */
    val Headline = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
        color = EduColors.Ink,
    )

    /** Section heading inside a screen. */
    val Section = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
        color = EduColors.Ink,
    )

    /** Title of a card or list row. */
    val CardTitle = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        color = EduColors.Ink,
    )

    val Body = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        color = EduColors.Muted,
    )

    val BodyStrong = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        color = EduColors.Ink,
    )

    val BodySmall = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = EduColors.Muted,
    )

    val Meta = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = EduColors.Muted,
    )

    val Label = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = EduColors.Ink,
    )

    /** All-caps label above headlines. */
    val Eyebrow = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
        color = EduColors.Teal,
    )

    val Button = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = EduColors.Surface,
    )

    val Chip = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    )

    val NavLabel = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    /** Inline link with arrow, e.g. "Lihat materi". */
    val Link = TextStyle(
        fontFamily = EduFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = EduColors.Teal,
    )
}

/** Material typography mapped onto the design scale. */
val Typography = Typography(
    displayLarge = EduText.Display,
    displayMedium = EduText.Display,
    displaySmall = EduText.DisplaySmall,
    headlineLarge = EduText.Display,
    headlineMedium = EduText.DisplaySmall,
    headlineSmall = EduText.Headline,
    titleLarge = EduText.Headline,
    titleMedium = EduText.Section,
    titleSmall = EduText.CardTitle,
    bodyLarge = EduText.Body,
    bodyMedium = EduText.BodySmall,
    bodySmall = EduText.BodySmall,
    labelLarge = EduText.Button,
    labelMedium = EduText.Label,
    labelSmall = EduText.Eyebrow,
)
