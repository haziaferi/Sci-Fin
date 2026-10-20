package app.scifin.engine

import app.scifin.model.AppLock
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * §4.7's app lock (owner, 2026-10-19): an optional PIN of the app's own, off by default, on the phone and the
 * desktop alike. Four to six digits, an optional hint, a wait that grows after five wrong PINs, and -- for a
 * forgotten PIN -- erasing the device and starting over. The PIN guards the screen, not the data: the store
 * is not encrypted with it, so a forgotten PIN can cost nothing that a backup cannot bring back.
 */

/** "Lock after leaving the app": leaving is switching away or minimising, not a notification shade. */
enum class LockAfter(val token: String, val label: String, val wait: Duration) {
    IMMEDIATELY("immediately", "Immediately", Duration.ZERO),
    ONE_MINUTE("one_minute", "After 1 minute", 1.minutes),
    FIVE_MINUTES("five_minutes", "After 5 minutes", 5.minutes);

    companion object {
        fun of(token: String): LockAfter = entries.firstOrNull { it.token == token } ?: ONE_MINUTE
    }
}

object Pin {
    const val MIN = 4
    const val MAX = 6

    /**
     * PBKDF2-HMAC-SHA256's cost. A PIN has at most a million values, so no cost makes a copied hash safe on its
     * own; the cost is a brake, the sandbox is the wall. This one is about a third of a second on a 2018 phone.
     */
    const val ITERATIONS = 120_000

    /** Wrong PINs that cost nothing; the next one starts the wait. */
    const val FREE_TRIES = 5
    private val FIRST_WAIT = 30.seconds
    private val LONGEST_WAIT = 1.hours

    fun valid(pin: String): Boolean = pin.length in MIN..MAX && pin.all(Char::isDigit)

    /** What a field keeps of what is typed: digits only, at most [MAX]. */
    fun clean(typed: String): String = typed.filter(Char::isDigit).take(MAX)

    /**
     * The wait owed after [failed] wrong PINs in a row: none for the first [FREE_TRIES - 1], then 30 seconds,
     * doubling with each further miss, never more than an hour.
     */
    fun waitAfter(failed: Int): Duration {
        if (failed < FREE_TRIES) return Duration.ZERO
        val doublings = (failed - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT * (1 shl doublings)).coerceAtMost(LONGEST_WAIT)
    }

    /** A new lock row for [pin], salted afresh. */
    fun lockFor(pin: String, hint: String?, after: LockAfter, iterations: Int = ITERATIONS): AppLock {
        require(valid(pin))
        val salt = randomBytes(16)
        return AppLock(
            salt = Base64.encode(salt), hash = Base64.encode(pbkdf2Sha256(pin, salt, iterations)),
            iterations = iterations, pinLength = pin.length, hint = hint?.trim()?.ifEmpty { null }, lockAfter = after.token,
        )
    }

    /** Whether [pin] is the one [lock] holds; compared in constant time. */
    fun matches(lock: AppLock, pin: String): Boolean {
        if (!valid(pin)) return false
        val want = Base64.decode(lock.hash)
        val got = pbkdf2Sha256(pin, Base64.decode(lock.salt), lock.iterations)
        if (want.size != got.size) return false
        var diff = 0
        for (i in want.indices) diff = diff or (want[i].toInt() xor got[i].toInt())
        return diff == 0
    }
}

/**
 * Why [hint] can't be kept beside [secret], or null when it can. An empty hint is fine (the hint is optional).
 * "The same" is judged after trimming and ignoring case, so "1234 " or "Secret" can't pass as different from
 * "1234" or "secret". [noun] names the secret in the message: "PIN" or "password".
 */
fun hintProblem(secret: String, hint: String, noun: String): String? {
    val h = hint.trim()
    if (h.isEmpty() || secret.isEmpty()) return null
    return if (h.equals(secret.trim(), ignoreCase = true)) "The hint can’t be the $noun itself." else null
}

/** PBKDF2-HMAC-SHA256, 32 bytes. The platform's own implementation (javax.crypto on both hosts). */
internal expect fun pbkdf2Sha256(pin: String, salt: ByteArray, iterations: Int): ByteArray

/** Cryptographically strong random bytes. */
internal expect fun randomBytes(n: Int): ByteArray
