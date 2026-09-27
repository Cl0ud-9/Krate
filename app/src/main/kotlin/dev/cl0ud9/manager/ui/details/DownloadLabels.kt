package dev.cl0ud9.manager.ui.details

private const val BYTES_PER_MB = 1024 * 1024

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MB.toFloat())

private const val PERCENT = 100

// "Starting download..." until the first byte arrives, then how far along it is
internal fun downloadingLabel(
    bytes: Long,
    total: Long?,
    fraction: Float,
): String =
    when {
        total == null && bytes == 0L -> "Starting download..."
        total != null -> "Downloading ${formatMb(bytes)} of ${formatMb(total)} MB (${(fraction * PERCENT).toInt()}%)"
        else -> "Downloading ${formatMb(bytes)} MB"
    }
