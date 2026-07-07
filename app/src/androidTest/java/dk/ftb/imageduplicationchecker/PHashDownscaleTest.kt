package dk.ftb.imageduplicationchecker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dk.ftb.imageduplicationchecker.core.PHash
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that PHash produces a stable hash for perceptually identical images
 * where one is a downscaled copy of the other, on the real production decode path.
 *
 * The decode step mirrors BitmapDecoder.decodeFullForHash: full ARGB_8888 with
 * inSampleSize = 1, no power-of-two downsampling. This is the path DuplicateScanner
 * actually uses; a previous version of this test decoded with the default options
 * and accidentally bypassed the bug.
 *
 * Each pair must satisfy hammingDistance <= 6 and similarityPercent >= 90 after
 * the fix. Pre-fix expected: Examples 1 and 2 exceed the threshold.
 */
@RunWith(AndroidJUnit4::class)
class PHashDownscaleTest {

	private data class Pair(val a: String, val b: String)

	private val pairs = listOf(
		Pair("example_1_a.png", "example_1_b.png"),  // 1080x2340 vs 432x936  (40% scale)
		Pair("example_2_a.png", "example_2_b.png"),  // 1080x2340 vs 648x1404 (60% scale)
		Pair("example_3_a.jpg", "example_3_b.jpg")   // 1920x1280 vs 1000x667 (~52% scale)
	)

	@Test
	fun downscalePairHashesAreClose() {
		val context = InstrumentationRegistry.getInstrumentation().context
		for (pair in pairs) {
			val a = decodeFull(context, pair.a)
			val b = decodeFull(context, pair.b)
			assertNotNull("Could not decode ${pair.a}", a)
			assertNotNull("Could not decode ${pair.b}", b)

			val hashA = PHash.compute(a!!)
			val hashB = PHash.compute(b!!)
			val distance = PHash.hammingDistance(hashA, hashB)
			val similarity = PHash.similarityPercent(hashA, hashB)

			assertTrue(
				"${pair.a} vs ${pair.b}: expected Hamming distance <= 6, got $distance",
				distance <= 6
			)
			assertTrue(
				"${pair.a} vs ${pair.b}: expected similarity >= 90%, got $similarity",
				similarity >= 90
			)

			a.recycle()
			b.recycle()
		}
	}

	/** Mirrors BitmapDecoder.decodeFullForHash: full ARGB_8888, no downsampling. */
	private fun decodeFull(context: android.content.Context, name: String): Bitmap? {
		val opts = BitmapFactory.Options().apply {
			inSampleSize = 1
			inPreferredConfig = Bitmap.Config.ARGB_8888
		}
		return context.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
	}
}
