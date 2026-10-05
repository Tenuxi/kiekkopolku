package fi.kiekkopolku.app.data

import androidx.room.withTransaction
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/** Room is the only UI source; all writes (including sample import) are atomic. */
class LocalHistoryRepository(private val db: KiekkopolkuDatabase,
    private val source: DiscGolfMetrixDataSource = SampleDataSource(),
    private val now: () -> Long = System::currentTimeMillis,
    private val credentials: CredentialStore = NoCredentialStore,
    private val verifier: IntegrationCodeVerifier = IntegrationCodeVerifier { throw MetrixConnectionException() },
) : HistoryRepository, PlayerRepository, SyncRepository {
    private val dao get() = db.history()
    private val writes = Mutex()
    override val history: Flow<History> = db.invalidationTracker.createFlow(
        "players", "courses", "rounds", "round_players", "hole_scores", "sync_states"
    ).map { snapshot() }

    suspend fun snapshot(): History = db.withTransaction {
        val sync = dao.syncStates().associateBy { it.playerId }
        val rounds = dao.rounds().associateBy { it.id }
        val holes = dao.holes().groupBy { it.roundId to it.playerId }
        History(dao.players().map {
            Player(it.id, it.externalPlayerId, it.displayName, it.colorKey, it.isActive, it.isSample, sync[it.id]?.lastSyncAt, it.hasIntegrationCode)
        }, dao.courses().map { Course(it.id, it.name, it.city, it.countryCode, it.latitude, it.longitude) },
            dao.entries().map { e ->
                val r = rounds.getValue(e.roundId)
                RoundEntry(r.id, r.externalRoundId, r.source, e.playerId, e.courseId, r.playedDate,
                    e.layoutName, e.tee, e.totalScore, e.relativeToPar, e.status, e.scoringMode == "INDIVIDUAL",
                    e.holeDataComplete, holes[e.roundId to e.playerId].orEmpty().map { Hole(it.ordinal, it.label, it.par, it.score) })
            })
    }

    override suspend fun addPlayer(metrixId: String, name: String, integrationCode: String) = writes.withLock {
        val rawId = metrixId.trim()
        val id = rawId.takeIf { it.isNotEmpty() }?.trimStart('0')
        val code = integrationCode.trim()
        require((id != null || code.isNotEmpty()) && name.trim().length in 1..60)
        require(id == null || (rawId.matches(Regex("[0-9]{1,20}")) && id.isNotEmpty()))
        require(code.length <= 512 && code.none { it.isISOControl() })
        val profiles = dao.players()
        val byId = profiles.find { id != null && it.externalPlayerId == id }
        var byCode: PlayerEntity? = null
        if (code.isNotEmpty()) {
            verifier.verify(code) // No database or secret writes until the provider accepts the code.
            byCode = profiles.filter { it.hasIntegrationCode }.find { credentials.get(it.id) == code }
        }
        if (byId != null && code.isEmpty()) throw DuplicatePlayerException()
        if (byId != null && byCode != null && byId.id != byCode.id) throw DuplicatePlayerException()
        if (byCode?.externalPlayerId != null && id != null && byCode.externalPlayerId != id) throw DuplicatePlayerException()
        val existing = byId ?: byCode
        val localId = existing?.id ?: UUID.randomUUID().toString()
        val oldCode = if (code.isNotEmpty()) credentials.get(localId) else null
        if (code.isNotEmpty()) credentials.put(localId, code)
        try {
            db.withTransaction {
                if (existing == null) dao.insertPlayer(PlayerEntity(localId, id ?: "code:$localId", name.trim(), profiles.size % 5,
                    externalPlayerId = id, hasIntegrationCode = code.isNotEmpty()))
                else dao.updatePlayer(existing.copy(identityKey = id ?: existing.identityKey,
                    externalPlayerId = id ?: existing.externalPlayerId, hasIntegrationCode = true))
            }
        } catch (e: Exception) {
            if (code.isNotEmpty()) withContext(NonCancellable) {
                if (oldCode == null) credentials.remove(localId) else credentials.put(localId, oldCode)
            }
            throw e
        }
    }
    override suspend fun selectPlayer(id: String, selected: Boolean) = writes.withLock { dao.selectPlayer(id, selected) }
    override suspend fun deletePlayer(id: String) = writes.withLock {
        db.withTransaction { dao.deletePlayer(id); dao.deleteOrphanRounds(); dao.deleteOrphanSampleCourses() }
        credentials.remove(id)
    }
    override suspend fun loadSample() = writes.withLock {
        val sample = SampleDataSource()
        // Fetch before opening a Room transaction. Sample adapter is replaceable in tests.
        val ids = sample.players.map { it.id }.toSet()
        val batch = source.fetchHistory(ids)
        db.withTransaction {
            val existing = dao.players().map { it.id }.toSet()
            sample.players.filter { it.id !in existing }.forEach { dao.insertPlayer(it) }
            persist(batch)
            val timestamp = now()
            ids.forEach { dao.putSync(SyncStateEntity(it, "sample", timestamp, timestamp, "SUCCESS", "SAMPLE")) }
        }
    }
    override suspend fun removeSample() = writes.withLock {
        db.withTransaction { dao.deleteSamplePlayers(); dao.deleteOrphanRounds(); dao.deleteOrphanSampleCourses() }
    }
    override suspend fun refreshSelected(): RefreshResult = writes.withLock {
        val selected = dao.players().filter { it.isActive }
        if (selected.isEmpty()) return@withLock RefreshResult.NOTHING_SELECTED
        val ids = selected.filter { it.isSample }.map { it.id }.toSet()
        if (ids.isNotEmpty()) {
            val batch = source.fetchHistory(ids)
            db.withTransaction {
                persist(batch)
                val timestamp = now()
                ids.forEach { dao.putSync(SyncStateEntity(it, "sample", timestamp, timestamp, "SUCCESS", "SAMPLE")) }
            }
        }
        if (selected.any { !it.isSample }) RefreshResult.METRIX_NOT_CONNECTED else RefreshResult.UPDATED_SAMPLE
    }
    private suspend fun persist(batch: ImportBatch) {
        // The local adapter owns stable IDs; phase 3 will map provider IDs before reaching this boundary.
        dao.putCourses(batch.courses); dao.putRefs(batch.refs); dao.putRounds(batch.rounds)
        dao.putEntries(batch.entries)
        batch.entries.filter { it.holeDataComplete }.forEach { dao.deleteHoles(it.roundId, it.playerId) }
        dao.putHoles(batch.holes)
    }
}
