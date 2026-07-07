package dk.ftb.imageduplicationchecker.util

import android.graphics.Bitmap
import android.util.LruCache

/**
 * A small in-memory LRU cache for image thumbnails, keyed by URI string. Sized in bytes
 * of bitmap memory (default: ~1/8 of the available JVM heap).
 */
class ThumbnailCache(maxBytes: Int = (Runtime.getRuntime().maxMemory() / 8).toInt()) :
	LruCache<String, Bitmap>(maxBytes) {

	override fun sizeOf(key: String, value: Bitmap): Int {
		return value.byteCount
	}
}
