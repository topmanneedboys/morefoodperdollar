package com.valuepilot.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Immutable, consumer-facing explanation of what this build can and cannot
 * know.  The panel is deliberately descriptive: it does not inspect a
 * retailer, manufacture an offer, or change a shopping decision.
 */
internal data class PracticalShoppingDataStatusPresentation(
    val identityCatalog: String,
    val privateObservations: String,
    val currentOffers: String,
    val storeDirectory: String,
    val flyers: String,
    val connectivity: String,
    val demoData: String
) {
    init {
        listOf(
            identityCatalog,
            privateObservations,
            currentOffers,
            storeDirectory,
            flyers,
            connectivity,
            demoData
        ).forEach { require(it.isNotBlank()) }
    }

    /** Dialog-safe copy with one clear line per independent evidence boundary. */
    val message: String
        get() = listOf(
            "Product identities\n$identityCatalog",
            "Your private observations\n$privateObservations",
            "Current offers\n$currentOffers",
            "Store directory\n$storeDirectory",
            "Flyers\n$flyers",
            "Connectivity\n$connectivity",
            "Sample data\n$demoData"
        ).joinToString("\n\n")

    companion object {
        /** The signed launch rail is checked by the asset and catalog tests. */
        const val BUNDLED_IDENTITY_RECORD_COUNT = 30_000

        fun from(
            privateMemory: CompareHerePrivatePriceMemoryState,
            privateMemoryAvailable: Boolean = true,
            storeDirectorySummary: StoreDirectorySummary? = null
        ): PracticalShoppingDataStatusPresentation {
            val privateText =
                if (!privateMemoryAvailable) {
                    "Unavailable — stored history could not be read, so stale observations are hidden."
                } else {
                    val count = privateMemory.entries.size
                    val noun = if (count == 1) "observation" else "observations"
                    "$count private $noun saved on this device; these are personal, not live retailer offers."
                }

            val directoryText =
                when {
                    storeDirectorySummary == null ->
                        "Not included yet. No bundled store location is being presented as a retailer or stock claim."
                    !storeDirectorySummary.accepted ->
                        "Unavailable — the signed store-directory snapshot is ${storeDirectorySummary.admissionState.name.lowercase(Locale.ROOT)}; no location list is exposed from it."
                    else -> {
                        val gta = storeDirectorySummary.regionRecordCounts["ca-gta"] ?: 0
                        val metroVancouver = storeDirectorySummary.regionRecordCounts["ca-metro-vancouver"] ?: 0
                        val date = observedDate(storeDirectorySummary.observedAtEpochMillis)
                        "${String.format(Locale.ROOT, "%,d", storeDirectorySummary.totalRecordCount)} source-listed locations: ${String.format(Locale.ROOT, "%,d", gta)} in GTA and ${String.format(Locale.ROOT, "%,d", metroVancouver)} in Metro Vancouver. ${storeDirectorySummary.sourceDisplayName}, ${storeDirectorySummary.licenseId}, observed $date. Location only — no price, stock or availability fact."
                    }
                }

            return PracticalShoppingDataStatusPresentation(
                identityCatalog =
                    "${String.format(Locale.ROOT, "%,d", BUNDLED_IDENTITY_RECORD_COUNT)} Canada-labelled identities across signed, source-labelled GTA and Metro Vancouver snapshots. Identity only — no price, package, stock or availability fact.",
                privateObservations = privateText,
                currentOffers =
                    "Not included (0 authorized current-offer records). Unknown prices stay unknown.",
                storeDirectory = directoryText,
                flyers =
                    "Not included. No flyer content is copied or treated as a current offer.",
                connectivity =
                    "Offline core: this Android build has no INTERNET or ACCESS_NETWORK_STATE permission and does not fetch remote data.",
                demoData =
                    "Home and Search planner examples are clearly fictional sample data, not local retailer results."
            )
        }

        private fun observedDate(epochMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(epochMillis))
    }
}
