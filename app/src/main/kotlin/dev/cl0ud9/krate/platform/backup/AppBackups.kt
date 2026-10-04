package dev.cl0ud9.krate.platform.backup

import android.content.Context
import java.io.File

// an app's settings as the text it exported them to, and when Krate saved it
data class SavedBackup(
    val text: String,
    val savedAtMillis: Long,
)

// one saved backup per app, kept in Krate's own storage where no other app can read it
object AppBackups {
    private const val DIRECTORY = "app-backups"

    fun save(
        context: Context,
        packageName: String,
        text: String,
    ) {
        val file = fileFor(context, packageName)
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    fun read(
        context: Context,
        packageName: String,
    ): SavedBackup? =
        fileFor(context, packageName)
            .takeIf { it.isFile }
            ?.let { file -> runCatching { SavedBackup(file.readText(), file.lastModified()) }.getOrNull() }
            ?.takeIf { it.text.isNotBlank() }

    private fun fileFor(
        context: Context,
        packageName: String,
    ) = File(File(context.filesDir, DIRECTORY), "$packageName.txt")
}
