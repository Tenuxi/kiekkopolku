package fi.kiekkopolku.app.domain

import kotlinx.coroutines.flow.Flow

interface HistoryRepository { val history: Flow<History> }
interface PlayerRepository {
    suspend fun addPlayer(metrixId: String, name: String, integrationCode: String = "", profileId: String? = null)
    suspend fun selectPlayer(id: String, selected: Boolean)
    suspend fun deletePlayer(id: String)
}
interface SyncRepository {
    suspend fun loadSample()
    suspend fun removeSample()
    val progress: Flow<SyncProgress?> get() = kotlinx.coroutines.flow.flowOf(null)
    suspend fun refreshSelected(): RefreshResult
    suspend fun refreshOnOpen(): RefreshResult = RefreshResult.NOTHING_SELECTED
}
enum class RefreshResult { UPDATED_SAMPLE, METRIX_NOT_CONNECTED, NOTHING_SELECTED, UPDATED_METRIX, PARTIAL, FAILED }
class DuplicatePlayerException : IllegalArgumentException()
class InvalidIntegrationCodeException : Exception()
class MetrixConnectionException : Exception()

data class SyncProgress(val playerName: String, val processed: Int, val total: Int, val phase: String = "RESULTS")
