package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class CFCheckptTest {
    @Test
    fun encodeRejectsUnsupportedFilterType() {
        assertFailsMatching("unsupported filter type 1") {
            encodeCFCheckpt(
                CFCheckpt(filterType = 1, stopHash = GENESIS_STOP_INTERNAL, filterHeaders = emptyList()),
            )
        }
    }

    @Test
    fun vectorOneHeader() {
        val hex =
            "000000000000000000000000000000000000000000000000000000000000000000" +
                "010100000000000000000000000000000000000000000000000000000000000000"
        val msg = CFCheckpt(
            filterType = 0,
            stopHash = hexToBytes("0000000000000000000000000000000000000000000000000000000000000000"),
            filterHeaders = listOf(hexToBytes("0100000000000000000000000000000000000000000000000000000000000000")),
        )
        assertEquals(hex, bytesToHex(encodeCFCheckpt(msg)))
        val decoded = decodeCFCheckpt(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(bytesToHex(msg.stopHash), bytesToHex(decoded.stopHash))
        assertEquals(msg.filterHeaders.map(::bytesToHex), decoded.filterHeaders.map(::bytesToHex))
        assertEquals(hex, bytesToHex(encodeCFCheckpt(decoded)))
    }
}
