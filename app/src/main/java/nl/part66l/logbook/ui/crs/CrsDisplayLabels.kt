package nl.part66l.logbook.ui.crs

import nl.part66l.logbook.domain.SignatureState

val SignatureState.displayLabel: String
    get() = when (this) {
        SignatureState.DRAFT -> "Draft"
        SignatureState.SIGNED_LOCAL -> "Signed (local)"
        SignatureState.SIGNED_QES -> "Signed (qualified)"
        SignatureState.ISSUED_UNSIGNED_PRINT -> "Issued — print and sign by hand"
        SignatureState.TIMESTAMP_PENDING -> "Signed — timestamp pending"
        SignatureState.VOID -> "Void"
    }
