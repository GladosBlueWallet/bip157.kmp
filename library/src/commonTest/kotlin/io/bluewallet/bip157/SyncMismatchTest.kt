package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun mismatchHashAt(height: Int): ByteArray {
    val h = ByteArray(32)
    h[0] = (height and 0xff).toByte()
    h[1] = ((height shr 8) and 0xff).toByte()
    h[3] = 0xb7.toByte()
    return h
}

private fun mismatchFilterAt(height: Int): ByteArray =
    byteArrayOf((height and 0xff).toByte(), 0xc3.toByte())

private fun syncWithHeaders(tipHeight: Int): Triple<FilterSync, List<ByteArray>, List<ByteArray>> {
    val filters = List(tipHeight + 1) { mismatchFilterAt(it) }
    val filterHashes = filters.map(::filterHash)
    val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
    val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
    sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
    sync.push(SyncInput.Start)
    sync.push(
        SyncInput.CfHeaders(
            startHeight = 0,
            msg = CFHeaders(
                filterType = 0,
                stopHash = mismatchHashAt(tipHeight),
                previousFilterHeader = ByteArray(32),
                filterHashes = filterHashes,
            ),
        ),
    )
    return Triple(sync, filters, headers)
}

class SyncMismatchTest {
    @Test
    fun rejectsTipHashThatDoesNotMatchBlockHashAt() {
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        assertFailsMatching("tip.hash must equal blockHashAt") {
            sync.push(SyncInput.SetTip(BlockHeaderTip(0, ByteArray(32) { 0xff.toByte() })))
        }
    }

    @Test
    fun rejectsInvalidConstructionOptions() {
        assertFailsMatching("only the basic compact filter type") {
            createFilterSync(blockHashAt = ::mismatchHashAt, filterType = 1)
        }
        assertFailsMatching("filterBatchSize must be a finite positive integer") {
            createFilterSync(blockHashAt = ::mismatchHashAt, filterBatchSize = 0)
        }
        assertFailsMatching("headerBatchSize must be a finite positive integer") {
            createFilterSync(blockHashAt = ::mismatchHashAt, headerBatchSize = -1)
        }
    }

    @Test
    fun startWithoutTipMismatchesAndFurtherInputIsIgnored() {
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        val started = sync.push(SyncInput.Start)
        assertEquals(listOf(SyncEvent.Mismatch(reason = "tip is not set")), started.events)
        assertEquals(emptyList(), started.send)

        val after = sync.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        assertEquals(SyncOutput(emptyList(), emptyList()), after)
    }

