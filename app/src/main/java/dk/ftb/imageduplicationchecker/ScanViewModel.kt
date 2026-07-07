package dk.ftb.imageduplicationchecker

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dk.ftb.imageduplicationchecker.core.DuplicateScan
import dk.ftb.imageduplicationchecker.data.DuplicateGroup
import dk.ftb.imageduplicationchecker.data.FolderFilter
import dk.ftb.imageduplicationchecker.data.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanViewModel(app: Application) : AndroidViewModel(app) {

	sealed class State {
		object Idle : State()
		object Hashing : State()
		data class Scanning(val scanned: Int, val total: Int) : State()
		data class Done(val groups: List<DuplicateGroup>) : State()
		object NoImages : State()
		object Stopped : State()
		data class Error(val message: String) : State()
	}

	private val _state = MutableLiveData<State>(State.Idle)
	val state: LiveData<State> = _state

	private var scanJob: Job? = null
	private var scanner: DuplicateScan? = null
	private var currentGroups: List<DuplicateGroup> = emptyList()

	fun runScan(resolver: ContentResolver, filter: FolderFilter) {
		scanJob?.cancel()
		val scan = DuplicateScan(resolver, filter)
		scanner = scan
		scanJob = viewModelScope.launch {
			_state.value = State.Hashing
			try {
				val groups = withContext(Dispatchers.IO) {
					scan.scan { scanned, total ->
						_state.postValue(State.Scanning(scanned, total))
					}
				}
				when {
					scan.isCancelled() -> _state.value = State.Stopped
					groups.isEmpty() && currentGroups.isEmpty() -> {
						currentGroups = emptyList()
						_state.value = State.NoImages
					}
					groups.isEmpty() -> {
						currentGroups = emptyList()
						_state.value = State.Done(emptyList())
					}
					else -> {
						currentGroups = groups
						_state.value = State.Done(groups)
					}
				}
			} catch (e: Exception) {
				_state.value = State.Error(e.message ?: "Scan failed")
			}
		}
	}

	fun stopScan() {
		scanner?.cancel()
		scanJob?.cancel()
		_state.value = State.Stopped
	}

	fun clearResults() {
		scanJob?.cancel()
		scanner?.cancel()
		currentGroups = emptyList()
		_state.value = State.Idle
	}

	fun performDelete(uri: Uri) {
		currentGroups = currentGroups.mapNotNull { group ->
			val remaining = group.images.filter { it.uri != uri }
			if (remaining.size >= 2) {
				val newSim = scanner?.computeGroupSimilarity(remaining) ?: group.similarityPercent
				group.copy(images = remaining, similarityPercent = newSim)
			} else null
		}
		_state.value = State.Done(currentGroups)
	}

	/**
	 * Called when the user returns to the activity. If results are stale relative to the
	 * current folder filter, the next scan will refresh them; for now we keep the existing
	 * results visible so the user doesn't lose their work on resume.
	 */
	fun refreshIfStale(filter: FolderFilter) {
		// No-op: results are kept on resume; the user can re-scan to pick up filter changes.
	}
}
