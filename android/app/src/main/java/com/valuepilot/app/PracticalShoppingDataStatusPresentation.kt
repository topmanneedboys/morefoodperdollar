package com.valuepilot.app

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
        fun from(
            privateMemory: CompareHerePrivatePriceMemoryState,
            privateMemoryAvailable: Boolean = true
        ): PracticalShoppingDataStatusPresentation {
            val privateText =
                if (!privateMemoryAvailable) {
                    "Unavailable — stored history could not be read, so stale observations are hidden."
                } else {
                    val count = privateMemory.entries.size
                    val noun = if (count == 1) "observation" else "observations"
                    "$count private $noun saved on this device; these are personal, not live retailer offers."
                }

            return PracticalShoppingDataStatusPresentation(
                identityCatalog =
                    "No bundled product catalogue is shipped. User-triggered Argentina identity Search is source-attributed and does not prove price, stock or availability.",
                privateObservations = privateText,
                currentOffers =
                    "None included. There is no authorized live-offer feed. Unknown prices stay unknown.",
                storeDirectory =
                    "No bundled store directory is shipped. A private store name or a qualified Argentina Search result remains location-only evidence.",
                flyers =
                    "None included. Flyer content is not copied or treated as a current offer.",
                connectivity =
                    "No background syncing. Only the user-triggered Argentina identity Search may use the secured connection; it does not supply prices or stock.",
                demoData =
                    "Home examples are fictional sample data. Search identities are source-attributed names, not current offers or Best Value decisions."
            )
        }
    }
}
