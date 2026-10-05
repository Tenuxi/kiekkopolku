package fi.kiekkopolku.app

import fi.kiekkopolku.app.data.*
import fi.kiekkopolku.app.domain.*
import fi.kiekkopolku.app.ui.markerKind
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

internal fun publicVisitHtml(id: String = "101", player: String = "424242", date: String = "6/2/20", played: String = "F") = """
<input id="idCompetitionID" value="$id">
<header class="main-header"><h1>Harjoitus</h1><p>$date 09:34<span>|</span><a href="/course/900000">Rata &amp; metsä</a></p></header>
<table id="id_results"><tr><td class="player-cell"><a class="profile-link" href="/player/$player">Pelaaja</a><a class="profile-link" href="/player/$player">Profiili</a></td><td title="Played holes">$played</td><td>42</td></tr></table>
"""
class PublicVisitTest {
    private val player = PlayerEntity("local", "424242", "Testi")
    @Test fun oldPublicLeafRetainsMetadataButNeverScores() {
        val result = parsePublicMetrixVisit(publicVisitHtml(), "101", player, 123)!!
        assertEquals("2020-06-02", result.rounds.single().playedDate)
        assertEquals("900000", result.refs.single().externalId)
        assertEquals("Rata & metsä", result.courses.single().name)
        assertEquals("METADATA_ONLY", result.entries.single().status)
        assertNull(result.entries.single().totalScore)
        assertFalse(result.entries.single().holeDataComplete)
        assertTrue(result.holes.isEmpty())
    }
    @Test fun wrongIdentityRegistrationPrivateOrChangedPageNeverInventsVisit() {
        listOf(publicVisitHtml(id="102"), publicVisitHtml(player="424243"), publicVisitHtml(played="0"),
            publicVisitHtml(date="2/30/20"), publicVisitHtml().replace("main-header", "changed"),
            publicVisitHtml().replace("/course/900000", "/other/900000"), "<p>Private</p>",
            publicVisitHtml().replace("</table>", "<tr><td class='player-cell'><a class='profile-link' href='/player/424242'>Duplicate</a></td><td title='Played holes'>F</td></tr></table>")
        ).forEach { assertNull(parsePublicMetrixVisit(it, "101", player, 0)) }
    }
    @Test fun markerBoundariesAndMetadataVisitsAreHonest() {
        val today = LocalDate.of(2026,10,5)
        val course = Course("c", "Rata", null, null, 61.0, 24.0)
        val entry = RoundEntry("r", "r", "metrix", "p", "c", "2025-10-05", null, null, 42, null, "FINISHED", true, true, emptyList())
        assertEquals("recent", markerKind(CourseVisit(course, listOf(entry)), today))
        assertEquals("historic", markerKind(CourseVisit(course, listOf(entry.copy(date="2025-10-04"))), today))
        assertEquals("recent", markerKind(CourseVisit(course, listOf(entry, entry.copy(date="2020-01-01",status="METADATA_ONLY"))), today))
        assertEquals("unknown", markerKind(CourseVisit(course, listOf(entry.copy(date="2026-10-06"))), today))
        assertEquals("unknown", markerKind(CourseVisit(course, listOf(entry.copy(status="METADATA_ONLY"))), today))
        assertTrue(entry.copy(status="METADATA_ONLY", total=null, holes=emptyList()).played)
    }
    @Test fun optionalObservedPublicContract() {
        val path = System.getenv("METRIX_PUBLIC_METADATA_FIXTURE") ?: return
        val result = parsePublicMetrixVisit(java.io.File(path).readText(), "437198", player.copy(externalPlayerId="130"), 0)!!
        assertEquals("3091", result.refs.single().externalId)
        assertEquals("2017-06-02", result.rounds.single().playedDate)
    }
}
