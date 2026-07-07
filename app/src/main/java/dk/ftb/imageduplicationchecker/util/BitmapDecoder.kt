package dk.ftb.imageduplicationchecker.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Shared bitmap decoding helpers. All methods sample down the image so that the
 * decoded bitmap's largest dimension is at most [targetPx] on either side.
 */
object BitmapDecoder {

	/**
	 * Decodes a sampled bitmap from [uri]. When [knownWidth] and [knownHeight] are > 0
	 * they are used to compute the sample size without a separate bounds-reading pass;
	 * otherwise the image header is read first via `inJustDecodeBounds`.
	 */
	fun decodeSampled(
		resolver: ContentResolver,
		uri: Uri,
		targetPx: Int,
		knownWidth: Int = 0,
		knownHeight: Int = 0
	): Bitmap? {
		val (width, height) = if (knownWidth > 0 && knownHeight > 0) {
			knownWidth to knownHeight
		} else {
			val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
			bounds.outWidth to bounds.outHeight
		}
		if (width <= 0 || height <= 0) return null
		val sample = computeSampleSize(width, height, targetPx)
		val opts = BitmapFactory.Options().apply {
			inSampleSize = sample
			inPreferredConfig = Bitmap.Config.RGB_565
		}
		return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
	}

	/** Returns a power-of-two sample size that fits [targetPx] in the larger dimension. */
	fun computeSampleSize(width: Int, height: Int, targetPx: Int): Int {
		var sample = 1
		var maxDim = maxOf(width, height)
		while (maxDim / sample > targetPx) sample *= 2
		return sample.coerceAtLeast(1)
	}

	/**
	 * Decodes the image at full resolution in ARGB_8888, with no downsampling. Used
	 * only by the hash pipeline: a single bilinear resize from this native-resolution
	 * bitmap is more scale-stable than the two-stage cascade of `decodeSampled` (which
	 * picks a power-of-two inSampleSize and yields different intermediate sizes for
	 * different source resolutions) followed by `createScaledBitmap` inside PHash.
	 *
	 * The caller is responsible for recycling the returned bitmap.
	 */
	fun decodeFullForHash(resolver: ContentResolver, uri: Uri): Bitmap? {
		val opts = BitmapFactory.Options().apply {
			inSampleSize = 1
			inPreferredConfig = Bitmap.Config.ARGB_8888
		}
		return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
	}
}
