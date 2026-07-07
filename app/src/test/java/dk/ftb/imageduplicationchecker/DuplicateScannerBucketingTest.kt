package dk.ftb.imageduplicationchecker

import dk.ftb.imageduplicationchecker.core.PHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pure-Kotlin unit tests for the 8-band LSH bucketing used by
 * DuplicateScanner.groupDuplicates. These mirror the production logic and verify:
 *   - band key coverage of the 64-bit hash (no overlap)
 *   - probability of two near-duplicate (d=6) hashes sharing at least one band
 *   - Hamming-distance helper behaviour
 *
 * The instrumented androidTest suite covers the end-to-end path on real images.
 */
class DuplicateScannerBucketingTest {

    private val bandCount = 8
    private val bandBits = 8
    private val bandMask = (1L shl bandBits) - 1

    private fun bandKey(phash: Long, band: Int): Int =
        ((phash ushr (band * bandBits)) and bandMask).toInt()

    @Test
    fun bandsAreNonOverlappingAndCoverAllBits() {
        // 0x76543210FEDCBA98 has bits set in all 8 byte-aligned windows.
        val hash = 0x76543210FEDCBA98UL.toLong()
        val keys = (0 until bandCount).map { bandKey(hash, it) }
        val expected = listOf(
            (hash and 0xFFL).toInt(),
            ((hash ushr 8) and 0xFFL).toInt(),
            ((hash ushr 16) and 0xFFL).toInt(),
            ((hash ushr 24) and 0xFFL).toInt(),
            ((hash ushr 32) and 0xFFL).toInt(),
            ((hash ushr 40) and 0xFFL).toInt(),
            ((hash ushr 48) and 0xFFL).toInt(),
            ((hash ushr 56) and 0xFFL).toInt()
        )
        assertEquals(expected, keys)
    }

    @Test
    fun nearDuplicatePairsShareAtLeastOneBand() {
        // For Hamming distance d=6 across 64 bits with 8 disjoint 8-bit bands,
        // each band's survival probability is C(56,6)/C(64,6) ≈ 0.471. Across 8
        // bands the probability of catching the pair is ≈ 1 - 0.529^8 ≈ 0.997.
        // Use a loose threshold: >= 95% of 2000 trials should be caught.
        val rng = Random(42)
        var sharedBandCount = 0
        val trials = 2000
        repeat(trials) {
            val base = rng.nextLong()
            val partner = flipRandomBits(base, 6, rng)
            for (band in 0 until bandCount) {
                if (bandKey(base, band) == bandKey(partner, band)) {
                    sharedBandCount++
                    return@repeat
                }
            }
        }
        assertTrue(
            "Expected >= 95% of d=6 pairs to share a band, got $sharedBandCount/$trials",
            sharedBandCount >= trials * 95 / 100
        )
    }

    @Test
    fun farApartPairsRarelyShareBands() {
        // d=32 hashes: chance of sharing one specific 8-bit band is C(32,8)/C(64,8)
        // ≈ 0.00238. Across 8 bands ≈ 1.9%. Across 500 trials expect ~10 collisions;
        // assert < 50 (i.e., well under 10%) — Hamming-distance gate still rejects.
        val rng = Random(7)
        var pairsSharingAnyBand = 0
        val trials = 500
        repeat(trials) {
            val base = rng.nextLong()
            val partner = flipRandomBits(base, 32, rng)
            for (band in 0 until bandCount) {
                if (bandKey(base, band) == bandKey(partner, band)) {
                    pairsSharingAnyBand++
                    break
                }
            }
        }
        assertTrue(
            "Far-apart pair band-sharing rate too high: $pairsSharingAnyBand/$trials",
            pairsSharingAnyBand < trials / 10
        )
    }

    @Test
    fun hammingDistanceHelperBehaves() {
        // Sanity check on the helper used by the scanner gate.
        assertEquals(0, PHash.hammingDistance(0L, 0L))
        assertEquals(64, PHash.hammingDistance(0L, -1L))
        assertEquals(6, PHash.hammingDistance(0L, 0x3FL))
        assertEquals(100, PHash.similarityPercent(0L, 0L))
        // 1 bit different -> (64-1)*100/64 = 98.
        assertEquals(98, PHash.similarityPercent(0L, 1L shl 6))
        assertNotEquals(100, PHash.similarityPercent(0L, -1L))
    }

    private fun flipRandomBits(value: Long, count: Int, rng: Random): Long {
        var v = value
        val flipped = HashSet<Int>()
        while (flipped.size < count) {
            val bit = rng.nextInt(64)
            if (flipped.add(bit)) {
                v = v xor (1L shl bit)
            }
        }
        return v
    }
}
