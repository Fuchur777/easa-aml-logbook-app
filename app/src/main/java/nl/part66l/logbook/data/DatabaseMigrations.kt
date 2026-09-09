package nl.part66l.logbook.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change from version 12 onward, in order. Room throws at startup if the
 * installed database's version needs a migration that isn't listed here — that crash is
 * intentional (see [nl.part66l.logbook.di.DatabaseModule]'s doc comment): it means a schema
 * change shipped without one, which would otherwise silently drop the device's data.
 *
 * Writing one: bump `version` in [AppDatabase], run `:app:compileDebugKotlin` once so Room
 * exports the new `app/schemas/.../<n>.json`, diff it against `<n-1>.json` for the exact
 * column/table/index changes, add a `Migration(n - 1, n) { ... }` below using real `ALTER
 * TABLE`/`CREATE TABLE` SQL (Room does not infer migrations from the entity classes), and add
 * it to [ALL_MIGRATIONS]. Room validates the resulting schema against what the entities expect
 * at startup and throws (loudly, not silently) if this SQL doesn't match — so a mistake here
 * fails safe.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // New table (SigningKeyEntity) — the local signer's own generation history, per §9.3/§9.4.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `signing_key` (
                `id` TEXT NOT NULL,
                `fingerprint` TEXT NOT NULL,
                `certificatePem` TEXT NOT NULL,
                `keyStorage` TEXT NOT NULL,
                `generatedAt` INTEGER NOT NULL,
                `retiredAt` INTEGER,
                `retiredReason` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_12_13)
