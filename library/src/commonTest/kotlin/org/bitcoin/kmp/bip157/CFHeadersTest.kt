package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class CFHeadersTest {
    @Test
    fun encodeRejectsUnsupportedFilterTypeAndOversizedHashLists() {
        val previousFilterHeader = ByteArray(32)
        assertFailsMatching("unsupported filter type 1") {
            encodeCFHeaders(
                CFHeaders(
                    filterType = 1,
                    stopHash = GENESIS_STOP_INTERNAL,
                    previousFilterHeader = previousFilterHeader,
                    filterHashes = emptyList(),
                ),
            )
        }
        assertFailsMatching("exceeds max") {
            encodeCFHeaders(
                CFHeaders(
                    filterType = 0,
                    stopHash = GENESIS_STOP_INTERNAL,
                    previousFilterHeader = previousFilterHeader,
                    filterHashes = List(2001) { ByteArray(32) },
                ),
            )
        }
    }

    @Test
    fun vectorOneHash() {
        val hex =
            "000000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "010100000000000000000000000000000000000000000000000000000000000000"
        val msg = CFHeaders(
            filterType = 0,
            stopHash = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"),
            previousFilterHeader = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"),
            filterHashes = listOf(hexToBytes("0100000000000000000000000000000000000000000000000000000000000000")),
        )
        assertEquals(hex, bytesToHex(encodeCFHeaders(msg)))
        val decoded = decodeCFHeaders(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(bytesToHex(msg.stopHash), bytesToHex(decoded.stopHash))
        assertEquals(bytesToHex(msg.previousFilterHeader), bytesToHex(decoded.previousFilterHeader))
        assertEquals(msg.filterHashes.map(::bytesToHex), decoded.filterHashes.map(::bytesToHex))
        assertEquals(hex, bytesToHex(encodeCFHeaders(decoded)))
    }
}
