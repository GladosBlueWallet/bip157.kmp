package io.bluewallet.bip157

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun happyHashAt(height: Int): ByteArray {
    val h = ByteArray(32)
    h[0] = (height and 0xff).toByte()
    h[1] = ((height shr 8) and 0xff).toByte()
    h[2] = ((height shr 16) and 0xff).toByte()
    h[3] = 0xa5.toByte()
    return h
}

private fun happyFilterAt(height: Int): ByteArray =
    byteArrayOf((height and 0xff).toByte(), ((height shr 8) and 0xff).toByte(), 0x5a)

class SyncHappyPathTest {
    @Test
    fun syncsHeadersThenAcceptsEveryMatchingCompactFilter() {
        val tipHeight = 2
        val filters = (0..2).map(::happyFilterAt)
        val filterHashes = filters.map(::filterHash)
        val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
        val sync = createFilterSync(blockHashAt = ::happyHashAt)

        assertEquals(
            emptyList(),
            sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, happyHashAt(tipHeight)))).send,
        )

        val started = sync.push(SyncInput.Start)
        assertEquals(
            listOf(
                OutboundMessage.GetCFHeaders(
                    GetCFHeaders(filterType = 0, startHeight = 0, stopHash = happyHashAt(tipHeight)),
                ),
            ),
            started.send,
        )

        val accepted = sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(tipHeight),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filterHashes,
                ),
            ),
        )
        assertEquals(
            listOf(
                SyncEvent.HeadersAccepted(fromHeight = 0, headers = headers),
                SyncEvent.Idle,
            ),
            accepted.events,
        )
        for (height in 0..tipHeight) {
            assertTrue(equalBytes(sync.getFilterHeader(height)!!, headers[height]))
        }

        val requested = sync.push(SyncInput.RequestFilters(fromHeight = 0, toHeight = tipHeight))
        assertEquals(
            listOf(
                OutboundMessage.GetCFilters(
                    GetCFilters(filterType = 0, startHeight = 0, stopHash = happyHashAt(tipHeight)),
                ),
            ),
            requested.send,
        )

        for (height in 0..tipHeight) {
            val filtered = sync.push(
                SyncInput.CFilterMsg(
                    height = height,
                    msg = CFilter(
                        filterType = 0,
                        blockHash = happyHashAt(height),
                        filterBytes = filters[height],
                    ),
                ),
            )
            assertEquals(
                listOf(
                    SyncEvent.FilterAccepted(
                        height = height,
                        blockHash = happyHashAt(height),
                        filterBytes = filters[height],
                    ),
                ),
                filtered.events,
            )
            assertEquals(emptyList(), filtered.send)
        }
    }

    @Test
    fun headerRequestsEndAtTheNextCheckpointSuffixUsesHeaderBatchSize() {
        val tipHeight = CF_CHECKPT_INTERVAL + 500
        val filters = List(tipHeight + 1) { happyFilterAt(it) }
        val filterHashes = filters.map(::filterHash)
        val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
        val sync = createFilterSync(blockHashAt = ::happyHashAt, headerBatchSize = 400)

        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, happyHashAt(tipHeight))))

        val started = sync.push(SyncInput.Start)
        assertEquals(
            listOf(
                OutboundMessage.GetCFCheckpt(
                    GetCFCheckpt(filterType = 0, stopHash = happyHashAt(tipHeight)),
                ),
            ),
            started.send,
        )

        val checkpointed = sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = happyHashAt(tipHeight),
                    filterHeaders = listOf(headers[CF_CHECKPT_INTERVAL]),
                ),
            ),
        )
        assertEquals(
            listOf(
                SyncEvent.CheckpointAccepted(headers = listOf(headers[CF_CHECKPT_INTERVAL])),
            ),
            checkpointed.events,
        )
        assertEquals(
            listOf(
                OutboundMessage.GetCFHeaders(
                    GetCFHeaders(
                        filterType = 0,
                        startHeight = 0,
                        stopHash = happyHashAt(CF_CHECKPT_INTERVAL),
                    ),
                ),
            ),
            checkpointed.send,
        )

        val first = sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(CF_CHECKPT_INTERVAL),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filterHashes.subList(0, CF_CHECKPT_INTERVAL + 1),
                ),
            ),
        )
        assertEquals(
            listOf(
                SyncEvent.HeadersAccepted(
                    fromHeight = 0,
                    headers = headers.subList(0, CF_CHECKPT_INTERVAL + 1),
                ),
            ),
            first.events,
        )
        assertNull(sync.getFilterHeader(CF_CHECKPT_INTERVAL + 1))
        assertEquals(
            listOf(
                OutboundMessage.GetCFHeaders(
                    GetCFHeaders(
                        filterType = 0,
                        startHeight = CF_CHECKPT_INTERVAL + 1,
                        stopHash = happyHashAt(CF_CHECKPT_INTERVAL + 400),
                    ),
                ),
            ),
            first.send,
        )

        val suffixBatches = listOf(
            CF_CHECKPT_INTERVAL + 1 to CF_CHECKPT_INTERVAL + 400,
            CF_CHECKPT_INTERVAL + 401 to tipHeight,
        )
        for (i in suffixBatches.indices) {
            val (from, to) = suffixBatches[i]
            val result = sync.push(
                SyncInput.CfHeaders(
                    startHeight = from,
                    msg = CFHeaders(
                        filterType = 0,
                        stopHash = happyHashAt(to),
                        previousFilterHeader = headers[from - 1],
                        filterHashes = filterHashes.subList(from, to + 1),
                    ),
                ),
            )
            assertTrue(
                result.events.any {
                    it is SyncEvent.HeadersAccepted &&
                        it.fromHeight == from &&
                        it.headers.size == to - from + 1
                },
            )
            if (i < suffixBatches.lastIndex) {
                val nextTo = suffixBatches[i + 1].second
                assertEquals(
                    listOf(
                        OutboundMessage.GetCFHeaders(
                            GetCFHeaders(
                                filterType = 0,
                                startHeight = to + 1,
                                stopHash = happyHashAt(nextTo),
                            ),
                        ),
                    ),
                    result.send,
                )
            } else {
                assertEquals(emptyList(), result.send)
                assertTrue(SyncEvent.Idle in result.events)
            }
        }
    }

    @Test
    fun doesNotAcceptHeadersInAnIntervalBeforeThatCheckpointMatches() {
        val tipHeight = CF_CHECKPT_INTERVAL * 2
        val filters = List(tipHeight + 1) { happyFilterAt(it) }
        val filterHashes = filters.map(::filterHash)
        val headers = deriveFilterHeaders(ByteArray(32), filterHashes)
        val sync = createFilterSync(blockHashAt = ::happyHashAt)

        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, happyHashAt(tipHeight))))
        sync.push(SyncInput.Start)
        val checkpointed = sync.push(
            SyncInput.CfCheckpt(
                CFCheckpt(
                    filterType = 0,
                    stopHash = happyHashAt(tipHeight),
                    filterHeaders = listOf(
                        headers[CF_CHECKPT_INTERVAL],
                        headers[CF_CHECKPT_INTERVAL * 2],
                    ),
                ),
            ),
        )
        assertEquals(
            OutboundMessage.GetCFHeaders(
                GetCFHeaders(
                    filterType = 0,
                    startHeight = 0,
                    stopHash = happyHashAt(CF_CHECKPT_INTERVAL),
                ),
            ),
            checkpointed.send[0],
        )

        val first = sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(CF_CHECKPT_INTERVAL),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filterHashes.subList(0, CF_CHECKPT_INTERVAL + 1),
                ),
            ),
        )
        assertEquals(
            listOf(
                SyncEvent.HeadersAccepted(
                    fromHeight = 0,
                    headers = headers.subList(0, CF_CHECKPT_INTERVAL + 1),
                ),
            ),
            first.events,
        )
        assertNull(sync.getFilterHeader(CF_CHECKPT_INTERVAL + 1))
        assertEquals(
            listOf(
                OutboundMessage.GetCFHeaders(
                    GetCFHeaders(
                        filterType = 0,
                        startHeight = CF_CHECKPT_INTERVAL + 1,
                        stopHash = happyHashAt(CF_CHECKPT_INTERVAL * 2),
                    ),
                ),
            ),
            first.send,
        )
    }

    @Test
    fun requestFiltersSplitsIntoCoreLegalBatches() {
        val tipHeight = 5
        val filters = List(tipHeight + 1) { happyFilterAt(it) }
        val filterHashes = filters.map(::filterHash)
        val sync = createFilterSync(blockHashAt = ::happyHashAt, filterBatchSize = 2)

        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, happyHashAt(tipHeight))))
        sync.push(SyncInput.Start)
        sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(tipHeight),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filterHashes,
                ),
            ),
        )

        val requested = sync.push(SyncInput.RequestFilters(fromHeight = 1, toHeight = tipHeight))
        assertEquals(
            listOf(
                OutboundMessage.GetCFilters(GetCFilters(0, 1, happyHashAt(2))),
                OutboundMessage.GetCFilters(GetCFilters(0, 3, happyHashAt(4))),
                OutboundMessage.GetCFilters(GetCFilters(0, 5, happyHashAt(5))),
            ),
            requested.send,
        )
    }

    @Test
    fun encodeOutboundPayloadsDecodeAsTheSameOutboundIntents() {
        val tipHeight = 1
        val sync = createFilterSync(blockHashAt = ::happyHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(tipHeight, happyHashAt(tipHeight))))
        val started = sync.push(SyncInput.Start)
        val outbound = started.send[0]
        assertEquals("getcfheaders", outbound.command)
        check(outbound is OutboundMessage.GetCFHeaders)
        val encoded = encodeOutbound(outbound)
        assertEquals(BIP157_SHORT_IDS.getcfheaders, encoded.shortId)
        assertEquals(outbound.msg, decodeGetCFHeaders(encoded.payload))

        val tall = createFilterSync(blockHashAt = ::happyHashAt)
        tall.push(SyncInput.SetTip(BlockHeaderTip(CF_CHECKPT_INTERVAL, happyHashAt(CF_CHECKPT_INTERVAL))))
        val checkptOut = tall.push(SyncInput.Start).send[0]
        val encodedCheckpt = encodeOutbound(checkptOut)
        assertEquals(BIP157_SHORT_IDS.getcfcheckpt, encodedCheckpt.shortId)
        check(checkptOut is OutboundMessage.GetCFCheckpt)
        assertEquals(checkptOut.msg, decodeGetCFCheckpt(encodedCheckpt.payload))

        val filters = listOf(happyFilterAt(0), happyFilterAt(1))
        val short = createFilterSync(blockHashAt = ::happyHashAt)
        short.push(SyncInput.SetTip(BlockHeaderTip(1, happyHashAt(1))))
        short.push(SyncInput.Start)
        short.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(1),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = filters.map(::filterHash),
                ),
            ),
        )
        val filterOut = short.push(SyncInput.RequestFilters(0, 1)).send[0]
        assertEquals("getcfilters", filterOut.command)
        check(filterOut is OutboundMessage.GetCFilters)
        val encodedFilters = encodeOutbound(filterOut)
        assertEquals(BIP157_SHORT_IDS.getcfilters, encodedFilters.shortId)
        assertEquals(filterOut.msg, decodeGetCFilters(encodedFilters.payload))
    }

    @Test
    fun emittedHeadersAndGettersDoNotAliasInternalState() {
        val filter = happyFilterAt(0)
        val sync = createFilterSync(blockHashAt = ::happyHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(0, happyHashAt(0))))
        sync.push(SyncInput.Start)
        val accepted = sync.push(
            SyncInput.CfHeaders(
                startHeight = 0,
                msg = CFHeaders(
                    filterType = 0,
                    stopHash = happyHashAt(0),
                    previousFilterHeader = ByteArray(32),
                    filterHashes = listOf(filterHash(filter)),
                ),
            ),
        )

        val event = accepted.events.filterIsInstance<SyncEvent.HeadersAccepted>().single()
        val before = bytesToHex(sync.getFilterHeader(0)!!)
        event.headers[0].fill(0xff.toByte())
        sync.getFilterHeader(0)!!.fill(0xee.toByte())
        assertEquals(before, bytesToHex(sync.getFilterHeader(0)!!))

        val filterBytes = filter.copyOf()
        val blockHash = happyHashAt(0)
        val filtered = sync.push(
            SyncInput.CFilterMsg(
                height = 0,
                msg = CFilter(filterType = 0, blockHash = blockHash, filterBytes = filterBytes),
            ),
        )
        val filterEvent = filtered.events.filterIsInstance<SyncEvent.FilterAccepted>().single()
        val filterHex = bytesToHex(filterEvent.filterBytes)
        val hashHex = bytesToHex(filterEvent.blockHash)
        filterBytes.fill(0xff.toByte())
        blockHash.fill(0xee.toByte())
        assertEquals(filterHex, bytesToHex(filterEvent.filterBytes))
        assertEquals(hashHex, bytesToHex(filterEvent.blockHash))
    }

    @Test
    fun setTipCopiesTheTipHashSoLaterMutationCannotChangeOutboundStopHash() {
        val hash = happyHashAt(CF_CHECKPT_INTERVAL)
        val sync = createFilterSync(blockHashAt = ::happyHashAt)
        sync.push(SyncInput.SetTip(BlockHeaderTip(CF_CHECKPT_INTERVAL, hash)))
        hash.fill(0xff.toByte())
        val started = sync.push(SyncInput.Start)
        assertEquals(
            OutboundMessage.GetCFCheckpt(
                GetCFCheckpt(filterType = 0, stopHash = happyHashAt(CF_CHECKPT_INTERVAL)),
            ),
            started.send[0],
        )
    }
}
