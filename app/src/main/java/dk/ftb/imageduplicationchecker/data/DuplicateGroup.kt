package dk.ftb.imageduplicationchecker.data

/** A group of images that are perceptually similar to each other. */
data class DuplicateGroup(
	val id: String,
	val images: List<ImageItem>,
	val similarityPercent: Int
) {
	val size: Int get() = images.size
}
