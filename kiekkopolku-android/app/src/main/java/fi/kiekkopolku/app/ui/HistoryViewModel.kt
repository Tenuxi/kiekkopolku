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
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: DuplicatePlayerException) { message.value = R.string.duplicate_player }
            catch (_: IllegalArgumentException) { message.value = R.string.invalid_player }
            catch (_: Exception) { message.value = R.string.operation_failed }
            finally { busy.value = false }
        }
    }
    fun retry() { reload.value++ }
    fun select(id: String, selected: Boolean) = action { players.selectPlayer(id, selected) }
    fun add(id: String, name: String) = action { players.addPlayer(id, name); message.value = R.string.player_added }
    fun delete(id: String) = action { players.deletePlayer(id) }
    fun sample() = action { sync.loadSample(); message.value = R.string.sample_loaded }
    fun removeSample() = action { sync.removeSample() }
    fun refresh() = action {
        message.value = when (sync.refreshSelected()) {
            RefreshResult.UPDATED_SAMPLE -> R.string.sample_updated
            RefreshResult.METRIX_NOT_CONNECTED -> R.string.metrix_not_connected
            RefreshResult.NOTHING_SELECTED -> R.string.select_players
        }
    }
}