    @Test
    fun rejectsCheckpointsWhoseCountOrStopHashIsWrong() {
        val tipHash = mismatchHashAt(CF_CHECKPT_INTERVAL)
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(CF_CHECKPT_INTERVAL, tipHash)))
        assertEquals("getcfcheckpt", sync.push(SyncInput.Start).send[0].command)

        val wrongStop = sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = mismatchHashAt(0),
                    filterHeaders = listOf(ByteArray(32) { 1 }),
                ),
            ),
        )
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "checkpoint stop hash does not match tip")),
            wrongStop.events,
        )

        val sync2 = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync2.push(SyncInput.SetTip(BlockHeaderTip(CF_CHECKPT_INTERVAL, tipHash)))
        sync2.push(SyncInput.Start)
        val wrongCount = sync2.push(
            SyncInput.CfCheckpt(
                CFCheckpt(filterType = 0, stopHash = tipHash, filterHeaders = emptyList()),
            ),
        )
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "checkpoint count does not match tip height")),
            wrongCount.events,
        )
    }

    @Test
    fun rejectsUnexpectedCfheadersRangeStopHashLengthAndPrevHeader() {
        val tipHeight = 2
        val filters = (0..2).map(::mismatchFilterAt)
        val filterHashes = filters.map(::filterHash)
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
        sync.push(SyncInput.Start)

        val wrongStart = createFilterSync(blockHashAt = ::mismatchHashAt)
        wrongStart.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
        wrongStart.push(SyncInput.Start)
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "unexpected cfheaders range")),
            wrongStart.push(
                SyncInput.CfHeaders(
                    startHeight = 1,
                    msg = CFHeaders(
                        filterType = 0,
                        stopHash = mismatchHashAt(tipHeight),
                        previousFilterHeader = ByteArray(32),
                        filterHashes = filterHashes.subList(1, filterHashes.size),
                    ),
                ),
            ).events,
        )

        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "cfheaders stop hash does not match requested range")),
            sync.push(
                SyncInput.CfHeaders(
                    startHeight = 0,
                    msg = CFHeaders(
                        filterType = 0,
                        stopHash = mismatchHashAt(0),
                        previousFilterHeader = ByteArray(32),
                        filterHashes = filterHashes,
                    ),
                ),
            ).events,
        )

        val syncLen = createFilterSync(blockHashAt = ::mismatchHashAt)
        syncLen.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
        syncLen.push(SyncInput.Start)
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "cfheaders length does not match requested range")),
            syncLen.push(
                SyncInput.CfHeaders(
                    startHeight = 0,
                    msg = CFHeaders(
                        filterType = 0,
                        stopHash = mismatchHashAt(tipHeight),
                        previousFilterHeader = ByteArray(32),
                        filterHashes = filterHashes.subList(0, 2),
                    ),
                ),
            ).events,
        )

        val syncPrev = createFilterSync(blockHashAt = ::mismatchHashAt)
        syncPrev.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        syncPrev.push(SyncInput.Start)
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "cfheaders previous header does not match")),
            syncPrev.push(
                SyncInput.CfHeaders(
                    startHeight = 0,
                    msg = CFHeaders(
                        filterType = 0,
                        stopHash = mismatchHashAt(0),
                        previousFilterHeader = ByteArray(32) { 0xab.toByte() },
                        filterHashes = listOf(filterHash(mismatchFilterAt(0))),
                    ),
                ),
            ).events,
        )
    }

    @Test
    fun rejectsDerivedHeadersThatDisagreeWithACheckpoint() {
        val tipHeight = CF_CHECKPT_INTERVAL
        val filters = List(tipHeight + 1) { mismatchFilterAt(it) }
        val filterHashes = filters.map(::filterHash)
        val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
        val wrongCheckpoint = ByteArray(32) { 0x11 }
        val sync = createFilterSync(
            blockHashAt = ::mismatchHashAt,
            headerBatchSize = tipHeight + 1,
        )

        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
        sync.push(SyncInput.Start)
        sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = mismatchHashAt(tipHeight),
                    filterHeaders = listOf(wrongCheckpoint),
                ),
            ),
        )

        val mismatch = sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = mismatchHashAt(tipHeight),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filterHashes,
                ),
            ),
        )
        assertEquals(
            listOf(
                SyncEvent.Mismatch(
                    reason = "derived header does not match checkpoint",
                    height = CF_CHECKPT_INTERVAL,
                ),
            ),
            mismatch.events,
        )
        assertFalse(headers[CF_CHECKPT_INTERVAL].contentEquals(wrongCheckpoint))
    }

    @Test
    fun rejectsCompactFiltersThatFailVerificationOrArriveEarly() {
        val (sync, filters, _) = syncWithHeaders(0)

        val unknown = createFilterSync(blockHashAt = ::mismatchHashAt)
        unknown.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "filter header is unknown", height = 0)),
            unknown.push(
                SyncInput.CFilterMsg(
                    height = 0,
                    msg = CFilter(
                        filterType = 0,
                        blockHash = mismatchHashAt(0),
                        filterBytes = filters[0],
                    ),
                ),
            ).events,
        )

        val wrongBytes = sync.push(
            SyncInput.CFilterMsg(
                height = 0,
                msg = CFilter(
                    filterType = 0,
                    blockHash = mismatchHashAt(0),
                    filterBytes = byteArrayOf(0xff.toByte()),
                ),
            ),
        )
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "compact filter does not match header", height = 0)),
            wrongBytes.events,
        )
        assertEquals(
            SyncOutput(emptyList(), emptyList()),
            sync.push(SyncInput.RequestFilters(0, 0)),
        )

        val (syncHash, filters2, _) = syncWithHeaders(0)
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "cfilter block hash does not match height", height = 0)),
            syncHash.push(
                SyncInput.CFilterMsg(
                    height = 0,
                    msg = CFilter(
                        filterType = 0,
                        blockHash = mismatchHashAt(9),
                        filterBytes = filters2[0],
                    ),
                ),
            ).events,
        )
    }

    @Test
    fun rejectsInvalidFilterRangesAndUnexpectedFilterTypes() {
        val (sync, _, _) = syncWithHeaders(2)

        assertFailsMatching("invalid filter range") {
            sync.push(SyncInput.RequestFilters(fromHeight = 2, toHeight = 1))
        }

        assertEquals(1, sync.push(SyncInput.RequestFilters(0, 2)).send.size)

        val (sync2, _, _) = syncWithHeaders(1)
        assertFailsMatching("invalid filter range") {
            sync2.push(SyncInput.RequestFilters(0, 9))
        }

        val sync3 = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync3.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        sync3.push(SyncInput.Start)
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "unexpected filter type")),
            sync3.push(
                SyncInput.CfHeaders(
                    startHeight = 0,
                    msg = CFHeaders(
                        filterType = 1,
                        stopHash = mismatchHashAt(0),
                        previousFilterHeader = ByteArray(32),
                        filterHashes = listOf(filterHash(mismatchFilterAt(0))),
                    ),
                ),
            ).events,
        )
    }

    @Test
    fun rejectsStartTwiceTipChangesAfterStartAndFiltersBeforeHeaders() {
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        sync.push(SyncInput.Start)
        assertFailsMatching("already started") { sync.push(SyncInput.Start) }
        assertFailsMatching("cannot change tip after start") {
            sync.push(SyncInput.SetTip(BlockHeaderTip(0, mismatchHashAt(0))))
        }
        assertFailsMatching("filter header is unknown") {
            sync.push(SyncInput.RequestFilters(0, 0))
        }
    }

    @Test
    fun rejectsASecondCheckpointMessageAfterCheckpointsAreStored() {
        val tipHeight = CF_CHECKPT_INTERVAL
        val filters = List(tipHeight + 1) { mismatchFilterAt(it) }
        val filterHashes = filters.map(::filterHash)
        val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
        val sync = createFilterSync(blockHashAt = ::mismatchHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, mismatchHashAt(tipHeight))))
        sync.push(SyncInput.Start)
        sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = mismatchHashAt(tipHeight),
                    filterHeaders = listOf(headers[tipHeight]),
                ),
            ),
        )
        val second = sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = mismatchHashAt(tipHeight),
                    filterHeaders = listOf(headers[tipHeight]),
                ),
            ),
        )
        assertEquals(
            listOf(SyncEvent.Mismatch(reason = "unexpected cfcheckpt")),
            second.events,
        )
    }

    @Test
    fun rejectsATipHashThatIsNot32Bytes() {
        val sync = createFilterSync(blockHashAt = { ByteArray(31) })
        assertFailsMatching("must be 32 bytes") {
            sync.push(SyncInput.SetTip(BlockHeaderTip(0, ByteArray(31))))
        }
    }
}
