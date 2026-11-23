package com.eduvark.app.core.common

/**
 * Shared identifier rules for the login screen and admin account creation.
 *
 * Students use a 10–11 digit NIM and lecturers use a 10 digit NIP/NIDN.
 * The admin account is the only exception and signs in with the `admin`
 * username.
 */
object LoginIdRules {
    const val ADMIN_LOGIN_ID = "admin"
    const val ACADEMIC_ID_MESSAGE = "NIM/NIP/NIDN harus 10–11 digit."
    const val LOGIN_ID_MESSAGE =
        "NIM/NIP/NIDN harus 10–11 digit, atau gunakan username admin."

    private val academicIdPattern = Regex("^[0-9]{10,11}$")

    fun isAcademicId(value: String): Boolean = academicIdPattern.matches(value.trim())

    fun isValidLoginId(value: String): Boolean {
        val trimmed = value.trim()
        return isAcademicId(trimmed) || trimmed.equals(ADMIN_LOGIN_ID, ignoreCase = true)
    }
}
