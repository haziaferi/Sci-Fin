package app.scifin.app

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * §4.7's automatic copy on Windows: the chosen folder is a plain path, and the copies' password is sealed with
 * DPAPI for the current user, so only this Windows account on this machine can read it back.
 *
 * DPAPI is reached through PowerShell, as the notifications are. The secret never appears in a script or on a
 * command line: it goes in through the process's standard input and comes back through its output, both
 * Base64 so no code page can bend a character on the way.
 */
object WindowsCopyPlace : CopyPlace {

    override fun write(folder: String, name: String, text: String): Boolean = runCatching {
        val dir = File(folder)
        if (!dir.isDirectory) return false
        // written aside and renamed, so a sync app never picks up half a file
        val part = File(dir, "$name.part")
        part.writeText(text, Charsets.UTF_8)
        part.renameTo(File(dir, name)) || run { part.delete(); false }
    }.getOrDefault(false)

    override fun list(folder: String): List<String>? = File(folder).takeIf { it.isDirectory }?.list()?.toList()

    override fun delete(folder: String, name: String): Boolean = File(folder, name).delete()

    override fun seal(secret: String): String? = dpapi(
        "${'$'}b = [Convert]::FromBase64String([Console]::In.ReadToEnd().Trim()); " +
            "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect(${'$'}b, ${'$'}null, 'CurrentUser'))",
        Base64.getEncoder().encodeToString(secret.toByteArray(Charsets.UTF_8)),
    )

    override fun unseal(sealed: String): String? = dpapi(
        "${'$'}b = [Convert]::FromBase64String([Console]::In.ReadToEnd().Trim()); " +
            "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Unprotect(${'$'}b, ${'$'}null, 'CurrentUser'))",
        sealed,
    )?.let { runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrNull() }

    /** Runs [script] with [input] on standard input; its Base64 output, or null on any failure. */
    private fun dpapi(script: String, input: String): String? = runCatching {
        val p = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "Add-Type -AssemblyName System.Security; ${'$'}ErrorActionPreference = 'Stop'; $script",
        ).redirectErrorStream(false).start()
        p.outputStream.bufferedWriter().use { it.write(input) }
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0 || out.isEmpty()) null else out
    }.getOrNull()
}
