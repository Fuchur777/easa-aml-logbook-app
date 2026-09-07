package nl.part66l.logbook.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.part66l.logbook.data.AircraftDao
import nl.part66l.logbook.data.AppDatabase
import nl.part66l.logbook.data.AttachmentDao
import nl.part66l.logbook.data.CatalogueDao
import nl.part66l.logbook.data.CrsDao
import nl.part66l.logbook.data.DeferredItemDao
import nl.part66l.logbook.data.DocumentationRefDao
import nl.part66l.logbook.data.EntryHelperDao
import nl.part66l.logbook.data.PartUsedDao
import nl.part66l.logbook.data.ProfileDao
import nl.part66l.logbook.data.RecencyDao
import nl.part66l.logbook.data.SearchDao
import nl.part66l.logbook.data.TaskCompletionDao
import nl.part66l.logbook.data.WorkEntryDao
import nl.part66l.logbook.data.WorkSessionDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "part66log.db")
            // No Migration path exists yet — the schema is still moving during initial
            // development and nothing has shipped. Recreating the DB on a schema change
            // is correct for now; this must be replaced with real Migrations (schemas/
            // are already exported for exactly that) before this app ever holds real CRS
            // records that can't be regenerated.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideWorkEntryDao(db: AppDatabase): WorkEntryDao = db.workEntries()
    @Provides fun provideRecencyDao(db: AppDatabase): RecencyDao = db.recency()
    @Provides fun provideCatalogueDao(db: AppDatabase): CatalogueDao = db.catalogue()
    @Provides fun provideCrsDao(db: AppDatabase): CrsDao = db.crs()
    @Provides fun provideAircraftDao(db: AppDatabase): AircraftDao = db.aircraft()
    @Provides fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachments()
    @Provides fun provideSearchDao(db: AppDatabase): SearchDao = db.search()
    @Provides fun provideWorkSessionDao(db: AppDatabase): WorkSessionDao = db.workSessions()
    @Provides fun provideEntryHelperDao(db: AppDatabase): EntryHelperDao = db.entryHelpers()
    @Provides fun provideDocumentationRefDao(db: AppDatabase): DocumentationRefDao = db.documentationRefs()
    @Provides fun providePartUsedDao(db: AppDatabase): PartUsedDao = db.partsUsed()
    @Provides fun provideDeferredItemDao(db: AppDatabase): DeferredItemDao = db.deferredItems()
    @Provides fun provideTaskCompletionDao(db: AppDatabase): TaskCompletionDao = db.taskCompletions()
    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profile()
}
