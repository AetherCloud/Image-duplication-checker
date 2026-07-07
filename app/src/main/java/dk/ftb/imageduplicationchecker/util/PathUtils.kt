package dk.ftb.imageduplicationchecker.util

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Shared filesystem-path helpers. All functions normalize trailing slashes and a leading
 * slash so that comparisons are stable regardless of how the path was obtained.
 */
object PathUtils {

	/** Normalizes a path: trims, strips trailing slashes (except for root), ensures a leading slash. */
	fun normalize(path: String): String {
		var p = path.trim()
		if (p.isEmpty()) return p
		while (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
		if (p.isEmpty()) return "/"
		if (!p.startsWith("/")) p = "/$p"
		return p
	}

	/**
	 * Returns the normalized parent folder of [path], or null when [path] is empty or
	 * already at the root. Equivalent to repeatedly stripping the last path segment.
	 */
	fun parentOf(path: String): String? {
		if (path.isEmpty()) return null
		val normalized = normalize(path)
		if (normalized == "/") return null
		val withoutTrailing =
			if (normalized.endsWith("/")) normalized.dropLast(1) else normalized
		val idx = withoutTrailing.lastIndexOf('/')
		if (idx <= 0) return "/"
		return normalize(withoutTrailing.substring(0, idx))
	}

	/** Returns the parent folder of a raw filesystem path from MediaStore's DATA column. */
	fun parentFolder(rawPath: String): String? {
		if (rawPath.isEmpty()) return null
		val p = if (rawPath.endsWith("/")) rawPath.dropLast(1) else rawPath
		val idx = p.lastIndexOf('/')
		if (idx <= 0) return null
		return p.substring(0, idx)
	}

	/**
	 * Converts a SAF tree URI (from `ActivityResultContracts.OpenDocumentTree`) into a filesystem
	 * path that matches the format `MediaStore.Images.Media.DATA` produces, or returns `null`
	 * when the URI cannot be mapped (cloud providers, removable storage, unknown authorities).
	 *
	 * Only `com.android.externalstorage.documents` with a `primary` volume is supported, which
	 * maps to `/storage/emulated/0`. Other volume names can't be reliably mapped across devices.
	 */
	fun treeUriToPath(uri: Uri): String? {
		if (uri.authority != "com.android.externalstorage.documents") return null
		val documentId = DocumentsContract.getTreeDocumentId(uri)
		val colonIdx = documentId.indexOf(':')
		if (colonIdx < 0) return null
		val volume = documentId.substring(0, colonIdx)
		val relativePath = documentId.substring(colonIdx + 1)
		val mountPoint = when (volume) {
			"primary" -> "/storage/emulated/0"
			else -> return null
		}
		return normalize("$mountPoint/$relativePath")
	}
}
