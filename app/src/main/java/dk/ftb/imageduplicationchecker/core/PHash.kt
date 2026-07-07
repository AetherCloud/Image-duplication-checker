package dk.ftb.imageduplicationchecker.core

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Perceptual hash (pHash) using DCT. Returns a 64-bit hash that is resistant to
 * scaling, compression, mild colour changes and minor cropping, so images that
 * look the same but have different resolutions still hash close together.
 *
 * Algorithm:
 *  1. Scale the image to a small fixed size (32×32) and convert to grayscale.
 *  2. Compute the 2D DCT-II of the grayscale grid.
 *  3. Take the top-left 8×8 low-frequency coefficients, keeping the DC term and
 *     dropping the single highest-frequency corner coefficient (u=7, v=7) which
 *     is the most sensitive to aliasing under resampling.
 *  4. Build a 64-bit hash from whether each coefficient is above the median.
 *
 * Two perceptually similar images produce hashes that differ in only a few bits;
 * the Hamming distance between them is small.
 */
object PHash {

	private const val SIZE = 32
	private const val HASH_BITS = 64
	private const val SMALL = 8

	fun compute(bitmap: Bitmap): Long {
		val small = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
		val gray = FloatArray(SIZE * SIZE)
		for (y in 0 until SIZE) {
			for (x in 0 until SIZE) {
				val pixel = small.getPixel(x, y)
				gray[y * SIZE + x] = toGray(pixel)
			}
		}
		if (small != bitmap) small.recycle()

		val dct = dct2d(gray, SIZE)
		// Top-left 8×8 block of low-frequency coefficients. Include the DC term
		// (mean luminance, very stable under resampling) and drop the single
		// highest-frequency corner (u=7, v=7), which is most sensitive to aliasing.
		// 64 coefficients → 64-bit hash.
		val values = FloatArray(HASH_BITS)
		var idx = 0
		outer@ for (v in 0 until SMALL) {
			for (u in 0 until SMALL) {
				if (u == 7 && v == 7) continue
				values[idx++] = dct[v * SIZE + u]
				if (idx == HASH_BITS) break@outer
			}
		}

		val median = median(values)
		var hash = 0L
		for (i in 0 until HASH_BITS) {
			if (values[i] > median) {
				hash = hash or (1L shl i)
			}
		}
		return hash
	}

	fun hammingDistance(a: Long, b: Long): Int {
		return java.lang.Long.bitCount(a xor b)
	}

	/** Returns a 0–100 similarity score based on Hamming distance. 100 = identical hash. */
	fun similarityPercent(a: Long, b: Long): Int {
		val distance = hammingDistance(a, b)
		return ((HASH_BITS - distance) * 100 / HASH_BITS).coerceIn(0, 100)
	}

	private fun toGray(pixel: Int): Float {
		val r = Color.red(pixel)
		val g = Color.green(pixel)
		val b = Color.blue(pixel)
		return 0.299f * r + 0.587f * g + 0.114f * b
	}

	private fun dct2d(values: FloatArray, n: Int): FloatArray {
		val result = FloatArray(n * n)
		val cosTable = cosineTable(n)
		// 2D DCT separable: DCT along rows then along columns.
		val tmp = FloatArray(n * n)
		for (y in 0 until n) {
			for (u in 0 until n) {
				var sum = 0f
				for (x in 0 until n) {
					sum += values[y * n + x] * cosTable[u * n + x]
				}
				val cu = if (u == 0) 1f / Math.sqrt(n.toDouble()).toFloat() else Math.sqrt(2.0 / n).toFloat()
				tmp[y * n + u] = cu * sum
			}
		}
		for (x in 0 until n) {
			for (v in 0 until n) {
				var sum = 0f
				for (y in 0 until n) {
					sum += tmp[y * n + x] * cosTable[v * n + y]
				}
				val cv = if (v == 0) 1f / Math.sqrt(n.toDouble()).toFloat() else Math.sqrt(2.0 / n).toFloat()
				result[v * n + x] = cv * sum
			}
		}
		return result
	}

	/** Cached cosine lookup table keyed by grid size [n]. */
	private val cosTables = HashMap<Int, FloatArray>()

	private fun cosineTable(n: Int): FloatArray {
		return cosTables.getOrPut(n) {
			FloatArray(n * n) { i ->
				val row = i / n
				val col = i % n
				Math.cos((2.0 * col + 1) * row * Math.PI / (2.0 * n)).toFloat()
			}
		}
	}

	private fun median(values: FloatArray): Float {
		val sorted = values.sortedArray()
		val mid = sorted.size / 2
		return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
	}
}
