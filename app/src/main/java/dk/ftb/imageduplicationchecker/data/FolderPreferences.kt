package dk.ftb.imageduplicationchecker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's whitelist and blacklist folder tree URIs in SharedPreferences.
 * Each list is a set of strings (the document-tree URIs as returned by SAF).
 */
class FolderPreferences(context: Context) {

	private val prefs: SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	fun getWhitelist(): List<String> =
		prefs.getStringSet(KEY_WHITELIST, emptySet())?.toList() ?: emptyList()

	fun getBlacklist(): List<String> =
		prefs.getStringSet(KEY_BLACKLIST, emptySet())?.toList() ?: emptyList()

	fun saveWhitelist(paths: List<String>) {
		prefs.edit().putStringSet(KEY_WHITELIST, paths.toSet()).apply()
	}

	fun saveBlacklist(paths: List<String>) {
		prefs.edit().putStringSet(KEY_BLACKLIST, paths.toSet()).apply()
	}

	fun addToWhitelist(path: String) {
		saveWhitelist((getWhitelist() + path).distinct())
	}

	fun removeFromWhitelist(path: String) {
		saveWhitelist(getWhitelist() - path)
	}

	fun addToBlacklist(path: String) {
		saveBlacklist((getBlacklist() + path).distinct())
	}

	fun removeFromBlacklist(path: String) {
		saveBlacklist(getBlacklist() - path)
	}

	companion object {
		private const val PREFS_NAME = "folder_filters"
		private const val KEY_WHITELIST = "whitelist"
		private const val KEY_BLACKLIST = "blacklist"
	}
}
