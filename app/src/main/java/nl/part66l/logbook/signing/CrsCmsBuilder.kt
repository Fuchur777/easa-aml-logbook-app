package nl.part66l.logbook.signing

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSAbsentContent
import org.bouncycastle.cms.CMSAttributeTableGenerator
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

/**
 * Builds a detached CMS/PKCS#7 SignedData over an already-computed digest, signing with
 * an already-unlocked [Signature] rather than a raw [java.security.PrivateKey] — Android
 * Keystore never exposes the latter for a per-use biometric-gated key, so the standard
 * `JcaContentSignerBuilder(privateKey)` path isn't available here.
 *
 * [CMSSignedDataGenerator] normally hashes real content it's handed. There is none here —
 * [CrsSigner.sign] only ever receives a digest, never the original bytes — so [digest] is
 * injected directly as the CMS `messageDigest` signed attribute (see
 * [PrecomputedDigestAttributeTableGenerator]), with [CMSAbsentContent] standing in for the
 * content BC would otherwise want to hash itself.
 *
 * Kept free of any AndroidKeyStore dependency so it can be exercised in a plain JVM/Robolectric
 * test against an ordinary [java.security.KeyPairGenerator]-generated EC key.
 */
object CrsCmsBuilder {

    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    fun build(digest: ByteArray, signature: Signature, certificate: X509Certificate): ByteArray {
        val algorithmIdentifier = DefaultSignatureAlgorithmIdentifierFinder().find(SIGNATURE_ALGORITHM)
        val contentSigner = AuthorizedSignatureContentSigner(signature, algorithmIdentifier)
        val digestCalculatorProvider = JcaDigestCalculatorProviderBuilder().build()
        val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(digestCalculatorProvider)
            .setSignedAttributeGenerator(PrecomputedDigestAttributeTableGenerator(digest))
            .build(contentSigner, certificate)

        val generator = CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(signerInfoGenerator)
        generator.addCertificates(JcaCertStore(listOf(certificate)))
        val signedData = generator.generate(CMSAbsentContent(), false)
        return signedData.encoded
    }

    /** Wraps an already-unlocked [Signature] as a BouncyCastle [ContentSigner]: buffers the DER-encoded signed attributes BC feeds it, then signs them in one call when asked. */
    private class AuthorizedSignatureContentSigner(
        private val signature: Signature,
        private val algorithmId: AlgorithmIdentifier,
    ) : ContentSigner {
        private val buffer = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algorithmId
        override fun getOutputStream(): OutputStream = buffer
        override fun getSignature(): ByteArray {
            signature.update(buffer.toByteArray())
            return signature.sign()
        }
    }

    /** Supplies [digest] directly as the CMS `messageDigest` signed attribute — see [build]'s doc comment for why. */
    private class PrecomputedDigestAttributeTableGenerator(
        private val digest: ByteArray,
    ) : CMSAttributeTableGenerator {
        override fun getAttributes(parameters: MutableMap<*, *>): AttributeTable {
            val contentType = parameters[CMSAttributeTableGenerator.CONTENT_TYPE] as ASN1ObjectIdentifier
            val vector = ASN1EncodableVector()
            vector.add(Attribute(CMSAttributes.contentType, DERSet(contentType)))
            vector.add(Attribute(CMSAttributes.messageDigest, DERSet(DEROctetString(digest))))
            vector.add(Attribute(CMSAttributes.signingTime, DERSet(Time(Date()))))
            return AttributeTable(vector)
        }
    }
}
