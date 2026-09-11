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

/**
 * `work_entry.role` (EntryRole) is dropped — it fed nothing (not the recency evaluator, not
 * the CRS PDF; only the list row's own display, per the review that removed it) — and
 * `work_entry.explanation` is added, the optional multiline "explanation of work done" that
 * now sits alongside the short `description` header line. SQLite's `ALTER TABLE ... DROP
 * COLUMN` isn't reliable across every Android SQLite build in the field, so this recreates
 * the table instead, the same pattern any column drop here needs.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `work_entry_new` (
                `id` TEXT NOT NULL,
                `aircraftId` TEXT,
                `description` TEXT NOT NULL,
                `explanation` TEXT,
                `supervisedAnother` INTEGER NOT NULL,
                `airframeHoursAtWork` REAL,
                `launchesAtWork` INTEGER,
                `workorderIssuerName` TEXT,
                `workorderDate` TEXT,
                `workorderRequestedWork` TEXT,
                `workorderReference` TEXT,
                `workorderReferenceNormalised` TEXT,
                `workorderAttachmentId` TEXT,
                `annualInspection` INTEGER NOT NULL,
                `concurrentWithArc` INTEGER NOT NULL,
                `provenance` TEXT NOT NULL,
                `externalId` TEXT,
                `daysWorkedOverride` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`aircraftId`) REFERENCES `aircraft`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `work_entry_new` (
                `id`, `aircraftId`, `description`, `explanation`, `supervisedAnother`,
                `airframeHoursAtWork`, `launchesAtWork`, `workorderIssuerName`, `workorderDate`,
                `workorderRequestedWork`, `workorderReference`, `workorderReferenceNormalised`,
                `workorderAttachmentId`, `annualInspection`, `concurrentWithArc`, `provenance`,
                `externalId`, `daysWorkedOverride`, `createdAt`, `updatedAt`
            )
            SELECT
                `id`, `aircraftId`, `description`, NULL, `supervisedAnother`,
                `airframeHoursAtWork`, `launchesAtWork`, `workorderIssuerName`, `workorderDate`,
                `workorderRequestedWork`, `workorderReference`, `workorderReferenceNormalised`,
                `workorderAttachmentId`, `annualInspection`, `concurrentWithArc`, `provenance`,
                `externalId`, `daysWorkedOverride`, `createdAt`, `updatedAt`
            FROM `work_entry`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `work_entry`")
        db.execSQL("ALTER TABLE `work_entry_new` RENAME TO `work_entry`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entry_aircraftId` ON `work_entry` (`aircraftId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entry_provenance` ON `work_entry` (`provenance`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entry_workorderReferenceNormalised` ON `work_entry` (`workorderReferenceNormalised`)")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_12_13, MIGRATION_13_14)
