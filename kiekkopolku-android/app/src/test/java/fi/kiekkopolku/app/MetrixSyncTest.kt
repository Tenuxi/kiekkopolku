package fi.kiekkopolku.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fi.kiekkopolku.app.data.*
import fi.kiekkopolku.app.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic data uses the documented contract and field shapes observed on public live results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MetrixSyncTest {
    private lateinit var db: KiekkopolkuDatabase
    private lateinit var repo: LocalHistoryRepository
    private val secrets = mutableMapOf<String, String>()
    private var timestamp = 100_000L
    private val calls = mutableListOf<String>()
    private var publicHtml: String? = null
    private var response: suspend (String, String?) -> JsonObject = { content, id ->
        if (content == "my_competitions") json("""{"my_competitions":[101, "101"],"Errors":[]}""") else round(id!!)
    }
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KiekkopolkuDatabase::class.java).build()
        repo = LocalHistoryRepository(db, now = { timestamp }, credentials = object : CredentialStore {
            override suspend fun get(playerId: String) = secrets[playerId]
            override suspend fun put(playerId: String, code: String) { secrets[playerId] = code }
            override suspend fun remove(playerId: String) { secrets.remove(playerId) }
        }, verifier = IntegrationCodeVerifier { }, metrix = object : MetrixApi {
            override suspend fun publicEvent(id: String): String? { calls += "public:$id"; return publicHtml }
            override suspend fun get(content: String, code: String, id: String?): JsonObject {
            calls += "$content:$id"
            return if (content == "course") json("""{"course":{"ID":"$id","ParentID":"800000","Fullname":"Testirata","Lat":"61.5","Lng":"23.7"},"Errors":[]}""")
            else response(content, id)
            }
        }, requestDelayMillis = 0)
    }
    @After fun close() { db.close() }
    private suspend fun add(id: String = "424242") = repo.addPlayer(id, "Oma", "synthetic-code-$id")

    @Test fun blockedHistoryGetsCachedVisitAndCourseWithoutInventingScorecard() = runBlocking {
        add()
        publicHtml = publicVisitHtml()
        response = { content, _ -> if (content == "my_competitions") json("""{"my_competitions":[101]}""")
            else json("""{"Competition":null,"Errors":["data older than year"]}""") }
        assertEquals(RefreshResult.HISTORY_LIMITED, repo.refreshSelected())
        val h = repo.snapshot()
        assertEquals(1, h.stats().rounds)
        assertEquals(1, h.coverage().blockedCards)
        assertEquals("METADATA_ONLY", h.entries.single().status)
        assertNull(h.entries.single().total)
        assertTrue(h.entries.single().holes.isEmpty())
        assertTrue(h.courses.single().hasLocation)
        repo.refreshOnOpen()
        assertEquals(1, calls.count { it == "public:101" })
        repo.refreshSelected()
        assertEquals(1, repo.snapshot().stats().rounds)
        assertEquals(1, calls.count { it == "public:101" })
        response = { content, id -> if (content == "my_competitions") json("""{"my_competitions":[101]}""") else round(id!!) }
        repo.refreshSelected()
        assertEquals("FINISHED", repo.snapshot().entries.single().status)
        assertEquals(2, repo.snapshot().entries.single().holes.size)
    }
    @Test fun importIsIdempotentAndPersistsOnlyLinkedPlayerWithExactHoles() = runBlocking {
        add()
        assertEquals(RefreshResult.UPDATED_METRIX, repo.refreshSelected())
        repo.refreshSelected()
        val h = repo.snapshot()
        assertEquals(1, h.entries.size)
        assertEquals(2, h.entries.single().holes.size)
        assertEquals(1, h.stats().aces)
        assertEquals(4, h.entries.single().total)
        assertEquals(1, h.courses.size)
        assertEquals("9B", h.entries.single().holes.last().label)
        assertEquals(timestamp, h.players.single().lastSyncAt)
        assertEquals(2, calls.count { it == "result:101" })
    }
    @Test fun sharedRoundRetainsBothPlayersAndDeleteKeepsOtherPlayer() = runBlocking {
        add(); add("424243")
        repo.refreshSelected()
        assertEquals(1, db.history().rounds().size)
        assertEquals(2, db.history().entries().size)
        val first = repo.snapshot().players.first { it.metrixId == "424242" }
        repo.deletePlayer(first.id)
        assertEquals(1, db.history().rounds().size)
        assertEquals(1, db.history().entries().size)
        assertEquals(0, repo.snapshot().stats().aces)
    }
    @Test fun hierarchyImportsLeafOnceAndNeverCountsParentSummary() = runBlocking {
        add()
        response = { content, id -> when {
            content == "my_competitions" -> json("""{"my_competitions":[100,101]}""")
            id == "100" -> json("""{"Competition":{"ID":100,"SubCompetitions":[{"ID":"101"},101]},"Errors":[]}""")
            else -> round(id!!)
        } }
        repo.refreshSelected()
        assertEquals(listOf("101"), db.history().rounds().map { it.externalRoundId })
        assertEquals(1, calls.count { it == "result:101" })
    }
    @Test fun embeddedChildResultsAvoidExtraRequestsAndDoublesNeverBecomePersonalAces() = runBlocking {
        add()
        response = { content, _ -> if (content == "my_competitions") json("""{"my_competitions":[100]}""")
            else buildJsonObject { put("Competition", buildJsonObject {
                put("ID", 100); put("HasSubcompetitions", 1)
                put("SubCompetitions", buildJsonArray { add(round("101").getValue("Competition")) })
            }) } }
        repo.refreshSelected()
        assertEquals(1, repo.snapshot().stats().rounds)
        assertFalse(calls.contains("result:101"))
        val doubles = round("102").getValue("Competition").jsonObject.toMutableMap().apply { put("Type", JsonPrimitive("6")) }
        try { parseMetrixResult(buildJsonObject { put("Competition", JsonObject(doubles)) }, "102",
            PlayerEntity("p", "424242", "Oma"), 0); fail("Doubles must not become personal aces") }
        catch (e: MetrixImportException) { assertEquals("UNSUPPORTED", e.reason) }
    }
    @Test fun expectedHistoryLimitKeepsRoundsAndFullSuccessTimeWithoutReportingImportFailure() = runBlocking {
        add(); repo.refreshSelected()
        timestamp = 200_000
        response = { content, id -> when {
            content == "my_competitions" -> json("""{"my_competitions":[102,103]}""")
            id == "102" -> round(id)
            else -> json("""{"Competition":null,"Errors":["If you are interested data older than year please send us a message"]}""")
        } }
        repo.refreshSelected()
        val h = repo.snapshot()
        assertEquals(2, h.entries.size)
        assertEquals(100_000L, h.players.single().lastSyncAt)
        assertEquals("LIMITED", h.players.single().syncStatus)
        assertEquals("HISTORY_LIMIT", h.players.single().syncError)
        assertNull(repo.progress.value)
    }
    @Test fun realErrorSurvivesALaterHistoryLimitIncludingCachedLimits() = runBlocking {
        add()
        response = { content, id -> when {
            content == "my_competitions" -> json("""{"my_competitions":[101,102,103]}""")
            id == "101" -> round(id)
            id == "102" -> json("""{"Competition":null,"Errors":["Access denied"]}""")
            else -> json("""{"Competition":null,"Errors":["data older than year"]}""")
        } }
        assertEquals(RefreshResult.PARTIAL, repo.refreshSelected())
        assertEquals("ACCESS", repo.snapshot().players.single().syncError)
        repo.refreshOnOpen() // The cached history limit used to overwrite ACCESS as well.
        assertEquals("ACCESS", repo.snapshot().players.single().syncError)
        assertEquals(1, repo.snapshot().coverage().blockedCards)
        assertEquals(1, repo.snapshot().entries.size)
    }
    @Test fun offlineAndInvalidCodeKeepPreviouslyImportedData() = runBlocking {
        add(); repo.refreshSelected()
        timestamp++
        response = { _, _ -> throw MetrixConnectionException() }
        assertEquals(RefreshResult.FAILED, repo.refreshSelected())
        assertEquals(1, repo.snapshot().entries.size)
        assertEquals("CONNECTION", repo.snapshot().players.single().syncError)
        response = { _, _ -> json("""{"Errors":[]}""") }
        assertEquals(RefreshResult.FAILED, repo.refreshSelected())
        assertEquals("INVALID_CODE", repo.snapshot().players.single().syncError)
        assertEquals(100_000L, repo.snapshot().players.single().lastSyncAt)
    }
    @Test fun automaticRefreshIncludesUnselectedSavedPlayersAndCachesOldCompleteRounds() = runBlocking {
        add(); repo.refreshSelected()
        val p = repo.snapshot().players.single()
        repo.selectPlayer(p.id, false)
        calls.clear()
        repo.refreshOnOpen()
        assertTrue(calls.contains("my_competitions:null"))
        assertFalse(calls.contains("result:101"))
        timestamp += 86_400_001L
        repo.refreshOnOpen()
        assertTrue(calls.contains("result:101"))
    }
    @Test fun codeOnlyProfileCanAddIdWithoutReenteringSecret() = runBlocking {
        repo.addPlayer("", "Oma", "synthetic-code")
        val p = repo.snapshot().players.single()
        repo.refreshOnOpen()
        assertEquals("NEEDS_ID", repo.snapshot().players.single().syncError)
        repo.addPlayer("424242", "Oma", "", p.id)
        assertEquals("synthetic-code", secrets[p.id])
        repo.refreshOnOpen()
        assertEquals(1, repo.snapshot().entries.size)
        assertEquals(p.id, repo.snapshot().players.single().id)
    }
    @Test fun partialCardCannotEraseCompleteCachedCard() = runBlocking {
        add(); repo.refreshSelected()
        response = { content, id -> if (content == "my_competitions") json("""{"my_competitions":[101]}""")
            else round(id!!, firstScore = "null") }
        repo.refreshSelected()
        assertTrue(repo.snapshot().entries.single().completeHoles)
        assertEquals(1, repo.snapshot().stats().aces)
    }
    @Test fun mismatchedIdentityNeverImportsOtherPlayersResults() = runBlocking {
        add("999999")
        repo.refreshSelected()
        assertTrue(repo.snapshot().entries.isEmpty())
        assertEquals("NO_RESULTS", repo.snapshot().players.single().syncError)
        assertNull(repo.snapshot().players.single().lastSyncAt)
    }
    @Test fun cancellationReleasesSpinnerAndPreservesEarlierCommittedBatch() = runBlocking {
        add()
        val enteredSecond = CompletableDeferred<Unit>()
        response = { content, id -> when {
            content == "my_competitions" -> json("""{"my_competitions":[101,102]}""")
            id == "101" -> round(id)
            else -> { enteredSecond.complete(Unit); awaitCancellation() }
        } }
        val job = launch { repo.refreshSelected() }
        enteredSecond.await()
        job.cancelAndJoin()
        assertEquals(1, repo.snapshot().entries.size)
        assertNull(repo.snapshot().players.single().lastSyncAt)
        assertNull(repo.progress.value)
    }
    @Test fun malformedOrUnsupportedResultsNeverProduceAces() {
        val player = PlayerEntity("p", "424242", "Oma")
        for (body in listOf(
            """{"Competition":{"ID":102,"SubCompetitions":[]},"Errors":[]}""",
            round("101").toString().replace("\"MetrixMode\":\"1\"", "\"MetrixMode\":\"unknown\"")
        )) {
            try { parseMetrixResult(json(body), "101", player, 0); fail("Must reject ambiguous card") }
            catch (_: MetrixImportException) { }
        }
    }
    @Test fun fullEventListIsRetainedWithoutInventing800PlayedRounds() = runBlocking {
        add()
        response = { content, _ -> if (content == "my_competitions") buildJsonObject {
            put("my_competitions", buildJsonArray { (1000..1799).forEach { add(it) } })
        } else json("""{"Competition":null,"Errors":["data older than year"]}""") }
        repo.refreshSelected()
        val h = repo.snapshot()
        assertEquals(800, h.coverage().listedEvents)
        assertEquals(800, h.coverage().blockedCards)
        assertEquals(0, h.stats().rounds)
        assertTrue(h.courses.isEmpty())
        calls.clear()
        repo.refreshOnOpen()
        assertEquals(0, calls.count { it.startsWith("result:") })
        assertEquals(800, repo.snapshot().metrixEvents.size)
        repo.deletePlayer(h.players.single().id)
        assertTrue(repo.snapshot().metrixEvents.isEmpty())
    }
    @Test fun coordinatesParentLinkAndHistorySurviveLaterDeniedScorecard() = runBlocking {
        add(); repo.refreshSelected()
        val h = repo.snapshot()
        assertEquals(61.5, h.courses.single().latitude!!, 0.0001)
        assertEquals("metrix:course:800000", h.courses.single().physicalId)
        // A v0.3 installation may have an old round but no metadata yet.
        db.history().putCourses(db.history().courses().map { it.copy(latitude = null, longitude = null) })
        response = { content, _ -> if (content == "my_competitions") json("""{"my_competitions":[101]}""")
            else json("""{"Competition":null,"Errors":["data older than year"]}""") }
        repo.refreshSelected()
        assertEquals(1, repo.snapshot().stats().rounds)
        assertTrue(repo.snapshot().courses.single().hasLocation)
        assertEquals(1, repo.snapshot().coverage().blockedCards)
        assertEquals(2, calls.count { it == "course:800001" })
    }
    @Test fun laterScorecardDoesNotEraseCourseMetadata() = runBlocking {
        add(); repo.refreshSelected(); repo.refreshSelected()
        assertTrue(repo.snapshot().courses.single().hasLocation)
        assertEquals("metrix:course:800000", repo.snapshot().courses.single().physicalId)
        assertEquals(1, repo.snapshot().coverage().listedEvents)
    }
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun round(id: String, firstScore: String = "1") = json("""{
      "Competition":{"ID":$id,"Date":"2025-01-02","Time":"12:30:00","CourseID":"800001",
      "CourseName":"Testirata","MetrixMode":"1","HasSubcompetitions":0,
      "Tracks":[{"Number":"1","NumberAlt":"","Par":"3"},{"Number":"2","NumberAlt":"9B","Par":"3"}],
      "Results":[{"UserID":"424242","Sum":4,"Diff":-2,"DNF":null,
        "PlayerResults":[{"Result":$firstScore},{"Result":"3"}]},
        {"UserID":"424243","Sum":6,"Diff":0,"DNF":"0","PlayerResults":[{"Result":"3"},{"Result":"3"}]}]},
      "Errors":[]}
    """)
}
