package fi.kiekkopolku.app.data

import fi.kiekkopolku.app.domain.Course
import kotlinx.serialization.json.*

internal data class CourseUpdate(val course: CourseEntity, val ref: CourseSourceRefEntity, val metadata: CourseExternalMetadataEntity)
/** Course coordinates are Lat/Lng, unlike the X/Y fields of courses_list. */
internal fun parseMetrixCourse(root: JsonObject, existing: CourseEntity, externalId: String, now: Long): CourseUpdate {
    val errors = root["Errors"]
    if (errors != null && (errors !is JsonArray || errors.isNotEmpty())) throw MetrixImportException("COURSE")
    val c = root["course"] as? JsonObject ?: throw MetrixImportException("COURSE")
    if (c.text("ID") != externalId) throw MetrixImportException("COURSE")
    val latitude = c.text("Lat")?.toDoubleOrNull()
    val longitude = c.text("Lng")?.toDoubleOrNull()
    val valid = Course(existing.id, existing.name, null, null, latitude, longitude).hasLocation
    val parent = c.text("ParentID")?.takeIf { it.matches(Regex("[0-9]+")) && it.any { ch -> ch != '0' } && it != externalId }
    val name = c.text("Fullname")?.takeIf { it.isNotBlank() } ?: existing.name
    val updated = existing.copy(name = name.replace("&rarr;", "→").replace("&amp;", "&"),
        city = c.text("City")?.takeIf { it.isNotBlank() } ?: existing.city,
        countryCode = c.text("CountryCode")?.takeIf { it.isNotBlank() } ?: existing.countryCode,
        latitude = if (valid) latitude else existing.latitude,
        longitude = if (valid) longitude else existing.longitude)
    return CourseUpdate(updated, CourseSourceRefEntity("metrix", externalId, existing.id, parent, c.text("Name")),
        CourseExternalMetadataEntity("metrix", externalId, existing.id, city = updated.city, countryCode = updated.countryCode,
            latitude = updated.latitude, longitude = updated.longitude, courseType = c.text("Type"),
            holeCount = (root["baskets"] as? JsonArray)?.size, fetchedAt = now, attribution = "DiscGolfMetrix"))
}
