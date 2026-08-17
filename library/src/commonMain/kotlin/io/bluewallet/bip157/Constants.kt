package io.bluewallet.bip157

const val FILTER_TYPE_BASIC: Int = 0x00

/**
 * Max filters per `getcfilters` / hashes per `cfheaders` response, matching
 * Bitcoin Core `MAX_GETCFILTERS_SIZE` / `MAX_GETCFHEADERS_SIZE`.
 * Core disconnects when `stopHeight - startHeight >=` these values, so a
 * request may cover at most this many blocks (`stop = start + N - 1`).
 */
const val MAX_GETCFILTERS_RANGE: Int = 1000
const val MAX_GETCFHEADERS_RANGE: Int = 2000

/**
 * Cap on `cfcheckpt` header count. 65_535 keeps the CompactSize count within
 * the 1-3 byte `FilterHeadersLength` field of the BIP-157 message table.
 */
const val MAX_CFCHECKPT_HEADERS: Int = 65_535
const val CF_CHECKPT_INTERVAL: Int = 1000

/** Bitcoin Core `NODE_COMPACT_FILTERS` service bit (BIP-157). */
const val NODE_COMPACT_FILTERS: Int = 1 shl 6

/**
 * Max encoded filter byte length accepted in a `cfilter` payload.
 * Bitcoin Core caps the whole message payload at
 * `MAX_PROTOCOL_MESSAGE_LENGTH` (4_000_000), so the filter body may be at
 * most that minus the envelope: 1 (type) + 32 (block hash) + 5 (CompactSize).
 */
const val MAX_CFILTER_BYTES: Int = 4_000_000 - 1 - 32 - 5

/** BIP-324 short message type IDs for BIP-157 commands (Bitcoin Core / bip324). */
object BIP157_SHORT_IDS {
    const val getcfilters: Int = 22
    const val cfilter: Int = 23
    const val getcfheaders: Int = 24
    const val cfheaders: Int = 25
    const val getcfcheckpt: Int = 26
    const val cfcheckpt: Int = 27
}
