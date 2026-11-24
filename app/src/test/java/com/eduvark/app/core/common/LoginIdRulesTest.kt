package com.eduvark.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginIdRulesTest {

    @Test
    fun `typing keeps digits only`() {
        assertEquals("2025000001", LoginIdRules.filterDigits("2025abc0000-01"))
    }

    @Test
    fun `input stops accepting after eleven digits`() {
        assertEquals("20250000011", LoginIdRules.filterDigits("202500000112345"))
    }

    @Test
    fun `paste with separators is cleaned and truncated`() {
        assertEquals("20250000011", LoginIdRules.filterDigits("2025 0000 0111"))
    }

    @Test
    fun `academic ids accept ten or eleven digits`() {
        assertTrue(LoginIdRules.isAcademicId("2025000001"))
        assertTrue(LoginIdRules.isAcademicId("20250000011"))
        assertFalse(LoginIdRules.isAcademicId("202500000"))
        assertFalse(LoginIdRules.isAcademicId("202500000112"))
        assertFalse(LoginIdRules.isAcademicId("20250000AB"))
        assertFalse(LoginIdRules.isAcademicId(""))
    }

    @Test
    fun `leading zero ids are valid`() {
        assertTrue(LoginIdRules.isAcademicId("0025000001"))
    }

    @Test
    fun `admin ids require the 014014 prefix and five digits`() {
        assertTrue(LoginIdRules.isAdminId("01401400001"))
        assertTrue(LoginIdRules.isAdminId("01401499999"))
        assertFalse(LoginIdRules.isAdminId("0140140000"))
        assertFalse(LoginIdRules.isAdminId("01401500001"))
        assertFalse(LoginIdRules.isAdminId("20250000011"))
    }

    @Test
    fun `login validation rejects the legacy admin username`() {
        assertFalse(LoginIdRules.isValidLoginId("admin"))
        assertTrue(LoginIdRules.isValidLoginId("2025000001"))
        assertTrue(LoginIdRules.isValidLoginId("01401400001"))
    }

    @Test
    fun `submit is gated between ten and eleven digits`() {
        assertFalse(LoginIdRules.canSubmit(""))
        assertFalse(LoginIdRules.canSubmit("202500000"))
        assertTrue(LoginIdRules.canSubmit("2025000001"))
        assertTrue(LoginIdRules.canSubmit("01401400001"))
        assertFalse(LoginIdRules.canSubmit("admin"))
    }
}
