package io.bluewallet.bip157

/** Hash of a serialized filter: `SHA256d(filterBytes)` (BIP-158 "Filter Hash"). */
fun filterHash(filterBytes: ByteArray): ByteArray = sha256d(filterBytes)

/**
 * Chains a filter hash onto the previous filter header (BIP-158 "Filter Header"):
 * `SHA256d(filterHash || prevHeader)`.
 *
 * Both arguments and the return value are in **internal** (little-endian) byte
 * order. RPC / vector filter headers are display-order; convert with
 * `displayHashToInternal()` before calling and when comparing results.
 */
fun filterHeader(filterHashBytes: ByteArray, prevHeader: ByteArray): ByteArray {
    if (filterHashBytes.size != 32) {
        throw IllegalArgumentException(
            "filter hash must be 32 bytes, got ${filterHashBytes.size}",
        )
    }
    if (prevHeader.size != 32) {
        throw IllegalArgumentException(
            "previous header must be 32 bytes, got ${prevHeader.size}",
        )
    }
    return sha256d(concatBytes(filterHashBytes, prevHeader))
}

/**
 * Reverses a display-order (RPC/big-endian hex) block hash into Bitcoin's
 * internal little-endian byte representation.
 */
fun displayHashToInternal(display: ByteArray): ByteArray {
    if (display.size != 32) {
        throw IllegalArgumentException(
            "display hash must be 32 bytes, got ${display.size}",
        )
    }
    val out = ByteArray(display.size)
    for (i in display.indices) {
        out[i] = display[display.size - 1 - i]
    }
    return out
}

fun deriveFilterHeaders(prevHeader: ByteArray, filterHashes: List<ByteArray>): List<ByteArray> {
    var prev = prevHeader
    val out = ArrayList<ByteArray>(filterHashes.size)
    for (fh in filterHashes) {
        val h = filterHeader(fh, prev)
        out.add(h)
        prev = h
    }
    return out
}

fun verifyFilterHeaderChain(
    prevHeader: ByteArray,
    filterHashes: List<ByteArray>,
    expectedHeaders: List<ByteArray>? = null,
    expectedTipHeader: ByteArray? = null,
): Boolean {
    val derived = deriveFilterHeaders(prevHeader, filterHashes)

    if (expectedHeaders != null) {
        if (!derived.contentsEqual(expectedHeaders)) return false
    }

    if (expectedTipHeader != null) {
        if (derived.isEmpty()) return false
        val tip = derived.last()
        if (!equalBytes(tip, expectedTipHeader)) return false
    }

    return true
}

fun verifyCFilterAgainstHeader(
    filterBytes: ByteArray,
    previousFilterHeader: ByteArray,
    expectedFilterHeader: ByteArray,
): Boolean {
    val fh = filterHash(filterBytes)
    val header = filterHeader(fh, previousFilterHeader)
    return equalBytes(header, expectedFilterHeader)
}
