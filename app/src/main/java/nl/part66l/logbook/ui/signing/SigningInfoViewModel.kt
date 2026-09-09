package nl.part66l.logbook.ui.signing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.data.SigningKeyDao
import nl.part66l.logbook.data.SigningKeyEntity
import nl.part66l.logbook.pdf.SigningInfoPdfRenderer
import nl.part66l.logbook.pdf.SigningInfoRenderData
import nl.part66l.logbook.signing.LocalKeystoreSigner

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

data class SigningInfoState(
    val loading: Boolean = true,
    val licenceHolderName: String = "",
    val licenceNumber: String = "",
    val issuingAuthority: String = "",
    val method: String = "",
    val keyStorage: String = "",
    val authentication: String = "",
    val certificateSubject: String = "",
    val certificatePem: String = "",
    val fingerprint: String = "",
    val exporting: Boolean = false,
)

/**
 * Surfaces the local signer's certificate/fingerprint (§9.3) so it can be read off the
 * device and exported as a document (§9.4) — see [SigningInfoPdfRenderer]'s doc comment for
 * why this needs to exist at all. Only ever touches [LocalKeystoreSigner]'s non-signing
 * methods (describe/certificatePem/fingerprint), none of which need a biometric prompt, so
 * unlike [nl.part66l.logbook.ui.crs.CrsViewModel] this needs no Activity at the Compose layer.
 */
@HiltViewModel
class SigningInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localKeystoreSigner: LocalKeystoreSigner,
    private val profileRepository: ProfileRepository,
    signingKeyDao: SigningKeyDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SigningInfoState())
    val state: StateFlow<SigningInfoState> = _state.asStateFlow()

    /** Every key generation this device has ever had, newest first — current one has a null [SigningKeyEntity.retiredAt]. */
    val keyHistory: StateFlow<List<SigningKeyEntity>> = signingKeyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val profile = profileRepository.get()
            // Key generation (on first-ever call) touches real hardware and can take a
            // noticeable moment — keep it off the main thread.
            val (description, pem, fingerprint) = withContext(Dispatchers.IO) {
                Triple(localKeystoreSigner.describe(), localKeystoreSigner.certificatePem(), localKeystoreSigner.fingerprint())
            }
            _state.value = SigningInfoState(
                loading = false,
                licenceHolderName = profile?.name.orEmpty(),
                licenceNumber = profile?.licenceNumber.orEmpty(),
                issuingAuthority = profile?.issuingAuthority.orEmpty(),
                method = description.method,
                keyStorage = description.keyStorage,
                authentication = description.authentication,
                certificateSubject = description.certificateSubject.orEmpty(),
                certificatePem = pem,
                fingerprint = fingerprint,
            )
        }
    }

    /** Renders the current state to a one-page PDF in app-private storage and hands the path to [onReady] once done. */
    fun exportPdf(onReady: (String) -> Unit) {
        val current = _state.value
        if (current.loading || current.exporting) return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            val pdfFile = withContext(Dispatchers.IO) {
                val renderData = SigningInfoRenderData(
                    licenceHolderName = current.licenceHolderName,
                    licenceNumber = current.licenceNumber,
                    issuingAuthority = current.issuingAuthority,
                    generatedDate = LocalDate.now().format(DATE_FORMAT),
                    method = current.method,
                    keyStorage = current.keyStorage,
                    authentication = current.authentication,
                    certificateSubject = current.certificateSubject,
                    fingerprint = current.fingerprint,
                    certificatePem = current.certificatePem,
                )
                val document = PDDocument()
                try {
                    SigningInfoPdfRenderer.render(document, renderData, context)
                    val destDir = File(context.filesDir, "signing").apply { mkdirs() }
                    File(destDir, "system-description-${UUID.randomUUID()}.pdf").also { document.save(it) }
                } finally {
                    document.close()
                }
            }
            _state.update { it.copy(exporting = false) }
            onReady(pdfFile.absolutePath)
        }
    }
}
