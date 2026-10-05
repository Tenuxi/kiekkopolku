package fi.kiekkopolku.app.data

import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

/** Conservative fallback for public leaf event pages, observed against the API documentation example.
 * Only the course, date and evidence that this exact player played are retained; never hole scores.
 * Changed markup, aggregate events, private pages, registrations and ambiguous dates fail closed.
 */
internal fun parsePublicMetrixVisit(html: String, id: String, player: PlayerEntity, timestamp: Long): ImportBatch? {
    val document = Jsoup.parse(html)
    if (document.select("input#idCompetitionID").singleOrNull()?.attr("value") != id) return null
    val header = document.select("header.main-header").singleOrNull() ?: return null
    val course = header.select("a[href^=/course/]").singleOrNull() ?: return null
    val externalCourse = Regex("/course/([1-9][0-9]*)").matchEntire(course.attr("href"))?.groupValues?.get(1) ?: return null
    val rawDate = header.select("p").singleOrNull()?.ownText()?.trim()?.substringBefore(' ') ?: return null
    // The public request explicitly uses locale=en (month/day/two-digit year).
    val formatter = DateTimeFormatterBuilder().appendPattern("M/d/")
        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    val date = runCatching { LocalDate.parse(rawDate, formatter) }.getOrNull() ?: return null
    if (!date.isBefore(LocalDate.now().minusYears(1))) return null
    val rows = document.select("table#id_results tr").filter { row ->
        row.select("td.player-cell a.profile-link").any { it.attr("href") == "/player/${player.externalPlayerId}" }
    }
    val row = rows.singleOrNull() ?: return null
    // A pair/team must not be attributed as an individual visit.
    if (row.select("td.player-cell a.profile-link").map { it.attr("href") }.distinct().size != 1) return null
    val played = row.select("td[title=Played holes]").singleOrNull()?.text() ?: return null
    if (played != "F" && (played.toIntOrNull() ?: 0) <= 0) return null
    val courseId = "metrix:course:$externalCourse"
    val roundId = "metrix:$id"
    return ImportBatch(listOf(CourseEntity(courseId, course.text().take(300), null, null)),
        listOf(CourseSourceRefEntity("metrix", externalCourse, courseId)),
        listOf(RoundEntity(roundId, "metrix", id, date.toString(), fetchedAt = timestamp)),
        listOf(RoundPlayerEntity(roundId, player.id, courseId, sourceCourseId = externalCourse,
            status = "METADATA_ONLY", scoringMode = "UNKNOWN")), emptyList())
}
