package dk.ftb.imageduplicationchecker.ui

import android.content.ContentResolver
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dk.ftb.imageduplicationchecker.R
import dk.ftb.imageduplicationchecker.data.DuplicateGroup
import dk.ftb.imageduplicationchecker.data.ImageItem
import dk.ftb.imageduplicationchecker.databinding.ItemDuplicateGroupBinding
import dk.ftb.imageduplicationchecker.util.ThumbnailCache

/**
 * Adapter for the top-level list of duplicate groups. Each group renders its own
 * inner vertical list of images via [ImageAdapter].
 */
class DuplicateGroupAdapter(
	private val resolver: ContentResolver,
	private val cache: ThumbnailCache,
	private val groups: MutableList<DuplicateGroup> = mutableListOf(),
	private val onViewImage: (ImageItem) -> Unit,
	private val onDeletedImage: (DuplicateGroup, ImageItem, Int) -> Unit,
	private val onOpenFullImage: (ImageItem) -> Unit
) : RecyclerView.Adapter<DuplicateGroupAdapter.GroupViewHolder>() {

	fun submitList(items: List<DuplicateGroup>) {
		groups.clear()
		groups.addAll(items)
		notifyDataSetChanged()
	}

	fun clear() {
		groups.clear()
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
		val binding =
			ItemDuplicateGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return GroupViewHolder(binding)
	}

	override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
		holder.bind(groups[position])
	}

	override fun getItemCount(): Int = groups.size

	inner class GroupViewHolder(private val binding: ItemDuplicateGroupBinding) :
		RecyclerView.ViewHolder(binding.root) {

		// One adapter per holder, reused across binds so coroutines and the LinearLayoutManager
		// aren't recreated on every scroll.
		private var currentGroup: DuplicateGroup? = null
		private val imageAdapter = ImageAdapter(
			resolver = resolver,
			cache = cache,
			onView = onViewImage,
			onDelete = { item, positionInGroup ->
				currentGroup?.let { onDeletedImage(it, item, positionInGroup) }
			},
			onOpenFullImage = onOpenFullImage
		)

		init {
			binding.imagesList.layoutManager = LinearLayoutManager(binding.root.context)
			binding.imagesList.isNestedScrollingEnabled = false
			binding.imagesList.adapter = imageAdapter
		}

		fun bind(group: DuplicateGroup) {
			currentGroup = group
			val context = binding.root.context
			binding.groupHeader.text = context.getString(
				R.string.group_header,
				group.images.size,
				group.similarityPercent
			)
			imageAdapter.submitList(group.images)
		}
	}
}
