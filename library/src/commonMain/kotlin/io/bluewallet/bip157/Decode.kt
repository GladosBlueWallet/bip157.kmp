package io.bluewallet.bip157

private fun readUint32LE(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xff) or
        ((bytes[offset + 1].toInt() and 0xff) shl 8) or
        ((bytes[offset + 2].toInt() and 0xff) shl 16) or
        ((bytes[offset + 3].toInt() and 0xff) shl 24)
}

private fun readFilterType(bytes: ByteArray, message: String): Int {
    val filterType = bytes[0].toInt() and 0xff
    if (filterType != FILTER_TYPE_BASIC) {
        throw IllegalArgumentException("$message: unsupported filter type $filterType")
    }
    return filterType
}

private fun compactSizeAsInt(decoded: CompactSizeDecodeResult, label: String): Int {
    if (decoded.value > Int.MAX_VALUE) {
        throw IllegalArgumentException("$label exceeds max")
    }
    return decoded.value.toInt()
}

fun decodeGetCFilters(bytes: ByteArray): GetCFilters {
    if (bytes.size != 37) {
        throw IllegalArgumentException("getcfilters length ${bytes.size}, expected 37")
    }
    val filterType = readFilterType(bytes, "getcfilters")
    val startHeight = readUint32LE(bytes, 1)
    val stopHash = bytes.copyOfRange(5, 37)
    return GetCFilters(filterType, startHeight, stopHash)
}

fun decodeCFilter(bytes: ByteArray): CFilter {
    if (bytes.size < 34) throw IllegalArgumentException("cfilter truncated")
    val filterType = readFilterType(bytes, "cfilter")
    val blockHash = bytes.copyOfRange(1, 33)
    val decoded = decodeCompactSize(bytes, 33)
    val n = decoded.value
    if (n > MAX_CFILTER_BYTES) {
        throw IllegalArgumentException(
            "cfilter filterBytes length $n exceeds max $MAX_CFILTER_BYTES",
        )
    }
    val start = 33 + decoded.length
    if (start + n != bytes.size.toLong()) {
        throw IllegalArgumentException("cfilter length mismatch or trailing bytes")
    }
    val count = n.toInt()
    return CFilter(filterType, blockHash, bytes.copyOfRange(start, start + count))
}

fun decodeGetCFHeaders(bytes: ByteArray): GetCFHeaders {
    if (bytes.size != 37) {
        throw IllegalArgumentException("getcfheaders length ${bytes.size}, expected 37")
    }
    val filterType = readFilterType(bytes, "getcfheaders")
    val startHeight = readUint32LE(bytes, 1)
    val stopHash = bytes.copyOfRange(5, 37)
    return GetCFHeaders(filterType, startHeight, stopHash)
}

fun decodeCFHeaders(bytes: ByteArray): CFHeaders {
    if (bytes.size < 66) throw IllegalArgumentException("cfheaders truncated")
    val filterType = readFilterType(bytes, "cfheaders")
    val stop = readHash256(bytes, 1)
    val prev = readHash256(bytes, stop.next)
    val decoded = decodeCompactSize(bytes, prev.next)
    val count = compactSizeAsInt(decoded, "cfheaders count")
    if (count > MAX_GETCFHEADERS_RANGE) {
        throw IllegalArgumentException("cfheaders count $count exceeds max")
    }
    val hashesStart = prev.next + decoded.length
    val expectedLen = hashesStart + count * 32
    if (expectedLen != bytes.size) {
        throw IllegalArgumentException("cfheaders length mismatch or trailing bytes")
    }
    val filterHashes = ArrayList<ByteArray>(count)
    var offset = hashesStart
    repeat(count) {
        val read = readHash256(bytes, offset)
        filterHashes.add(read.hash)
        offset = read.next
    }
    return CFHeaders(filterType, stop.hash, prev.hash, filterHashes)
}

fun decodeGetCFCheckpt(bytes: ByteArray): GetCFCheckpt {
    if (bytes.size != 33) {
        throw IllegalArgumentException("getcfcheckpt length ${bytes.size}, expected 33")
    }
    val filterType = readFilterType(bytes, "getcfcheckpt")
    val stopHash = bytes.copyOfRange(1, 33)
    return GetCFCheckpt(filterType, stopHash)
}

fun decodeCFCheckpt(bytes: ByteArray): CFCheckpt {
    if (bytes.size < 34) throw IllegalArgumentException("cfcheckpt truncated")
    val filterType = readFilterType(bytes, "cfcheckpt")
    val stop = readHash256(bytes, 1)
    val decoded = decodeCompactSize(bytes, stop.next)
    val count = compactSizeAsInt(decoded, "cfcheckpt count")
    if (count > MAX_CFCHECKPT_HEADERS) {
        throw IllegalArgumentException("cfcheckpt count $count exceeds max")
    }
    val headersStart = stop.next + decoded.length
    val expectedLen = headersStart + count * 32
    if (expectedLen != bytes.size) {
        throw IllegalArgumentException("cfcheckpt length mismatch or trailing bytes")
    }
    val filterHeaders = ArrayList<ByteArray>(count)
    var offset = headersStart
    repeat(count) {
        val read = readHash256(bytes, offset)
        filterHeaders.add(read.hash)
        offset = read.next
    }
    return CFCheckpt(filterType, stop.hash, filterHeaders)
}
