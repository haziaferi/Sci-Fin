package app.scifin.desktop

import app.scifin.app.CopyFolder
import app.scifin.app.Files
import javax.swing.JFileChooser
import javax.swing.UIManager
import app.scifin.app.PickedFile
import app.scifin.i18n.tr
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

/** Windows: the system file dialogs (`java.awt.FileDialog`), on the Swing thread. */
object AwtFiles : Files {
    override suspend fun open(extensions: List<String>): PickedFile? = withContext(Dispatchers.Swing) {
        val dlg = FileDialog(null as Frame?, if ("json" in extensions) tr("Restore a backup") else tr("Import a statement"), FileDialog.LOAD)
        // a wildcard name is the hint the Windows dialog honours; setFilenameFilter does nothing there
        dlg.file = extensions.joinToString(";") { "*.$it" }
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext null
        val name = dlg.file ?: return@withContext null
        val f = File(dir, name)
        PickedFile(name, f.readText(Charsets.UTF_8))
    }

    override suspend fun save(suggestedName: String, text: String): Boolean = withContext(Dispatchers.Swing) {
        val dlg = FileDialog(null as Frame?, tr("Save a backup"), FileDialog.SAVE)
        dlg.file = suggestedName
        dlg.isVisible = true
        val dir = dlg.directory ?: return@withContext false
        val name = dlg.file ?: return@withContext false
        File(dir, name).writeText(text, Charsets.UTF_8)
        true
    }

    /** §4.7's automatic copy: Swing's folder chooser in the system look, since AWT's dialog cannot pick a folder. */
    override suspend fun pickFolder(): CopyFolder? = withContext(Dispatchers.Swing) {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = tr("Choose a folder for the automatic copies")
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showDialog(null, tr("Use this folder")) != JFileChooser.APPROVE_OPTION) return@withContext null
        val dir = chooser.selectedFile?.takeIf { it.isDirectory } ?: return@withContext null
        CopyFolder(dir.absolutePath, dir.absolutePath)
    }
}
