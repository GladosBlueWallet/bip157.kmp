package io.bluewallet.bip157

import kotlin.test.Test

class MalformedWireTest {
    @Test
    fun getcfiltersRejectsTrailingByte() {
        val valid = hexToBytes(
            "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length 38, expected 37") { decodeGetCFilters(trailing) }
    }

    @Test
    fun getcfiltersRejectsTruncatedStopHash() {
        val truncated = hexToBytes("0000000000")
        assertFailsMatching("length 5, expected 37") { decodeGetCFilters(truncated) }
    }

    @Test
    fun cfilterRejectsTruncatedBlockHash() {
        assertFailsMatching("truncated") { decodeCFilter(ByteArray(33)) }
    }

    @Test
    fun cfilterRejectsTrailingByteAfterFilterPayload() {
        val valid = hexToBytes(
            "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea33090000000004019dfca8",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length mismatch or trailing bytes") { decodeCFilter(trailing) }
    }

    @Test
    fun getcfheadersRejectsTrailingByte() {
        val valid = hexToBytes(
            "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length 38, expected 37") { decodeGetCFHeaders(trailing) }
    }

    @Test
    fun getcfcheckptRejectsTrailingByte() {
        val valid = hexToBytes(
            "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length 34, expected 33") { decodeGetCFCheckpt(trailing) }
    }

    @Test
    fun cfheadersRejectsTrailingByteAfterHashVector() {
        val valid = hexToBytes(
            "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010100000000000000000000000000000000000000000000000000000000000000",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length mismatch or trailing bytes") { decodeCFHeaders(trailing) }
    }

    @Test
    fun cfcheckptRejectsTrailingByteAfterHeaderVector() {
        val valid = hexToBytes(
            "000000000000000000000000000000000000000000000000000000000000000000010100000000000000000000000000000000000000000000000000000000000000",
        )
        val trailing = concatBytes(valid, byteArrayOf(0x00))
        assertFailsMatching("length mismatch or trailing bytes") { decodeCFCheckpt(trailing) }
    }

    @Test
    fun allDecodersRejectUnsupportedFilterTypes() {
        val getcfilters = hexToBytes(
            "000000000043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
        )
        val cfilter = hexToBytes(
            "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea33090000000004019dfca8",
        )
        val getcfcheckpt = hexToBytes(
            "0043497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000",
        )
        val cfheaders = hexToBytes(
            "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010100000000000000000000000000000000000000000000000000000000000000",
        )
        val cfcheckpt = hexToBytes(
            "000000000000000000000000000000000000000000000000000000000000000000010100000000000000000000000000000000000000000000000000000000000000",
        )
        val cases = listOf<(ByteArray) -> Any>(
            ::decodeGetCFilters,
            ::decodeGetCFHeaders,
            ::decodeCFilter,
            ::decodeGetCFCheckpt,
            ::decodeCFHeaders,
            ::decodeCFCheckpt,
        )
        val payloads = listOf(getcfilters, getcfilters, cfilter, getcfcheckpt, cfheaders, cfcheckpt)
        for (i in cases.indices) {
            val valid = payloads[i]
            val decode = cases[i]
            val nonBasic = valid.copyOf()
            nonBasic[0] = 0x01
            assertFailsMatching("unsupported filter type 1") { decode(nonBasic) }
            decode(valid)
        }
    }

    @Test
    fun cfilterRejectsNonCanonicalCompactSizeEncoding() {
        val filterBytes = hexToBytes("019dfca8")
        val nonCanonicalLen = hexToBytes("fd0400")
        val bytes = concatBytes(
            byteArrayOf(0x00),
            GENESIS_STOP_INTERNAL,
            nonCanonicalLen,
            filterBytes,
        )
        assertFailsMatching("non-canonical CompactSize encoding") { decodeCFilter(bytes) }
    }
}
