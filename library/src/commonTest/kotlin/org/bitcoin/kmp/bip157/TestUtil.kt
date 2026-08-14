package org.bitcoin.kmp.bip157

import kotlin.test.assertFails
import kotlin.test.assertTrue

internal fun assertFailsMatching(substring: String, block: () -> Unit) {
    val ex = assertFails(block)
    val message = ex.message ?: ""
    assertTrue(
        message.contains(substring),
        "expected message to contain '$substring', got '$message'",
    )
}

internal val GENESIS_STOP_INTERNAL =
    hexToBytes("43497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000")
