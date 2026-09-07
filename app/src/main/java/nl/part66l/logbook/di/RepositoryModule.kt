package nl.part66l.logbook.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftRepositoryImpl
import nl.part66l.logbook.data.CrsNumberingRepository
import nl.part66l.logbook.data.CrsNumberingRepositoryImpl
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.data.PersonRepositoryImpl
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.data.ProfileRepositoryImpl
import nl.part66l.logbook.data.RecencyRepository
import nl.part66l.logbook.data.RecencyRepositoryImpl
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.data.SettingsRepositoryImpl
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.data.WorkEntryRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindRecencyRepository(impl: RecencyRepositoryImpl): RecencyRepository

    @Binds
    abstract fun bindCrsNumberingRepository(impl: CrsNumberingRepositoryImpl): CrsNumberingRepository

    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    abstract fun bindAircraftRepository(impl: AircraftRepositoryImpl): AircraftRepository

    @Binds
    abstract fun bindWorkEntryRepository(impl: WorkEntryRepositoryImpl): WorkEntryRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindPersonRepository(impl: PersonRepositoryImpl): PersonRepository
}
