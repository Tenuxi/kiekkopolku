package fi.kiekkopolku.app.domain

data class Player(val id: String, val metrixId: String?, val name: String, val color: Int,
    val active: Boolean, val sample: Boolean, val lastSyncAt: Long?, val hasIntegrationCode: Boolean = false, val syncStatus: String? = null, val syncError: String? = null)
data class Course(val id: String, val name: String, val city: String?, val country: String?,
    val latitude: Double?, val longitude: Double?)
data class Hole(val ordinal: Int, val label: String, val par: Int?, val score: Int?)
data class RoundEntry(val roundId: String, val externalId: String, val source: String,
    val playerId: String, val courseId: String?, val date: String, val layout: String?, val tee: String?,
    val total: Int?, val relative: Int?, val status: String, val individual: Boolean,
    val completeHoles: Boolean, val holes: List<Hole>) {
    val played: Boolean get() = status == "FINISHED" || (status in listOf("DNF", "IN_PROGRESS") && holes.any { (it.score ?: 0) > 0 })
}
data class History(val players: List<Player> = emptyList(), val courses: List<Course> = emptyList(),
    val entries: List<RoundEntry> = emptyList()) {
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
fun History.courseVisits(query: String = "", order: CourseOrder = CourseOrder.NAME): List<CourseVisit> {
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
