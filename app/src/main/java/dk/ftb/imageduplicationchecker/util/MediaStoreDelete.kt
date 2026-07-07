package dk.ftb.imageduplicationchecker.util

import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore

/** Wraps MediaStore.createDeleteRequest — on Android 11+ the system-mediated
 *  user-confirmation dialog is mandatory for any image the calling app does not own. */
object MediaStoreDelete {
    fun createRequestSender(resolver: ContentResolver, uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender
}
