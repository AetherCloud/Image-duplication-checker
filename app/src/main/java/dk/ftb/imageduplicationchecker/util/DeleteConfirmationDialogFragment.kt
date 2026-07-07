package dk.ftb.imageduplicationchecker.util

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import dk.ftb.imageduplicationchecker.R

/**
 * Delete-confirmation dialog hosted by FragmentManager so it survives configuration changes
 * (e.g. device rotation) and process death. Results are delivered via the FragmentResult API
 * using [REQUEST_KEY]; the URI to delete is carried under [RESULT_URI].
 */
class DeleteConfirmationDialogFragment : DialogFragment() {

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val args = requireArguments()
		val displayName = args.getString(ARG_DISPLAY_NAME).orEmpty()
		val uriString = args.getString(ARG_URI).orEmpty()
		val context = requireContext()
		return AlertDialog.Builder(context)
			.setTitle(context.getString(R.string.delete_confirm_title))
			.setMessage(context.getString(R.string.delete_confirm_message, displayName))
			.setPositiveButton(context.getString(R.string.delete_ok)) { _, _ ->
				setFragmentResult(
					REQUEST_KEY,
					bundleOf(RESULT_URI to uriString)
				)
			}
			.setNegativeButton(context.getString(R.string.delete_cancel), null)
			.create()
	}

	companion object {
		const val REQUEST_KEY = "DeleteConfirmationDialog.result"
		const val RESULT_URI = "uri"
		private const val ARG_DISPLAY_NAME = "display_name"
		private const val ARG_URI = "uri"

		fun newInstance(displayName: String, uri: Uri): DeleteConfirmationDialogFragment {
			return DeleteConfirmationDialogFragment().apply {
				arguments = bundleOf(
					ARG_DISPLAY_NAME to displayName,
					ARG_URI to uri.toString()
				)
			}
		}
	}
}
