package app.scifin.engine

import app.scifin.i18n.tr
import app.scifin.i18n.trf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/*
 * §4.7: a backup file with a password (owner, 2026-10-19/20). The plain file is wrapped whole in an AES-256-GCM
 * envelope keyed by PBKDF2-HMAC-SHA256 -- Lunar's shape, which a sibling project reached independently. Outside
 * the encryption, and readable by anyone holding the file: that it is Sci-Fin's, the date, and the person's hint
 * (mandatory, so a file can always say what its password was meant to recall). Those three are bound to the
 * ciphertext as associated data, so none can be altered without the file refusing to open.
 */

@Serializable
data class EncryptedBackupFile(
    val app: String = "sci-fin",
    val encrypted: Int = 1,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val exportedAt: String,
    val hint: String,
    val data: String,
)

/** A file that needs its password before it can be validated: what the prompt shows, and the text to open. */
data class LockedBackup(val text: String, val hint: String, val exportedAt: String)

object BackupCrypto {
    /** A file leaves the device, so its cost is a file's (OWASP's figure for PBKDF2-HMAC-SHA256), not the PIN's. */
    const val ITERATIONS = 600_000
    /** What a file may ask for: below, it was not written by this app; above, it would hang the phone. */
    val ACCEPTED_ITERATIONS = 100_000..5_000_000
    const val MIN_PASSWORD = 8

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /**
     * Why [password] can't protect a file yet, or null when it can: long enough, typed twice the same, and with a
     * hint -- required for a file (owner, 2026-10-20), and never the password itself ([hintProblem]).
     */
    fun passwordProblem(password: String, again: String, hint: String): String? = when {
        password.length < MIN_PASSWORD -> trf("At least {0} characters.", MIN_PASSWORD)
        again != password -> if (again.isEmpty()) tr("Type the password again.") else tr("The two don’t match yet.")
        hint.isBlank() -> tr("A hint is needed: it is the one thing that can help you remember this password.")
        else -> hintProblem(password, hint, "password")
    }

    private fun aad(exportedAt: String, hint: String, iterations: Int) = "sci-fin|1|$iterations|$exportedAt|$hint".encodeToByteArray()

    fun encrypt(plain: String, password: String, hint: String, exportedAt: String, iterations: Int = ITERATIONS): String {
        val salt = randomBytes(16); val iv = randomBytes(12)
        val key = pbkdf2Bytes(password, salt, iterations)
        val h = hint.trim()
        val sealed = aesGcmEncrypt(key, iv, plain.encodeToByteArray(), aad(exportedAt, h, iterations))
        return json.encodeToString(
            EncryptedBackupFile.serializer(),
            EncryptedBackupFile(iterations = iterations, salt = Base64.encode(salt), iv = Base64.encode(iv), exportedAt = exportedAt, hint = h, data = Base64.encode(sealed)),
        )
    }

    /** The prompt's view of [text] when it is an encrypted backup; null for anything else (a plain file goes to [Backup.validate]). */
    fun locked(text: String): LockedBackup? {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if ((o["encrypted"] as? JsonPrimitive)?.content == null) return null
        val f = runCatching { json.decodeFromJsonElement(EncryptedBackupFile.serializer(), o) }.getOrNull() ?: return LockedBackup(text, "", "")
        return LockedBackup(text, f.hint, f.exportedAt)
    }

    sealed class Opened {
        data class Plain(val text: String) : Opened()
        data object WrongPassword : Opened()
        data class Refused(val reason: String) : Opened()
    }

    fun decrypt(text: String, password: String): Opened {
        val f = runCatching { json.decodeFromJsonElement(EncryptedBackupFile.serializer(), json.parseToJsonElement(text) as JsonObject) }.getOrNull()
            ?: return Opened.Refused(tr("That backup is damaged and can’t be opened. Nothing was changed."))
        if (f.app != "sci-fin") return Opened.Refused(tr("That file is not a Sci-Fin backup."))
        if (f.encrypted != 1 || f.kdf != "PBKDF2-HMAC-SHA256") return Opened.Refused(tr("That backup was made by a newer version of the app. Nothing was changed."))
        if (f.iterations !in ACCEPTED_ITERATIONS) return Opened.Refused(tr("That backup is damaged and can’t be opened. Nothing was changed."))
        val parts = runCatching { Triple(Base64.decode(f.salt), Base64.decode(f.iv), Base64.decode(f.data)) }.getOrNull()
            ?: return Opened.Refused(tr("That backup is damaged and can’t be opened. Nothing was changed."))
        val key = pbkdf2Bytes(password, parts.first, f.iterations)
        // GCM cannot tell a wrong password from an altered file: both fail the tag
        val plain = aesGcmDecrypt(key, parts.second, parts.third, aad(f.exportedAt, f.hint, f.iterations)) ?: return Opened.WrongPassword
        return Opened.Plain(plain.decodeToString())
    }
}

/** PBKDF2-HMAC-SHA256 over any password, 32 bytes (the PIN's [pbkdf2Sha256] is the same function). */
internal fun pbkdf2Bytes(password: String, salt: ByteArray, iterations: Int): ByteArray = pbkdf2Sha256(password, salt, iterations)

/** AES-256-GCM, 128-bit tag appended. */
internal expect fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray

/** The plain bytes, or null when the tag does not verify (wrong key, or the file was altered). */
internal expect fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray?
