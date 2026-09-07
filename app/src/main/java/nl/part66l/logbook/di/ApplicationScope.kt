package nl.part66l.logbook.di

import javax.inject.Qualifier

/** The process-lifetime [kotlinx.coroutines.CoroutineScope], for work with no natural screen owner (e.g. first-run seeding). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
