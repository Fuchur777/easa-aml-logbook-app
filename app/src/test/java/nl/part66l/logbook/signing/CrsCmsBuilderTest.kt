package nl.part66l.logbook.signing

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [CrsCmsBuilder] against a plain [KeyPairGenerator]-generated EC key and a
 * BouncyCastle self-signed test certificate — no AndroidKeyStore involved, so this runs
 * under plain JVM/Robolectric, unlike the real [LocalKeystoreSignerImpl] path it stands in for.
 */
class CrsCmsBuilderTest {

    private fun testKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun selfSignedTestCertificate(keyPair: KeyPair): X509Certificate {
        val subject = X500Principal("CN=Test Signer")
        val now = Date()
        val later = Date(now.time + 365L * 24 * 60 * 60 * 1000)
        val builder = JcaX509v3CertificateBuilder(subject, BigInteger.ONE, now, later, subject, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    /**
     * A detached CMS SignedData carries no content of its own (see [CrsCmsBuilder]'s doc
     * comment) — [SignerInformation.verify] only recomputes and checks the `messageDigest`
     * signed attribute when it is handed the original bytes separately, exactly as a real PDF
     * reader does with the byte range it reads from the file itself. Reconstructing without
     * this would make `verify()` hash zero bytes instead and always report a digest mismatch.
     */
    private fun originalContent() = "the PDF byte range".toByteArray()

    @Test
    fun `build produces a CMS SignedData that verifies against its own certificate`() {
        val keyPair = testKeyPair()
        val certificate = selfSignedTestCertificate(keyPair)
        val content = originalContent()
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        val signature = Signature.getInstance(CrsCmsBuilder.SIGNATURE_ALGORITHM).apply { initSign(keyPair.private) }

        val cms = CrsCmsBuilder.build(digest, signature, certificate)

        val signedData = CMSSignedData(CMSProcessableByteArray(content), cms)
        val signerInfos = signedData.signerInfos.signers.toList()
        assertEquals(1, signerInfos.size)
        val signerInfo = signerInfos.first()

        assertTrue(signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate)))

        val messageDigestAttr = signerInfo.signedAttributes.get(CMSAttributes.messageDigest)
        val storedDigest = (messageDigestAttr.attrValues.getObjectAt(0) as ASN1OctetString).octets
        assertArrayEquals(digest, storedDigest)
    }

    @Test
    fun `build fails verification against a different certificate`() {
        val signingKeyPair = testKeyPair()
        val otherKeyPair = testKeyPair()
        val signingCertificate = selfSignedTestCertificate(signingKeyPair)
        val otherCertificate = selfSignedTestCertificate(otherKeyPair)
        val content = originalContent()
        val digest = MessageDigest.getInstance("SHA-256").digest(content)
        val signature = Signature.getInstance(CrsCmsBuilder.SIGNATURE_ALGORITHM).apply { initSign(signingKeyPair.private) }

        val cms = CrsCmsBuilder.build(digest, signature, signingCertificate)

        val signerInfo = CMSSignedData(CMSProcessableByteArray(content), cms).signerInfos.signers.first()
        assertTrue(!signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().build(otherCertificate)))
    }
}
