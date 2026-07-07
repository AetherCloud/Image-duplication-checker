package dk.ftb.imageduplicationchecker.core

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import dk.ftb.imageduplicationchecker.data.DuplicateGroup
import dk.ftb.imageduplicationchecker.data.FolderFilter
import dk.ftb.imageduplicationchecker.data.ImageItem
import dk.ftb.imageduplicationchecker.util.BitmapDecoder
import dk.ftb.imageduplicationchecker.util.PathUtils
import java.util.UUID

/**
 * Scans the device for images, computes a perceptual hash for each one and groups
 * perceptually similar images together. The scan can be cancelled mid-flight.
 */
class DuplicateScan(
	private val contentResolver: ContentResolver,
	private val folderFilter: FolderFilter,
	private val similarityThreshold: Int = DEFAULT_THRESHOLD
) {

	fun interface ProgressListener {
		fun onProgress(scanned: Int, total: Int)
	}

	@Volatile
	private var cancelled = false

	fun cancel() {
		cancelled = true
	}

	fun isCancelled(): Boolean = cancelled

	/** Returns the list of duplicate groups found. Empty if none or if cancelled. */
	fun scan(listener: ProgressListener? = null): List<DuplicateGroup> {
		cancelled = false
		val candidates = queryImages()
		val filtered = filterByFolders(candidates)
		if (filtered.isEmpty()) return emptyList()

		val hashed = ArrayList<ImageItem>(filtered.size)
		for ((index, item) in filtered.withIndex()) {
			if (cancelled) return emptyList()
			// Throttle progress updates so we don't post a new state per image.
			if (index % PROGRESS_BATCH == 0 || index == filtered.size - 1) {
				listener?.onProgress(index, filtered.size)
			}
			try {
				val hash = computeHash(item) ?: continue
				hashed.add(item.copy(phash = hash))
			} catch (_: Exception) {
				// Skip images we cannot decode.
			}
		}
		if (cancelled) return emptyList()

		return groupDuplicates(hashed)
	}

	private fun queryImages(): List<ImageItem> {
		val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
		} else {
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI
		}
		val projection = arrayOf(
			MediaStore.Images.Media._ID,
			MediaStore.Images.Media.DISPLAY_NAME,
			MediaStore.Images.Media.DATA,
			MediaStore.Images.Media.WIDTH,
			MediaStore.Images.Media.HEIGHT,
			MediaStore.Images.Media.SIZE,
			MediaStore.Images.Media.DATE_ADDED,
			MediaStore.Images.Media.DATE_MODIFIED
		)
		val results = ArrayList<ImageItem>(64)
		val cursor: Cursor? = contentResolver.query(
			collection,
			projection,
			null,
			null,
			"${MediaStore.Images.Media.DATE_MODIFIED} DESC"
		)
		cursor?.use { c ->
			val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
			val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
			val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
			val widthIdx = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
			val heightIdx = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
			val sizeIdx = c.getColumnIndex(MediaStore.Images.Media.SIZE)
			val addedIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
			val modifiedIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

			while (c.moveToNext()) {
				if (cancelled) break
				val id = c.getLong(idIdx)
				val uri = ContentUris.withAppendedId(collection, id)
				val data = if (dataIdx >= 0) c.getString(dataIdx) ?: "" else ""
				val width = if (widthIdx >= 0) c.getInt(widthIdx) else 0
				val height = if (heightIdx >= 0) c.getInt(heightIdx) else 0
				val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
				val added = if (addedIdx >= 0) c.getLong(addedIdx) else 0L
				val modified = if (modifiedIdx >= 0) c.getLong(modifiedIdx) else 0L
				results.add(
					ImageItem(
						uri = uri,
						displayName = c.getString(nameIdx) ?: "image",
						path = data,
						width = width,
						height = height,
						sizeBytes = size,
						dateAdded = added,
						dateModified = modified,
						phash = 0L
					)
				)
			}
		}
		return results
	}

	private fun filterByFolders(items: List<ImageItem>): List<ImageItem> {
		return items.filter { item ->
			val folder = PathUtils.parentFolder(item.path)
			folder != null && folderFilter.shouldInclude(folder)
		}
	}

	private fun computeHash(item: ImageItem): Long? {
		// Reuse the MediaStore dimensions when available to skip the bounds-reading pass.
		val bmp = BitmapDecoder.decodeSampled(
			contentResolver,
			item.uri,
			targetPx = HASH_TARGET_PX,
			knownWidth = item.width,
			knownHeight = item.height
		) ?: return null
		val hash = PHash.compute(bmp)
		bmp.recycle()
		return hash
	}

	/**
	 * Group images whose hashes are within the Hamming-distance threshold of each other.
	 * Uses single-pass LSH-style bucketing on the lowest few bits to avoid O(n²) comparisons.
	 */
	private fun groupDuplicates(items: List<ImageItem>): List<DuplicateGroup> {
		if (items.size < 2) return emptyList()

		val thresholdBits = (HASH_BITS * (100 - similarityThreshold) / 100).coerceAtLeast(1)
		val buckets = HashMap<Long, MutableList<Int>>(items.size)
		// Project the 64-bit hash into a small bucket key. Images that share the top
		// bits almost always hash close together; we then verify with a real Hamming check.
		for (i in items.indices) {
			val key = items[i].phash ushr BUCKET_SHIFT
			buckets.getOrPut(key) { mutableListOf() }.add(i)
			// Also bucket on a second, offset band of bits to catch near-misses.
			val altKey = (items[i].phash ushr (BUCKET_SHIFT / 2)) and ALT_MASK.toLong()
			buckets.getOrPut(altKey) { mutableListOf() }.add(i)
		}

		val visited = BooleanArray(items.size)
		val groups = ArrayList<DuplicateGroup>()

		for (i in items.indices) {
			if (visited[i]) continue
			val component = ArrayList<Int>()
			val queue = ArrayDeque<Int>()
			queue.add(i)
			visited[i] = true
			while (queue.isNotEmpty()) {
				val cur = queue.removeFirst()
				component.add(cur)
				// Inline the two candidate bucket keys to avoid allocating a Set per iteration.
				val primaryKey = items[cur].phash ushr BUCKET_SHIFT
				val altKey = (items[cur].phash ushr (BUCKET_SHIFT / 2)) and ALT_MASK.toLong()
				for (key in listOf(primaryKey, altKey)) {
					val bucket = buckets[key] ?: continue
					for (j in bucket) {
						if (!visited[j] && j != cur) {
							if (PHash.hammingDistance(items[cur].phash, items[j].phash) <= thresholdBits) {
								visited[j] = true
								queue.add(j)
							}
						}
					}
				}
			}
			if (component.size >= 2) {
				val images = component.map { items[it] }
				val similarity = computeGroupSimilarity(images)
				groups.add(
					DuplicateGroup(
						id = UUID.randomUUID().toString(),
						images = images.sortedByDescending { it.width * it.height },
						similarityPercent = similarity
					)
				)
			}
		}
		return groups.sortedByDescending { it.size }
	}

	private fun computeGroupSimilarity(images: List<ImageItem>): Int {
		if (images.size < 2) return 100
		var minSim = 100
		for (i in images.indices) {
			for (j in (i + 1) until images.size) {
				val sim = PHash.similarityPercent(images[i].phash, images[j].phash)
				if (sim < minSim) minSim = sim
			}
		}
		return minSim
	}

	companion object {
		const val DEFAULT_THRESHOLD = 90
		private const val HASH_BITS = 64
		private const val BUCKET_SHIFT = 48
		private const val ALT_MASK = 0xFFFF
		private const val HASH_TARGET_PX = 64
		private const val PROGRESS_BATCH = 16
	}
}
