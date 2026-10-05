package fi.kiekkopolku.app.data

/** Internal import model. This is NOT a guessed DiscGolfMetrix JSON/DTO contract. */
data class ImportBatch(val courses: List<CourseEntity>, val refs: List<CourseSourceRefEntity>,
    val rounds: List<RoundEntity>, val entries: List<RoundPlayerEntity>, val holes: List<HoleScoreEntity>)

interface DiscGolfMetrixDataSource {
    // TODO PHASE 3: validate history access, credential ownership, old-data permission and live DTOs.
    suspend fun fetchHistory(playerIds: Set<String>): ImportBatch
}
interface CourseMetadataDataSource {
    // TODO PHASE 5: provider API contract and reuse permission; no scraper or assumed endpoint.
    suspend fun findMetadata(courseId: String): List<CourseExternalMetadataEntity>
}

class SampleDataSource : DiscGolfMetrixDataSource {
    val players = listOf(
        PlayerEntity("sample-1", "sample:1", "Minä (esimerkki)", 0, isSample = true),
        PlayerEntity("sample-2", "sample:2", "Puoliso (esimerkki)", 1, isSample = true),
        PlayerEntity("sample-3", "sample:3", "Lapsi (esimerkki)", 2, isSample = true),
    )
    override suspend fun fetchHistory(playerIds: Set<String>): ImportBatch {
        val courses = listOf(
            CourseEntity("sample-forest", "Metsäpolku", "Tampere", "FI", 61.50, 23.78, true),
            CourseEntity("sample-lake", "Järvenranta", "Ylöjärvi", "FI", 61.55, 23.59, true),
            CourseEntity("sample-park", "Puistokierros", "Nokia", "FI", isSample = true),
        )
        val dates = listOf("2026-05-02", "2026-06-14", "2026-07-20", "2026-08-12", "2026-09-06")
        val rounds = dates.mapIndexed { i, date -> RoundEntity("sample:r$i", "sample", "r$i", date) }
        val entries = mutableListOf<RoundPlayerEntity>()
        val holes = mutableListOf<HoleScoreEntity>()
        rounds.forEachIndexed { index, round ->
            players.filter { it.id in playerIds }.forEachIndexed { _, player ->
                val p = players.indexOf(player)
                if (p == 2 && index < 2) return@forEachIndexed
                val course = courses[index % courses.size]
                val missing = index == 0 && p == 1
                val scores = (1..9).map { hole ->
                    when {
                        hole == 3 && (index == 1 && p == 0 || index == 3 && p == 1 || index == 4 && p == 2) -> 1
                        (hole + index + p) % 4 == 0 -> 4
                        else -> 3
                    }
                }
                entries += RoundPlayerEntity(round.id, player.id, course.id,
                    sourceCourseId = "${course.id}-${index % 2}", layoutName = if (index % 2 == 0) "9 väylää" else "Lyhyt 9",
                    totalScore = scores.sum(), relativeToPar = scores.sum() - 27, holeDataComplete = !missing)
                if (!missing) holes += scores.mapIndexed { h, score -> HoleScoreEntity(round.id, player.id, h, "${h + 1}", 3, score) }
            }
        }
        val used = entries.mapNotNull { it.courseId }.toSet()
        return ImportBatch(courses.filter { it.id in used }, entries.map {
            CourseSourceRefEntity("sample", it.sourceCourseId!!, it.courseId!!, layoutName = it.layoutName)
        }.distinctBy { it.externalId }, rounds.filter { r -> entries.any { it.roundId == r.id } }, entries, holes)
    }
}
