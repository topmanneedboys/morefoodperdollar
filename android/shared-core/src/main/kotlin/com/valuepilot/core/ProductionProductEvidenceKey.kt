package com.valuepilot.core

/**
 * Deterministic product-identity key scope.
 *
 * CROSS_SOURCE_GTIN means only that the representation can be shared across
 * sources when those sources independently support the same canonical GTIN.
 * It does not by itself prove allocation, product ownership, freshness,
 * authorization, quantity authority, or rankability.
 *
 * PROVIDER_ITEM and PROVIDER_SKU are deliberately provider-scoped and must not
 * be used to join unrelated providers merely because names/descriptions match.
 */
enum class ProductionProductKeyScope {
    CROSS_SOURCE_GTIN,
    PROVIDER_ITEM,
    PROVIDER_SKU
}

data class ProductionProductEvidenceKey(
    val value: String,
    val scope: ProductionProductKeyScope
) {
    init {
        require(value.isNotBlank())
        require(value.length <= 640)
    }

    val usesCrossSourceRepresentation: Boolean
        get() = scope == ProductionProductKeyScope.CROSS_SOURCE_GTIN
}

/**
 * Provider-neutral identity-key resolver.
 *
 * Priority:
 * 1. checksum-valid canonical GTIN;
 * 2. provider-scoped provider item id;
 * 3. provider-scoped SKU.
 *
 * Invalid GTIN is never repaired. Product names, descriptions, images and price
 * are never identity inputs. Provider-scoped keys use length-prefixed components
 * so delimiter characters inside source ids cannot create accidental collisions.
 */
object ProductionProductEvidenceKeyResolver {

    fun resolve(
        providerId: EvidenceProviderId,
        identity: SourceProductIdentity
    ): ProductionProductEvidenceKey? {
        val canonicalGtin = identity.gtin?.let(GtinValidation::canonicalOrNull)
        if (canonicalGtin != null) {
            return ProductionProductEvidenceKey(
                value = "gtin:$canonicalGtin",
                scope = ProductionProductKeyScope.CROSS_SOURCE_GTIN
            )
        }

        identity.providerItemId?.let { providerItemId ->
            return ProductionProductEvidenceKey(
                value =
                    providerScopedKey(
                        providerId = providerId,
                        kind = "item",
                        sourceId = providerItemId
                    ),
                scope = ProductionProductKeyScope.PROVIDER_ITEM
            )
        }

        identity.sku?.let { sku ->
            return ProductionProductEvidenceKey(
                value =
                    providerScopedKey(
                        providerId = providerId,
                        kind = "sku",
                        sourceId = sku
                    ),
                scope = ProductionProductKeyScope.PROVIDER_SKU
            )
        }

        return null
    }

    private fun providerScopedKey(
        providerId: EvidenceProviderId,
        kind: String,
        sourceId: String
    ): String =
        "provider:${component(providerId.value)}:$kind:${component(sourceId)}"

    private fun component(value: String): String =
        "${value.length}:$value"
}
