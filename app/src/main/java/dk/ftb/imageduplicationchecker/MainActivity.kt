package dk.ftb.imageduplicationchecker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dk.ftb.imageduplicationchecker.data.DuplicateGroup
import dk.ftb.imageduplicationchecker.data.FolderFilter
import dk.ftb.imageduplicationchecker.data.FolderPreferences
import dk.ftb.imageduplicationchecker.data.ImageItem
import dk.ftb.imageduplicationchecker.databinding.ActivityMainBinding
import dk.ftb.imageduplicationchecker.ui.DuplicateGroupAdapter
import dk.ftb.imageduplicationchecker.util.DeleteConfirmationDialogFragment
import dk.ftb.imageduplicationchecker.util.Dialogs
import dk.ftb.imageduplicationchecker.util.MediaStoreDelete
import dk.ftb.imageduplicationchecker.util.ThumbnailCache
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding
	private val viewModel: ScanViewModel by viewModels()

	private lateinit var folderPrefs: FolderPreferences
	private lateinit var adapter: DuplicateGroupAdapter
	private val thumbnailCache = ThumbnailCache()

	private val permissionLauncher = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { result ->
		val granted = result.values.any { it }
		if (granted) {
			startScan()
		} else {
			showPermissionDenied()
		}
	}

	private var pendingDeleteUri: Uri? = null

	private var lastBackPressMs: Long = 0L

	private val exitCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			val now = SystemClock.elapsedRealtime()
			if (now - lastBackPressMs <= EXIT_CONFIRMATION_TIMEOUT_MS) {
				// Second press within window — let the default back behavior run.
				isEnabled = false
				onBackPressedDispatcher.onBackPressed()
				return
			}
			lastBackPressMs = now
			Snackbar.make(binding.root, getString(R.string.exit_warning), Snackbar.LENGTH_SHORT).show()
		}
	}

	private val deleteLauncher = registerForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult()
	) { result ->
		val uri = pendingDeleteUri
		pendingDeleteUri = null
		if (uri == null) return@registerForActivityResult
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		lifecycleScope.launch {
			viewModel.performDelete(uri)
			Snackbar.make(binding.root, getString(R.string.delete_success), Snackbar.LENGTH_SHORT).show()
		}
	}

	private val detailResultLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		val deletedUriStr = result.data?.getStringExtra(ImageDetailActivity.EXTRA_DELETED_URI)
			?: return@registerForActivityResult
		val uri = Uri.parse(deletedUriStr)
		lifecycleScope.launch {
			viewModel.performDelete(uri)
			Snackbar.make(binding.root, getString(R.string.delete_success), Snackbar.LENGTH_SHORT).show()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)

		onBackPressedDispatcher.addCallback(this, exitCallback)

		pendingDeleteUri = savedInstanceState?.getString(STATE_PENDING_DELETE_URI)?.let(Uri::parse)

		supportFragmentManager.setFragmentResultListener(
			DeleteConfirmationDialogFragment.REQUEST_KEY, this
		) { _, bundle ->
			val uri = bundle.getString(DeleteConfirmationDialogFragment.RESULT_URI)
				?.let(Uri::parse) ?: return@setFragmentResultListener
			performConfirmedDelete(uri)
		}

		folderPrefs = FolderPreferences(this)

		adapter = DuplicateGroupAdapter(
			resolver = contentResolver,
			cache = thumbnailCache,
			onViewImage = { item -> openImageDetail(item) },
			onDeletedImage = { group, item, _ ->
				confirmDelete(group, item)
			},
			onOpenFullImage = { item -> openFullImage(item) }
		)
		binding.duplicateList.layoutManager = LinearLayoutManager(this)
		binding.duplicateList.adapter = adapter

		binding.scanFab.setOnClickListener {
			when (viewModel.state.value) {
				is ScanViewModel.State.Scanning, ScanViewModel.State.Hashing -> viewModel.stopScan()
				else -> if (hasImagePermission()) startScan() else requestImagePermission()
			}
		}

		viewModel.state.observe(this) { state -> renderState(state) }
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_filters -> {
				startActivity(Intent(this, FilterActivity::class.java))
				true
			}
			R.id.action_clear -> {
				viewModel.clearResults()
				true
			}
			R.id.action_settings -> {
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", packageName, null)
				}
				startActivity(intent)
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	override fun onResume() {
		super.onResume()
		// If the user changed the folder filters, refresh the existing results so a removed
		// folder's images don't linger on screen.
		folderFilter()?.let { viewModel.refreshIfStale(it) }
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		pendingDeleteUri?.let { outState.putString(STATE_PENDING_DELETE_URI, it.toString()) }
	}

	private fun hasImagePermission(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.READ_MEDIA_IMAGES
			) == PackageManager.PERMISSION_GRANTED
		} else {
			ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.READ_EXTERNAL_STORAGE
			) == PackageManager.PERMISSION_GRANTED
		}
	}

	private fun requestImagePermission() {
		val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
		} else {
			arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
		}
		permissionLauncher.launch(perms)
	}

	private fun showPermissionDenied() {
		AlertDialog.Builder(this)
			.setTitle(getString(R.string.perm_rationale))
			.setMessage(getString(R.string.perm_denied))
			.setPositiveButton(getString(R.string.perm_open_settings)) { _, _ ->
				val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", packageName, null)
				}
				startActivity(intent)
			}
			.setNegativeButton(getString(R.string.delete_cancel), null)
			.show()
	}

	private fun startScan() {
		val filter = folderFilter()
		if (filter == null) {
			Snackbar.make(binding.root, getString(R.string.filters_whitelist_empty), Snackbar.LENGTH_LONG)
				.setAction(getString(R.string.action_filters)) {
					startActivity(Intent(this, FilterActivity::class.java))
				}
				.show()
			return
		}
		lifecycleScope.launch {
			viewModel.runScan(contentResolver, filter)
		}
	}

	private fun folderFilter(): FolderFilter? {
		val whitelist = folderPrefs.getWhitelist()
		if (whitelist.isEmpty()) return null
		return FolderFilter(whitelist, folderPrefs.getBlacklist())
	}

	private fun renderState(state: ScanViewModel.State) {
		when (state) {
			ScanViewModel.State.Idle -> {
				adapter.clear()
				binding.statusText.text = getString(R.string.scan_idle)
				binding.progressBar.visibility = View.GONE
				binding.scanFab.text = getString(R.string.action_scan)
				binding.scanFab.setIconResource(R.drawable.ic_search_rounded)
				binding.emptyText.visibility = View.VISIBLE
				binding.emptyText.text = getString(R.string.no_duplicates)
			}
			ScanViewModel.State.Hashing -> {
				binding.statusText.text = getString(R.string.scan_hashing)
				binding.progressBar.visibility = View.VISIBLE
				binding.progressBar.isIndeterminate = true
				binding.scanFab.text = getString(R.string.action_stop)
				binding.scanFab.setIconResource(R.drawable.ic_close_rounded)
			}
			is ScanViewModel.State.Scanning -> {
				binding.statusText.text = getString(
					R.string.scan_scanning,
					state.scanned,
					state.total
				)
				binding.progressBar.visibility = View.VISIBLE
				binding.progressBar.isIndeterminate = false
				val percent = if (state.total > 0) (state.scanned * 100 / state.total) else 0
				binding.progressBar.progress = percent
				binding.scanFab.text = getString(R.string.action_stop)
				binding.scanFab.setIconResource(R.drawable.ic_close_rounded)
			}
			is ScanViewModel.State.Done -> {
				binding.progressBar.visibility = View.GONE
				binding.scanFab.text = getString(R.string.action_scan)
				binding.scanFab.setIconResource(R.drawable.ic_search_rounded)
				if (state.groups.isEmpty()) {
					binding.statusText.text = getString(R.string.scan_no_duplicates)
					binding.emptyText.visibility = View.VISIBLE
					binding.emptyText.text = getString(R.string.scan_no_duplicates)
				} else {
					binding.statusText.text = getString(R.string.scan_done, state.groups.size)
					binding.emptyText.visibility = View.GONE
				}
				adapter.submitList(state.groups)
			}
			ScanViewModel.State.NoImages -> {
				binding.progressBar.visibility = View.GONE
				binding.scanFab.text = getString(R.string.action_scan)
				binding.statusText.text = getString(R.string.scan_no_images)
				binding.emptyText.visibility = View.VISIBLE
				binding.emptyText.text = getString(R.string.scan_no_images)
				adapter.clear()
			}
			ScanViewModel.State.Stopped -> {
				binding.progressBar.visibility = View.GONE
				binding.scanFab.text = getString(R.string.action_scan)
				binding.statusText.text = getString(R.string.scan_stopped)
			}
			is ScanViewModel.State.Error -> {
				binding.progressBar.visibility = View.GONE
				binding.scanFab.text = getString(R.string.action_scan)
				binding.statusText.text = state.message
			}
		}
	}

	private fun openImageDetail(item: ImageItem) {
		val intent = Intent(this, ImageDetailActivity::class.java).apply {
			putExtra(ImageDetailActivity.EXTRA_URI, item.uri.toString())
			putExtra(ImageDetailActivity.EXTRA_NAME, item.displayName)
			putExtra(ImageDetailActivity.EXTRA_PATH, item.path)
			putExtra(ImageDetailActivity.EXTRA_WIDTH, item.width)
			putExtra(ImageDetailActivity.EXTRA_HEIGHT, item.height)
			putExtra(ImageDetailActivity.EXTRA_SIZE, item.sizeBytes)
			putExtra(ImageDetailActivity.EXTRA_DATE_ADDED, item.dateAdded)
			putExtra(ImageDetailActivity.EXTRA_DATE_MODIFIED, item.dateModified)
		}
		detailResultLauncher.launch(intent)
	}

	private fun openFullImage(item: ImageItem) {
		val intent = Intent(this, FullImageActivity::class.java).apply {
			putExtra(ImageDetailActivity.EXTRA_URI, item.uri.toString())
			putExtra(ImageDetailActivity.EXTRA_NAME, item.displayName)
			putExtra(ImageDetailActivity.EXTRA_WIDTH, item.width)
			putExtra(ImageDetailActivity.EXTRA_HEIGHT, item.height)
		}
		startActivity(intent)
		overridePendingTransition(R.anim.fade_scale_in, R.anim.hold)
	}

	private fun confirmDelete(
		group: DuplicateGroup,
		item: ImageItem
	) {
		Dialogs.showDeleteConfirmation(this, item.displayName, item.uri)
	}

	private fun performConfirmedDelete(uri: Uri) {
		try {
			pendingDeleteUri = uri
			val sender = MediaStoreDelete.createRequestSender(contentResolver, listOf(uri))
			deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
		} catch (e: Exception) {
			pendingDeleteUri = null
			Log.w(TAG, "createDeleteRequest failed for $uri", e)
			Snackbar.make(binding.root, getString(R.string.delete_failed), Snackbar.LENGTH_LONG).show()
		}
	}

	companion object {
		private const val TAG = "MainActivity"
		private const val STATE_PENDING_DELETE_URI = "state_pending_delete_uri"
		private const val EXIT_CONFIRMATION_TIMEOUT_MS = 2000L
	}
}
