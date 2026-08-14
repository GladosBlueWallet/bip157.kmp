package org.bitcoin.kmp.bip157

private val HEX_DIGITS = "0123456789abcdef"

fun hexToBytes(hex: String): ByteArray {
    if (hex.length % 2 != 0) throw IllegalArgumentException("hex length must be even")
    for (ch in hex) {
        val ok = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
        if (!ok) throw IllegalArgumentException("invalid hex")
    }
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return out
}

fun bytesToHex(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xff
        out.append(HEX_DIGITS[v ushr 4])
        out.append(HEX_DIGITS[v and 0x0f])
    }
    return out.toString()
}

fun equalBytes(a: ByteArray, b: ByteArray): Boolean = a.contentEquals(b)

fun concatBytes(vararg parts: ByteArray): ByteArray {
    var n = 0
    for (p in parts) n += p.size
    val out = ByteArray(n)
    var o = 0
    for (p in parts) {
        p.copyInto(out, o)
        o += p.size
    }
    return out
}

internal fun List<ByteArray>.contentsEqual(other: List<ByteArray>): Boolean {
    if (size != other.size) return false
    for (i in indices) if (!this[i].contentEquals(other[i])) return false
    return true
}

internal fun List<ByteArray>.contentsHash(): Int {
    var result = 1
    for (b in this) result = 31 * result + b.contentHashCode()
    return result
}

internal fun List<ByteArray>.copyBytes(): List<ByteArray> = map { it.copyOf() }
