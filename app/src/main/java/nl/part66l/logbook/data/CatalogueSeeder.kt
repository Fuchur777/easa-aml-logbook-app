package nl.part66l.logbook.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the seeded catalogue (spec §6: "seeded in the app") into the database on
 * first run. A no-op on every later launch, once `catalogue_task` is non-empty —
 * catalogue updates are a separate, signed-fetch mechanism (§6), not this.
 */
@Singleton
class CatalogueSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogueDao: CatalogueDao,
) {
    suspend fun seedIfNeeded(assetPath: String = DEFAULT_ASSET_PATH) {
        if (catalogueDao.count() > 0) return
        val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        catalogueDao.upsertAll(CatalogueLoader.parse(json))
    }

    companion object {
        const val DEFAULT_ASSET_PATH = "catalogue/appendix-ii-2026.1.json"
    }
}
