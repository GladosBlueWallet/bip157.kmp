package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyCFilterTest {
    @Test
    fun acceptsFilterAtEachFixtureHeight() {
        for (row in HEADER_FIXTURES) {
            assertTrue(
                verifyCFilterAgainstHeader(
                    filterBytes = hexToBytes(row.filterHex),
                    previousFilterHeader = displayHashToInternal(hexToBytes(row.previousHeaderDisplay)),
                    expectedFilterHeader = displayHashToInternal(hexToBytes(row.headerDisplay)),
                ),
                "verify mismatch at height ${row.height}",
            )
        }
    }

    @Test
    fun rejectsTamperedFilterBytes() {
        val row = HEADER_FIXTURES[0]
        val filterBytes = hexToBytes(row.filterHex)
        val tampered = filterBytes.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0xff).toByte()

        assertFalse(
            verifyCFilterAgainstHeader(
                filterBytes = tampered,
                previousFilterHeader = displayHashToInternal(hexToBytes(row.previousHeaderDisplay)),
                expectedFilterHeader = displayHashToInternal(hexToBytes(row.headerDisplay)),
            ),
        )
    }

    @Test
    fun rejectsWrongPreviousHeaderEvenWhenFilterBytesAreHonest() {
        val row = HEADER_FIXTURES.first { it.height == 3 }
        assertFalse(
            verifyCFilterAgainstHeader(
                filterBytes = hexToBytes(row.filterHex),
                previousFilterHeader = ByteArray(32),
                expectedFilterHeader = displayHashToInternal(hexToBytes(row.headerDisplay)),
            ),
        )
    }
}
