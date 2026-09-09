package nl.part66l.logbook.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.part66l.logbook.data.ALL_MIGRATIONS
import nl.part66l.logbook.data.AircraftDao
import nl.part66l.logbook.data.AppDatabase
import nl.part66l.logbook.data.AttachmentDao
import nl.part66l.logbook.data.CatalogueDao
import nl.part66l.logbook.data.CrsDao
import nl.part66l.logbook.data.DeferredItemDao
import nl.part66l.logbook.data.DocumentDao
import nl.part66l.logbook.data.DocumentationRefDao
import nl.part66l.logbook.data.EntryHelperDao
import nl.part66l.logbook.data.PartUsedDao
import nl.part66l.logbook.data.PersonDao
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
            // Versions 1-11 predate real device data worth keeping (every schema bump up to
            // and including 12 wiped the DB anyway, back when this whole app was still a
            // moving target) — destructive fallback is fine for anyone still on one of those.
            // From 12 onward the device may hold real signed CRS records, deferred items and
            // a locally-generated signing key none of which can be regenerated, so every
            // future schema change MUST ship its own Migration in DatabaseMigrations.kt and
            // be added to ALL_MIGRATIONS below — never widen this destructive-from list to
            // include 12 or later as a shortcut.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            .addMigrations(*ALL_MIGRATIONS)
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
    @Provides fun providePersonDao(db: AppDatabase): PersonDao = db.people()
    @Provides fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documents()
}
