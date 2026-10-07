package com.valuepilot.app

import android.content.Context
import android.util.Base64
import com.valuepilot.core.ShoppingRequestDetailsCodec
import java.nio.charset.StandardCharsets

/**
 * Validates the small durable envelope used to remember the last local Home session.
 *
 * This is only local UX state. Request-details bytes remain opaque and are validated by the
 * existing shared-core codec/session when the model is restored; no product, price, evidence,
 * ranking or provider fact is created here.
 */
object PracticalShoppingHomeSessionStoreCodec {

    private val MAX_PERSISTED_QUERY_LENGTH =
        LocalSamplePracticalShoppingDemo.MAX_QUERY_CHARACTERS + 1

    fun decode(
        query: String?,
        wasSubmitted: Boolean,
        chickenChoice: String?,
        extraStopMinimumSavingsChoice: String?,
        requestDetailsLifecycleState: ByteArray?,
        privateIdentities: String? = null
    ): PracticalShoppingHomeSession.Snapshot? {
        val safeQuery = query ?: return null
        if (safeQuery.length > MAX_PERSISTED_QUERY_LENGTH) return null

        val safeChickenChoice =
            chickenChoice?.let { value ->
                runCatching {
                    LocalSamplePracticalShoppingDemo.ChickenChoice.valueOf(value)
                }.getOrNull()
            }
        val safeDetails =
            requestDetailsLifecycleState
                ?.takeIf { it.size <= ShoppingRequestDetailsCodec.maximumEncodedBytes }
                ?.clone()

        return PracticalShoppingHomeSession.Snapshot(
            query = safeQuery,
            wasSubmitted = wasSubmitted,
            chickenChoice = safeChickenChoice,
            extraStopMinimumSavingsChoice =
                PracticalShoppingHomePreferenceCodec.decode(extraStopMinimumSavingsChoice),
            requestDetailsLifecycleState = safeDetails,
            privateIdentities = decodePrivateIdentities(privateIdentities)
        )
    }

    /** Deterministic, bounded local encoding for the identity-only private list. */
    fun encodePrivateIdentities(
        values: List<PracticalShoppingPrivateIdentity>
    ): String? {
        val unique = LinkedHashMap<String, PracticalShoppingPrivateIdentity>()
        values.take(PracticalShoppingPrivateIdentity.MAX_COUNT).forEach { value ->
            if (!unique.containsKey(value.identityKey)) unique[value.identityKey] = value
        }
        if (unique.isEmpty()) return null
        return buildString {
            append(PRIVATE_IDENTITIES_VERSION)
            append(':')
            unique.values.joinTo(this, separator = ";") { value ->
                listOf(
                    value.identityKey,
                    value.displayName,
                    value.brand,
                    value.sourceLabel,
                    value.packageQuantityLabel
                ).joinToString(",", transform = ::encodeField)
            }
        }
    }

