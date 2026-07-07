package dk.ftb.imageduplicationchecker

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dk.ftb.imageduplicationchecker.databinding.ActivityFullImageBinding
import dk.ftb.imageduplicationchecker.util.BitmapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FullImageActivity : AppCompatActivity() {

	private lateinit var binding: ActivityFullImageBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityFullImageBinding.inflate(layoutInflater)
		setContentView(binding.root)

		val uriString = intent.getStringExtra(ImageDetailActivity.EXTRA_URI) ?: run {
			finish(); return
		}
		val uri = Uri.parse(uriString)
		val width = intent.getIntExtra(ImageDetailActivity.EXTRA_WIDTH, 0)
		val height = intent.getIntExtra(ImageDetailActivity.EXTRA_HEIGHT, 0)

		loadFullImage(uri, width, height)
	}

	override fun finish() {
		super.finish()
		overridePendingTransition(R.anim.hold, R.anim.fade_scale_out)
	}

	private fun loadFullImage(uri: Uri, knownWidth: Int, knownHeight: Int) {
		lifecycleScope.launch {
			val bmp = withContext(Dispatchers.IO) {
				try {
					BitmapDecoder.decodeSampled(
						contentResolver, uri, targetPx = TARGET_PX,
						knownWidth = knownWidth, knownHeight = knownHeight
					)
				} catch (_: Exception) {
					null
				}
			}
			binding.fullImageProgress.visibility = View.GONE
			if (bmp != null) {
				binding.fullImage.setImageBitmap(bmp)
			}
		}
	}

	companion object {
		private const val TARGET_PX = 4096
	}
}
