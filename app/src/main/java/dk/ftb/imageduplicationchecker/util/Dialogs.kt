package dk.ftb.imageduplicationchecker.util

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity

/** Shared dialog builders so the delete-confirmation UI is consistent across screens. */
object Dialogs {

	private const val TAG = "DeleteConfirmationDialog"

	fun showDeleteConfirmation(
		activity: AppCompatActivity,
		displayName: CharSequence,
		uri: Uri
	) {
		if (activity.isFinishing || activity.isDestroyed) return
		if (activity.supportFragmentManager.findFragmentByTag(TAG) != null) return
		DeleteConfirmationDialogFragment
			.newInstance(displayName.toString(), uri)
			.show(activity.supportFragmentManager, TAG)
	}
}
