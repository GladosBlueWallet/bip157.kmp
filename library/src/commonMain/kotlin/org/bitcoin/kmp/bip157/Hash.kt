package org.bitcoin.kmp.bip157

import org.kotlincrypto.hash.sha2.SHA256

/** SHA256d: `SHA256(SHA256(data))`, as used throughout Bitcoin consensus code. */
internal fun sha256d(data: ByteArray): ByteArray {
    val inner = SHA256().digest(data)
    return SHA256().digest(inner)
}
