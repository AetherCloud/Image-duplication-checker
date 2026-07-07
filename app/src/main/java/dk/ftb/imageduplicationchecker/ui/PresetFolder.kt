package dk.ftb.imageduplicationchecker.ui

import dk.ftb.imageduplicationchecker.util.PathUtils

/** A top-level folder the user can add without going through the SAF tree picker. */
data class PresetFolder(val label: String, val path: String)

object PresetFolders {
	/**
	 * Top-level folders the SAF tree picker is known to refuse on Android 13+
	 * (DocumentsUI's "choose another folder" gate). Path values match what
	 * `MediaStore.Images.Media.DATA` returns for files inside these folders.
	 */
	val ALL: List<PresetFolder> = listOf(
		PresetFolder("Downloads", "/storage/emulated/0/Download"),
		PresetFolder("DCIM", "/storage/emulated/0/DCIM"),
		PresetFolder("Pictures", "/storage/emulated/0/Pictures"),
		PresetFolder("Screenshots", "/storage/emulated/0/Pictures/Screenshots"),
	)

	/** True when [path] matches one of the preset paths (after normalisation). */
	fun findRestricted(path: String): PresetFolder? {
		val normalized = PathUtils.normalize(path)
		return ALL.firstOrNull { PathUtils.normalize(it.path) == normalized }
	}
}
