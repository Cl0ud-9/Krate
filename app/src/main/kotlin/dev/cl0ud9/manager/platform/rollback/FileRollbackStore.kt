package dev.cl0ud9.manager.platform.rollback

import android.content.Context
import java.io.File

// android keeps installed apks world-readable under sourceDir specifically so tools like this can
// read them back out, section 21 + 42.13 of the spec - retention policy is one previous apk per app
class FileRollbackStore(
    private val context: Context,
) : RollbackStore {
    private val rollbackDir = File(context.filesDir, "rollback")

    override fun capture(packageName: String): Boolean {
        val sourcePath = runCatching { context.packageManager.getApplicationInfo(packageName, 0).sourceDir }.getOrNull()
        val sourceFile = sourcePath?.let { File(it) }?.takeIf { it.exists() } ?: return false
        rollbackDir.mkdirs()
        val destFile = File(rollbackDir, fileNameFor(packageName))
        return runCatching { sourceFile.copyTo(destFile, overwrite = true) }.isSuccess
    }

    override fun rollbackFile(packageName: String): File? =
        File(rollbackDir, fileNameFor(packageName)).takeIf {
            it.exists()
        }

    private fun fileNameFor(packageName: String): String = "$packageName.apk"
}
