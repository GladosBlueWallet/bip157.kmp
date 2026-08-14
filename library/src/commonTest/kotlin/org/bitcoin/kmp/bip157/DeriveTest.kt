package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeriveTest {
    @Test
    fun derivesHeaderAtEachFixtureHeight() {
        for (row in HEADER_FIXTURES) {
            val prevInternal = displayHashToInternal(hexToBytes(row.previousHeaderDisplay))
            val expectedInternal = displayHashToInternal(hexToBytes(row.headerDisplay))
            val filterBytes = hexToBytes(row.filterHex)
            val derived = deriveFilterHeaders(prevInternal, listOf(filterHash(filterBytes)))
            assertTrue(
                equalBytes(derived[0], expectedInternal),
                "derive mismatch at height ${row.height}",
            )
        }
    }

    @Test
    fun chainsConsecutiveLinkedRowsHeights2To3() {
        val row2 = HEADER_FIXTURES.first { it.height == 2 }
        val row3 = HEADER_FIXTURES.first { it.height == 3 }
        assertEquals(row2.headerDisplay, row3.previousHeaderDisplay)

        val prevInternal = displayHashToInternal(hexToBytes(row2.previousHeaderDisplay))
        val expected = listOf(
            displayHashToInternal(hexToBytes(row2.headerDisplay)),
            displayHashToInternal(hexToBytes(row3.headerDisplay)),
        )
        val filterHashes = listOf(row2, row3).map { filterHash(hexToBytes(it.filterHex)) }
        val derived = deriveFilterHeaders(prevInternal, filterHashes)
        assertEquals(2, derived.size)
        assertTrue(equalBytes(derived[0], expected[0]))
        assertTrue(equalBytes(derived[1], expected[1]))
    }

    @Test
    fun verifyFilterHeaderChainAcceptsFullChainAndTip() {
        val row2 = HEADER_FIXTURES.first { it.height == 2 }
        val row3 = HEADER_FIXTURES.first { it.height == 3 }
        val prevInternal = displayHashToInternal(hexToBytes(row2.previousHeaderDisplay))
        val expectedHeaders = listOf(row2, row3).map {
            displayHashToInternal(hexToBytes(it.headerDisplay))
        }
        val filterHashes = listOf(row2, row3).map { filterHash(hexToBytes(it.filterHex)) }

        assertTrue(
            verifyFilterHeaderChain(
                prevHeader = prevInternal,
                filterHashes = filterHashes,
                expectedHeaders = expectedHeaders,
            ),
        )
        assertTrue(
            verifyFilterHeaderChain(
                prevHeader = prevInternal,
                filterHashes = filterHashes,
                expectedTipHeader = expectedHeaders[1],
            ),
        )
    }

    @Test
    fun verifyFilterHeaderChainRejectsWrongTip() {
        val row = HEADER_FIXTURES[0]
        val prevInternal = displayHashToInternal(hexToBytes(row.previousHeaderDisplay))
        val filterHashes = listOf(filterHash(hexToBytes(row.filterHex)))
        val wrongTip = ByteArray(32)
        assertFalse(
            verifyFilterHeaderChain(
                prevHeader = prevInternal,
                filterHashes = filterHashes,
                expectedTipHeader = wrongTip,
            ),
        )
    }

    @Test
    fun verifyFilterHeaderChainRejectsLengthMismatchAndEmptyTipCheck() {
        val row = HEADER_FIXTURES[0]
        val prevInternal = displayHashToInternal(hexToBytes(row.previousHeaderDisplay))
        val header = displayHashToInternal(hexToBytes(row.headerDisplay))
        val filterHashes = listOf(filterHash(hexToBytes(row.filterHex)))

        assertFalse(
            verifyFilterHeaderChain(
                prevHeader = prevInternal,
                filterHashes = filterHashes,
                expectedHeaders = listOf(header, header),
            ),
        )
        assertFalse(
            verifyFilterHeaderChain(
                prevHeader = prevInternal,
                filterHashes = emptyList(),
                expectedTipHeader = header,
            ),
        )
    }

    @Test
    fun deriveFilterHeadersReturnsEmptyForEmptyInput() {
        assertEquals(0, deriveFilterHeaders(ByteArray(32), emptyList()).size)
    }
}
