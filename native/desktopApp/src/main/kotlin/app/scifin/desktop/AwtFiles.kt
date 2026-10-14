package app.scifin.desktop

import app.scifin.app.Files
import app.scifin.app.PickedFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

/** Windows: the system file dialogs (`java.awt.FileDialog`), on the Swing thread. */
object AwtFiles : Files {
    override suspend fun open(extensions: List<String>): PickedFile? = withContext(Dispatchers.Swing) {
        val dlg = FileDialog(null as Frame?, if ("json" in extensions) "Restore a backup" else "Import a statement", FileDialog.LOAD)
        // a wildcard name is the hint the Windows dialog honours; setFilenameFilter does nothing there
        dlg.file = extensions.joinToString(";") { "*.$it" }
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext null
        val name = dlg.file ?: return@withContext null
        val f = File(dir, name)
        PickedFile(name, f.readText(Charsets.UTF_8))
    }

    override suspend fun save(suggestedName: String, text: String): Boolean = withContext(Dispatchers.Swing) {
        val dlg = FileDialog(null as Frame?, "Save a backup", FileDialog.SAVE)
        dlg.file = suggestedName
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext false
        val name = dlg.file ?: return@withContext false
        File(dir, name).writeText(text, Charsets.UTF_8)
        true
    }
}
