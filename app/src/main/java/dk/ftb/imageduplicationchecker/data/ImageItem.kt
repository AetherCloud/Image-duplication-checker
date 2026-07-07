package dk.ftb.imageduplicationchecker.data

import android.net.Uri

/** A single image discovered on the device, with metadata and its perceptual hash. */
data class ImageItem(
	val uri: Uri,
	val displayName: String,
	val path: String,
	val width: Int,
	val height: Int,
	val sizeBytes: Long,
	val dateAdded: Long,
	val dateModified: Long,
	val phash: Long
) {
	val resolutionString: String
		get() = if (width > 0 && height > 0) "${width}×${height}" else "—"

	val sizeString: String
		get() = formatFileSize(sizeBytes)

	companion object {
		fun formatFileSize(bytes: Long): String {
			if (bytes < 1024) return "$bytes B"
			val kb = bytes / 1024.0
			if (kb < 1024) return String.format("%.1f KB", kb)
			val mb = kb / 1024.0
			if (mb < 1024) return String.format("%.1f MB", mb)
			val gb = mb / 1024.0
			return String.format("%.2f GB", gb)
		}
	}
}
