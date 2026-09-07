package nl.part66l.logbook.domain

import java.time.LocalDate

/**
 * Whether the AML licence itself is in date. Feeds [BasisAvailability.Input.licenceValid] —
 * recency currency is evaluated separately by [RecencyEvaluator].
 *
 * An unset bound imposes no constraint: a licence with no recorded "valid from" or expiry
 * is treated as valid, consistent with how the rest of the app never invents a restriction
 * from absent data.
 */
object LicenceValidity {
    fun isValid(today: LocalDate, validFrom: LocalDate?, expiry: LocalDate?): Boolean {
        if (validFrom != null && today.isBefore(validFrom)) return false
        if (expiry != null && today.isAfter(expiry)) return false
        return true
    }
}
