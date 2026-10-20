package app.scifin.engine

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal actual fun pbkdf2Sha256(pin: String, salt: ByteArray, iterations: Int): ByteArray {
    val chars = pin.toCharArray()
    val spec = PBEKeySpec(chars, salt, iterations, 256)
    try {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword(); chars.fill('\u0000')
    }
}

private val random = SecureRandom()

internal actual fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)
