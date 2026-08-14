package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BytesTest {
    @Test
    fun decodesLowercaseAndUppercaseHex() {
        assertEquals(listOf(0, 255), hexToBytes("00ff").map { it.toInt() and 0xff })
        assertEquals(listOf(0, 255), hexToBytes("00FF").map { it.toInt() and 0xff })
    }

    @Test
    fun rejectsOddLength() {
        assertFailsMatching("even") { hexToBytes("abc") }
    }

    @Test
    fun rejectsNonHexDigitsInsteadOfCoercingThemToZero() {
        assertFailsMatching("invalid hex") { hexToBytes("gg") }
        assertFailsMatching("invalid hex") { hexToBytes("fg") }
        assertFailsMatching("invalid hex") { hexToBytes("0x00") }
    }

    @Test
    fun roundTripsAndCompares() {
        val bytes = hexToBytes("deadbeef")
        assertEquals("deadbeef", bytesToHex(bytes))
        assertTrue(equalBytes(bytes, hexToBytes("deadbeef")))
        assertEquals("deadbeef00", bytesToHex(concatBytes(bytes, byteArrayOf(0))))
    }
}
