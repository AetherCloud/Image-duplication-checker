package dk.ftb.imageduplicationchecker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.provider.MediaStore
import com.google.android.material.snackbar.Snackbar
import dk.ftb.imageduplicationchecker.databinding.ActivityImageDetailBinding
import dk.ftb.imageduplicationchecker.util.BitmapDecoder
import dk.ftb.imageduplicationchecker.util.DeleteConfirmationDialogFragment
import dk.ftb.imageduplicationchecker.util.Dialogs
import dk.ftb.imageduplicationchecker.util.MediaStoreDelete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class ImageDetailActivity : AppCompatActivity() {

	private lateinit var binding: ActivityImageDetailBinding

	private var imageUri: Uri? = null
	private var displayName: String = ""
	private var path: String = ""
	private var width: Int = 0
	private var height: Int = 0
	private var sizeBytes: Long = 0L
	private var dateAdded: Long = 0L
	private var dateModified: Long = 0L

	private val deleteLauncher = registerForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult()
	) { result ->
		val uri = imageUri ?: return@registerForActivityResult
		if (result.resultCode != RESULT_OK) return@registerForActivityResult
		setResult(RESULT_OK, Intent().putExtra(EXTRA_DELETED_URI, uri.toString()))
		finish()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityImageDetailBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setSupportActionBar(binding.toolbar)
		binding.toolbar.setNavigationOnClickListener { finish() }

		imageUri = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
		displayName = intent.getStringExtra(EXTRA_NAME) ?: ""
		path = intent.getStringExtra(EXTRA_PATH) ?: ""
		width = intent.getIntExtra(EXTRA_WIDTH, 0)
		height = intent.getIntExtra(EXTRA_HEIGHT, 0)
		sizeBytes = intent.getLongExtra(EXTRA_SIZE, 0L)
		dateAdded = intent.getLongExtra(EXTRA_DATE_ADDED, 0L)
		dateModified = intent.getLongExtra(EXTRA_DATE_MODIFIED, 0L)

		populateDetails()
		loadPreview()

		supportFragmentManager.setFragmentResultListener(
			DeleteConfirmationDialogFragment.REQUEST_KEY, this
		) { _, bundle ->
			val uri = bundle.getString(DeleteConfirmationDialogFragment.RESULT_URI)
				?.let(Uri::parse) ?: return@setFragmentResultListener
			performConfirmedDelete(uri)
		}

		binding.openButton.setOnClickListener { openExternal() }
		binding.deleteButton.setOnClickListener { confirmDelete() }
		binding.imagePreview.setOnClickListener { openFullImage() }
	}

	private fun populateDetails() {
		binding.detailName.text = displayName.ifEmpty { getString(R.string.unknown) }
		binding.detailPath.text = path.ifEmpty { imageUri?.toString() ?: getString(R.string.unknown) }
		binding.detailResolution.text = if (width > 0 && height > 0) {
			getString(R.string.resolution_format, width, height)
		} else {
			getString(R.string.unknown)
		}
		binding.detailSize.text = Formatter.formatFileSize(this, sizeBytes)
		binding.detailDateAdded.text = formatDate(dateAdded)
		binding.detailDateModified.text = formatDate(dateModified)
		binding.toolbar.title = displayName.ifEmpty { getString(R.string.title_image_detail) }
	}

	private fun formatDate(seconds: Long): String {
		if (seconds <= 0L) return getString(R.string.unknown)
		return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
			.format(Date(seconds * 1000L))
	}

	private fun loadPreview() {
		val uri = imageUri ?: return
		lifecycleScope.launch {
			val bmp = withContext(Dispatchers.IO) {
				BitmapDecoder.decodeSampled(
					contentResolver, uri, targetPx = PREVIEW_TARGET_PX,
					knownWidth = width, knownHeight = height
				)
			}
			if (bmp != null) {
				binding.imagePreview.setImageBitmap(bmp)
			} else {
				binding.imagePreview.visibility = View.GONE
			}
		}
	}

	private fun openExternal() {
		val uri = imageUri ?: run {
			Snackbar.make(binding.root, getString(R.string.image_not_found), Snackbar.LENGTH_LONG).show()
			return
		}
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "image/*")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		try {
			startActivity(Intent.createChooser(intent, getString(R.string.action_open)))
		} catch (_: Exception) {
			Snackbar.make(binding.root, getString(R.string.open_failed), Snackbar.LENGTH_LONG).show()
		}
	}

	private fun openFullImage() {
		val intent = Intent(this, FullImageActivity::class.java).apply {
			putExtra(EXTRA_URI, imageUri.toString())
			putExtra(EXTRA_NAME, displayName)
			putExtra(EXTRA_WIDTH, width)
			putExtra(EXTRA_HEIGHT, height)
		}
		startActivity(intent)
		overridePendingTransition(R.anim.fade_scale_in, R.anim.hold)
	}

	private fun confirmDelete() {
		val uri = imageUri ?: return
		Dialogs.showDeleteConfirmation(this, displayName, uri)
	}

	private fun performConfirmedDelete(uri: Uri) {
		try {
			val sender = MediaStoreDelete.createRequestSender(contentResolver, listOf(uri))
			deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
		} catch (e: Exception) {
			Log.w(TAG, "createDeleteRequest failed for $uri", e)
			Snackbar.make(binding.root, getString(R.string.delete_failed), Snackbar.LENGTH_LONG).show()
		}
	}

	companion object {
		private const val TAG = "ImageDetailActivity"
		private const val PREVIEW_TARGET_PX = 1024
		const val EXTRA_URI = "extra_uri"
		const val EXTRA_NAME = "extra_name"
		const val EXTRA_PATH = "extra_path"
		const val EXTRA_WIDTH = "extra_width"
		const val EXTRA_HEIGHT = "extra_height"
		const val EXTRA_SIZE = "extra_size"
		const val EXTRA_DATE_ADDED = "extra_date_added"
		const val EXTRA_DATE_MODIFIED = "extra_date_modified"
		const val EXTRA_DELETED_URI = "extra_deleted_uri"
	}
}
