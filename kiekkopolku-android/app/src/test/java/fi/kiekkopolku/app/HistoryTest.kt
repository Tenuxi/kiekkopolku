package fi.kiekkopolku.app

import fi.kiekkopolku.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class HistoryTest {
    private val players = listOf(Player("a", "1", "A", 0, true, false, null), Player("b", "2", "B", 1, true, false, null))
    private val courses = listOf(Course("c", "Metsä", "Tampere", "FI", null, null))
    private fun entry(player: String, id: String = "r", layout: String = "Pitkä") = RoundEntry(id, id, "sample", player, "c", "2026-05-01", layout,
        null, 5, -1, "FINISHED", true, true, listOf(Hole(0, "1", 3, 1), Hole(1, "2", 3, 4)))
    @Test fun uniqueCoursesAcrossPlayersAndLayouts() {
        val h = History(players, courses, listOf(entry("a"), entry("b", layout = "Lyhyt")))
        assertEquals(1, h.stats().courses); assertEquals(2, h.stats().rounds); assertEquals(1, h.stats().events)
    }
    @Test fun multiPlayerAndEmptySelection() {
        val h = History(players, courses, listOf(entry("a"), entry("b")))
        assertEquals(2, h.selectedEntries().size)
        assertEquals(listOf("a"), h.copy(players = players.map { it.copy(active = it.id == "a") }).selectedEntries().map { it.playerId })
        assertTrue(h.copy(players = players.map { it.copy(active = false) }).selectedEntries().isEmpty())
    }
    @Test fun acesExcludeTeamAndUnknownScoresButIncludeKnownDnfHoles() {
        val e = entry("a")
        val entries = listOf(e, e.copy(roundId = "team", individual = false), e.copy(roundId = "dnf", status = "DNF", completeHoles = false),
            e.copy(roundId = "dns", status = "DNS"), e.copy(roundId = "unknown", holes = listOf(Hole(0, "1", null, null))))
        assertEquals(2, aces(entries).size)
    }
    @Test fun searchAndAllSortOrders() {
        val h = History(players, courses + Course("d", "Aava", "Nokia", "FI", null, null),
            listOf(entry("a"), entry("b", "r2").copy(date = "2026-06-01"), entry("a", "r3").copy(courseId = "d", date = "2026-04-01")))
        assertEquals("d", h.courseVisits(order = CourseOrder.NAME).first().course.id)
        assertEquals("c", h.courseVisits(order = CourseOrder.MOST_ROUNDS).first().course.id)
        assertEquals("c", h.courseVisits(order = CourseOrder.LAST_PLAYED).first().course.id)
        assertEquals("d", h.courseVisits(order = CourseOrder.FIRST_PLAYED).first().course.id)
        assertEquals(1, h.courseVisits(" METSÄ ").size)
    }
    @Test fun matchingRequiresEvidenceAndDoesNotAutoMergeNamesOrCoordinates() {
        val matcher = CourseMatcher()
        val input = CourseCandidate(courses.single(), "metrix", "1")
        assertEquals(CourseMatch.Exact("c"), matcher.match(input, listOf(input)))
        val other = input.copy(source = "fgr", externalId = "other", course = input.course.copy(id = "d", name = " METSÄ "))
        assertEquals(CourseMatch.Suggested("d"), matcher.match(input, listOf(other)))
        assertTrue(matcher.match(input, listOf(other, other.copy(course = other.course.copy(id = "e")))) is CourseMatch.Ambiguous)
        assertEquals(CourseMatch.Unmatched, matcher.match(input, listOf(other.copy(course = other.course.copy(country = "EE")))))
        assertEquals(CourseMatch.Unmatched, matcher.match(input, listOf(other.copy(course = other.course.copy(name = "Muu rata")))))
    }
}
