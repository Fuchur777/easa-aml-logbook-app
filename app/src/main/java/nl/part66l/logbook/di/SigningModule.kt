package nl.part66l.logbook.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import nl.part66l.logbook.signing.LocalKeystoreSigner
import nl.part66l.logbook.signing.LocalKeystoreSignerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class SigningModule {

    @Binds
    abstract fun bindLocalKeystoreSigner(impl: LocalKeystoreSignerImpl): LocalKeystoreSigner
}

@Module
@InstallIn(SingletonComponent::class)
object KeyStoreModule {

    @Provides
    fun provideAndroidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

/**
 * [nl.part66l.logbook.ui.crs.CrsScreen] needs the concrete [LocalKeystoreSignerImpl] (not just
 * the [LocalKeystoreSigner] interface [SigningModule] binds) to build a
 * [nl.part66l.logbook.signing.BiometricSigningGate] — that gate needs Keystore-lifecycle methods
 * ([LocalKeystoreSignerImpl.newSignatureForAuthorization]) that aren't part of the general
 * signer seam. Fetched via this entry point rather than added to [nl.part66l.logbook.ui.crs.CrsViewModel]'s
 * constructor, so unit tests that build a `CrsViewModel` directly don't need a working
 * AndroidKeyStore (unavailable outside Robolectric/a real device) just to satisfy the type.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalKeystoreSignerEntryPoint {
    fun localKeystoreSigner(): LocalKeystoreSignerImpl
}
