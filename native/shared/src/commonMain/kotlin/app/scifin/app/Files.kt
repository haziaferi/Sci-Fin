package app.scifin.app

/** A file the person chose: its display name and its whole text. */
data class PickedFile(val name: String, val text: String)

/**
 * The host's file dialogs, behind one interface: Android answers with the Storage Access
 * Framework, Windows with the system dialog. §4.7's backup and §4.3's statement import are
 * the only callers -- nothing else in the app reads or writes a file the person can see.
 */
interface Files {
    /** Let the person pick a text file to read; null if they cancel. [extensions] is a hint (`json`, `csv`…). */
    suspend fun open(extensions: List<String>): PickedFile?

    /** Let the person choose where to write [text] under [suggestedName]; false if they cancel. */
    suspend fun save(suggestedName: String, text: String): Boolean

    /** For tests and the headless renders: no dialogs, nothing picked. */
    object None : Files {
        override suspend fun open(extensions: List<String>): PickedFile? = null
        override suspend fun save(suggestedName: String, text: String) = false
    }
}
