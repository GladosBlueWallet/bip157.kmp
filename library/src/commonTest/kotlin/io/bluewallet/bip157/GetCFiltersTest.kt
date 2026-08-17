package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class GetCFiltersTest {
    @Test
    fun encodeRejectsUnsupportedFilterTypeAndBadHeights() {
        assertFailsMatching("unsupported filter type 1") {
            encodeGetCFilters(GetCFilters(filterType = 1, startHeight = 0, stopHash = GENESIS_STOP_INTERNAL))
        }
        assertFailsMatching("startHeight out of range") {
            encodeGetCFilters(GetCFilters(filterType = 0, startHeight = -1, stopHash = GENESIS_STOP_INTERNAL))
        }
    }

    @Test
    fun vectorBasicStart0StopGenesisInternal() {
        val hex = "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"
        val msg = GetCFilters(
            filterType = 0,
            startHeight = 0,
            stopHash = GENESIS_STOP_INTERNAL,
        )
        assertEquals(hex, bytesToHex(encodeGetCFilters(msg)))
        val decoded = decodeGetCFilters(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(msg.startHeight, decoded.startHeight)
        assertEquals(bytesToHex(msg.stopHash), bytesToHex(decoded.stopHash))
    }
}
