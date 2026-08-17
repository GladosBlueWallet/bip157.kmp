package io.bluewallet.bip157

/**
 * Enforce BIP-157 / Bitcoin Core height-range rules for filter requests.
 * Core disconnects when `stopHeight - startHeight >= maxDiff`.
 */
fun assertHeightRange(
    startHeight: Int,
    stopHeight: Int,
    maxDiff: Int,
    label: String,
) {
    if (startHeight < 0) {
        throw IllegalArgumentException("$label startHeight must be a non-negative integer")
    }
    if (stopHeight < startHeight) {
        throw IllegalArgumentException("$label stopHeight must be an integer >= startHeight")
    }
    if (stopHeight - startHeight >= maxDiff) {
        throw IllegalArgumentException(
            "$label range too large: stopHeight - startHeight = ${stopHeight - startHeight} (must be < $maxDiff)",
        )
    }
}

fun assertGetCFiltersRange(startHeight: Int, stopHeight: Int) {
    assertHeightRange(startHeight, stopHeight, MAX_GETCFILTERS_RANGE, "getcfilters")
}

fun assertGetCFHeadersRange(startHeight: Int, stopHeight: Int) {
    assertHeightRange(startHeight, stopHeight, MAX_GETCFHEADERS_RANGE, "getcfheaders")
}
