package com.archivetv.data

import java.security.MessageDigest
import java.security.SecureRandom

/** The parental PIN: exactly four ASCII digits, stored only as a salted SHA-256. */
object PinHash {
    fun isValid(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * Wrong-PIN lockout: five wrong entries in a row lock entry for 30 s. A correct entry resets the count.
 * Kept in memory: a four-digit PIN only needs to resist a child trying numbers on the remote.
 */
class PinGate(private val now: () -> Long = System::currentTimeMillis) {
    private var wrong = 0
    private var lockedUntil = 0L

    fun lockedForMs(): Long = (lockedUntil - now()).coerceAtLeast(0L)

    fun record(correct: Boolean) {
        if (correct) {
            wrong = 0
            return
        }
        wrong++
        if (wrong >= MAX_WRONG) {
            wrong = 0
            lockedUntil = now() + LOCK_MS
        }
    }

    private companion object {
        const val MAX_WRONG = 5
        const val LOCK_MS = 30_000L
    }
}
