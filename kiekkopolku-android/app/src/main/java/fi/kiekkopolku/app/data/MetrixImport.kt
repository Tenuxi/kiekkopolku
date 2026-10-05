package fi.kiekkopolku.app.data

import kotlinx.serialization.json.*
import java.time.LocalDate

/** Mapping of the documented result contract, never player names or guessed owner IDs. */
internal class MetrixImportException(val reason: String) : Exception()
internal data class MetrixResult(val children: List<String>, val batch: ImportBatch, val embedded: Map<String, JsonObject> = emptyMap())
internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String) = text(key)?.toIntOrNull()
private fun externalId(value: String?) = value?.takeIf { it.matches(Regex("[0-9]+")) && it.any { c -> c != '0' } }
internal fun competitionIds(root: JsonObject): List<String> {
    validateMetrixCodeResponse(root.toString())
    return (root.getValue("my_competitions") as JsonArray).map { it.jsonPrimitive.content }.distinct()
}
internal fun parseMetrixResult(root: JsonObject, requestedId: String, player: PlayerEntity, timestamp: Long): MetrixResult {
    val errors = root["Errors"]
    if (errors != null && (errors !is JsonArray || errors.isNotEmpty())) {
        val old = (errors as? JsonArray)?.any { (it as? JsonPrimitive)?.contentOrNull?.contains("older than year", true) == true } == true
        throw MetrixImportException(if (old) "HISTORY_LIMIT" else "ACCESS")
    }
    val c = root["Competition"] as? JsonObject ?: throw MetrixImportException("RESPONSE")
    val id = externalId(c.text("ID")) ?: throw MetrixImportException("RESPONSE")
    if (id != requestedId) throw MetrixImportException("RESPONSE")
    val children = (c["SubCompetitions"] as? JsonArray ?: if (c.number("HasSubcompetitions") == 0) JsonArray(emptyList()) else throw MetrixImportException("RESPONSE")).map {
        externalId(if (it is JsonObject) it.text("ID") else (it as? JsonPrimitive)?.contentOrNull)
            ?: throw MetrixImportException("RESPONSE")
    }.distinct()
    if (c.number("HasSubcompetitions") == 1 && children.isEmpty()) throw MetrixImportException("RESPONSE")
    val embedded = (c["SubCompetitions"] as? JsonArray).orEmpty().mapNotNull { element ->
        val child = element as? JsonObject ?: return@mapNotNull null
        if (child["Results"] is JsonArray && child["Tracks"] is JsonArray)
            child.text("ID")!! to buildJsonObject { put("Competition", child) } else null
    }.toMap()
    val empty = ImportBatch(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    // A parent contains aggregate scores: only import its leaf rounds.
    if (children.isNotEmpty()) return MetrixResult(children, empty, embedded)
    if (c.text("Type") == "6") throw MetrixImportException("UNSUPPORTED") // Live doubles response, no individual user IDs.
    val results = c["Results"] as? JsonArray ?: throw MetrixImportException("RESPONSE")
    val matching = results.map { it as? JsonObject ?: throw MetrixImportException("RESPONSE") }
        .filter { it.text("UserID") == player.externalPlayerId }
    if (matching.size > 1) throw MetrixImportException("UNSUPPORTED")
    val result = matching.singleOrNull() ?: return MetrixResult(emptyList(), empty)
    // Mode 0 is documented; mode 1 with extra throw statistics was verified on live scorecards.
    if (c.text("MetrixMode") !in listOf("0", "1")) throw MetrixImportException("UNSUPPORTED")
    val date = c.text("Date")?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
        ?: throw MetrixImportException("RESPONSE")
    val tracks = c["Tracks"] as? JsonArray ?: throw MetrixImportException("RESPONSE")
    val scores = result["PlayerResults"] as? JsonArray ?: throw MetrixImportException("RESPONSE")
    if (scores.size > tracks.size) throw MetrixImportException("RESPONSE")
    val roundId = "metrix:$id"
    val holes = tracks.mapIndexed { index, element ->
        val track = element as? JsonObject ?: throw MetrixImportException("RESPONSE")
        val score = (scores.getOrNull(index) as? JsonObject)?.number("Result")?.takeIf { it > 0 }
        HoleScoreEntity(roundId, player.id, index,
            track.text("NumberAlt")?.takeIf { it.isNotBlank() } ?: track.text("Number") ?: "${index + 1}",
            track.number("Par")?.takeIf { it > 0 }, score)
    }
    val total = result.number("Sum")?.takeIf { it > 0 }
    if (holes.none { it.score != null } && total == null) return MetrixResult(emptyList(), empty)
    val dnf = result.text("DNF")?.lowercase() !in listOf(null, "", "0", "false")
    val complete = holes.isNotEmpty() && holes.all { it.score != null }
    val status = if (dnf) "DNF" else if (complete || total != null && scores.isEmpty()) "FINISHED" else "IN_PROGRESS"
    val courseExternal = externalId(c.text("CourseID"))
    val courseId = courseExternal?.let { "metrix:course:$it" }
    val courseName = c.text("CourseName")?.takeIf { it.isNotBlank() } ?: "Metrix-rata $courseExternal"
    val courses = if (courseId == null) emptyList() else listOf(CourseEntity(courseId, courseName.replace("&rarr;", "→").replace("&amp;", "&"), null, null))
    val refs = if (courseId == null) emptyList() else listOf(CourseSourceRefEntity("metrix", courseExternal!!, courseId))
    return MetrixResult(emptyList(), ImportBatch(courses, refs,
        listOf(RoundEntity(roundId, "metrix", id, date, localTime = c.text("Time"), fetchedAt = timestamp)),
        listOf(RoundPlayerEntity(roundId, player.id, courseId, sourceCourseId = courseExternal,
            totalScore = total, relativeToPar = result.number("Diff"), status = status, holeDataComplete = complete)), holes))
}
