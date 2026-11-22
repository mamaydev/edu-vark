package com.eduvark.app.domain.model

enum class VarkType(
    val wireValue: String,
    val label: String,
) {
    VISUAL("VISUAL", "Visual"),
    AURAL("AURAL", "Audio"),
    READ_WRITE("READ_WRITE", "Read/Write"),
    KINESTHETIC("KINESTHETIC", "Kinesthetic"),
    ;

    companion object {
        fun fromWire(value: String): VarkType? =
            entries.firstOrNull { it.wireValue == value }
    }
}
