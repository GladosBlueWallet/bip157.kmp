package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals

class InteropCoreTest {
    @Test
    fun nodeCompactFiltersMatchesCore() {
        assertEquals(64, NODE_COMPACT_FILTERS)
    }

    @Test
    fun bip324ShortIdsMatchCore() {
        assertEquals(22, BIP157_SHORT_IDS.getcfilters)
        assertEquals(23, BIP157_SHORT_IDS.cfilter)
        assertEquals(24, BIP157_SHORT_IDS.getcfheaders)
        assertEquals(25, BIP157_SHORT_IDS.cfheaders)
        assertEquals(26, BIP157_SHORT_IDS.getcfcheckpt)
        assertEquals(27, BIP157_SHORT_IDS.cfcheckpt)
    }

    @Test
    fun coreDisconnectBoundsStopMinusStartMustBeLessThanMaxSize() {
        assertGetCFiltersRange(0, MAX_GETCFILTERS_RANGE - 1)
        assertFailsMatching("range too large") { assertGetCFiltersRange(0, MAX_GETCFILTERS_RANGE) }
        assertGetCFiltersRange(100, 100 + MAX_GETCFILTERS_RANGE - 1)
        assertFailsMatching("range too large") {
            assertGetCFiltersRange(100, 100 + MAX_GETCFILTERS_RANGE)
        }
        assertGetCFHeadersRange(0, MAX_GETCFHEADERS_RANGE - 1)
        assertFailsMatching("range too large") { assertGetCFHeadersRange(0, MAX_GETCFHEADERS_RANGE) }
        assertFailsMatching("stopHeight must be an integer") { assertGetCFiltersRange(5, 4) }
        assertFailsMatching("startHeight must be") { assertGetCFHeadersRange(-1, 0) }
    }

    @Test
    fun cfheadersGoldPayloadFromBip158GenesisFilter() {
        val stop = GENESIS_STOP_INTERNAL
        val prev = ByteArray(32)
        val filterBytes = hexToBytes("019dfca8")
        val filterHashBytes = filterHash(filterBytes)
        val expectedHex =
            "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "01" +
                bytesToHex(filterHashBytes)

        val encoded = encodeCFHeaders(
            CFHeaders(
                filterType = 0,
                stopHash = stop,
                previousFilterHeader = prev,
                filterHashes = listOf(filterHashBytes),
            ),
        )
        assertEquals(expectedHex, bytesToHex(encoded))

        val decoded = decodeCFHeaders(encoded)
        val headers = deriveFilterHeaders(prev, decoded.filterHashes)
        assertEquals(
            "50b781aed7b7129012a6d20e2d040027937f3affaee573779908ebb779455821",
            bytesToHex(headers[0]),
        )
    }

    @Test
    fun cfilterRejectsOversizedFilterBodies() {
        val blockHash = ByteArray(32)
        val huge = ByteArray(MAX_CFILTER_BYTES + 1)
        assertFailsMatching("exceeds max") {
            encodeCFilter(CFilter(filterType = 0, blockHash = blockHash, filterBytes = huge))
        }

        val lenPrefix = hexToBytes("fe01093d00")
        val bytes = ByteArray(1 + 32 + lenPrefix.size)
        bytes[0] = 0
        lenPrefix.copyInto(bytes, 33)
        assertFailsMatching("exceeds max") { decodeCFilter(bytes) }
    }

    @Test
    fun getcfiltersPayloadMatchesCoreLittleEndianHeightLayout() {
        val encoded = encodeGetCFilters(
            GetCFilters(filterType = 0, startHeight = 0x01020304, stopHash = GENESIS_STOP_INTERNAL),
        )
        assertEquals("0004030201", bytesToHex(encoded.copyOfRange(0, 5)))
        assertEquals(bytesToHex(GENESIS_STOP_INTERNAL), bytesToHex(encoded.copyOfRange(5, encoded.size)))
    }
}
