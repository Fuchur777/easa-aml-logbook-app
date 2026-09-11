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

/**
 * Google Drive backup (§10) — v1, manual "Sync now" only. Three plain additive columns, so
 * unlike the two migrations above this is a real `ALTER TABLE ... ADD COLUMN`, not a table
 * rebuild: SQLite has always supported adding a nullable column without one (it's only
 * *dropping* a column that historically needed the recreate-table dance).
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `aircraft` ADD COLUMN `driveFolderId` TEXT")
        db.execSQL("ALTER TABLE `work_entry` ADD COLUMN `driveFolderId` TEXT")
        db.execSQL("ALTER TABLE `work_entry` ADD COLUMN `drivePhotosFolderId` TEXT")
    }
}

/** Google Drive backup, Documents sync (§10 v2) — the same additive `driveFileId` column CRS and attachments already have. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `document` ADD COLUMN `driveFileId` TEXT")
    }
}

/**
 * Links a documentation reference back to the directory entry it was picked from, so "documents
 * used on this aircraft" is an exact join rather than a match on reference text that a later
 * rename would break. Nullable by design: a reference typed by hand belongs to no directory entry,
 * and rows written before this column existed keep matching on text.
 *
 * The snapshot columns are untouched — what a past entry says it used stays frozen.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documentation_ref` ADD COLUMN `documentId` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documentation_ref_documentId` ON `documentation_ref` (`documentId`)")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
