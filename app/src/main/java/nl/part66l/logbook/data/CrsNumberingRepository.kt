package nl.part66l.logbook.data

import nl.part66l.logbook.domain.CrsNumberFormat

/**
 * Wires [CrsNumberFormat] to the issued numbers actually in the database.
 *
 * This is the read side only: computing what the next number *would* be, and
 * checking a candidate against what has already been issued. Allocating a
 * number for real — inserting the [CrsEntity] at the moment of signing, and
 * releasing or voiding it if signing then fails (§9.2) — belongs with the
 * signing pipeline once one exists (`CrsSigner` has no implementation yet), not
 * here: this class has no way to react to a signing outcome it can't observe.
 */
class CrsNumberingRepository(private val crsDao: CrsDao) {

    /** The number that would be allocated next, without allocating anything. */
    suspend fun nextNumber(format: CrsNumberFormat, year: Int? = null): String =
        format.nextNumber(crsDao.allNumbers(), year)

    /** The §9.2 collision check required before accepting a changed format. */
    suspend fun collides(format: CrsNumberFormat, candidate: String): Boolean =
        format.collidesWith(candidate, crsDao.allNumbers())
}
