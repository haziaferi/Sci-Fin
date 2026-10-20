package app.scifin.app

/** A folder chosen for §4.7's automatic copies: the platform's handle on it and the name to show. */
data class CopyFolder(val id: String, val label: String)

/**
 * What §4.7's automatic copy needs from the platform, with no person and no dialog present -- it runs from the
 * daily background task as well as from the app. Android: the Storage Access Framework on a persisted tree, and
 * the Android Keystore. Windows: the file system, and DPAPI for the current user.
 */
interface CopyPlace {
    /** Writes [text] as [name] in the folder; false when the folder is gone, unwritable or full. */
    fun write(folder: String, name: String, text: String): Boolean

    /** The file names in the folder; null when it cannot be read (gone, or access withdrawn). */
    fun list(folder: String): List<String>?

    fun delete(folder: String, name: String): Boolean

    /** [secret] sealed so only this device, for this person, can read it back; null if the key store refuses. */
    fun seal(secret: String): String?

    /** [seal]'s inverse; null when it cannot be read here (another device, a reset key store). */
    fun unseal(sealed: String): String?

    /** For tests and the headless renders. */
    object None : CopyPlace {
        override fun write(folder: String, name: String, text: String) = false
        override fun list(folder: String): List<String>? = null
        override fun delete(folder: String, name: String) = false
        override fun seal(secret: String): String? = null
        override fun unseal(sealed: String): String? = null
    }
}

/** How often the automatic copy runs. Weekly is what turning it on chooses (owner, 2026-10-19). */
enum class CopyEvery(val token: String, val label: String, val days: Int) {
    OFF("off", "Off", 0), DAILY("daily", "Daily", 1), WEEKLY("weekly", "Weekly", 7);

    companion object {
        fun of(token: String): CopyEvery = entries.firstOrNull { it.token == token } ?: OFF
    }
}
