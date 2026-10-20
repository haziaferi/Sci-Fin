package app.scifin.engine

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun gcm(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        updateAAD(aad)
    }

internal actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
    gcm(Cipher.ENCRYPT_MODE, key, iv, aad).doFinal(plain)

internal actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? =
    try { gcm(Cipher.DECRYPT_MODE, key, iv, aad).doFinal(sealed) } catch (_: AEADBadTagException) { null }
