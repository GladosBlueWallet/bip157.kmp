package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class GetCFHeadersTest {
    @Test
    fun encodeRejectsUnsupportedFilterTypeAndBadHeights() {
        assertFailsMatching("unsupported filter type 1") {
            encodeGetCFHeaders(GetCFHeaders(filterType = 1, startHeight = 0, stopHash = GENESIS_STOP_INTERNAL))
        }
        assertFailsMatching("startHeight out of range") {
            encodeGetCFHeaders(GetCFHeaders(filterType = 0, startHeight = -1, stopHash = GENESIS_STOP_INTERNAL))
        }
    }

    @Test
    fun vectorBasicStart0StopGenesisInternal() {
        val hex = "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"
        val msg = GetCFHeaders(
            filterType = 0,
            startHeight = 0,
            stopHash = GENESIS_STOP_INTERNAL,
        )
        assertEquals(hex, bytesToHex(encodeGetCFHeaders(msg)))
        val decoded = decodeGetCFHeaders(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(msg.startHeight, decoded.startHeight)
        assertEquals(bytesToHex(msg.stopHash), bytesToHex(decoded.stopHash))
    }
}
