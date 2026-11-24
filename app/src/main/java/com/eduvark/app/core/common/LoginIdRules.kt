package com.eduvark.app.core.common

/**
 * Shared identifier rules for login and admin account management (FR-001/034/036).
 *
 * Every role signs in with a numeric identifier: students use a 10–11 digit
 * NIM, lecturers use their NIP/NIDN, and Admins use a system-issued
 * `014014XXXXX` number. The legacy `admin` username is retired.
 */
object LoginIdRules {
    const val ADMIN_LOGIN_ID_PREFIX = "014014"
    const val MAX_LENGTH = 11
    const val MIN_LENGTH = 10
    const val ACADEMIC_ID_MESSAGE = "NIM/NIDN harus berupa 10–11 digit angka."
    const val LOGIN_ID_MESSAGE = "NIM/NIDN harus berupa 10–11 digit angka."

    private val academicIdPattern = Regex("^[0-9]{10,11}$")
    private val adminIdPattern = Regex("^014014[0-9]{5}$")

    /**
     * Keeps only ASCII digits 0–9 and stops accepting input after position 11.
     * Applied on every text change so both typing and pasting obey the limit.
     */
    fun filterDigits(value: String): String =
        value.filter { it in '0'..'9' }.take(MAX_LENGTH)

    fun isAcademicId(value: String): Boolean = academicIdPattern.matches(value.trim())

    fun isAdminId(value: String): Boolean = adminIdPattern.matches(value.trim())

    /** Login accepts either academic or Admin-shaped identifiers. */
    fun isValidLoginId(value: String): Boolean =
        isAcademicId(value) || isAdminId(value)

    /** Submit is enabled only at a plausible identifier length (10–11 digits). */
    fun canSubmit(value: String): Boolean =
        value.length in MIN_LENGTH..MAX_LENGTH
}
