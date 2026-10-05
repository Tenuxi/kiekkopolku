package fi.kiekkopolku.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "players", indices = [Index(value = ["metrixPlayerId"], unique = true)])
data class PlayerEntity(
    @PrimaryKey val id: String, val metrixPlayerId: String, val displayName: String,
    val colorKey: Int = 0, val iconKey: String = "person", val avatarUrl: String? = null,
    val isActive: Boolean = true, val isSample: Boolean = false,
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String, val name: String, val city: String?, val countryCode: String?,
    val latitude: Double? = null, val longitude: Double? = null, val isSample: Boolean = false,
)

@Entity(tableName = "course_refs", primaryKeys = ["source", "externalId"],
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")])
data class CourseSourceRefEntity(val source: String, val externalId: String, val courseId: String,
    val parentExternalId: String? = null, val layoutName: String? = null, val matchMethod: String = "EXACT")

@Entity(tableName = "rounds", indices = [Index(value = ["source", "externalRoundId"], unique = true)])
data class RoundEntity(@PrimaryKey val id: String, val source: String, val externalRoundId: String,
    val playedDate: String, val parentExternalEventId: String? = null, val localTime: String? = null,
    val zoneId: String? = null, val fetchedAt: Long = 0)

@Entity(tableName = "round_players", primaryKeys = ["roundId", "playerId"],
    foreignKeys = [
        ForeignKey(entity = RoundEntity::class, parentColumns = ["id"], childColumns = ["roundId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("playerId"), Index("courseId")])
data class RoundPlayerEntity(val roundId: String, val playerId: String, val courseId: String?,
    val sourceCourseId: String? = null, val layoutName: String? = null, val tee: String? = null,
    val totalScore: Int? = null, val relativeToPar: Int? = null, val status: String = "FINISHED",
    val scoringMode: String = "INDIVIDUAL", val holeDataComplete: Boolean = false)

@Entity(tableName = "hole_scores", primaryKeys = ["roundId", "playerId", "ordinal"],
    foreignKeys = [ForeignKey(entity = RoundPlayerEntity::class, parentColumns = ["roundId", "playerId"],
        childColumns = ["roundId", "playerId"], onDelete = ForeignKey.CASCADE)])
data class HoleScoreEntity(val roundId: String, val playerId: String, val ordinal: Int,
    val label: String, val par: Int?, val score: Int?)

@Entity(tableName = "course_metadata", primaryKeys = ["source", "externalId"],
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")])
data class CourseExternalMetadataEntity(val source: String, val externalId: String, val courseId: String,
    val address: String? = null, val city: String? = null, val countryCode: String? = null,
    val latitude: Double? = null, val longitude: Double? = null, val courseType: String? = null,
    val holeCount: Int? = null, val mapUrl: String? = null, val pageUrl: String? = null,
    val fetchedAt: Long = 0, val attribution: String? = null, val licenseUrl: String? = null)

@Entity(tableName = "sync_states", primaryKeys = ["playerId", "source"],
    foreignKeys = [ForeignKey(entity = PlayerEntity::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE)])
data class SyncStateEntity(val playerId: String, val source: String, val lastAttemptAt: Long?,
    val lastSyncAt: Long?, val status: String, val historyCoverage: String, val errorCode: String? = null)
