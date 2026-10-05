package fi.kiekkopolku.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fi.kiekkopolku.app.R
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(val history: History = History(), val loading: Boolean = true, val readFailed: Boolean = false)
@HiltViewModel
class HistoryViewModel @Inject constructor(history: HistoryRepository, private val players: PlayerRepository,
    private val sync: SyncRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = reload.flatMapLatest {
        history.history.map { HistoryState(it, loading = false) }
            .catch { emit(HistoryState(loading = false, readFailed = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<Int?>(null)
    val progress = sync.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private var lastOpened: Long? = null
    fun onOpen() {
        val time = android.os.SystemClock.elapsedRealtime()
        if (busy.value || lastOpened?.let { time - it < 60_000 } == true) return
        lastOpened = time
        action { report(sync.refreshOnOpen(), automatic = true) }
    }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: InvalidIntegrationCodeException) { message.value = R.string.invalid_code }
            catch (_: MetrixConnectionException) { message.value = R.string.metrix_connection_failed }
            catch (_: DuplicatePlayerException) { message.value = R.string.duplicate_player }
            catch (_: IllegalArgumentException) { message.value = R.string.invalid_player }
            catch (_: Exception) { message.value = R.string.operation_failed }
            finally { busy.value = false }
        }
    }
    fun retry() { reload.value++ }
    fun select(id: String, selected: Boolean) {
        viewModelScope.launch {
            try { players.selectPlayer(id, selected) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.operation_failed }
        }
    }
    fun add(id: String, name: String, code: String, profileId: String? = null, onSuccess: () -> Unit) = action {
        players.addPlayer(id, name, code, profileId)
        message.value = if (code.isBlank()) R.string.player_added else R.string.code_saved
        onSuccess()
        report(sync.refreshOnOpen(), automatic = true)
    }
    fun delete(id: String) = action { players.deletePlayer(id) }
    fun sample() = action { sync.loadSample(); message.value = R.string.sample_loaded }
    fun removeSample() = action { sync.removeSample() }
    fun refresh() = action { report(sync.refreshSelected()) }
    private fun report(result: RefreshResult, automatic: Boolean = false) {
        message.value = when (result) {
            RefreshResult.UPDATED_SAMPLE -> if (automatic) null else R.string.sample_updated
            RefreshResult.METRIX_NOT_CONNECTED -> R.string.metrix_not_connected
            RefreshResult.NOTHING_SELECTED -> if (automatic) null else R.string.select_players
            RefreshResult.UPDATED_METRIX -> if (automatic) null else R.string.metrix_updated
            RefreshResult.PARTIAL, RefreshResult.FAILED -> R.string.sync_partial
        }
    }
}
