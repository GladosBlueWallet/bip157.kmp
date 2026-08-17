package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class CFilterWireTest {
    @Test
    fun encodeRejectsUnsupportedFilterType() {
        assertFailsMatching("unsupported filter type 1") {
            encodeCFilter(
                CFilter(filterType = 1, blockHash = GENESIS_STOP_INTERNAL, filterBytes = ByteArray(0)),
            )
        }
    }

    @Test
    fun vectorBasicEmptyFilterGenesis() {
        val hex = "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea33090000000000"
        val msg = CFilter(
            filterType = 0,
            blockHash = GENESIS_STOP_INTERNAL,
            filterBytes = hexToBytes(""),
        )
        assertEquals(hex, bytesToHex(encodeCFilter(msg)))
        val decoded = decodeCFilter(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(bytesToHex(msg.blockHash), bytesToHex(decoded.blockHash))
        assertEquals(bytesToHex(msg.filterBytes), bytesToHex(decoded.filterBytes))
        assertEquals(hex, bytesToHex(encodeCFilter(decoded)))
    }

    @Test
    fun vectorBasicNonEmptyFilter() {
        val hex = "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea33090000000004019dfca8"
        val msg = CFilter(
            filterType = 0,
            blockHash = GENESIS_STOP_INTERNAL,
            filterBytes = hexToBytes("019dfca8"),
        )
        assertEquals(hex, bytesToHex(encodeCFilter(msg)))
        val decoded = decodeCFilter(hexToBytes(hex))
        assertEquals(msg.filterType, decoded.filterType)
        assertEquals(bytesToHex(msg.blockHash), bytesToHex(decoded.blockHash))
        assertEquals(bytesToHex(msg.filterBytes), bytesToHex(decoded.filterBytes))
        assertEquals(hex, bytesToHex(encodeCFilter(decoded)))
    }
}
