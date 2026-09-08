package nl.part66l.logbook.data

import androidx.room.*
import nl.part66l.logbook.domain.*
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun toLocalDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun fromLocalDate(v: LocalDate?): String? = v?.toString()

    @TypeConverter fun toInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilli()

    @TypeConverter fun toSubcategory(v: String?) = v?.let(Subcategory::valueOf)
    @TypeConverter fun fromSubcategory(v: Subcategory?) = v?.name
    @TypeConverter fun toPropulsion(v: String?) = v?.let(Propulsion::valueOf)
    @TypeConverter fun fromPropulsion(v: Propulsion?) = v?.name
    @TypeConverter fun toStructure(v: String?) = v?.let(Structure::valueOf)
    @TypeConverter fun fromStructure(v: Structure?) = v?.name
    @TypeConverter fun toActivityType(v: String?) = v?.let(ActivityType::valueOf)
    @TypeConverter fun fromActivityType(v: ActivityType?) = v?.name
    @TypeConverter fun toEntryRole(v: String?) = v?.let(EntryRole::valueOf)
    @TypeConverter fun fromEntryRole(v: EntryRole?) = v?.name
    @TypeConverter fun toHelperRole(v: String?) = v?.let(HelperRole::valueOf)
    @TypeConverter fun fromHelperRole(v: HelperRole?) = v?.name
    @TypeConverter fun toBasis(v: String?) = v?.let(CertificationBasis::valueOf)
    @TypeConverter fun fromBasis(v: CertificationBasis?) = v?.name
    @TypeConverter fun toSignatureState(v: String?) = v?.let(SignatureState::valueOf)
    @TypeConverter fun fromSignatureState(v: SignatureState?) = v?.name
    @TypeConverter fun toProvenance(v: String?) = v?.let(Provenance::valueOf)
    @TypeConverter fun fromProvenance(v: Provenance?) = v?.name
    @TypeConverter fun toOwnership(v: String?) = v?.let(OwnershipRelation::valueOf)
    @TypeConverter fun fromOwnership(v: OwnershipRelation?) = v?.name
    @TypeConverter fun toRuleStatus(v: String?) = v?.let(RuleStatus::valueOf)
    @TypeConverter fun fromRuleStatus(v: RuleStatus?) = v?.name
    @TypeConverter fun toDocumentCategory(v: String?) = v?.let(DocumentCategory::valueOf)
    @TypeConverter fun fromDocumentCategory(v: DocumentCategory?) = v?.name
}

/**
 * Schema version 1.
 *
 * Migration rule, non-negotiable: a migration must never alter a row in `crs` that
 * has been signed, nor any attachment a signed CRS manifest references. Take an
 * automated backup before every migration — these are legal records and the user
 * has no server-side copy to fall back on.
 */
@Database(
    entities = [
        AircraftEntity::class,
        AircraftRegistrationEntity::class,
        PersonEntity::class,
        WorkEntryEntity::class,
        WorkEntryFts::class,
        WorkEntryActivityTypeEntity::class,
        WorkSessionEntity::class,
        EntryHelperEntity::class,
        DocumentationRefEntity::class,
        PartUsedEntity::class,
        AttachmentEntity::class,
        CrsEntity::class,
        DeferredItemEntity::class,
        CatalogueTaskEntity::class,
        TaskCompletionEntity::class,
        ProfileEntity::class,
        DocumentEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workEntries(): WorkEntryDao
    abstract fun recency(): RecencyDao
    abstract fun catalogue(): CatalogueDao
    abstract fun crs(): CrsDao
    abstract fun aircraft(): AircraftDao
    abstract fun attachments(): AttachmentDao
    abstract fun search(): SearchDao
    abstract fun workSessions(): WorkSessionDao
    abstract fun entryHelpers(): EntryHelperDao
    abstract fun documentationRefs(): DocumentationRefDao
    abstract fun partsUsed(): PartUsedDao
    abstract fun deferredItems(): DeferredItemDao
    abstract fun taskCompletions(): TaskCompletionDao
    abstract fun profile(): ProfileDao
    abstract fun people(): PersonDao
    abstract fun documents(): DocumentDao
}
