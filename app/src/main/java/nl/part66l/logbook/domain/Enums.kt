package nl.part66l.logbook.domain

/**
 * Licence subcategories in scope. The enumeration matches the one prescribed in
 * AMC 66.A.20(b)(2) for the "subcategory used" field of the experience record.
 */
enum class Subcategory { L1, L1C, L2, L2C }

/**
 * Propulsion and structure together determine which subcategory's privileges are
 * exercised on an aircraft, and therefore which recency column the work credits.
 *
 * AMC 66.A.20(b)(2) frames similarity more finely — propulsion, flight controls,
 * avionic systems and structure. For sailplanes the subcategory captures what
 * matters, so similarity is implemented as "same subcategory" and the finer
 * attributes are deliberately not modelled.
 */
enum class Propulsion { UNPOWERED, POWERED_SAILPLANE, ELA1 }

enum class Structure { COMPOSITE, METAL, WOOD_AND_FABRIC, MIXED }

/**
 * Activities considered relevant for maintenance experience, enumerated in
 * AMC 66.A.20(b)(2) paragraph 2. This is a closed vocabulary — do not extend it
 * without a regulatory basis.
 */
enum class ActivityType {
    SERVICING,
    INSPECTION,
    OPERATIONAL_AND_FUNCTIONAL_TESTING,
    TROUBLESHOOTING,
    REPAIRING,
    MODIFYING,
    CHANGING_COMPONENT,
    SUPERVISING,
    RELEASING_TO_SERVICE,
}

/**
 * What the user did on this entry and how it was released. Determines which
 * recency counters the entry can feed. Distinct from [ActivityType], which
 * describes the nature of the work.
 */
enum class EntryRole {
    CERTIFIED_BY_ME_IN_APP,
    CERTIFIED_BY_ME_ON_PAPER,
    PERFORMED_BY_ME_RELEASED_BY_OTHER,
    PERFORMED_UNDER_SUPERVISION,
    SUPERVISED_ANOTHER,
    ASSISTED_ON_ARC,
    NO_RELEASE,
}

/** Helper roles. Basis: ML.A.801(d) — assistance under direct and continuous control. */
enum class HelperRole { ASSISTED, INDEPENDENT_INSPECTION }

/**
 * Which provision the release is issued under. Present from v1 even though only
 * the first value is implemented; retrofitting this is a refactor of the CRS core.
 */
enum class CertificationBasis {
    ML_A_801_B2_INDEPENDENT,
    ML_A_803_PILOT_OWNER,
}

enum class SignatureState {
    DRAFT,
    SIGNED_LOCAL,
    SIGNED_QES,
    ISSUED_UNSIGNED_PRINT,
    TIMESTAMP_PENDING,
    VOID,
}

/**
 * IMPORTED rows are user declarations from a prior logbook. They may never be
 * signed, may never produce a CRS, and are rendered separately in every export.
 */
enum class Provenance { NATIVE, IMPORTED }

/** Relationship of the user to the aircraft. Required for pilot-owner mode (ML.A.803(a)(2)). */
enum class OwnershipRelation { OWNER, JOINT_OWNER, DESIGNATED_MEMBER_OF_OWNING_ENTITY, NONE }

/** Lifecycle of a rule or catalogue entry, so proposed amendments can ship before adoption. */
enum class RuleStatus { IN_FORCE, PROPOSED, SUPERSEDED }

/**
 * The recency routes of 66.A.20(b)(2) and its AMC, plus the initial-certification
 * grace period. Route C originates in NPA 2025-12 and is PROPOSED until the
 * corresponding ED Decision is published.
 */
enum class RecencyRoute { DAYS, TASKS, ANNUAL_INSPECTIONS, INITIAL_CERTIFICATION_GRACE_PERIOD }
