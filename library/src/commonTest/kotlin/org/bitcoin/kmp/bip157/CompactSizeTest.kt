package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactSizeTest {
    @Test
    fun encodesCanonicalPrefixes() {
        assertEquals("00", bytesToHex(encodeCompactSize(0)))
        assertEquals("fc", bytesToHex(encodeCompactSize(0xFC)))
        assertEquals("fdfd00", bytesToHex(encodeCompactSize(0xFD)))
        assertEquals("fdffff", bytesToHex(encodeCompactSize(0xFFFF)))
        assertEquals("fe00000100", bytesToHex(encodeCompactSize(0x10000)))
        assertEquals("feffffffff", bytesToHex(encodeCompactSize(0xFFFF_FFFFL)))
        assertEquals("ff0000000001000000", bytesToHex(encodeCompactSize(0x1_0000_0000L)))
    }

    @Test
    fun decodesAndReportsConsumedLength() {
        val small = decodeCompactSize(hexToBytes("fc"))
        assertEquals(0xFC, small.value)
        assertEquals(1, small.length)

        val u16 = decodeCompactSize(hexToBytes("fdfd00"))
        assertEquals(0xFD, u16.value)
        assertEquals(3, u16.length)

        val u32 = decodeCompactSize(hexToBytes("fe00000100"))
        assertEquals(0x10000, u32.value)
        assertEquals(5, u32.length)
    }

    @Test
    fun rejectsNonCanonicalAndTruncated() {
        assertFailsMatching("non-canonical CompactSize encoding") {
            decodeCompactSize(hexToBytes("fd0000"))
        }
        assertFailsMatching("non-canonical CompactSize encoding") {
            decodeCompactSize(hexToBytes("feffff0000"))
        }
        assertFailsMatching("unexpected end of data reading CompactSize") {
            decodeCompactSize(hexToBytes("fd01"))
        }
        assertFailsMatching("CompactSize value must be non-negative") {
            encodeCompactSize(-1)
        }
    }
}
