package fi.kiekkopolku.app.data

import androidx.room.withTransaction
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val metrix: MetrixApi = MetrixHttpApi(),
    private val requestDelayMillis: Long = 250,
) : HistoryRepository, PlayerRepository, SyncRepository {
    private val dao get() = db.history()
    private val writes = Mutex()
    override val progress = MutableStateFlow<SyncProgress?>(null)
    override val history: Flow<History> = db.invalidationTracker.createFlow(
        "players", "courses", "rounds", "round_players", "hole_scores", "sync_states"
    ).map { snapshot() }

    suspend fun snapshot(): History = db.withTransaction {
        val sync = dao.syncStates().associateBy { it.playerId }
        val rounds = dao.rounds().associateBy { it.id }
        val holes = dao.holes().groupBy { it.roundId to it.playerId }
        History(dao.players().map {
            Player(it.id, it.externalPlayerId, it.displayName, it.colorKey, it.isActive, it.isSample, sync[it.id]?.lastSyncAt, it.hasIntegrationCode, sync[it.id]?.status, sync[it.id]?.errorCode)
        }, dao.courses().map { Course(it.id, it.name, it.city, it.countryCode, it.latitude, it.longitude) },
            dao.entries().map { e ->
                val r = rounds.getValue(e.roundId)
                RoundEntry(r.id, r.externalRoundId, r.source, e.playerId, e.courseId, r.playedDate,
                    e.layoutName, e.tee, e.totalScore, e.relativeToPar, e.status, e.scoringMode == "INDIVIDUAL",
                    e.holeDataComplete, holes[e.roundId to e.playerId].orEmpty().map { Hole(it.ordinal, it.label, it.par, it.score) })
            })
    }

    override suspend fun addPlayer(metrixId: String, name: String, integrationCode: String, profileId: String?) = writes.withLock {
        val rawId = metrixId.trim()
        val id = rawId.takeIf { it.isNotEmpty() }?.trimStart('0')
        val code = integrationCode.trim()
        require((id != null || code.isNotEmpty()) && name.trim().length in 1..60)
        require(id == null || (rawId.matches(Regex("[0-9]{1,20}")) && id.isNotEmpty()))
        require(code.length <= 512 && code.none { it.isISOControl() })
        val profiles = dao.players()
        val editing = profileId?.let { local -> profiles.singleOrNull { it.id == local && !it.isSample } ?: throw IllegalArgumentException() }
        if (editing?.externalPlayerId != null && editing.externalPlayerId != id) throw DuplicatePlayerException()
        val byId = profiles.find { id != null && it.externalPlayerId == id }
        var byCode: PlayerEntity? = null
        if (code.isNotEmpty()) {
            verifier.verify(code) // No database or secret writes until the provider accepts the code.
            byCode = profiles.filter { it.hasIntegrationCode }.find { credentials.get(it.id) == code }
        }
        if (byId != null && code.isEmpty() && editing?.id != byId.id) throw DuplicatePlayerException()
        if (byId != null && byCode != null && byId.id != byCode.id) throw DuplicatePlayerException()
        if (byCode?.externalPlayerId != null && id != null && byCode.externalPlayerId != id) throw DuplicatePlayerException()
        if (editing != null && listOfNotNull(byId, byCode).any { it.id != editing.id }) throw DuplicatePlayerException()
        val existing = editing ?: byId ?: byCode
        val localId = existing?.id ?: UUID.randomUUID().toString()
        val oldCode = if (code.isNotEmpty()) credentials.get(localId) else null
        if (code.isNotEmpty()) credentials.put(localId, code)
        try {
            db.withTransaction {
                if (existing == null) dao.insertPlayer(PlayerEntity(localId, id ?: "code:$localId", name.trim(), profiles.size % 5,
                    externalPlayerId = id, hasIntegrationCode = code.isNotEmpty()))
                else dao.updatePlayer(existing.copy(identityKey = id ?: existing.identityKey,
                    externalPlayerId = id ?: existing.externalPlayerId, hasIntegrationCode = existing.hasIntegrationCode || code.isNotEmpty()))
            }
        } catch (e: Exception) {
            if (code.isNotEmpty()) withContext(NonCancellable) {
                if (oldCode == null) credentials.remove(localId) else credentials.put(localId, oldCode)
            }
            throw e
        }
    }
    override suspend fun selectPlayer(id: String, selected: Boolean) = dao.selectPlayer(id, selected)
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
    override suspend fun refreshSelected(): RefreshResult = refresh(automatic = false)
    override suspend fun refreshOnOpen(): RefreshResult = refresh(automatic = true)

    private suspend fun refresh(automatic: Boolean): RefreshResult = writes.withLock {
        val selected = dao.players().filter { if (automatic) !it.isSample else it.isActive }
        if (selected.isEmpty()) return@withLock RefreshResult.NOTHING_SELECTED
        var succeeded = 0
        var failed = 0
        var partial = false
        try {
            for (player in selected) {
                if (player.isSample) {
                    val batch = source.fetchHistory(setOf(player.id))
                    db.withTransaction {
                        persist(batch)
                        dao.putSync(SyncStateEntity(player.id, "sample", now(), now(), "SUCCESS", "SAMPLE"))
                    }
                    continue
                }
                val previous = dao.syncStates().find { it.playerId == player.id && it.source == "metrix" }
                val attempted = now()
                var reason: String? = null
                var imported = 0
                var processed = 0
                val code = try { credentials.get(player.id) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (player.externalPlayerId == null) reason = "NEEDS_ID"
                else if (code.isNullOrBlank()) reason = "NEEDS_CODE"
                if (reason == null) {
                    progress.value = SyncProgress(player.displayName, 0, 0)
                    try {
                        val ids = competitionIds(metrix.get("my_competitions", code!!))
                        val pending = ArrayDeque(ids)
                        val seen = mutableSetOf<String>()
                        val embedded = mutableMapOf<String, kotlinx.serialization.json.JsonObject>()
                        val knownRounds = dao.rounds().filter { it.source == "metrix" }.associateBy { it.externalRoundId }
                        val knownEntries = dao.entries().filter { it.playerId == player.id }.associateBy { it.roundId }
                        var matched = false
                        while (pending.isNotEmpty()) {
                            val id = pending.removeFirst()
                            if (!seen.add(id)) continue
                            progress.value = SyncProgress(player.displayName, processed, seen.size + pending.count { it !in seen })
                            val cached = knownRounds[id]?.takeIf { it.source == "metrix" }
                            val entry = cached?.let { knownEntries[it.id] }
                            // Auto refresh revisits recent/incomplete rounds; manual refresh always rechecks everything.
                            val old = cached?.playedDate?.let { runCatching { java.time.LocalDate.parse(it).isBefore(java.time.LocalDate.now().minusDays(30)) }.getOrDefault(false) } == true
                            if (automatic && old && entry?.holeDataComplete == true && entry.status == "FINISHED" &&
                                attempted - cached!!.fetchedAt in 0 until 86_400_000L) {
                                matched = true
                                processed++
                                continue
                            }
                            delay(requestDelayMillis)
                            try {
                                val mapped = parseMetrixResult(embedded.remove(id) ?: metrix.get("result", code, id), id, player, now())
                                embedded.putAll(mapped.embedded.filterKeys { it !in seen })
                                pending.addAll(mapped.children.filter { it !in seen && it !in pending })
                                if (mapped.batch.entries.isNotEmpty()) {
                                    db.withTransaction { persist(mapped.batch) }
                                    imported += mapped.batch.entries.size
                                    matched = true
                                }
                            } catch (e: MetrixImportException) {
                                reason = e.reason // Continue other rounds after a per-result permission/format failure.
                            }
                            processed++
                        }
                        if (ids.isNotEmpty() && !matched && reason == null) reason = "NO_RESULTS"
                    } catch (e: CancellationException) { throw e }
                    catch (_: InvalidIntegrationCodeException) { reason = "INVALID_CODE" }
                    catch (_: MetrixConnectionException) { reason = "CONNECTION" }
                }
                val success = reason == null
                if (success) succeeded++ else { failed++; if (imported > 0) partial = true }
                dao.putSync(SyncStateEntity(player.id, "metrix", attempted,
                    if (success) now() else previous?.lastSyncAt,
                    if (success) "SUCCESS" else if (imported > 0) "PARTIAL" else "ERROR",
                    if (success) "API_AVAILABLE" else "PARTIAL", reason))
            }
            when {
                failed > 0 && succeeded == 0 && !partial -> RefreshResult.FAILED
                failed > 0 -> RefreshResult.PARTIAL
                succeeded > 0 -> RefreshResult.UPDATED_METRIX
                else -> RefreshResult.UPDATED_SAMPLE
            }
        } finally { progress.value = null }
    }
    private suspend fun persist(batch: ImportBatch) {
        // Do not downgrade a complete cached scorecard to a partial provider response.
        val existing = dao.entries().associateBy { it.roundId to it.playerId }
        val protected = batch.entries.filter { !it.holeDataComplete && existing[it.roundId to it.playerId]?.holeDataComplete == true }
            .map { it.roundId to it.playerId }.toSet()
        val entries = batch.entries.filter { it.roundId to it.playerId !in protected }
        val holes = batch.holes.filter { it.roundId to it.playerId !in protected }
        dao.putCourses(batch.courses); dao.putRefs(batch.refs)
        dao.putRounds(batch.rounds.filter { r -> batch.entries.any { it.roundId == r.id && it.roundId to it.playerId !in protected } })
        dao.putEntries(entries)
        entries.filter { it.holeDataComplete }.forEach { dao.deleteHoles(it.roundId, it.playerId) }
        dao.putHoles(holes)
    }
}
