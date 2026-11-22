package com.eduvark.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette taken directly from the EduVARK UI designs in `docs/Desain`.
 * Every value is sampled from the mockups so screens and designs stay in sync.
 */
object EduColors {
    /** App background (very light blue-grey). */
    val Page = Color(0xFFF7F8FC)

    /** Cards, sheets, top bars. */
    val Surface = Color(0xFFFFFFFF)

    /** Primary brand blue. */
    val Primary = Color(0xFF2557DE)

    /** Tinted blue used for selected cards, chips and info notes. */
    val PrimaryTint = Color(0xFFEAF0FF)

    /** Headline / body ink. */
    val Ink = Color(0xFF162947)

    /** Secondary text. */
    val Muted = Color(0xFF64748B)

    /** Inactive navigation icons and hints. */
    val MutedSoft = Color(0xFF98A2B3)

    /** Accent used for eyebrows, statuses and links. */
    val Teal = Color(0xFF087F79)

    /** Tinted teal used for success and objective metrics. */
    val TealTint = Color(0xFFE4F5EF)

    /** Warning text on [AmberTint]. */
    val Amber = Color(0xFF995F0B)

    /** Tinted amber used for pending / warning notes. */
    val AmberTint = Color(0xFFFFF4DE)

    /** Destructive text and buttons. */
    val Danger = Color(0xFFB93240)

    /** Tinted red used for errors and closed states. */
    val DangerTint = Color(0xFFFFF1F1)

    /** Hairlines and field borders. */
    val Border = Color(0xFFDFE8F1)

    /** Dark panel used by the video player card. */
    val MediaNavy = Color(0xFF162E54)

    /** Read/Write metric colour in analytics bars. */
    val Purple = Color(0xFF7354B2)
}
