package fi.kiekkopolku.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fi.kiekkopolku.app.data.*
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseTest {
    private lateinit var db: KiekkopolkuDatabase
    private lateinit var repo: LocalHistoryRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KiekkopolkuDatabase::class.java).build()
        repo = LocalHistoryRepository(db, now = { 42L })
    }
    @After fun close() { db.close() }
    private class TestCredentials : CredentialStore {
        val values = mutableMapOf<String, String>()
        override suspend fun get(playerId: String) = values[playerId]
        override suspend fun put(playerId: String, code: String) { values[playerId] = code }
        override suspend fun remove(playerId: String) { values.remove(playerId) }
    }
    @Test fun codeOnlyProfileAndIdAssociationDoNotInventOrDuplicateIdentity() = runBlocking {
        val secrets = TestCredentials()
        val repo = LocalHistoryRepository(db, credentials = secrets, verifier = IntegrationCodeVerifier { })
        repo.addPlayer("", "Oma", "synthetic-code")
        val player = repo.snapshot().players.single()
        assertNull(player.metrixId)
        assertTrue(player.hasIntegrationCode)
        assertNull(player.lastSyncAt)
        assertEquals("synthetic-code", secrets.get(player.id))
        repo.addPlayer("00424242", "Oma", "synthetic-code")
        assertEquals(1, repo.snapshot().players.size)
        assertEquals("424242", repo.snapshot().players.single().metrixId)
        assertEquals(player.id, repo.snapshot().players.single().id)
        repo.deletePlayer(player.id)
        assertTrue(secrets.values.isEmpty())
    }
    @Test fun attachingCodeToExistingIdKeepsProfileAndHistory() = runBlocking {
        val secrets = TestCredentials()
        val repo = LocalHistoryRepository(db, credentials = secrets, verifier = IntegrationCodeVerifier { })
        repo.addPlayer("424242", "Oma")
        val id = repo.snapshot().players.single().id
        repo.addPlayer("424242", "Uusi nimi", "synthetic-code")
        assertEquals(id, repo.snapshot().players.single().id)
        assertEquals("Oma", repo.snapshot().players.single().name)
        assertTrue(repo.snapshot().players.single().hasIntegrationCode)
    }
    @Test fun invalidCodeCreatesNeitherProfileNorSecret() = runBlocking {
        val secrets = TestCredentials()
        val repo = LocalHistoryRepository(db, credentials = secrets, verifier = IntegrationCodeVerifier { throw InvalidIntegrationCodeException() })
        try { repo.addPlayer("", "Oma", "invalid-synthetic-code"); fail("Expected verification failure") }
        catch (_: InvalidIntegrationCodeException) { }
        assertTrue(repo.snapshot().players.isEmpty())
        assertTrue(secrets.values.isEmpty())
    }
    @Test fun conflictingIdCannotReuseAnotherProfilesCode() = runBlocking {
        val secrets = TestCredentials()
        val repo = LocalHistoryRepository(db, credentials = secrets, verifier = IntegrationCodeVerifier { })
        repo.addPlayer("424242", "Oma", "synthetic-code")
        try { repo.addPlayer("424243", "Toinen", "synthetic-code"); fail("Expected conflict") }
        catch (_: DuplicatePlayerException) { }
        assertEquals(1, repo.snapshot().players.size)
    }
    @Test fun migrationPreservesV1PlayerRoundAndHole() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-v1-test.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("fi.kiekkopolku.app.data.KiekkopolkuDatabase/1.json")!!.bufferedReader().use { it.readText() }
        val entities = org.json.JSONObject(schema).getJSONObject("database").getJSONArray("entities")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO players VALUES ('p','424242','Oma',0,'person',NULL,1,0)")
            old.execSQL("INSERT INTO courses VALUES ('c','Rata',NULL,NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO rounds VALUES ('r','sample','r','2026-01-01',NULL,NULL,NULL,0)")
            old.execSQL("INSERT INTO round_players VALUES ('r','p','c',NULL,NULL,NULL,1,-2,'FINISHED','INDIVIDUAL',1)")
            old.execSQL("INSERT INTO hole_scores VALUES ('r','p',0,'1',3,1)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, KiekkopolkuDatabase::class.java, name).addMigrations(KiekkopolkuDatabase.MIGRATION_1_2).build()
        try {
            val h = LocalHistoryRepository(migrated).snapshot()
            assertEquals("424242", h.players.single().metrixId)
            assertFalse(h.players.single().hasIntegrationCode)
            assertEquals(1, h.stats().aces)
            assertEquals(1, h.stats().rounds)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
    @Test fun repeatedImportsDoNotDuplicateEventsPlayersOrAces() = runBlocking {
        repo.loadSample(); repo.loadSample(); repo.refreshSelected()
        assertEquals(3, db.history().players().size)
        assertEquals(5, db.history().rounds().size)
        assertEquals(13, db.history().entries().size)
        val stats = repo.snapshot().stats()
        assertEquals(3, stats.courses); assertEquals(3, stats.aces)
        assertEquals(1, stats.missingHoles)
        assertTrue(repo.snapshot().players.all { it.lastSyncAt == 42L })
    }
    @Test fun deletingPlayerPreservesSharedRoundAndOtherScores() = runBlocking {
        repo.loadSample(); repo.deletePlayer("sample-1")
        assertEquals(5, db.history().rounds().size)
        assertEquals(8, db.history().entries().size)
        assertFalse(db.history().holes().any { it.playerId == "sample-1" })
        assertEquals(2, repo.snapshot().stats().aces)
    }
    @Test fun duplicatePlayerIdIsNormalizedAndRejected() = runBlocking {
        repo.addPlayer("00123", "Oma")
        try { repo.addPlayer("123", "Toinen"); fail("Expected duplicate rejection") } catch (_: DuplicatePlayerException) { }
        assertEquals(1, db.history().players().size)
    }
    @Test fun removingSampleKeepsRealProfilesAndCleansOrphans() = runBlocking {
        repo.addPlayer("123", "Oma"); repo.loadSample(); repo.removeSample()
        assertEquals(1, repo.snapshot().players.size)
        assertTrue(db.history().rounds().isEmpty()); assertTrue(db.history().courses().isEmpty())
        assertEquals(RefreshResult.FAILED, repo.refreshSelected())
        assertNull(repo.snapshot().players.single().lastSyncAt)
    }
    @Test fun selectionControlsAllAggregates() = runBlocking {
        repo.loadSample()
        repo.selectPlayer("sample-1", false); repo.selectPlayer("sample-2", false)
        assertEquals(3, repo.snapshot().stats().rounds)
        assertEquals(1, repo.snapshot().stats().aces)
        repo.selectPlayer("sample-3", false)
        assertEquals(0, repo.snapshot().stats().courses)
        assertEquals(RefreshResult.NOTHING_SELECTED, repo.refreshSelected())
    }
    @Test fun fetchFailureDoesNotAdvanceSyncOrLoseCachedHistory() = runBlocking {
        repo.loadSample()
        val failing = LocalHistoryRepository(db, object : DiscGolfMetrixDataSource {
            override suspend fun fetchHistory(playerIds: Set<String>): ImportBatch = error("offline")
        }, now = { 99L })
        try { failing.refreshSelected(); fail("Expected fetch failure") } catch (_: IllegalStateException) { }
        assertEquals(13, repo.snapshot().stats().rounds)
        assertTrue(repo.snapshot().players.all { it.lastSyncAt == 42L })
    }
    @Test fun failedImportRollsBackAllWrites() = runBlocking {
        val invalid = LocalHistoryRepository(db, object : DiscGolfMetrixDataSource {
            override suspend fun fetchHistory(playerIds: Set<String>): ImportBatch {
                val batch = SampleDataSource().fetchHistory(playerIds)
                return batch.copy(holes = batch.holes + HoleScoreEntity("does-not-exist", "sample-1", 0, "1", 3, 1))
            }
        })
        try { invalid.loadSample(); fail("Expected foreign key violation") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertTrue(db.history().players().isEmpty()); assertTrue(db.history().rounds().isEmpty())
    }
    @Test fun databaseUniqueIndexPreventsDifferentLocalIdForSameRemoteRound() = runBlocking {
        repo.loadSample()
        db.history().putRounds(listOf(RoundEntity("different", "sample", "r0", "2026-05-02")))
        assertEquals(5, db.history().rounds().size)
        assertEquals("sample:r0", db.history().rounds().single { it.externalRoundId == "r0" }.id)
    }
    @Test fun databaseSurvivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, KiekkopolkuDatabase::class.java, name).build()
        LocalHistoryRepository(first).loadSample(); first.close()
        val second = Room.databaseBuilder(context, KiekkopolkuDatabase::class.java, name).build()
        try { assertEquals(13, LocalHistoryRepository(second).snapshot().stats().rounds) }
        finally { second.close(); context.deleteDatabase(name) }
    }
}
