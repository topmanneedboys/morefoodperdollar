package com.valuepilot.app

import com.valuepilot.core.OfflineCatalogIntegrityAssessment
import com.valuepilot.core.OfflineCatalogIntegrityState
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject

/**
 * Verifies the detached integrity files bundled alongside an offline catalog.
 *
 * This class performs only byte-level hashing/signature verification. It owns
 * no filesystem, network, clock, product, price, availability or ranking
 * policy. A malformed or tampered asset becomes a failed assessment so the
 * existing shared-core admission evaluator can fail closed.
 */
object OfflineCatalogAssetIntegrityVerifier {

    fun assess(
        manifestBytes: ByteArray,
        manifestChecksum: String,
        integrityJson: String,
        signatureBytes: ByteArray,
        publicKeyPem: String
    ): OfflineCatalogIntegrityAssessment {
        val manifestHash = sha256(manifestBytes)
        val manifestVerified =
            runCatching {
                val integrity = JSONObject(integrityJson)
                manifestChecksum == "$manifestHash  manifest.json\n" &&
                    integrity.getString("manifestSha256") == manifestHash
            }.getOrDefault(false)

        val signatureVerified =
            if (manifestVerified) {
                runCatching {
                    val integrity = JSONObject(integrityJson)
                    require(integrity.getString("signatureState") == "VERIFIED")
                    require(integrity.getString("signatureAlgorithm") == "SHA256withRSA")
                    require(integrity.getString("signatureFile") == "manifest.sig")
                    require(integrity.getString("signatureSha256") == sha256(signatureBytes))
                    require(signatureBytes.isNotEmpty())

                    val publicKey =
                        KeyFactory.getInstance("RSA")
                            .generatePublic(
                                X509EncodedKeySpec(
                                    decodePem(publicKeyPem)
                                )
                            )
                    Signature.getInstance("SHA256withRSA").run {
                        initVerify(publicKey)
                        update(manifestBytes)
                        verify(signatureBytes)
                    }
                }.getOrDefault(false)
            } else {
                false
            }

        return OfflineCatalogIntegrityAssessment(
            manifestHash =
                if (manifestVerified) {
                    OfflineCatalogIntegrityState.VERIFIED
                } else {
                    OfflineCatalogIntegrityState.FAILED
                },
            signature =
                if (signatureVerified) {
                    OfflineCatalogIntegrityState.VERIFIED
                } else {
                    OfflineCatalogIntegrityState.FAILED
                },
            basisId =
                if (manifestVerified && signatureVerified) {
                    "bundled-offline-catalog:$manifestHash"
                } else {
                    null
                }
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    /** Small platform-independent decoder for a SubjectPublicKeyInfo PEM body. */
    private fun decodePem(pem: String): ByteArray {
        val body =
            pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .filterNot(Char::isWhitespace)
        require(body.isNotEmpty())

        val output = ByteArray(body.length * 3 / 4 + 3)
        var outputCount = 0
        var accumulator = 0
        var bits = 0
        body.forEach { character ->
            if (character == '=') return@forEach
            val value = base64Value(character)
            accumulator = (accumulator shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[outputCount++] = (accumulator shr bits).toByte()
                accumulator = accumulator and ((1 shl bits) - 1)
            }
        }
        require(bits < 6)
        return output.copyOf(outputCount)
    }

    private fun base64Value(character: Char): Int =
        when (character) {
            in 'A'..'Z' -> character - 'A'
            in 'a'..'z' -> character - 'a' + 26
            in '0'..'9' -> character - '0' + 52
            '+' -> 62
            '/' -> 63
            else -> throw IllegalArgumentException("Invalid base64 public key")
        }
}
