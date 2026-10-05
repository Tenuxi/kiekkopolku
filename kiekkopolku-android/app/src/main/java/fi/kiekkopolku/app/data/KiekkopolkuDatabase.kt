package fi.kiekkopolku.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface HistoryDao {
    @Query("SELECT * FROM players ORDER BY isSample DESC, displayName, id") suspend fun players(): List<PlayerEntity>
    @Query("SELECT * FROM metrix_events") suspend fun metrixEvents(): List<MetrixEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addEvents(items: List<MetrixEventEntity>)
    @Query("UPDATE metrix_events SET listed = 1 WHERE playerId = :playerId AND externalId IN (:ids)")
    suspend fun markListed(playerId: String, ids: List<String>)
    @Query("UPDATE metrix_events SET outcome = :outcome, checkedAt = :checkedAt WHERE playerId = :playerId AND externalId = :externalId")
    suspend fun eventOutcome(playerId: String, externalId: String, outcome: String, checkedAt: Long)
    @Query("SELECT * FROM course_refs") suspend fun courseRefs(): List<CourseSourceRefEntity>
    @Query("SELECT * FROM course_metadata") suspend fun courseMetadata(): List<CourseExternalMetadataEntity>
    @Upsert suspend fun putMetadata(item: CourseExternalMetadataEntity)
    @Query("SELECT * FROM courses") suspend fun courses(): List<CourseEntity>
    @Query("SELECT * FROM rounds") suspend fun rounds(): List<RoundEntity>
    @Query("SELECT * FROM round_players") suspend fun entries(): List<RoundPlayerEntity>
    @Query("SELECT * FROM hole_scores ORDER BY ordinal") suspend fun holes(): List<HoleScoreEntity>
    @Query("SELECT * FROM sync_states") suspend fun syncStates(): List<SyncStateEntity>
    @Insert suspend fun insertPlayer(player: PlayerEntity)
    @Update suspend fun updatePlayer(player: PlayerEntity)
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
    CourseExternalMetadataEntity::class, SyncStateEntity::class, MetrixEventEntity::class], version = 3, exportSchema = true)
abstract class KiekkopolkuDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS metrix_events (playerId TEXT NOT NULL, externalId TEXT NOT NULL, listed INTEGER NOT NULL, outcome TEXT NOT NULL, checkedAt INTEGER, PRIMARY KEY(playerId, externalId), FOREIGN KEY(playerId) REFERENCES players(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO metrix_events (playerId, externalId, listed, outcome, checkedAt) SELECT rp.playerId, r.externalRoundId, 0, 'IMPORTED', r.fetchedAt FROM round_players rp JOIN rounds r ON r.id = rp.roundId WHERE r.source = 'metrix'")
            }
        }

        // Keep the legacy identity column and all parent/child rows intact; no table rebuild or destructive fallback.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE players ADD COLUMN externalPlayerId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE players ADD COLUMN hasIntegrationCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE players SET externalPlayerId = metrixPlayerId WHERE isSample = 0")
                db.execSQL("CREATE UNIQUE INDEX index_players_externalPlayerId ON players (externalPlayerId)")
            }
        }
    }
}
