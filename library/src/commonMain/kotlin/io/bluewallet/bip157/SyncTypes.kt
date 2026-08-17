package io.bluewallet.bip157

class FilterSyncOptions(
    val blockHashAt: (height: Int) -> ByteArray,
    val filterType: Int = FILTER_TYPE_BASIC,
    val headerBatchSize: Int? = null,
    val filterBatchSize: Int? = null,
)

class BlockHeaderTip(
    val height: Int,
    val hash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockHeaderTip) return false
        return height == other.height && hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int = 31 * height + hash.contentHashCode()
}

sealed interface SyncInput {
    class SetTip(val tip: BlockHeaderTip) : SyncInput
    data object Start : SyncInput
    class CfCheckpt(val msg: CFCheckpt) : SyncInput
    class CfHeaders(val msg: CFHeaders, val startHeight: Int) : SyncInput
    class CFilterMsg(val msg: CFilter, val height: Int) : SyncInput
    class RequestFilters(val fromHeight: Int, val toHeight: Int) : SyncInput
}

sealed interface OutboundMessage {
    val command: String

    class GetCFCheckpt(val msg: io.bluewallet.bip157.GetCFCheckpt) : OutboundMessage {
        override val command: String get() = "getcfcheckpt"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GetCFCheckpt) return false
            return msg == other.msg
        }

        override fun hashCode(): Int = msg.hashCode()
    }

    class GetCFHeaders(val msg: io.bluewallet.bip157.GetCFHeaders) : OutboundMessage {
        override val command: String get() = "getcfheaders"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GetCFHeaders) return false
            return msg == other.msg
        }

        override fun hashCode(): Int = msg.hashCode()
    }

    class GetCFilters(val msg: io.bluewallet.bip157.GetCFilters) : OutboundMessage {
        override val command: String get() = "getcfilters"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is GetCFilters) return false
            return msg == other.msg
        }

        override fun hashCode(): Int = msg.hashCode()
    }
}

sealed interface SyncEvent {
    class CheckpointAccepted(val headers: List<ByteArray>) : SyncEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CheckpointAccepted) return false
            return headers.contentsEqual(other.headers)
        }

        override fun hashCode(): Int = headers.contentsHash()
    }

    class HeadersAccepted(val fromHeight: Int, val headers: List<ByteArray>) : SyncEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HeadersAccepted) return false
            return fromHeight == other.fromHeight && headers.contentsEqual(other.headers)
        }

        override fun hashCode(): Int = 31 * fromHeight + headers.contentsHash()
    }

    class FilterAccepted(
        val height: Int,
        val blockHash: ByteArray,
        val filterBytes: ByteArray,
    ) : SyncEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FilterAccepted) return false
            return height == other.height &&
                blockHash.contentEquals(other.blockHash) &&
                filterBytes.contentEquals(other.filterBytes)
        }

        override fun hashCode(): Int {
            var result = height
            result = 31 * result + blockHash.contentHashCode()
            result = 31 * result + filterBytes.contentHashCode()
            return result
        }
    }

    data class Mismatch(val reason: String, val height: Int? = null) : SyncEvent

    data object Idle : SyncEvent
}

data class SyncOutput(
    val send: List<OutboundMessage>,
    val events: List<SyncEvent>,
)

interface FilterSync {
    fun push(input: SyncInput): SyncOutput
    fun getFilterHeader(height: Int): ByteArray?
}

class EncodedOutbound(
    val command: String,
    val shortId: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedOutbound) return false
        return command == other.command &&
            shortId == other.shortId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = command.hashCode()
        result = 31 * result + shortId
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

fun encodeOutbound(msg: OutboundMessage): EncodedOutbound =
    when (msg) {
        is OutboundMessage.GetCFCheckpt -> EncodedOutbound(
            command = "getcfcheckpt",
            shortId = BIP157_SHORT_IDS.getcfcheckpt,
            payload = encodeGetCFCheckpt(msg.msg),
        )
        is OutboundMessage.GetCFHeaders -> EncodedOutbound(
            command = "getcfheaders",
            shortId = BIP157_SHORT_IDS.getcfheaders,
            payload = encodeGetCFHeaders(msg.msg),
        )
        is OutboundMessage.GetCFilters -> EncodedOutbound(
            command = "getcfilters",
            shortId = BIP157_SHORT_IDS.getcfilters,
            payload = encodeGetCFilters(msg.msg),
        )
    }
