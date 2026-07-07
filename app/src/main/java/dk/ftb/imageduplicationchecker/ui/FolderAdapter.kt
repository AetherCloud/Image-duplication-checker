package dk.ftb.imageduplicationchecker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dk.ftb.imageduplicationchecker.databinding.ItemFolderBinding

/** Adapter for displaying a list of folder paths in either the whitelist or blacklist. */
class FolderAdapter(
	private val folders: MutableList<String> = mutableListOf(),
	private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

	fun submitList(items: List<String>) {
		folders.clear()
		folders.addAll(items)
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
		val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return FolderViewHolder(binding)
	}

	override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
		holder.bind(folders[position])
	}

	override fun getItemCount(): Int = folders.size

	inner class FolderViewHolder(private val binding: ItemFolderBinding) :
		RecyclerView.ViewHolder(binding.root) {
		fun bind(path: String) {
			binding.folderPath.text = path
			binding.removeButton.setOnClickListener { onRemove(path) }
		}
	}
}
