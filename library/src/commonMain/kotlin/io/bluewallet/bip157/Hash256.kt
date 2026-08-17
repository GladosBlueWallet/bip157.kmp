package io.bluewallet.bip157

fun requireHash32(hash: ByteArray, label: String) {
    if (hash.size != 32) {
        throw IllegalArgumentException("$label must be 32 bytes, got ${hash.size}")
    }
}

class Hash256Read(val hash: ByteArray, val next: Int)

fun readHash256(bytes: ByteArray, offset: Int): Hash256Read {
    if (offset + 32 > bytes.size) throw IllegalArgumentException("truncated hash256")
    return Hash256Read(hash = bytes.copyOfRange(offset, offset + 32), next = offset + 32)
}

fun writeHash256(hash: ByteArray): ByteArray {
    requireHash32(hash, "hash")
    return hash.copyOf()
}
