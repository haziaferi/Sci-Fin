package app.scifin.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * §4.7's automatic copy on Android. The folder is a Storage Access Framework tree the person picked, whose access
 * the app keeps (a persisted permission, taken when it was picked) -- so the daily background task can write to
 * it with no dialog. The copies' password is sealed with an AES key that lives in the Android Keystore and never
 * leaves it: the sealed text is useless on another phone, or after the app's data is cleared.
 */
class AndroidCopyPlace(context: Context) : CopyPlace {
    private val app = context.applicationContext
    private val resolver get() = app.contentResolver

    private fun treeDoc(folder: String): Uri {
        val tree = Uri.parse(folder)
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }

    /** Each child as (display name, document id); null when the tree cannot be read. */
    private fun children(folder: String): List<Pair<String, String>>? = runCatching {
        val tree = Uri.parse(folder)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }
    }.getOrNull()

    override fun write(folder: String, name: String, text: String): Boolean = runCatching {
        val doc = DocumentsContract.createDocument(resolver, treeDoc(folder), "application/json", name) ?: return false
        val ok = resolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)); true } ?: false
        if (!ok) DocumentsContract.deleteDocument(resolver, doc)
        ok
    }.getOrDefault(false)

    override fun list(folder: String): List<String>? = children(folder)?.map { it.first }

    override fun delete(folder: String, name: String): Boolean = runCatching {
        val id = children(folder)?.firstOrNull { it.first == name }?.second ?: return false
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(Uri.parse(folder), id))
    }.getOrDefault(false)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    override fun seal(secret: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = c.doFinal(secret.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(c.iv + sealed, Base64.NO_WRAP)
    }.getOrNull()

    override fun unseal(sealed: String): String? = runCatching {
        val all = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12)) }
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEY_ALIAS = "scifin.copies"
    }
}
