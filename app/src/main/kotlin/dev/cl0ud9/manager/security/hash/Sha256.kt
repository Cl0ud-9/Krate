package dev.cl0ud9.manager.security.hash

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

private const val STREAM_BUFFER_SIZE = 8192

// hex sha256 of a stream, section 19 + 42.9 of the spec, streamed so large apks never load fully into memory
fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    input.use {
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun sha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

// case-insensitive since manifest hex may differ in casing from a locally computed one
fun hashesMatch(
    expectedHex: String,
    actualHex: String,
): Boolean = expectedHex.equals(actualHex, ignoreCase = true)
