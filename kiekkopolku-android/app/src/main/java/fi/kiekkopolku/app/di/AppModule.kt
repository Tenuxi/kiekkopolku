package fi.kiekkopolku.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fi.kiekkopolku.app.data.*
import fi.kiekkopolku.app.domain.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): KiekkopolkuDatabase =
        Room.databaseBuilder(context, KiekkopolkuDatabase::class.java, "kiekkopolku.db")
            .addMigrations(KiekkopolkuDatabase.MIGRATION_1_2, KiekkopolkuDatabase.MIGRATION_2_3).build()
    @Provides @Singleton fun credentials(@ApplicationContext context: Context): CredentialStore = EncryptedCredentialStore(context)
    @Provides @Singleton fun metrix(): MetrixApi = MetrixHttpApi()
    @Provides @Singleton fun verifier(api: MetrixApi): IntegrationCodeVerifier = MetrixCodeVerifier(api)
    @Provides @Singleton fun repository(db: KiekkopolkuDatabase, credentials: CredentialStore, verifier: IntegrationCodeVerifier, api: MetrixApi) =
        LocalHistoryRepository(db, credentials = credentials, verifier = verifier, metrix = api)
    @Provides fun history(repo: LocalHistoryRepository): HistoryRepository = repo
    @Provides fun players(repo: LocalHistoryRepository): PlayerRepository = repo
    @Provides fun sync(repo: LocalHistoryRepository): SyncRepository = repo
}
