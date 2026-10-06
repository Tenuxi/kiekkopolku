package fi.kiekkopolku.app.domain

data class Player(val id: String, val metrixId: String?, val name: String, val color: Int,
    val active: Boolean, val sample: Boolean, val lastSyncAt: Long?, val hasIntegrationCode: Boolean = false, val syncStatus: String? = null, val syncError: String? = null)
private val inactiveCourseMarker = Regex("\\s*\\(\\s*ei\\s+käytössä\\s*\\)", RegexOption.IGNORE_CASE)

data class Course(val id: String, val name: String, val city: String?, val country: String?,
    val latitude: Double?, val longitude: Double?, val physicalId: String = id,
    val holeCount: Int? = null, val address: String? = null) {
    val isInactive: Boolean get() = inactiveCourseMarker.containsMatchIn(name)
    val displayName: String get() = name.replace(inactiveCourseMarker, " ").replace(Regex("\\s+"), " ").trim().ifBlank { name }
    /** A coordinate is not a municipality. Use only human-readable metadata already supplied by the source. */
    fun locationLabel(locale: java.util.Locale = java.util.Locale.getDefault()): String? {
        city?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        address?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val value = country?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (value.uppercase(java.util.Locale.ROOT) in java.util.Locale.getISOCountries())
            java.util.Locale("", value.uppercase(java.util.Locale.ROOT)).getDisplayCountry(locale)
        else value.takeIf { it.length > 2 }
    }
    val hasLocation: Boolean get() = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
        latitude in -85.0..85.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
}
data class Hole(val ordinal: Int, val label: String, val par: Int?, val score: Int?)
data class RoundEntry(val roundId: String, val externalId: String, val source: String,
    val playerId: String, val courseId: String?, val date: String, val layout: String?, val tee: String?,
    val total: Int?, val relative: Int?, val status: String, val individual: Boolean,
    val completeHoles: Boolean, val holes: List<Hole>) {
    val played: Boolean get() = status in listOf("FINISHED", "METADATA_ONLY") || (status in listOf("DNF", "IN_PROGRESS") && holes.any { (it.score ?: 0) > 0 })
}
data class History(val players: List<Player> = emptyList(), val courses: List<Course> = emptyList(),
    val entries: List<RoundEntry> = emptyList(), val metrixEvents: List<MetrixEvent> = emptyList()) {
    val activePlayers get() = players.filter { it.active }
    fun selectedEntries(): List<RoundEntry> {
        val ids = activePlayers.map { it.id }.toSet()
        return entries.filter { it.playerId in ids && it.played }
            .sortedWith(compareByDescending<RoundEntry> { it.date }.thenBy { it.roundId }.thenBy { it.playerId })
    }
    fun stats(entries: List<RoundEntry> = selectedEntries()) = HistoryStats(
        entries.mapNotNull { it.courseId }.distinct().size, entries.size,
        entries.map { it.roundId }.distinct().size, aces(entries).size,
        entries.minOfOrNull { it.date }, entries.maxOfOrNull { it.date },
        entries.count { !it.completeHoles }, entries.count { it.courseId == null })
}
data class HistoryStats(val courses: Int, val rounds: Int, val events: Int, val aces: Int,
    val first: String?, val last: String?, val missingHoles: Int, val missingCourses: Int)
data class Ace(val entry: RoundEntry, val hole: Hole)
fun aces(entries: List<RoundEntry>): List<Ace> = entries.filter { it.played && it.individual }
    .flatMap { entry -> entry.holes.filter { it.score == 1 }.map { Ace(entry, it) } }
    .sortedWith(compareByDescending<Ace> { it.entry.date }.thenBy { it.entry.roundId }.thenBy { it.hole.ordinal })

enum class CourseOrder { NAME, MOST_ROUNDS, LAST_PLAYED, FIRST_PLAYED }
data class CourseVisit(val course: Course, val entries: List<RoundEntry>) {
    val first get() = entries.minOf { it.date }
    val last get() = entries.maxOf { it.date }
}
fun History.courseVisits(query: String = "", order: CourseOrder = CourseOrder.LAST_PLAYED): List<CourseVisit> {
    val grouped = selectedEntries().groupBy { it.courseId }
    val visits = courses.filter { it.id in grouped && it.name.contains(query.trim(), ignoreCase = true) }
        .map { CourseVisit(it, grouped.getValue(it.id)) }
    val name = compareBy<CourseVisit> { it.course.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.course.id }
    return visits.sortedWith(when (order) {
        CourseOrder.NAME -> name
        CourseOrder.MOST_ROUNDS -> compareByDescending<CourseVisit> { it.entries.size }.then(name)
        CourseOrder.LAST_PLAYED -> compareByDescending<CourseVisit> { it.last }.then(name)
        CourseOrder.FIRST_PLAYED -> compareBy<CourseVisit> { it.first }.then(name)
    })
}

data class MetrixEvent(val playerId: String, val externalId: String, val listed: Boolean, val outcome: String)
data class Coverage(val listedEvents: Int, val blockedCards: Int, val otherUnavailable: Int, val pending: Int)
fun History.coverage(playerIds: Set<String> = activePlayers.map { it.id }.toSet()): Coverage {
    val selected = metrixEvents.filter { it.playerId in playerIds }
    return Coverage(selected.filter { it.listed }.map { it.externalId }.distinct().size,
        selected.filter { it.outcome in listOf("HISTORY_LIMIT", "HISTORY_METADATA_MISSING") }.map { it.externalId }.distinct().size,
        selected.filter { it.outcome in listOf("ACCESS", "RESPONSE", "UNSUPPORTED") }.map { it.externalId }.distinct().size,
        selected.filter { it.outcome == "PENDING" }.map { it.externalId }.distinct().size)
}
fun History.entriesLastYear(today: java.time.LocalDate = java.time.LocalDate.now(), entries: List<RoundEntry> = selectedEntries()) =
    entries.filter { it.date >= today.minusYears(1).toString() && it.date <= today.toString() }
fun History.physicalCourseCount(entries: List<RoundEntry> = selectedEntries()): Int {
    val ids = entries.mapNotNull { it.courseId }.toSet()
    return courses.filter { it.id in ids }.map { it.physicalId }.distinct().size
}

fun History.unresolvedHistoricalEvents(): Int {
    val active = activePlayers.map { it.id }.toSet()
    val locatedVisits = selectedEntries().filter { it.courseId != null }.map { it.playerId to it.externalId }.toSet()
    return metrixEvents.filter { it.playerId in active && it.outcome in listOf("HISTORY_LIMIT", "HISTORY_METADATA_MISSING") &&
        it.playerId to it.externalId !in locatedVisits }.map { it.externalId }.distinct().size
}
