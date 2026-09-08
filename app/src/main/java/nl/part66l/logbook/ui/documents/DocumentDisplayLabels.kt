package nl.part66l.logbook.ui.documents

import nl.part66l.logbook.domain.DocumentCategory

val DocumentCategory.displayLabel: String
    get() = when (this) {
        DocumentCategory.MANUAL -> "Manual"
        DocumentCategory.TCDS -> "TCDS"
        DocumentCategory.AD -> "AD"
        DocumentCategory.SD -> "SD"
        DocumentCategory.REGULATION -> "Regulation"
    }
