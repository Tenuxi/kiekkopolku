package fi.kiekkopolku.app

import fi.kiekkopolku.app.data.*
import fi.kiekkopolku.app.domain.*
import fi.kiekkopolku.app.ui.mapFeatures
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MapAndCoverageTest {
    private val players = listOf(Player("a", "1", "A", 0, true, false, null), Player("b", "2", "B", 1, false, false, null))
    private val courses = listOf(Course("c", "Zulu", null, null, 61.5, 23.7, "park"),
        Course("d", "Aava", null, null, 61.5, 23.7, "park"), Course("x", "Ei sijaintia", null, null, null, null))
    private fun round(id: String, course: String, date: String, player: String = "a") =
        RoundEntry(id, id, "metrix", player, course, date, null, null, 1, -2, "FINISHED", true, true, listOf(Hole(0, "1", 3, 1)))
    @Test fun mapSelectionAndDefaultSortUseAllStoredHistory() {
        val h = History(players, courses, listOf(round("old", "d", "2020-01-01"), round("new", "c", "2026-09-30"), round("other", "x", "2026-10-01", "b")))
        assertEquals(listOf("c", "d"), h.courseVisits().map { it.course.id })
        val geo = Json.parseToJsonElement(mapFeatures(h.courseVisits())).jsonObject["features"]!!.jsonArray
        assertEquals(2, geo.size)
        assertEquals(listOf(23.7, 61.5), geo.first().jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.jsonPrimitive.double })
        assertFalse(mapFeatures(h.courseVisits()).contains("player"))
        assertEquals(1, h.physicalCourseCount())
        assertEquals(2, h.stats().courses)
    }
    @Test fun lastYearDoesNotDiscardOldRoundsOrCountFutureRounds() {
        val h = History(players, courses, listOf(round("before", "c", "2025-10-04"), round("edge", "c", "2025-10-05"),
            round("today", "d", "2026-10-05"), round("future", "d", "2026-10-06")))
        assertEquals(4, h.stats().rounds)
        assertEquals(setOf("edge", "today"), h.entriesLastYear(LocalDate.of(2026, 10, 5)).map { it.roundId }.toSet())
    }
    @Test fun coverageSeparatesIdentifiersFromPlayedRoundsAndRespectsPlayerSelection() {
        val h = History(players, metrixEvents = listOf(MetrixEvent("a", "10", true, "HISTORY_LIMIT"), MetrixEvent("a", "11", true, "PARENT"),
            MetrixEvent("a", "12", false, "PENDING"), MetrixEvent("b", "20", true, "ACCESS")))
        assertEquals(Coverage(2, 1, 0, 1), h.coverage())
        assertEquals(0, h.stats().rounds)
        assertEquals(Coverage(1, 0, 1, 0), h.coverage(setOf("b")))
    }
    @Test fun invalidCoordinatesNeverCreateMapPins() {
        val invalid = listOf(null to null, Double.NaN to 20.0, 91.0 to 20.0, 60.0 to 181.0, 0.0 to 0.0)
        invalid.forEach { (lat, lng) -> assertFalse(courses.first().copy(latitude = lat, longitude = lng).hasLocation) }
    }
    @Test fun courseResponseValidatesIdentityAndKeepsCachedLocationWhenMissing() {
        val existing = CourseEntity("c", "Vanha", "Tampere", "FI", 61.5, 23.7)
        fun root(s: String) = Json.parseToJsonElement(s).jsonObject
        val update = parseMetrixCourse(root("""{"course":{"ID":"10","ParentID":"9","Fullname":"Rata &rarr; Layout","Lat":"61.6","Lng":"23.8","City":"Nokia"},"baskets":[{}],"Errors":[]}"""), existing, "10", 42)
        assertEquals("Rata → Layout", update.course.name)
        assertEquals("9", update.ref.parentExternalId)
        assertEquals(61.6, update.course.latitude!!, 0.0001)
        val missing = parseMetrixCourse(root("""{"course":{"ID":"10","Lat":null,"Lng":null}}"""), existing, "10", 43)
        assertEquals(existing.latitude, missing.course.latitude)
        try { parseMetrixCourse(root("""{"course":{"ID":"11"}}"""), existing, "10", 0); fail("Wrong course must not be mapped") }
        catch (_: MetrixImportException) { }
    }
}
