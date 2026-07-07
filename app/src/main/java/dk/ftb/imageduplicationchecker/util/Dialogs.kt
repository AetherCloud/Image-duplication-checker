package dk.ftb.imageduplicationchecker.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import dk.ftb.imageduplicationchecker.R

/** Shared dialog builders so the delete-confirmation UI is consistent across screens. */
object Dialogs {

	fun showDeleteConfirmation(
		context: Context,
		displayName: CharSequence,
		onConfirm: () -> Unit
	) {
		AlertDialog.Builder(context)
			.setTitle(context.getString(R.string.delete_confirm_title))
			.setMessage(context.getString(R.string.delete_confirm_message, displayName))
			.setPositiveButton(context.getString(R.string.delete_ok)) { _, _ -> onConfirm() }
			.setNegativeButton(context.getString(R.string.delete_cancel), null)
			.show()
	}
}
