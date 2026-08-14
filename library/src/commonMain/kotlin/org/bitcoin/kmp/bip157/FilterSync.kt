package org.bitcoin.kmp.bip157

fun createFilterSync(
    blockHashAt: (height: Int) -> ByteArray,
    filterType: Int = FILTER_TYPE_BASIC,
    headerBatchSize: Int? = null,
    filterBatchSize: Int? = null,
): FilterSync = createFilterSync(
    FilterSyncOptions(
        blockHashAt = blockHashAt,
        filterType = filterType,
        headerBatchSize = headerBatchSize,
        filterBatchSize = filterBatchSize,
    ),
)

fun createFilterSync(options: FilterSyncOptions): FilterSync {
    val filterType = options.filterType
    if (filterType != FILTER_TYPE_BASIC) {
        throw IllegalArgumentException("only the basic compact filter type is supported")
    }

    fun batchSize(value: Int?, maximum: Int, name: String): Int {
        if (value != null && value < 1) {
            throw IllegalArgumentException("$name must be a finite positive integer")
        }
        return minOf(value ?: maximum, maximum)
    }

    val headerBatchSize = batchSize(options.headerBatchSize, MAX_GETCFHEADERS_RANGE, "headerBatchSize")
    val filterBatchSize = batchSize(options.filterBatchSize, MAX_GETCFILTERS_RANGE, "filterBatchSize")
    val headersByHeight = mutableMapOf<Int, ByteArray>()
    val checkpoints = mutableMapOf<Int, ByteArray>()
    var tip: BlockHeaderTip? = null
    var nextHeaderHeight: Int? = null
    var started = false
    var halted = false

    fun mismatch(
        send: MutableList<OutboundMessage>,
        events: MutableList<SyncEvent>,
        reason: String,
        height: Int? = null,
    ): SyncOutput {
        halted = true
        events.add(SyncEvent.Mismatch(reason = reason, height = height))
        return SyncOutput(send, events)
    }

    fun hashAtHeight(height: Int): ByteArray {
        val hash = options.blockHashAt(height)
        requireHash32(hash, "blockHashAt")
        return hash.copyOf()
    }

    fun lastCheckpointHeight(): Int =
        (tip!!.height / CF_CHECKPT_INTERVAL) * CF_CHECKPT_INTERVAL

    fun headerStop(startHeight: Int): Int {
        val lastCheckpoint = lastCheckpointHeight()
        if (lastCheckpoint >= CF_CHECKPT_INTERVAL && startHeight <= lastCheckpoint) {
            return (startHeight / CF_CHECKPT_INTERVAL + 1) * CF_CHECKPT_INTERVAL
        }
        return minOf(startHeight + headerBatchSize - 1, tip!!.height)
    }

    fun requestHeaders(send: MutableList<OutboundMessage>, startHeight: Int) {
        val stop = headerStop(startHeight)
        assertGetCFHeadersRange(startHeight, stop)
        send.add(
            OutboundMessage.GetCFHeaders(
                GetCFHeaders(
                    filterType = filterType,
                    startHeight = startHeight,
                    stopHash = hashAtHeight(stop),
                ),
            ),
        )
    }

    return object : FilterSync {
        override fun push(input: SyncInput): SyncOutput {
            val send = mutableListOf<OutboundMessage>()
            val events = mutableListOf<SyncEvent>()
            if (halted) return SyncOutput(send, events)

            when (input) {
                is SyncInput.SetTip -> {
                    if (started) throw IllegalArgumentException("cannot change tip after start")
                    if (input.tip.height < 0) {
                        throw IllegalArgumentException("tip.height must be a non-negative integer")
                    }
                    requireHash32(input.tip.hash, "tip.hash")
                    if (!equalBytes(input.tip.hash, hashAtHeight(input.tip.height))) {
                        throw IllegalArgumentException("tip.hash must equal blockHashAt(tip.height)")
                    }
                    tip = BlockHeaderTip(height = input.tip.height, hash = input.tip.hash.copyOf())
                    return SyncOutput(send, events)
                }

                is SyncInput.Start -> {
                    if (started) throw IllegalArgumentException("sync already started")
                    val currentTip = tip ?: return mismatch(send, events, "tip is not set")
                    started = true
                    if (currentTip.height >= CF_CHECKPT_INTERVAL) {
                        send.add(
                            OutboundMessage.GetCFCheckpt(
                                GetCFCheckpt(
                                    filterType = filterType,
                                    stopHash = currentTip.hash.copyOf(),
                                ),
                            ),
                        )
                    } else {
                        nextHeaderHeight = 0
                        requestHeaders(send, 0)
                    }
                    return SyncOutput(send, events)
                }

                is SyncInput.CfCheckpt -> {
                    val currentTip = tip ?: return mismatch(send, events, "tip is not set")
                    if (!started || checkpoints.isNotEmpty() || nextHeaderHeight != null) {
                        return mismatch(send, events, "unexpected cfcheckpt")
                    }
                    if (input.msg.filterType != filterType) {
                        return mismatch(send, events, "unexpected filter type")
                    }
                    if (!equalBytes(input.msg.stopHash, currentTip.hash)) {
                        return mismatch(send, events, "checkpoint stop hash does not match tip")
                    }
                    if (input.msg.filterHeaders.size != currentTip.height / CF_CHECKPT_INTERVAL) {
                        return mismatch(send, events, "checkpoint count does not match tip height")
                    }
                    for (i in input.msg.filterHeaders.indices) {
                        checkpoints[(i + 1) * CF_CHECKPT_INTERVAL] =
                            input.msg.filterHeaders[i].copyOf()
                    }
                    events.add(
                        SyncEvent.CheckpointAccepted(headers = input.msg.filterHeaders.copyBytes()),
                    )
                    nextHeaderHeight = 0
                    requestHeaders(send, 0)
                    return SyncOutput(send, events)
                }

                is SyncInput.CfHeaders -> {
                    val currentTip = tip
                    if (currentTip == null || nextHeaderHeight != input.startHeight) {
                        return mismatch(send, events, "unexpected cfheaders range")
                    }
                    val stop = headerStop(input.startHeight)
                    if (input.msg.filterType != filterType) {
                        return mismatch(send, events, "unexpected filter type")
                    }
                    if (!equalBytes(input.msg.stopHash, hashAtHeight(stop))) {
                        return mismatch(send, events, "cfheaders stop hash does not match requested range")
                    }
                    if (input.msg.filterHashes.size != stop - input.startHeight + 1) {
                        return mismatch(send, events, "cfheaders length does not match requested range")
                    }
                    val expectedPrevious =
                        if (input.startHeight == 0) ByteArray(32)
                        else headersByHeight[input.startHeight - 1]
                    if (
                        expectedPrevious == null ||
                        !equalBytes(input.msg.previousFilterHeader, expectedPrevious)
                    ) {
                        return mismatch(send, events, "cfheaders previous header does not match")
                    }

                    val headers = deriveFilterHeaders(
                        input.msg.previousFilterHeader,
                        input.msg.filterHashes,
                    )
                    for (height in input.startHeight..stop) {
                        val checkpoint = checkpoints[height]
                        if (
                            checkpoint != null &&
                            !equalBytes(headers[height - input.startHeight], checkpoint)
                        ) {
                            return mismatch(
                                send,
                                events,
                                "derived header does not match checkpoint",
                                height,
                            )
                        }
                    }
                    headers.forEachIndexed { index, header ->
                        headersByHeight[input.startHeight + index] = header
                    }
                    events.add(
                        SyncEvent.HeadersAccepted(
                            fromHeight = input.startHeight,
                            headers = headers.map { it.copyOf() },
                        ),
                    )
                    val next = stop + 1
                    nextHeaderHeight = next
                    if (next <= currentTip.height) {
                        requestHeaders(send, next)
                    } else {
                        events.add(SyncEvent.Idle)
                    }
                    return SyncOutput(send, events)
                }

                is SyncInput.RequestFilters -> {
                    val currentTip = tip ?: return mismatch(send, events, "tip is not set")
                    if (
                        input.fromHeight < 0 ||
                        input.fromHeight > input.toHeight ||
                        input.toHeight > currentTip.height
                    ) {
                        throw IllegalArgumentException("invalid filter range")
                    }
                    for (height in input.fromHeight..input.toHeight) {
                        if (!headersByHeight.containsKey(height)) {
                            throw IllegalArgumentException("filter header is unknown")
                        }
                    }
                    var startHeight = input.fromHeight
                    while (startHeight <= input.toHeight) {
                        val stop = minOf(startHeight + filterBatchSize - 1, input.toHeight)
                        assertGetCFiltersRange(startHeight, stop)
                        send.add(
                            OutboundMessage.GetCFilters(
                                GetCFilters(
                                    filterType = filterType,
                                    startHeight = startHeight,
                                    stopHash = hashAtHeight(stop),
                                ),
                            ),
                        )
                        startHeight += filterBatchSize
                    }
                    return SyncOutput(send, events)
                }

                is SyncInput.CFilterMsg -> {
                    if (input.msg.filterType != filterType) {
                        return mismatch(send, events, "unexpected filter type", input.height)
                    }
                    if (!equalBytes(input.msg.blockHash, hashAtHeight(input.height))) {
                        return mismatch(
                            send,
                            events,
                            "cfilter block hash does not match height",
                            input.height,
                        )
                    }
                    val expectedFilterHeader = headersByHeight[input.height]
                    val previousFilterHeader =
                        if (input.height == 0) ByteArray(32)
                        else headersByHeight[input.height - 1]
                    if (expectedFilterHeader == null || previousFilterHeader == null) {
                        return mismatch(send, events, "filter header is unknown", input.height)
                    }
                    if (
                        !verifyCFilterAgainstHeader(
                            filterBytes = input.msg.filterBytes,
                            previousFilterHeader = previousFilterHeader,
                            expectedFilterHeader = expectedFilterHeader,
                        )
                    ) {
                        return mismatch(
                            send,
                            events,
                            "compact filter does not match header",
                            input.height,
                        )
                    }
                    events.add(
                        SyncEvent.FilterAccepted(
                            height = input.height,
                            blockHash = input.msg.blockHash.copyOf(),
                            filterBytes = input.msg.filterBytes.copyOf(),
                        ),
                    )
                    return SyncOutput(send, events)
                }
            }
        }

        override fun getFilterHeader(height: Int): ByteArray? =
            headersByHeight[height]?.copyOf()
    }
}
