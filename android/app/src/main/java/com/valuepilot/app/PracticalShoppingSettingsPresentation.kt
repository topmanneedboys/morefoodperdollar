package com.valuepilot.app

/**
 * Small, deterministic model for the low-frequency Settings/About area.
 *
 * Settings is a navigation container only. It does not own price, ranking,
 * store, network, or personal-record authority.
 */
internal enum class PracticalShoppingSettingsPage {
    LANDING,
    PREFERENCES,
    DATA_PRIVACY,
    DATA_SOURCES,
    ABOUT,
    LICENCES,
    TECHNICAL_DETAILS
}

internal data class PracticalShoppingSettingsRow(
    val key: String,
    val title: String,
    val summary: String,
    val action: PracticalShoppingSettingsAction? = null
) {
    init {
        require(key.isNotBlank())
        require(title.isNotBlank())
        require(summary.isNotBlank())
    }
}

internal data class PracticalShoppingSettingsState(
    val page: PracticalShoppingSettingsPage,
    val rows: List<PracticalShoppingSettingsRow> = emptyList(),
    val detail: String? = null
) {
    init {
        require(rows.size <= 8)
        require(detail == null || detail.isNotBlank())
    }
}

internal sealed interface PracticalShoppingSettingsAction {
    data object OpenPreferences : PracticalShoppingSettingsAction
    data object OpenDataPrivacy : PracticalShoppingSettingsAction
    data object OpenDataSources : PracticalShoppingSettingsAction
    data object OpenAbout : PracticalShoppingSettingsAction
    data object OpenLicences : PracticalShoppingSettingsAction
    data object OpenTechnicalDetails : PracticalShoppingSettingsAction
}

internal object PracticalShoppingSettingsPresentation {
    const val DEFAULT_VERSION = "101.1.24"

    fun page(
        page: PracticalShoppingSettingsPage,
        versionName: String = DEFAULT_VERSION,
        dataStatus: PracticalShoppingDataStatusPresentation? = null
    ): PracticalShoppingSettingsState =
        when (page) {
            PracticalShoppingSettingsPage.LANDING ->
                PracticalShoppingSettingsState(
                    page = page,
                    rows = listOf(
                        row(
                            key = "preferences",
                            title = "Preferences",
                            summary = "Choices that shape your shopping plan.",
                            action = PracticalShoppingSettingsAction.OpenPreferences
                        ),
                        row(
                            key = "data-privacy",
                            title = "Data & privacy",
                            summary = "What stays on this device and how your records are used.",
                            action = PracticalShoppingSettingsAction.OpenDataPrivacy
                        ),
                        row(
                            key = "data-sources",
                            title = "Data sources",
                            summary = "Where product identities, personal records and web photos come from.",
                            action = PracticalShoppingSettingsAction.OpenDataSources
                        ),
                        row(
                            key = "about",
                            title = "About",
                            summary = "ValuePilot, version, attribution and licences.",
                            action = PracticalShoppingSettingsAction.OpenAbout
                        )
                    )
                )

            PracticalShoppingSettingsPage.PREFERENCES ->
                PracticalShoppingSettingsState(
                    page = page,
                    detail =
                        "ValuePilot keeps preference choices beside the shopping decisions they affect. " +
                            "There are no account, sync or notification controls here."
                )

            PracticalShoppingSettingsPage.DATA_PRIVACY ->
                PracticalShoppingSettingsState(
                    page = page,
                    detail =
                        "Your saved products, prices, purchases and watches are personal records kept on " +
                            "this device. They are not presented as retailer offers. ValuePilot has no account " +
                            "or background sync in this build. Manage a personal record from the shopping surface " +
                            "where you created it."
                )

            PracticalShoppingSettingsPage.DATA_SOURCES ->
                PracticalShoppingSettingsState(
                    page = page,
                    rows = listOf(
                        row(
                            key = "argentina",
                            title = "Argentina public price and store dataset",
                            summary = "Search uses source-attributed product and store evidence. It is not a live stock or price promise."
                        ),
                        row(
                            key = "personal",
                            title = "Your prices and purchases",
                            summary = "Prices you enter or purchases you confirm are private records on this device, separate from public data."
                        ),
                        row(
                            key = "web-photos",
                            title = "Web photos",
                            summary = "Google Images opens outside ValuePilot. Photos are unverified and are not copied into the app."
                        )
                    )
                )

            PracticalShoppingSettingsPage.ABOUT ->
                PracticalShoppingSettingsState(
                    page = page,
                    rows = listOf(
                        row(
                            key = "product",
                            title = "ValuePilot",
                            summary = "A private shopping workspace that helps you understand a basket and the evidence behind it."
                        ),
                        row(
                            key = "version",
                            title = "Version",
                            summary = versionName,
                        ),
                        row(
                            key = "licences",
                            title = "Licences & attribution",
                            summary = "Source attribution and legal notices.",
                            action = PracticalShoppingSettingsAction.OpenLicences
                        ),
                        row(
                            key = "technical-details",
                            title = "Technical details",
                            summary = "Data status and evidence boundaries for support and review.",
                            action = PracticalShoppingSettingsAction.OpenTechnicalDetails
                        )
                    )
                )

            PracticalShoppingSettingsPage.LICENCES ->
                PracticalShoppingSettingsState(
                    page = page,
                    detail =
                        "Argentina public data: Precios Claros — Base SEPA, from the official datos.gob.ar " +
                            "catalog and datos.produccion.gob.ar source family. The catalog metadata identifies " +
                            "the dataset licence as Creative Commons Attribution 4.0. Source attribution stays " +
                            "with qualified data; this does not add permission for retailer logos, product images " +
                            "or unrelated website content. Google Images is an external website and its photos are " +
                            "not copied, verified or included by ValuePilot."
                )

            PracticalShoppingSettingsPage.TECHNICAL_DETAILS ->
                PracticalShoppingSettingsState(
                    page = page,
                    detail = buildString {
                        append("Product data needs an update until a qualified current release is ready. ")
                        append("Search remains honest about that state and does not turn it into a live offer.\n\n")
                        append(dataStatus?.message ?: "Data status is not available.")
                    }
                )
        }

    private fun row(
        key: String,
        title: String,
        summary: String,
        action: PracticalShoppingSettingsAction? = null
    ): PracticalShoppingSettingsRow =
        PracticalShoppingSettingsRow(key, title, summary, action)
}
