package dk.ftb.imageduplicationchecker.data

import dk.ftb.imageduplicationchecker.util.PathUtils

/**
 * Decides whether a folder should be scanned based on a whitelist and a blacklist.
 *
 * Rules (see the filter screen hint):
 *  - If no whitelist entry is an ancestor of (or equal to) the folder, the folder is ignored.
 *  - Otherwise, find the *closest* whitelist ancestor. If a blacklist entry is a strictly
 *    closer ancestor than that whitelist ancestor, the folder is ignored.
 *  - Otherwise the folder is included.
 *
 * Walk every parent from the folder up to the root and remember the first whitelist match
 * and the first blacklist match. Whichever is closer (deeper) wins. A tie (folder itself
 * is in both lists) is treated as whitelisted — the explicit whitelist is the strongest signal.
 */
class FolderFilter(
	whitelist: List<String>,
	blacklist: List<String>
) {

	private val normalizedWhitelist: Set<String> =
		whitelist.map { PathUtils.normalize(it) }.toHashSet()

	private val normalizedBlacklist: Set<String> =
		blacklist.map { PathUtils.normalize(it) }.toHashSet()

	fun shouldInclude(folderPath: String): Boolean {
		if (normalizedWhitelist.isEmpty()) return false

		var whitelistDepth = -1
		var blacklistDepth = -1

		// Walk from the folder itself up to the root. The deeper the match, the smaller depth.
		var current = PathUtils.normalize(folderPath)
		var depth = 0
		while (current.isNotEmpty()) {
			if (whitelistDepth == -1 && current in normalizedWhitelist) whitelistDepth = depth
			if (blacklistDepth == -1 && current in normalizedBlacklist) blacklistDepth = depth
			if (whitelistDepth != -1 && blacklistDepth != -1) break

			val parent = PathUtils.parentOf(current) ?: break
			if (parent == current) break
			current = parent
			depth++
		}

		// No whitelist ancestor → ignored.
		if (whitelistDepth == -1) return false
		// A blacklist ancestor blocks the folder only when it is strictly closer than the
		// closest whitelist ancestor. On a tie (folder itself is in both lists) the explicit
		// whitelist wins, so the folder is included.
		if (blacklistDepth != -1 && blacklistDepth < whitelistDepth) return false
		return true
	}

	/**
	 * Returns only the paths from [candidates] that should be included.
	 * Useful for testing and for filtering the MediaStore result set.
	 */
	fun filter(candidates: List<String>): List<String> = candidates.filter(::shouldInclude)
}
