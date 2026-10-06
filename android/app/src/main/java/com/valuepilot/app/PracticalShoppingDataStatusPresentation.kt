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
            "Product names\n$identityCatalog",
            "Your saved observations\n$privateObservations",
            "Live prices\n$currentOffers",
            "Store locations\n$storeDirectory",
            "Flyers\n$flyers",
            "Connection\n$connectivity",
            "Home sample\n$demoData"
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
                        "Not available in this build. No store location is presented as a price, stock or availability claim."
                    !storeDirectorySummary.accepted ->
                        "Unavailable — the signed location list is ${storeDirectorySummary.admissionState.name.lowercase(Locale.ROOT)}; no locations are shown from it."
                    else -> {
                        val gta = storeDirectorySummary.regionRecordCounts["ca-gta"] ?: 0
                        val metroVancouver = storeDirectorySummary.regionRecordCounts["ca-metro-vancouver"] ?: 0
                        val date = observedDate(storeDirectorySummary.observedAtEpochMillis)
                        "${String.format(Locale.ROOT, "%,d", storeDirectorySummary.totalRecordCount)} source-listed locations: ${String.format(Locale.ROOT, "%,d", gta)} in GTA and ${String.format(Locale.ROOT, "%,d", metroVancouver)} in Metro Vancouver. ${storeDirectorySummary.sourceDisplayName}, observed $date. Location only — no price, stock or availability."
                    }
                }

            return PracticalShoppingDataStatusPresentation(
                identityCatalog =
                    "${String.format(Locale.ROOT, "%,d", BUNDLED_IDENTITY_RECORD_COUNT)} Canada-labelled product names in the offline identity catalogue. Names only — this is not proof of GTA or Metro Vancouver availability, price, package size, stock or availability.",
                privateObservations = privateText,
                currentOffers =
                    "Qualified current listed-price previews may appear in the user-triggered Argentina Search when the signed release and freshness checks pass. Unknown prices stay unknown; a listed price is not stock or availability.",
                storeDirectory = directoryText,
                flyers =
                    "None included. Flyer content is not copied or treated as a current offer.",
                connectivity =
                    "No background syncing. Only the user-triggered Argentina Search may use the secured connection; it may supply bounded qualified price/store evidence, never stock or availability.",
                demoData =
                    "Home examples are fictional sample data. Search identities and any attached offer preview are source-attributed evidence, not Best Value decisions."
            )
        }

        private fun observedDate(epochMillis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(epochMillis))
    }
}
