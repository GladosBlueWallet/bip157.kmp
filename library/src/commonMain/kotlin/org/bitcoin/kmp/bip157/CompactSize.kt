package org.bitcoin.kmp.bip157

/** Bitcoin CompactSize (a.k.a. varint) encode/decode. */

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

data class CompactSizeDecodeResult(val value: Long, val length: Int)

fun encodeCompactSize(n: Long): ByteArray {
    val value = normalizeCompactSizeValue(n)
    return when {
        value <= 0xFCuL -> byteArrayOf(value.toByte())
        value <= 0xFFFFuL -> encodePrefixed(value, 0xFD, 2)
        value <= 0xFFFF_FFFFuL -> encodePrefixed(value, 0xFE, 4)
        else -> encodePrefixed(value, 0xFF, 8)
    }
}

fun encodeCompactSize(n: Int): ByteArray = encodeCompactSize(n.toLong())

fun decodeCompactSize(bytes: ByteArray, offset: Int = 0): CompactSizeDecodeResult {
    val decoded = decodeCompactSizeULong(bytes, offset)
    if (decoded.value > MAX_SAFE_INTEGER.toULong()) {
        throw IllegalArgumentException("CompactSize value exceeds safe integer range")
    }
    return CompactSizeDecodeResult(value = decoded.value.toLong(), length = decoded.length)
}

private data class ULongDecode(val value: ULong, val length: Int)

private fun decodeCompactSizeULong(bytes: ByteArray, offset: Int): ULongDecode {
    if (offset < 0) {
        throw IllegalArgumentException(
            "CompactSize offset must be a non-negative integer, got $offset",
        )
    }
    if (offset >= bytes.size) {
        throw IllegalArgumentException("unexpected end of data reading CompactSize")
    }
    val first = bytes[offset].toInt() and 0xff
    if (first <= 0xFC) {
        return ULongDecode(first.toULong(), 1)
    }
    if (first == 0xFD) {
        requireBytes(bytes, offset, 3)
        val value = readLittleEndian(bytes, offset + 1, 2)
        if (value <= 0xFCuL) {
            throw IllegalArgumentException("non-canonical CompactSize encoding")
        }
        return ULongDecode(value, 3)
    }
    if (first == 0xFE) {
        requireBytes(bytes, offset, 5)
        val value = readLittleEndian(bytes, offset + 1, 4)
        if (value <= 0xFFFFuL) {
            throw IllegalArgumentException("non-canonical CompactSize encoding")
        }
        return ULongDecode(value, 5)
    }
    requireBytes(bytes, offset, 9)
    val value = readLittleEndian(bytes, offset + 1, 8)
    if (value <= 0xFFFF_FFFFuL) {
        throw IllegalArgumentException("non-canonical CompactSize encoding")
    }
    return ULongDecode(value, 9)
}

private fun normalizeCompactSizeValue(value: Long): ULong {
    if (value < 0) {
        throw IllegalArgumentException("CompactSize value must be non-negative, got $value")
    }
    return value.toULong()
}

private fun encodePrefixed(value: ULong, prefix: Int, byteLength: Int): ByteArray {
    val out = ByteArray(byteLength + 1)
    out[0] = prefix.toByte()
    var remaining = value
    for (i in 0 until byteLength) {
        out[i + 1] = (remaining and 0xFFu).toByte()
        remaining = remaining shr 8
    }
    return out
}

private fun readLittleEndian(bytes: ByteArray, offset: Int, byteLength: Int): ULong {
    var value = 0uL
    for (i in 0 until byteLength) {
        value = value or ((bytes[offset + i].toInt() and 0xff).toULong() shl (8 * i))
    }
    return value
}

private fun requireBytes(bytes: ByteArray, offset: Int, needed: Int) {
    if (offset + needed > bytes.size) {
        throw IllegalArgumentException("unexpected end of data reading CompactSize")
    }
}
