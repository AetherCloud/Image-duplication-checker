package dk.ftb.imageduplicationchecker.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dk.ftb.imageduplicationchecker.data.ImageItem
import dk.ftb.imageduplicationchecker.databinding.ItemImageBinding
import dk.ftb.imageduplicationchecker.util.BitmapDecoder
import dk.ftb.imageduplicationchecker.util.ThumbnailCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for the list of images inside a duplicate group. Each row shows the thumbnail,
 * name, path, resolution and file size, plus View and Delete buttons.
 */
class ImageAdapter(
	private val resolver: ContentResolver,
	private val cache: ThumbnailCache,
	private val images: MutableList<ImageItem> = mutableListOf(),
	private val onView: (ImageItem) -> Unit,
	private val onDelete: (ImageItem, Int) -> Unit,
	private val onOpenFullImage: (ImageItem) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	fun submitList(items: List<ImageItem>) {
		images.clear()
		images.addAll(items)
		notifyDataSetChanged()
	}

	fun removeAt(position: Int) {
		if (position in images.indices) {
			images.removeAt(position)
			notifyItemRemoved(position)
			notifyItemRangeChanged(position, images.size - position)
		}
	}

	fun currentItems(): List<ImageItem> = images.toList()

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
		val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ImageViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
		holder.bind(images[position])
	}

	override fun onViewRecycled(holder: ImageViewHolder) {
		holder.cancelLoad()
	}

	override fun getItemCount(): Int = images.size

	override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
		super.onDetachedFromRecyclerView(recyclerView)
		scope.cancel()
	}

	inner class ImageViewHolder(private val binding: ItemImageBinding) :
		RecyclerView.ViewHolder(binding.root) {

		private var loadJob: Job? = null

		fun bind(item: ImageItem) {
			loadJob?.cancel()
			binding.imageName.text = item.displayName
			binding.imagePath.text = item.path.ifEmpty { item.uri.toString() }
			binding.imageMeta.text = "${item.resolutionString} · ${item.sizeString}"
			binding.viewButton.setOnClickListener { onView(item) }
			binding.listBackground.setOnClickListener { onView(item) }
			binding.thumb.setOnClickListener { onOpenFullImage(item) }
			binding.deleteButton.setOnClickListener {
				onDelete(item, bindingAdapterPosition)
			}

			// Cached thumbnail hits show synchronously; misses are loaded off-thread.
			val cached = cache.get(item.uri.toString())
			if (cached != null) {
				binding.thumb.setImageBitmap(cached)
			} else {
				binding.thumb.setImageDrawable(null)
				loadJob = scope.launch {
					val bmp = loadThumbnail(resolver, item.uri)
					if (bmp != null) {
						cache.put(item.uri.toString(), bmp)
						binding.thumb.setImageBitmap(bmp)
					}
				}
			}
		}

		fun cancelLoad() {
			loadJob?.cancel()
			loadJob = null
		}
	}

	private suspend fun loadThumbnail(resolver: ContentResolver, uri: Uri): Bitmap? =
		withContext(Dispatchers.IO) {
			return@withContext try {
				BitmapDecoder.decodeSampled(resolver, uri, targetPx = 128)
			} catch (_: Exception) {
				null
			}
		}
}