    /** Invalid or oversized private-list storage fails closed without affecting Home planner data. */
    fun decodePrivateIdentities(encoded: String?): List<PracticalShoppingPrivateIdentity> {
        if (encoded.isNullOrBlank() || encoded.length > MAX_PRIVATE_IDENTITIES_ENCODED_LENGTH) {
            return emptyList()
        }
        val prefix = "$PRIVATE_IDENTITIES_VERSION:"
        if (!encoded.startsWith(prefix)) return emptyList()
        val payload = encoded.removePrefix(prefix)
        if (payload.isEmpty()) return emptyList()
        val entries = payload.split(';')
        if (entries.isEmpty() || entries.size > PracticalShoppingPrivateIdentity.MAX_COUNT) {
            return emptyList()
        }
        val unique = LinkedHashMap<String, PracticalShoppingPrivateIdentity>()
        return try {
            entries.forEach { entry ->
                val fields = entry.split(',')
                if (fields.size != 5) return emptyList()
                val identityKey = decodeField(fields[0], PracticalShoppingPrivateIdentity.MAX_IDENTITY_KEY_LENGTH, true)
                    ?: return emptyList()
                val displayName = decodeField(fields[1], PracticalShoppingPrivateIdentity.MAX_DISPLAY_NAME_LENGTH, true)
                    ?: return emptyList()
                val brand = decodeOptionalField(fields[2], PracticalShoppingPrivateIdentity.MAX_BRAND_LENGTH)
                    ?: if (fields[2].isEmpty()) null else return emptyList()
                val sourceLabel = decodeOptionalField(fields[3], PracticalShoppingPrivateIdentity.MAX_SOURCE_LABEL_LENGTH)
                    ?: if (fields[3].isEmpty()) null else return emptyList()
                val quantity = decodeOptionalField(fields[4], PracticalShoppingPrivateIdentity.MAX_PACKAGE_QUANTITY_LENGTH)
                    ?: if (fields[4].isEmpty()) null else return emptyList()
                val identity = PracticalShoppingPrivateIdentity(
                    identityKey = identityKey,
                    displayName = displayName,
                    brand = brand,
                    sourceLabel = sourceLabel,
                    packageQuantityLabel = quantity
                )
                if (!unique.containsKey(identity.identityKey)) unique[identity.identityKey] = identity
            }
            unique.values.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** A canonical encoding check used before a failed read can be replaced by a save. */
    fun isValidPrivateIdentitiesEncoding(encoded: String): Boolean {
        if (encoded == EMPTY_PRIVATE_IDENTITIES_ENCODING) return true
        val decoded = decodePrivateIdentities(encoded)
        return decoded.isNotEmpty() && encodePrivateIdentities(decoded) == encoded
    }

    /**
     * Selects the private list for lifecycle restoration without allowing an older bundle to
     * replace a durable list. A present durable record is authoritative, including the explicit
     * empty sentinel used after the user removes the last identity.
     */
    fun resolvePrivateIdentities(
        savedInstanceStateContainsPrivateIdentities: Boolean,
        savedInstanceStatePrivateIdentities: List<PracticalShoppingPrivateIdentity>,
        durablePrivateIdentityRecordPresent: Boolean,
        durablePrivateIdentities: List<PracticalShoppingPrivateIdentity>
    ): List<PracticalShoppingPrivateIdentity> =
        if (durablePrivateIdentityRecordPresent) {
            durablePrivateIdentities
        } else if (savedInstanceStateContainsPrivateIdentities) {
            savedInstanceStatePrivateIdentities
        } else {
            durablePrivateIdentities
        }

    private fun encodeField(value: String?): String =
        value?.let {
            val bytes = it.toByteArray(StandardCharsets.UTF_8)
            buildString(bytes.size * 2) {
                bytes.forEach { byte ->
                    val unsigned = byte.toInt() and 0xff
                    append(HEX_DIGITS[unsigned ushr 4])
                    append(HEX_DIGITS[unsigned and 0x0f])
                }
            }
        }.orEmpty()

    private fun decodeField(value: String, maxLength: Int, required: Boolean): String? {
        if (value.isEmpty()) {
            if (required) return null
            return null
        }
        if (value.length % 2 != 0) return null
        val bytes = ByteArray(value.length / 2)
        (value.indices step 2).forEach { index ->
            val high = HEX_DIGITS.indexOf(value[index])
            val low = HEX_DIGITS.indexOf(value[index + 1])
            if (high < 0 || low < 0) return null
            bytes[index / 2] = ((high shl 4) or low).toByte()
        }
        val decoded = String(bytes, StandardCharsets.UTF_8)
        if (decoded.length > maxLength || (required && decoded.isBlank())) return null
        return decoded
    }

    private fun decodeOptionalField(value: String, maxLength: Int): String? =
        if (value.isEmpty()) null else decodeField(value, maxLength, required = true)

    private const val PRIVATE_IDENTITIES_VERSION = "v1"
    const val EMPTY_PRIVATE_IDENTITIES_ENCODING = "v1:"
    private const val MAX_PRIVATE_IDENTITIES_ENCODED_LENGTH = 400_000
    private const val HEX_DIGITS = "0123456789abcdef"
}

interface PracticalShoppingHomeSessionStore {

    fun load(): PracticalShoppingHomeSession.Snapshot?

    /** True when the durable store has an explicit private-list record, including empty. */
    fun hasPrivateIdentityRecord(): Boolean = false

    fun save(snapshot: PracticalShoppingHomeSession.Snapshot)

    fun clear()
}

/** Local-only persistence for the user's last bounded Home list and explicit preferences. */
class AndroidPracticalShoppingHomeSessionStore(context: Context) :
    PracticalShoppingHomeSessionStore {

    private val preferences =
        (context.applicationContext ?: context).getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    override fun load(): PracticalShoppingHomeSession.Snapshot? {
        if (!preferences.contains(KEY_QUERY)) return null

        val details =
            preferences.getString(KEY_REQUEST_DETAILS, null)?.let { encoded ->
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            }
        return PracticalShoppingHomeSessionStoreCodec.decode(
            query = preferences.getString(KEY_QUERY, null),
            wasSubmitted = preferences.getBoolean(KEY_WAS_SUBMITTED, false),
            chickenChoice = preferences.getString(KEY_CHICKEN_CHOICE, null),
            extraStopMinimumSavingsChoice =
                preferences.getString(KEY_EXTRA_STOP_MINIMUM_SAVINGS, null),
            requestDetailsLifecycleState = details,
            privateIdentities = preferences.getString(KEY_PRIVATE_IDENTITIES, null)
        )
    }

    override fun hasPrivateIdentityRecord(): Boolean =
        preferences.contains(KEY_PRIVATE_IDENTITIES)

    override fun save(snapshot: PracticalShoppingHomeSession.Snapshot) {
        val editor =
            preferences
                .edit()
                .putString(KEY_QUERY, snapshot.query)
                .putBoolean(KEY_WAS_SUBMITTED, snapshot.wasSubmitted)
                .putString(KEY_CHICKEN_CHOICE, snapshot.chickenChoice?.name)
                .putString(
                    KEY_EXTRA_STOP_MINIMUM_SAVINGS,
                    PracticalShoppingHomePreferenceCodec.encode(
                        snapshot.extraStopMinimumSavingsChoice
                    )
                )

        val existingPrivateIdentities = preferences.getString(KEY_PRIVATE_IDENTITIES, null)
        val encodedPrivateIdentities =
            PracticalShoppingHomeSessionStoreCodec.encodePrivateIdentities(
                snapshot.privateIdentities
            )
        when {
            encodedPrivateIdentities != null ->
                editor.putString(KEY_PRIVATE_IDENTITIES, encodedPrivateIdentities)
            existingPrivateIdentities == null ->
                editor.putString(
                    KEY_PRIVATE_IDENTITIES,
                    PracticalShoppingHomeSessionStoreCodec.EMPTY_PRIVATE_IDENTITIES_ENCODING
                )
            !PracticalShoppingHomeSessionStoreCodec.isValidPrivateIdentitiesEncoding(
                existingPrivateIdentities
            ) -> {
                // Preserve an unreadable record for diagnostics instead of silently replacing it
                // with an empty list during the first render after a failed read.
            }
            else ->
                editor.putString(
                    KEY_PRIVATE_IDENTITIES,
                    PracticalShoppingHomeSessionStoreCodec.EMPTY_PRIVATE_IDENTITIES_ENCODING
                )
        }

        snapshot.requestDetailsLifecycleState?.let { bytes ->
            editor.putString(
                KEY_REQUEST_DETAILS,
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            )
        } ?: editor.remove(KEY_REQUEST_DETAILS)

        editor.apply()
    }

    override fun clear() {
        preferences
            .edit()
            .remove(KEY_QUERY)
            .remove(KEY_WAS_SUBMITTED)
            .remove(KEY_CHICKEN_CHOICE)
            .remove(KEY_EXTRA_STOP_MINIMUM_SAVINGS)
            .remove(KEY_REQUEST_DETAILS)
            .remove(KEY_PRIVATE_IDENTITIES)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "valuepilot_practical_shopping_home_session"
        const val KEY_QUERY = "query"
        const val KEY_WAS_SUBMITTED = "was_submitted"
        const val KEY_CHICKEN_CHOICE = "chicken_choice"
        const val KEY_EXTRA_STOP_MINIMUM_SAVINGS = "extra_stop_minimum_savings"
        const val KEY_REQUEST_DETAILS = "request_details"
        const val KEY_PRIVATE_IDENTITIES = "private_identities"
    }
}
