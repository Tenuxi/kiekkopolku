package fi.kiekkopolku.app.data

import androidx.room.withTransaction
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Room is the only UI source; all writes (including sample import) are atomic. */
class LocalHistoryRepository(private val db: KiekkopolkuDatabase,
    private val source: DiscGolfMetrixDataSource = SampleDataSource(),
    private val now: () -> Long = System::currentTimeMillis) : HistoryRepository, PlayerRepository, SyncRepository {
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
            Player(it.id, it.metrixPlayerId, it.displayName, it.colorKey, it.isActive, it.isSample, sync[it.id]?.lastSyncAt)
        }, dao.courses().map { Course(it.id, it.name, it.city, it.countryCode, it.latitude, it.longitude) },
            dao.entries().map { e ->
                val r = rounds.getValue(e.roundId)
                RoundEntry(r.id, r.externalRoundId, r.source, e.playerId, e.courseId, r.playedDate,
                    e.layoutName, e.tee, e.totalScore, e.relativeToPar, e.status, e.scoringMode == "INDIVIDUAL",
                    e.holeDataComplete, holes[e.roundId to e.playerId].orEmpty().map { Hole(it.ordinal, it.label, it.par, it.score) })
            })
    }

    override suspend fun addPlayer(metrixId: String, name: String) = writes.withLock {
        val id = metrixId.trim().trimStart('0').ifEmpty { "0" }
        require(id.matches(Regex("[0-9]{1,20}")) && id != "0" && name.trim().length in 1..60)
        db.withTransaction {
            if (dao.players().any { it.metrixPlayerId == id }) throw DuplicatePlayerException()
            dao.insertPlayer(PlayerEntity(UUID.randomUUID().toString(), id, name.trim(), dao.players().size % 5))
        }
    }
    override suspend fun selectPlayer(id: String, selected: Boolean) = writes.withLock { dao.selectPlayer(id, selected) }
    override suspend fun deletePlayer(id: String) = writes.withLock {
        db.withTransaction { dao.deletePlayer(id); dao.deleteOrphanRounds(); dao.deleteOrphanSampleCourses() }
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
