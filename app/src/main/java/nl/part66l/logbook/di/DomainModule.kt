package nl.part66l.logbook.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.part66l.logbook.domain.RecencyEvaluator

/**
 * Pure domain classes stay free of DI annotations — this is the one place that
 * bridges them into the graph. Dagger resolves every `@Inject constructor`
 * parameter type regardless of a Kotlin default value, so RecencyEvaluator
 * needs an explicit binding even though `RecencyEvaluator()` alone would do.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    fun provideRecencyEvaluator(): RecencyEvaluator = RecencyEvaluator()
}
