package dk.ftb.imageduplicationchecker

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import dk.ftb.imageduplicationchecker.data.FolderPreferences
import dk.ftb.imageduplicationchecker.databinding.ActivityFilterBinding
import dk.ftb.imageduplicationchecker.ui.FolderAdapter
import dk.ftb.imageduplicationchecker.util.PathUtils

class FilterActivity : AppCompatActivity() {

	private lateinit var binding: ActivityFilterBinding
	private lateinit var folderPrefs: FolderPreferences
	private lateinit var whitelistAdapter: FolderAdapter
	private lateinit var blacklistAdapter: FolderAdapter

	private var pendingTarget: Target = Target.WHITELIST

	private enum class Target { WHITELIST, BLACKLIST }

	private val folderPicker = registerForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri != null) {
			val path = PathUtils.treeUriToPath(uri)
			if (path != null) {
				when (pendingTarget) {
					Target.WHITELIST -> folderPrefs.addToWhitelist(path)
					Target.BLACKLIST -> folderPrefs.addToBlacklist(path)
				}
				refreshLists()
			} else {
				Toast.makeText(this, getString(R.string.folder_picker_unsupported), Toast.LENGTH_LONG).show()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityFilterBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)
		binding.toolbar.setNavigationOnClickListener { finish() }

		folderPrefs = FolderPreferences(this)

		whitelistAdapter = FolderAdapter { path ->
			folderPrefs.removeFromWhitelist(path)
			refreshLists()
		}
		blacklistAdapter = FolderAdapter { path ->
			folderPrefs.removeFromBlacklist(path)
			refreshLists()
		}

		binding.whitelistList.layoutManager = LinearLayoutManager(this)
		binding.whitelistList.adapter = whitelistAdapter
		binding.blacklistList.layoutManager = LinearLayoutManager(this)
		binding.blacklistList.adapter = blacklistAdapter

		binding.addWhitelist.setOnClickListener {
			pendingTarget = Target.WHITELIST
			folderPicker.launch(null)
		}
		binding.addBlacklist.setOnClickListener {
			pendingTarget = Target.BLACKLIST
			folderPicker.launch(null)
		}

		refreshLists()
	}

	private fun refreshLists() {
		val whitelist = folderPrefs.getWhitelist().sorted()
		val blacklist = folderPrefs.getBlacklist().sorted()

		whitelistAdapter.submitList(whitelist)
		blacklistAdapter.submitList(blacklist)

		binding.whitelistEmpty.visibility = if (whitelist.isEmpty()) View.VISIBLE else View.GONE
		binding.blacklistEmpty.visibility = if (blacklist.isEmpty()) View.VISIBLE else View.GONE
	}
}
