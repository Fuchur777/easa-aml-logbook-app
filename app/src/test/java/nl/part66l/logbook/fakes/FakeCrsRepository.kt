package nl.part66l.logbook.fakes

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.data.CrsRepository
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.SignatureState

data class GenerateCrsCall(
    val entryId: String,
    val limitations: String?,
    val maintenanceIncomplete: Boolean,
    val deferredItemDescriptions: List<String> = emptyList(),
)

class FakeCrsRepository(initial: List<CrsEntity> = emptyList()) : CrsRepository {
    private val entries = MutableStateFlow(initial)
    val generateCalls = mutableListOf<GenerateCrsCall>()

    override fun forEntry(entryId: String): Flow<List<CrsEntity>> =
        entries.map { list -> list.filter { it.entryId == entryId } }

    override suspend fun generateUnsigned(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String>,
    ): CrsEntity? {
        generateCalls += GenerateCrsCall(entryId, limitations, maintenanceIncomplete, deferredItemDescriptions)
        val sequence = entries.value.count { it.entryId == entryId } + 1
        val crs = CrsEntity(
            id = UUID.randomUUID().toString(),
            entryId = entryId,
            number = "TEST-$sequence",
            numberNormalised = "TEST$sequence",
            sequence = sequence,
            year = 2026,
            basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
            statementVersion = "test",
            completionDate = LocalDate.now(),
            limitations = limitations,
            maintenanceIncomplete = maintenanceIncomplete,
            signatureState = SignatureState.ISSUED_UNSIGNED_PRINT,
            snapshotJson = "{}",
            pdfLocalPath = "/fake/$sequence.pdf",
            pdfSha256 = "fake-sha",
        )
        entries.value = entries.value + crs
        return crs
    }

    override suspend fun setSignedPhoto(id: String, path: String?) {
        entries.value = entries.value.map { if (it.id == id) it.copy(signedPhotoLocalPath = path) else it }
    }
}
