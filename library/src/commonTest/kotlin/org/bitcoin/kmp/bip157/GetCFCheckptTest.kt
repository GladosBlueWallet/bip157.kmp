package org.bitcoin.kmp.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class GetCFCheckptTest {
    @Test
    fun encodeRejectsUnsupportedFilterType() {
        assertFailsMatching("unsupported filter type 1") {
            encodeGetCFCheckpt(GetCFCheckpt(filterType = 1, stopHash = GENESIS_STOP_INTERNAL))
        }
    }

    @Test
    fun vectorBasicStopGenesis() {
        val hex = "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"
        val msg = GetCFCheckpt(filterType = 0, stopHash = GENESIS_STOP_INTERNAL)
        assertEquals(hex, bytesToHex(encodeGetCFCheckpt(msg)))
        val decoded = decodeGetCFCheckpt(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(bytesToHex(msg.stopHash), bytesToHex(decoded.stopHash))
    }
}
