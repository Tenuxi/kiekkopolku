package fi.kiekkopolku.app.domain

import kotlinx.coroutines.flow.Flow

interface HistoryRepository { val history: Flow<History> }
interface PlayerRepository {
    suspend fun addPlayer(metrixId: String, name: String, integrationCode: String = "")
    suspend fun selectPlayer(id: String, selected: Boolean)
    suspend fun deletePlayer(id: String)
}
interface SyncRepository {
    suspend fun loadSample()
    suspend fun removeSample()
    suspend fun refreshSelected(): RefreshResult
}
enum class RefreshResult { UPDATED_SAMPLE, METRIX_NOT_CONNECTED, NOTHING_SELECTED }
class DuplicatePlayerException : IllegalArgumentException()
class InvalidIntegrationCodeException : Exception()
class MetrixConnectionException : Exception()
