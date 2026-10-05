package fi.kiekkopolku.app.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

data class CourseCandidate(val course: Course, val source: String, val externalId: String)
sealed interface CourseMatch {
    data class Exact(val courseId: String) : CourseMatch
    data class Suggested(val courseId: String) : CourseMatch
    data class Ambiguous(val courseIds: List<String>) : CourseMatch
    data object Unmatched : CourseMatch
}
class CourseMatcher {
    private fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    fun match(input: CourseCandidate, candidates: List<CourseCandidate>): CourseMatch {
        val exact = candidates.filter { it.source == input.source && it.externalId == input.externalId }
            .map { it.course.id }.distinct()
        if (exact.size == 1) return CourseMatch.Exact(exact.single())
        if (exact.size > 1) return CourseMatch.Ambiguous(exact)
        val possible = candidates.filter {
            val a = input.course; val b = it.course
            normalize(a.name) == normalize(b.name) && a.country != null && a.country == b.country &&
                ((a.city != null && b.city != null && normalize(a.city) == normalize(b.city)) || near(a, b))
        }.map { it.course.id }.distinct()
        return when (possible.size) {
            0 -> CourseMatch.Unmatched
            1 -> CourseMatch.Suggested(possible.single())
            else -> CourseMatch.Ambiguous(possible)
        }
    }
    // Conservative candidate generation only: distance never triggers an automatic merge.
    private fun near(a: Course, b: Course): Boolean {
        val lat1 = a.latitude ?: return false; val lat2 = b.latitude ?: return false
        val lon1 = a.longitude ?: return false; val lon2 = b.longitude ?: return false
        if (lat1 !in -90.0..90.0 || lat2 !in -90.0..90.0 || lon1 !in -180.0..180.0 || lon2 !in -180.0..180.0) return false
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 6371000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0))) <= 150
    }
}
