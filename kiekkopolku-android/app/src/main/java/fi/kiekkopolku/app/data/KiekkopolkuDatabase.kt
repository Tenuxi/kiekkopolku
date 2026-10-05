package fi.kiekkopolku.app.data

import androidx.room.*

@Dao
interface HistoryDao {
    @Query("SELECT * FROM players ORDER BY isSample DESC, displayName, id") suspend fun players(): List<PlayerEntity>
    @Query("SELECT * FROM courses") suspend fun courses(): List<CourseEntity>
    @Query("SELECT * FROM rounds") suspend fun rounds(): List<RoundEntity>
    @Query("SELECT * FROM round_players") suspend fun entries(): List<RoundPlayerEntity>
    @Query("SELECT * FROM hole_scores ORDER BY ordinal") suspend fun holes(): List<HoleScoreEntity>
    @Query("SELECT * FROM sync_states") suspend fun syncStates(): List<SyncStateEntity>
    @Insert suspend fun insertPlayer(player: PlayerEntity)
    @Upsert suspend fun putCourses(items: List<CourseEntity>)
    @Upsert suspend fun putRefs(items: List<CourseSourceRefEntity>)
    @Upsert suspend fun putRounds(items: List<RoundEntity>)
    @Upsert suspend fun putEntries(items: List<RoundPlayerEntity>)
    @Upsert suspend fun putHoles(items: List<HoleScoreEntity>)
    @Upsert suspend fun putSync(item: SyncStateEntity)
    @Query("UPDATE players SET isActive = :active WHERE id = :id") suspend fun selectPlayer(id: String, active: Boolean)
    @Query("DELETE FROM players WHERE id = :id") suspend fun deletePlayer(id: String)
    @Query("DELETE FROM players WHERE isSample = 1") suspend fun deleteSamplePlayers()
    @Query("DELETE FROM rounds WHERE id NOT IN (SELECT roundId FROM round_players)") suspend fun deleteOrphanRounds()
    @Query("DELETE FROM courses WHERE isSample = 1 AND id NOT IN (SELECT courseId FROM round_players WHERE courseId IS NOT NULL)")
    suspend fun deleteOrphanSampleCourses()
    @Query("DELETE FROM hole_scores WHERE roundId = :roundId AND playerId = :playerId")
    suspend fun deleteHoles(roundId: String, playerId: String)
}

@Database(entities = [PlayerEntity::class, CourseEntity::class, CourseSourceRefEntity::class,
    RoundEntity::class, RoundPlayerEntity::class, HoleScoreEntity::class,
    CourseExternalMetadataEntity::class, SyncStateEntity::class], version = 1, exportSchema = true)
abstract class KiekkopolkuDatabase : RoomDatabase() { abstract fun history(): HistoryDao }
