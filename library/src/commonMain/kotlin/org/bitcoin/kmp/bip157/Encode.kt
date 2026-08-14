package org.bitcoin.kmp.bip157

private fun writeUint32LE(out: ByteArray, offset: Int, value: Int) {
    out[offset] = (value and 0xff).toByte()
    out[offset + 1] = ((value ushr 8) and 0xff).toByte()
    out[offset + 2] = ((value ushr 16) and 0xff).toByte()
    out[offset + 3] = ((value ushr 24) and 0xff).toByte()
}

private fun requireStartHeight(startHeight: Int) {
    if (startHeight < 0) {
        throw IllegalArgumentException("startHeight out of range")
    }
}

private fun requireBasicFilterType(filterType: Int) {
    if (filterType != FILTER_TYPE_BASIC) {
        throw IllegalArgumentException("unsupported filter type $filterType")
    }
}

fun encodeGetCFilters(msg: GetCFilters): ByteArray {
    requireHash32(msg.stopHash, "stopHash")
    requireStartHeight(msg.startHeight)
    requireBasicFilterType(msg.filterType)
    val out = ByteArray(1 + 4 + 32)
    out[0] = msg.filterType.toByte()
    writeUint32LE(out, 1, msg.startHeight)
    msg.stopHash.copyInto(out, 5)
    return out
}

fun encodeCFilter(msg: CFilter): ByteArray {
    requireHash32(msg.blockHash, "blockHash")
    requireBasicFilterType(msg.filterType)
    if (msg.filterBytes.size > MAX_CFILTER_BYTES) {
        throw IllegalArgumentException(
            "cfilter filterBytes length ${msg.filterBytes.size} exceeds max $MAX_CFILTER_BYTES",
        )
    }
    val len = encodeCompactSize(msg.filterBytes.size)
    val out = ByteArray(1 + 32 + len.size + msg.filterBytes.size)
    out[0] = msg.filterType.toByte()
    msg.blockHash.copyInto(out, 1)
    len.copyInto(out, 33)
    msg.filterBytes.copyInto(out, 33 + len.size)
    return out
}

fun encodeGetCFHeaders(msg: GetCFHeaders): ByteArray {
    requireHash32(msg.stopHash, "stopHash")
    requireStartHeight(msg.startHeight)
    requireBasicFilterType(msg.filterType)
    val out = ByteArray(1 + 4 + 32)
    out[0] = msg.filterType.toByte()
    writeUint32LE(out, 1, msg.startHeight)
    msg.stopHash.copyInto(out, 5)
    return out
}

fun encodeCFHeaders(msg: CFHeaders): ByteArray {
    requireHash32(msg.stopHash, "stopHash")
    requireHash32(msg.previousFilterHeader, "previousFilterHeader")
    requireBasicFilterType(msg.filterType)
    if (msg.filterHashes.size > MAX_GETCFHEADERS_RANGE) {
        throw IllegalArgumentException("filterHashes length ${msg.filterHashes.size} exceeds max")
    }
    for (i in msg.filterHashes.indices) {
        requireHash32(msg.filterHashes[i], "filterHashes[$i]")
    }
    val count = encodeCompactSize(msg.filterHashes.size)
    val out = ByteArray(1 + 32 + 32 + count.size + msg.filterHashes.size * 32)
    out[0] = msg.filterType.toByte()
    msg.stopHash.copyInto(out, 1)
    msg.previousFilterHeader.copyInto(out, 33)
    count.copyInto(out, 65)
    var offset = 65 + count.size
    for (hash in msg.filterHashes) {
        hash.copyInto(out, offset)
        offset += 32
    }
    return out
}

fun encodeGetCFCheckpt(msg: GetCFCheckpt): ByteArray {
    requireHash32(msg.stopHash, "stopHash")
    requireBasicFilterType(msg.filterType)
    val out = ByteArray(1 + 32)
    out[0] = msg.filterType.toByte()
    msg.stopHash.copyInto(out, 1)
    return out
}

fun encodeCFCheckpt(msg: CFCheckpt): ByteArray {
    requireHash32(msg.stopHash, "stopHash")
    requireBasicFilterType(msg.filterType)
    if (msg.filterHeaders.size > MAX_CFCHECKPT_HEADERS) {
        throw IllegalArgumentException(
            "filterHeaders length ${msg.filterHeaders.size} exceeds max",
        )
    }
    for (i in msg.filterHeaders.indices) {
        requireHash32(msg.filterHeaders[i], "filterHeaders[$i]")
    }
    val count = encodeCompactSize(msg.filterHeaders.size)
    val out = ByteArray(1 + 32 + count.size + msg.filterHeaders.size * 32)
    out[0] = msg.filterType.toByte()
    msg.stopHash.copyInto(out, 1)
    count.copyInto(out, 33)
    var offset = 33 + count.size
    for (header in msg.filterHeaders) {
        header.copyInto(out, offset)
        offset += 32
    }
    return out
}
