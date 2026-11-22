package com.eduvark.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VarkTypeTest {

    @Test
    fun `wire values match the API contract`() {
        assertEquals(
            setOf("VISUAL", "AURAL", "READ_WRITE", "KINESTHETIC"),
            VarkType.entries.map { it.wireValue }.toSet(),
        )
    }

    @Test
    fun `aural is labelled Audio`() {
        assertEquals("Audio", VarkType.AURAL.label)
    }

    @Test
    fun `unknown wire value is rejected`() {
        assertNull(VarkType.fromWire("AUDIO"))
        assertNull(VarkType.fromWire(""))
    }
}
