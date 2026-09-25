package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogIntegrityState
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCatalogAssetIntegrityVerifierTest {

    @Test
    fun `valid detached checksum and rsa signature are admitted`() {
        val manifest = "{\"catalogRole\":\"IDENTITY_ONLY\"}\n".toByteArray()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(manifest)
                sign()
            }
        val manifestHash = sha256(manifest)
        val integrity =
            """
            {"manifestSha256":"$manifestHash","signatureAlgorithm":"SHA256withRSA","signatureFile":"manifest.sig","signatureSha256":"${sha256(signature)}","signatureState":"VERIFIED"}
            """.trimIndent()

        val result =
            OfflineCatalogAssetIntegrityVerifier.assess(
                manifestBytes = manifest,
                manifestChecksum = "$manifestHash  manifest.json\n",
                integrityJson = integrity,
                signatureBytes = signature,
                publicKeyPem = pem(keyPair.public.encoded)
            )

        assertEquals(OfflineCatalogIntegrityState.VERIFIED, result.manifestHash)
        assertEquals(OfflineCatalogIntegrityState.VERIFIED, result.signature)
        assertTrue(result.basisId.orEmpty().startsWith("bundled-offline-catalog:"))
    }

    @Test
    fun `tampered manifest or detached files fail closed`() {
        val manifest = "{\"catalogRole\":\"IDENTITY_ONLY\"}\n".toByteArray()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(manifest)
                sign()
            }
        val manifestHash = sha256(manifest)
        val integrity =
            """
            {"manifestSha256":"$manifestHash","signatureAlgorithm":"SHA256withRSA","signatureFile":"manifest.sig","signatureSha256":"${sha256(signature)}","signatureState":"VERIFIED"}
            """.trimIndent()

        val result =
            OfflineCatalogAssetIntegrityVerifier.assess(
                manifestBytes = "tampered\n".toByteArray(),
                manifestChecksum = "$manifestHash  manifest.json\n",
                integrityJson = integrity,
                signatureBytes = signature,
                publicKeyPem = pem(keyPair.public.encoded)
            )

        assertNotEquals(OfflineCatalogIntegrityState.VERIFIED, result.manifestHash)
        assertNotEquals(OfflineCatalogIntegrityState.VERIFIED, result.signature)
        assertEquals(null, result.basisId)
    }

    private fun pem(encoded: ByteArray): String =
        "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getEncoder().encodeToString(encoded) +
            "\n-----END PUBLIC KEY-----\n"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
}
