package io.bluewallet.bip157

class GetCFilters(
    val filterType: Int,
    val startHeight: Int,
    val stopHash: ByteArray, // 32 bytes, internal order
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetCFilters) return false
        return filterType == other.filterType &&
            startHeight == other.startHeight &&
            stopHash.contentEquals(other.stopHash)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + startHeight
        result = 31 * result + stopHash.contentHashCode()
        return result
    }
}

class CFilter(
    val filterType: Int,
    val blockHash: ByteArray,
    val filterBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CFilter) return false
        return filterType == other.filterType &&
            blockHash.contentEquals(other.blockHash) &&
            filterBytes.contentEquals(other.filterBytes)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + blockHash.contentHashCode()
        result = 31 * result + filterBytes.contentHashCode()
        return result
    }
}

class GetCFHeaders(
    val filterType: Int,
    val startHeight: Int,
    val stopHash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetCFHeaders) return false
        return filterType == other.filterType &&
            startHeight == other.startHeight &&
            stopHash.contentEquals(other.stopHash)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + startHeight
        result = 31 * result + stopHash.contentHashCode()
        return result
    }
}

class CFHeaders(
    val filterType: Int,
    val stopHash: ByteArray,
    val previousFilterHeader: ByteArray,
    val filterHashes: List<ByteArray>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CFHeaders) return false
        return filterType == other.filterType &&
            stopHash.contentEquals(other.stopHash) &&
            previousFilterHeader.contentEquals(other.previousFilterHeader) &&
            filterHashes.contentsEqual(other.filterHashes)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + stopHash.contentHashCode()
        result = 31 * result + previousFilterHeader.contentHashCode()
        result = 31 * result + filterHashes.contentsHash()
        return result
    }
}

class GetCFCheckpt(
    val filterType: Int,
    val stopHash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GetCFCheckpt) return false
        return filterType == other.filterType && stopHash.contentEquals(other.stopHash)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + stopHash.contentHashCode()
        return result
    }
}

class CFCheckpt(
    val filterType: Int,
    val stopHash: ByteArray,
    val filterHeaders: List<ByteArray>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CFCheckpt) return false
        return filterType == other.filterType &&
            stopHash.contentEquals(other.stopHash) &&
            filterHeaders.contentsEqual(other.filterHeaders)
    }

    override fun hashCode(): Int {
        var result = filterType
        result = 31 * result + stopHash.contentHashCode()
        result = 31 * result + filterHeaders.contentsHash()
        return result
    }
}
