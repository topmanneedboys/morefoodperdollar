package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valuepilot.app.ArgentinaBackendContract
import com.valuepilot.app.ArgentinaLiveSearchSurfaceMode
import com.valuepilot.app.ArgentinaLiveSearchSurfaceState
import com.valuepilot.app.PracticalShoppingSearchIdentityPresentation
import com.valuepilot.app.R
import java.text.NumberFormat
import java.util.Locale

internal fun usesCompactDiscoveryContent(availableWidth: Dp, fontScale: Float): Boolean =
    availableWidth < 420.dp || fontScale >= 1.2f

internal fun stacksHomeQuickActions(availableWidth: Dp, fontScale: Float): Boolean =
    availableWidth < 340.dp || fontScale >= 1.3f

@Composable
internal fun ValuePilotHomeScreen(
    basketCount: Int,
    areaLabel: String,
    onSearch: () -> Unit,
    onBasket: () -> Unit,
    onGoodPriceCheck: () -> Unit,
    onTripPlanner: () -> Unit,
    onCompare: () -> Unit
) {
    ValuePilotDiscoveryColumn {
        item(key = "home-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(valuePilotNextText("NORMALIZED UNIT PRICES", "PRECIOS UNITARIOS NORMALIZADOS"))
                EvidencePill(
                    areaLabel.uppercase(),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    valuePilotNextText("Make a better call on every shop.", "Decidí mejor en cada compra."),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 23.sp,
                        lineHeight = 29.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    valuePilotNextText(
                        "Compare published prices and the cost of getting there. Every data point keeps its evidence.",
                        "Compará precios publicados y costo del recorrido. Cada dato conserva su evidencia."
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "home-search") {
            SearchCommandCard(onClick = onSearch)
        }
        item(key = "home-basket") {
            HomeFeatureCard(
                eyebrow = valuePilotNextText("YOUR PRIVATE BASKET", "TU CANASTA PRIVADA"),
                title = if (basketCount == 0) {
                    valuePilotNextText("Your basket is ready to start", "Canasta lista para empezar")
                } else {
                    valuePilotNextText("$basketCount saved products", "$basketCount productos guardados")
                },
                detail = if (basketCount == 0) {
                    valuePilotNextText(
                        "Add chosen identities. Prices are confirmed separately.",
                        "Sumá identidades elegidas. Los precios se confirman por separado."
                    )
                } else {
                    valuePilotNextText(
                        "Review your products and request a comparison when evidence is available.",
                        "Revisá tus productos y consultá una comparación cuando haya evidencia disponible."
                    )
                },
                actionLabel = valuePilotNextText("View basket", "Ver canasta"),
                onClick = onBasket
            )
        }
        item(key = "home-trip-planner") {
            HomeFeatureCard(
                eyebrow = valuePilotNextText("SHOPPING TRIP PLANNER", "PLANIFICADOR DE RECORRIDOS"),
                title = valuePilotNextText("Plan your shopping trip.", "Planificá tu recorrido de compra."),
                detail = valuePilotNextText(
                    "Enter distance, fuel, tolls, and parking.",
                    "Ingresá distancia, combustible, peajes y estacionamiento."
                ),
                actionLabel = valuePilotNextText("Calculate trip", "Calcular recorrido"),
                onClick = onTripPlanner
            )
        }
        item(key = "home-quick-tools") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stack = stacksHomeQuickActions(maxWidth, LocalDensity.current.fontScale)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow(valuePilotNextText("TOOLS", "HERRAMIENTAS"))
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeQuickAction(valuePilotNextText("Check a price", "Verificar precio"), onGoodPriceCheck, Modifier.fillMaxWidth())
                            HomeQuickAction(valuePilotNextText("Compare products", "Comparar productos"), onCompare, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeQuickAction(valuePilotNextText("Check a price", "Verificar precio"), onGoodPriceCheck, Modifier.weight(1f))
                            HomeQuickAction(valuePilotNextText("Compare products", "Comparar productos"), onCompare, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item(key = "home-principle") {
            EvidenceFootnote(
                valuePilotNextText(
                    "A published price does not confirm stock, availability, or verified savings.",
                    "Un precio publicado no confirma stock, disponibilidad ni ahorro verificado."
                )
            )
        }
    }
}

@Composable
private fun SearchCommandCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vp_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(valuePilotNextText("Search products", "Buscar productos"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                Text(valuePilotNextText("Name, brand, or barcode", "Nombre, marca o código de barras"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(valuePilotNextText("Search  →", "Buscar  →"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeFeatureCard(
    eyebrow: String,
    title: String,
    detail: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompactTextAction(actionLabel, onClick)
        }
    }
}

@Composable
private fun HomeQuickAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompactTextAction(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "$label  →",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun ValuePilotSearchScreen(
    query: String,
    state: ArgentinaLiveSearchSurfaceState,
    showDesignFixture: Boolean,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClarificationSelected: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenMatch: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onAddToBasket: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onOpenGoogleImages: (PracticalShoppingSearchIdentityPresentation.Match, String) -> Unit,
    onShowDesignFixture: () -> Unit
) {
    var selectedStoreName by rememberSaveable(query) { mutableStateOf<String?>(null) }
    var sortByLowestUnitPrice by rememberSaveable(query) { mutableStateOf(false) }
    val presentation = state.presentation
    val matches = presentation?.matches.orEmpty()
    val filterOptions = searchResultFilterOptions(matches)
    val activeStoreName = selectedStoreName?.takeIf { name ->
        filterOptions.stores.any { it.storeName.equals(name, ignoreCase = true) }
    }
    val visibleMatches = filterAndSortSearchMatches(
        matches = matches,
        selectedStoreName = activeStoreName,
        sortByLowestUnitPrice = sortByLowestUnitPrice,
        comparableBasis = filterOptions.comparableUnitPriceBasis
    )

    ValuePilotDiscoveryColumn {
        item(key = "search-intro") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(valuePilotNextText("SEARCH PRODUCTS", "BUSCAR PRODUCTOS"))
                    Text(
                        state.areaLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "ARGENTINA",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        item(key = "search-field") {
            SearchProductField(
                query = query,
                enabled = state.mode != ArgentinaLiveSearchSurfaceMode.LOADING,
                onQueryChanged = onQueryChanged,
                onSubmit = onSubmit
            )
        }
        if (query.isBlank() && state.mode == ArgentinaLiveSearchSurfaceMode.IDLE) {
            item(key = "search-suggestions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        valuePilotNextText("Try a search", "Probá una búsqueda"),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Leche", "Arroz", "Yerba", "Tomate").forEach { suggestion ->
                            SearchSuggestionChip(suggestion) { onQueryChanged(suggestion) }
                        }
                    }
                }
            }
        }
        if (state.mode != ArgentinaLiveSearchSurfaceMode.IDLE) {
            item(key = "search-state") {
                SearchStatePanel(state)
            }
        }

        presentation?.clarification?.let { clarification ->
            item(key = "search-family-clarification") {
                SearchFamilyClarificationCard(
                    clarification = clarification,
                    onChoiceSelected = onClarificationSelected
                )
            }
        }

        if (matches.isNotEmpty() && (filterOptions.stores.isNotEmpty() || filterOptions.comparableUnitPriceBasis != null)) {
            item(key = "search-current-offer-filters") {
                SearchCurrentOfferFilters(
                    stores = filterOptions.stores,
                    selectedStoreName = activeStoreName,
                    onStoreSelected = { selectedStoreName = it },
                    comparableBasis = filterOptions.comparableUnitPriceBasis,
                    sortByLowestUnitPrice = sortByLowestUnitPrice,
                    onSortByLowestUnitPriceChanged = { sortByLowestUnitPrice = it }
                )
            }
        }

        if (isSearchUnavailable(state.mode) &&
            state.presentation?.matches.isNullOrEmpty()
        ) {
            item(key = "layout-fixture-entry") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Eyebrow(valuePilotNextText("PRICE COMPARISON", "COMPARACIÓN DE PRECIOS"))
                        Text(
                            valuePilotNextText("Product details without a confirmed price or store.", "Detalle de producto sin precio o tienda confirmados."),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        CompactTextAction(
                            if (showDesignFixture) {
                                valuePilotNextText("View comparison", "Ver comparación")
                            } else {
                                valuePilotNextText("Open product view", "Abrir producto")
                            },
                            onClick = onShowDesignFixture,
                        )
                    }
                }
            }
        }

        if (showDesignFixture) {
            item(key = "layout-fixture-label") {
                EvidenceNotice(
                    valuePilotNextText(
                        "LOCAL COMPARISON · source unavailable. Values are illustrative and separate from saved data.",
                        "COMPARACIÓN LOCAL · fuente no disponible. Los valores son ilustrativos y están separados de tus datos guardados."
                    )
                )
            }
            item(key = "layout-fixture-product") {
                SearchResultCard(
                    match = ValuePilotNextLayoutFixture.match,
                    isDesignFixture = true,
                    onClick = { onOpenMatch(ValuePilotNextLayoutFixture.match) },
                    onAddToBasket = {},
                    onOpenGoogleImages = {
                        onOpenGoogleImages(ValuePilotNextLayoutFixture.match, state.areaLabel)
                    }
                )
            }
        }
        if (matches.isNotEmpty()) {
            item(key = "search-results-heading") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            SearchResultsCount(visibleMatches.size)
                    Text(
                        valuePilotNextText("Page ", "Página ") + ((presentation?.page ?: 0) + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SearchResultsCount(visibleMatches.size)
                            Text(
                                valuePilotNextText("Page ", "Página ") + ((presentation?.page ?: 0) + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (visibleMatches.isEmpty()) {
                item(key = "search-empty-filter-result") {
                    EmptyStateCard(
                        eyebrow = valuePilotNextText("SEARCH FILTERS", "FILTROS DE BÚSQUEDA"),
                        title = valuePilotNextText("No results for this store.", "No hay resultados con esta sucursal."),
                        detail = valuePilotNextText(
                            "Choose another store to see the identities found again.",
                            "Elegí otra sucursal para volver a ver las identidades encontradas."
                        ),
                        actionLabel = valuePilotNextText("Show all stores", "Ver todas las sucursales"),
                        onAction = { selectedStoreName = null }
                    )
                }
            }
            items(visibleMatches, key = { it.identityKey }) { match ->
                SearchResultCard(
                    match = match,
                    isDesignFixture = false,
                    onClick = { onOpenMatch(match) },
                    onAddToBasket = { onAddToBasket(match) },
                    onOpenGoogleImages = { onOpenGoogleImages(match, state.areaLabel) },
                    preferredOffer = preferredSearchOffer(
                        match = match,
                        selectedStoreName = activeStoreName,
                        sortByLowestUnitPrice = sortByLowestUnitPrice,
                        comparableBasis = filterOptions.comparableUnitPriceBasis
                    )
                )
            }
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                item(key = "search-more-loading") {
                    LoadingRow(valuePilotNextText("Loading more identities", "Cargando más identidades"))
                }
            } else if (presentation?.hasMore == true && presentation.nextPage != null) {
                item(key = "search-load-more") {
                    SecondaryActionButton(valuePilotNextText("Show more results", "Ver más resultados"), onLoadMore)
                }
            }
            item(key = "search-caveat") {
                EvidenceFootnote(
                    valuePilotNextLocalizedSearchText(
                        LocalValuePilotNextLanguage.current,
                        state.caveat
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchFamilyClarificationCard(
    clarification: ArgentinaBackendContract.SearchClarification,
    onChoiceSelected: (String) -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                valuePilotNextText("Choose a product type", "Elegí el tipo de producto"),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                valuePilotNextText(
                    "This name appears in more than one product form. Choose one to refine the results.",
                    "Este nombre aparece en más de una presentación. Elegí una para afinar los resultados."
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            clarification.choices.forEach { choice ->
                val label = if (language == ValuePilotNextLanguage.ENGLISH) {
                    choice.labelEn ?: choice.label
                } else {
                    choice.label
                }
                Surface(
                    onClick = { onChoiceSelected(choice.query) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ValuePilotSpacing.minimumTouchTarget),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp))
                        Text("→", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                valuePilotNextText(
                    "Options based on names observed in the catalog.",
                    "Opciones basadas en nombres observados en el catálogo."
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchCurrentOfferFilters(
    stores: List<SearchStoreFilterOption>,
    selectedStoreName: String?,
    onStoreSelected: (String?) -> Unit,
    comparableBasis: ComparableCurrentUnitPriceBasis?,
    sortByLowestUnitPrice: Boolean,
    onSortByLowestUnitPriceChanged: (Boolean) -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        comparableBasis?.let { basis ->
            SearchFilterChip(
                label = lowestUnitPriceLabel(basis.unitBasis, language),
                selected = sortByLowestUnitPrice,
                onClick = { onSortByLowestUnitPriceChanged(!sortByLowestUnitPrice) },
                accessibleLabel = valuePilotNextText(
                    "Sort by lowest unit price",
                    "Ordenar por precio unitario más bajo"
                )
            )
        }
        if (stores.isNotEmpty()) {
            SearchFilterChip(
                label = valuePilotNextText("All", "Todas"),
                selected = selectedStoreName == null,
                onClick = { onStoreSelected(null) },
                accessibleLabel = valuePilotNextText(
                    "Show offers from all stores",
                    "Mostrar ofertas de todas las sucursales"
                )
            )
            stores.forEach { store ->
                SearchFilterChip(
                    label = store.storeName,
                    selected = selectedStoreName.equals(store.storeName, ignoreCase = true),
                    onClick = {
                        onStoreSelected(
                            if (selectedStoreName.equals(store.storeName, ignoreCase = true)) null else store.storeName
                        )
                    },
                    accessibleLabel = valuePilotNextText(
                        "Filter by ${store.storeName}",
                        "Filtrar por ${store.storeName}"
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accessibleLabel: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            .semantics {
                contentDescription = accessibleLabel
            },
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun lowestUnitPriceLabel(
    unitBasis: String,
    language: ValuePilotNextLanguage
): String = when (unitBasis.lowercase()) {
    "l", "lt", "liter", "litre" -> valuePilotNextText(language, "Lowest $/L", "Menor $/L")
    "kg", "kilogram" -> valuePilotNextText(language, "Lowest $/kg", "Menor $/kg")
    else -> valuePilotNextText(language, "Lowest /$unitBasis", "Menor /$unitBasis")
}

@Composable
private fun SearchProductField(
    query: String,
    enabled: Boolean,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val submitEnabled = enabled && query.isNotBlank() &&
        query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS
    val searchDescription = valuePilotNextText("Search for a product by name or brand", "Buscar un producto por nombre o marca")
    val clearDescription = valuePilotNextText("Clear search", "Borrar búsqueda")
    val searchButtonDescription = valuePilotNextText("Search products", "Buscar productos")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vp_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .weight(1f)
                .semantics {
                    contentDescription = searchDescription
                },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (submitEnabled) onSubmit() }),
                decorationBox = { innerTextField ->
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                valuePilotNextText("Name, brand, or product…", "Nombre, marca o producto…"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotBlank()) {
                Text(
                    "×",
                    modifier = Modifier
                        .clickable(enabled = enabled) { onQueryChanged("") }
                        .semantics {
                            contentDescription = clearDescription
                        }
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = { if (submitEnabled) onSubmit() },
                enabled = submitEnabled,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = searchButtonDescription
                },
                shape = RoundedCornerShape(11.dp),
                color = if (submitEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (submitEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text("→", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
    }
}

@Composable
private fun SearchResultsCount(count: Int) {
    Text(
        count.toString() + if (count == 1) {
            valuePilotNextText(" identity", " identidad")
        } else {
            valuePilotNextText(" identities", " identidades")
        },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

private fun isSearchUnavailable(mode: ArgentinaLiveSearchSurfaceMode): Boolean =
    mode == ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE ||
        mode == ArgentinaLiveSearchSurfaceMode.STALE_RELEASE ||
        mode == ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE ||
        mode == ArgentinaLiveSearchSurfaceMode.TIMEOUT

@Composable
private fun SearchStatePanel(state: ArgentinaLiveSearchSurfaceState) {
    val unavailable = state.mode == ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.STALE_RELEASE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE ||
        state.mode == ArgentinaLiveSearchSurfaceMode.TIMEOUT
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = if (unavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f)
        else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (unavailable) MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (unavailable) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (state.mode) {
                        ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE -> valuePilotNextText(
                            "Live Search is not authorized on this installation.",
                            "Live Search no está autorizado en esta instalación."
                        )
                        ArgentinaLiveSearchSurfaceMode.STALE_RELEASE -> valuePilotNextText(
                            "Search needs an updated release.",
                            "La búsqueda necesita una versión actualizada."
                        )
                        ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE -> valuePilotNextText(
                            "Search is not available right now.",
                            "La búsqueda no está disponible por ahora."
                        )
                        ArgentinaLiveSearchSurfaceMode.TIMEOUT -> valuePilotNextText(
                            "Search timed out.",
                            "La búsqueda superó el tiempo de espera."
                        )
                        ArgentinaLiveSearchSurfaceMode.LOADING -> valuePilotNextText(
                            "Searching products…",
                            "Buscando productos…"
                        )
                        ArgentinaLiveSearchSurfaceMode.NO_MATCHES -> valuePilotNextText(
                            "No matching identity was found for this search.",
                            "No hay una identidad coincidente en esta búsqueda."
                        )
                        else -> valuePilotNextLocalizedSearchText(
                            LocalValuePilotNextLanguage.current,
                            state.statusMessage
                        )
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    valuePilotNextText(
                        "Prices, stores, and availability are hidden without source evidence.",
                        "No se muestran precios, tiendas ni disponibilidad sin evidencia de la fuente."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    isDesignFixture: Boolean,
    onClick: () -> Unit,
    onAddToBasket: () -> Unit,
    onOpenGoogleImages: () -> Unit,
    preferredOffer: ArgentinaBackendContract.SearchOffer? = null
) {
    val language = LocalValuePilotNextLanguage.current
    val currentOffer = preferredOffer ?: match.qualifiedOffers.firstOrNull {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    }
    val detailDescription = valuePilotNextText(
        "View details for ${match.displayName}",
        "Ver detalles de ${match.displayName}"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = detailDescription
            },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchMatchPill(
                    if (isDesignFixture) {
                        valuePilotNextText("COMPARISON", "COMPARACIÓN")
                    } else {
                        valuePilotNextLocalizedMatchLabel(match.matchLabel, language)
                    }
                )
                match.packageQuantityLabel?.let {
                    EvidencePill(
                        valuePilotNextLocalizedFactText(language, it),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    match.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                match.brand?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (currentOffer != null) {
                SearchOfferPricePanel(currentOffer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        currentOffer.storeName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SearchFreshnessLabel(true)
                }
            } else if (!isDesignFixture && match.qualifiedOffers.isNotEmpty()) {
                Text(
                    valuePilotNextText(
                        "The source does not confirm a current price.",
                        "La fuente no confirma un precio actual."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoogleImagesAction(match, onOpenGoogleImages, Modifier.weight(1f))
                AddToBasketAction(
                    match = match,
                    enabled = !isDesignFixture && !match.sourceLabel.isNullOrBlank(),
                    onClick = onAddToBasket,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchOfferPricePanel(offer: ArgentinaBackendContract.SearchOffer) {
    val language = LocalValuePilotNextLanguage.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val unitPrice = offer.unitPrice
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (unitPrice == null) {
                        valuePilotNextText("PUBLISHED PRICE", "PRECIO PUBLICADO")
                    } else {
                        valuePilotNextText("NORMALIZED UNIT PRICE", "PRECIO UNITARIO NORMALIZADO")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.45.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unitPrice == null) {
                    Text(
                        valuePilotNextLocalizedFactText(
                            language,
                            "${offer.listPrice.currency} ${offer.listPrice.amount}"
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            valuePilotNextLocalizedFactText(
                                language,
                                "${unitPrice.currency} ${unitPrice.amount}"
                            ),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        offer.unitPriceBasis?.takeIf(String::isNotBlank)?.let {
                            Text(
                                "/ $it",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (unitPrice != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        valuePilotNextText("PUBLISHED PRICE", "PRECIO PUBLICADO"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.35.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        valuePilotNextLocalizedFactText(
                            language,
                            "${offer.listPrice.currency} ${offer.listPrice.amount}"
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleImagesAction(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionDescription = valuePilotNextText(
        "Search Google Images for photos of ${match.displayName}",
        "Buscar fotos de ${match.displayName} en Google Images"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = actionDescription
            },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                valuePilotNextText("View on Google Images", "Ver en Google Images"),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddToBasketAction(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionDescription = valuePilotNextText(
        "Add ${match.displayName} to your private basket",
        "Agregar ${match.displayName} a tu canasta privada"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = actionDescription
            },
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                valuePilotNextText("＋  Add", "＋  Agregar"),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchIdentitySummary(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    modifier: Modifier = Modifier
) {
    val language = LocalValuePilotNextLanguage.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Text(
            match.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        listOfNotNull(
            match.brand,
            match.packageQuantityLabel?.let { valuePilotNextLocalizedFactText(language, it) }
        )
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

@Composable
private fun SearchPriceLabel(
    offer: ArgentinaBackendContract.SearchOffer,
    modifier: Modifier = Modifier
) {
    val language = LocalValuePilotNextLanguage.current
    Text(
        valuePilotNextLocalizedFactText(
            language,
            offer.listPrice.currency + " " + offer.listPrice.amount
        ),
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SearchMatchPill(matchLabel: String) {
    EvidencePill(
        matchLabel.uppercase(),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
}

internal fun valuePilotNextLocalizedMatchLabel(
    matchLabel: String,
    language: ValuePilotNextLanguage
): String = when (matchLabel.lowercase()) {
    "exact barcode" -> valuePilotNextText(language, "exact barcode", "código exacto")
    "exact name" -> valuePilotNextText(language, "exact name", "nombre exacto")
    "name match" -> valuePilotNextText(language, "name match", "coincidencia de nombre")
    "name prefix" -> valuePilotNextText(language, "name prefix", "inicio del nombre")
    "possible typo match" -> valuePilotNextText(language, "possible typo", "posible error de escritura")
    "provider identity match" -> valuePilotNextText(
        language,
        "provider identity match",
        "coincidencia de identidad de fuente"
    )
    else -> matchLabel
}

@Composable
private fun SearchFreshnessLabel(hasFreshPrice: Boolean) {
    Text(
        if (hasFreshPrice) {
            valuePilotNextText("Recent published price", "Precio publicado reciente")
        } else {
            valuePilotNextText("Identity only", "Solo identidad")
        },
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = if (hasFreshPrice) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun ValuePilotProductDetailScreen(
    match: PracticalShoppingSearchIdentityPresentation.Match?,
    isSearchLoading: Boolean,
    isDesignFixture: Boolean,
    areaLabel: String,
    onAddToBasket: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onSearchAgain: () -> Unit,
    onOpenGoogleImages: (PracticalShoppingSearchIdentityPresentation.Match, String) -> Unit,
    onOpenMapContext: (ArgentinaBackendContract.SearchOffer) -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    Column(modifier = Modifier.fillMaxSize()) {
        ValuePilotDiscoveryColumn(modifier = Modifier.weight(1f)) {
        if (match == null) {
            item(key = "product-detail-unavailable") {
                if (isSearchLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = ValuePilotSpacing.section),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            valuePilotNextText(
                                "Restoring the selected identity…",
                                "Restaurando la identidad seleccionada…"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    EmptyStateCard(
                        eyebrow = valuePilotNextText("SELECTED PRODUCT", "PRODUCTO SELECCIONADO"),
                        title = valuePilotNextText(
                            "This selection is no longer in the current results.",
                            "Esta selección ya no está en los resultados actuales."
                        ),
                        detail = valuePilotNextText(
                            "Search again to refresh its identity and any price evidence.",
                            "Volvé a buscar para actualizar su identidad y cualquier evidencia de precio."
                        ),
                        actionLabel = valuePilotNextText("Back to Search", "Volver a Buscar"),
                        onAction = onSearchAgain
                    )
                }
            }
        } else {
            if (isDesignFixture) {
                item(key = "product-detail-fixture-disclosure") {
                    CompactEvidenceNotice(
                        valuePilotNextText(
                            "LOCAL COMPARISON · source unavailable. Values are illustrative and remain separate from saved data.",
                            "COMPARACIÓN LOCAL · fuente no disponible. Los valores son ilustrativos y se mantienen separados de tus datos guardados."
                        )
                    )
                }
            }
            item(key = "product-detail-identity") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        match.brand?.let {
                            EvidencePill(
                                it.uppercase(),
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        match.packageQuantityLabel?.let {
                            EvidencePill(
                                valuePilotNextLocalizedFactText(LocalValuePilotNextLanguage.current, it),
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EvidencePill(
                            if (isDesignFixture) {
                                valuePilotNextText("CABA · COMPARISON", "CABA · COMPARACIÓN")
                            } else {
                                "${areaLabel.uppercase()} · " + valuePilotNextText("ARGENTINA", "ARGENTINA")
                            },
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        match.displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isDesignFixture) {
                            Text(
                                valuePilotNextText("PRICE CONTEXT", "CONTEXTO DE PRECIO"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        } else {
                            Text(
                                valuePilotNextLocalizedMatchLabel(match.matchLabel, language),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            match.sourceLabel?.let {
                                Text("· $it", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        if (isDesignFixture) {
                            valuePilotNextText(
                                "This view has no confirmed price or store evidence.",
                                "Esta vista no tiene precio ni tienda confirmados."
                            )
                        } else {
                            valuePilotNextText(
                                "The identity does not confirm price, stock, or availability.",
                                "La identidad no confirma precio, stock ni disponibilidad."
                            )
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "product-detail-google-images") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_vp_image_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    valuePilotNextText("No photos in the official catalog", "Sin fotos en el catálogo oficial"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    valuePilotNextText(
                                        "Check the real package online before choosing.",
                                        "Revisá el envase real en la web antes de elegir."
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        PrimaryDiscoveryAction(
                            label = valuePilotNextText("View photos on Google Images  ↗", "Ver fotos en Google Images  ↗"),
                            onClick = { onOpenGoogleImages(match, areaLabel) }
                        )
                        Text(
                            valuePilotNextText(
                                "Google controls the results; ValuePilot does not download or verify images.",
                                "Google controla los resultados; ValuePilot no descarga ni verifica imágenes."
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "product-detail-offers-title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(valuePilotNextText("PRICES AND STORES", "PRECIOS Y TIENDAS"))
                    Text(
                        if (isDesignFixture) {
                            valuePilotNextText("Illustrative unit range", "Rango unitario ilustrativo")
                        } else if (match.qualifiedOffers.isEmpty()) {
                            valuePilotNextText("Current price not provided", "Precio actual no informado")
                        } else {
                            valuePilotNextText("Collected prices", "Precios relevados")
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (isDesignFixture) {
                item(key = "product-detail-fixture-price-range") {
                    ProductOfferPriceRangeCard(ValuePilotDesignPreviewPriceRange, isDesignFixture = true)
                }
            } else {
                currentOfferPriceRange(match.qualifiedOffers)?.let { range ->
                    item(key = "product-detail-current-price-range") {
                        ProductOfferPriceRangeCard(range, isDesignFixture = false)
                    }
                }
            }
            if (match.qualifiedOffers.isEmpty()) {
                item(key = "product-detail-no-offer") {
                    CompactEvidenceNotice(
                        if (isDesignFixture) {
                            valuePilotNextText(
                                "No authorized price evidence is available for this identity.",
                                "No hay evidencia de precio autorizada para esta identidad."
                            )
                        } else {
                            valuePilotNextText(
                                "No current price evidence is associated with this identity.",
                                "No hay evidencia de precio actual asociada a esta identidad."
                            )
                        }
                    )
                }
            } else {
                items(match.qualifiedOffers, key = { it.offerKey }) { offer ->
                    OfferEvidenceCard(offer, onOpenMapContext = { onOpenMapContext(offer) })
                }
            }
            if (isDesignFixture) {
                item(key = "product-detail-fixture-save") {
                    Text(
                        valuePilotNextText(
                            "This comparison row is read-only and cannot be saved or added.",
                            "Esta fila comparativa es de solo lectura y no se puede guardar ni agregar."
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
        match?.let {
            ProductDetailStickyActions(
                match = it,
                areaLabel = areaLabel,
                isDesignFixture = isDesignFixture,
                onAddToBasket = { onAddToBasket(it) },
                onOpenGoogleImages = { onOpenGoogleImages(it, areaLabel) }
            )
        }
    }
}

@Composable
private fun ProductOfferPriceRangeCard(
    range: CurrentOfferPriceRange,
    isDesignFixture: Boolean
) {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    val minimum = format.format(range.minimum)
    val median = format.format(range.median)
    val maximum = format.format(range.maximum)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Eyebrow(
                    if (isDesignFixture) {
                        valuePilotNextText("PRICE CONTEXT · UNVERIFIED VALUES", "CONTEXTO DE PRECIO · VALORES SIN CONFIRMAR")
                    } else {
                        valuePilotNextText("RECENT OFFERS", "OFERTAS RECIENTES")
                    }
                )
                if (!isDesignFixture) {
                    Text(
                        "${range.offerCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                if (isDesignFixture) {
                    valuePilotNextText("Unit comparison", "Comparación por unidad")
                } else {
                    valuePilotNextText("Range across published offers", "Rango entre ofertas publicadas")
                },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            OfferRangeTrack(range.medianPosition)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                OfferRangePoint(
                    title = valuePilotNextText("Minimum", "Mínimo"),
                    currency = range.currency,
                    amount = minimum,
                    basis = range.unitBasis,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                )
                OfferRangePoint(
                    title = valuePilotNextText("Median", "Mediana"),
                    currency = range.currency,
                    amount = median,
                    basis = range.unitBasis,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                OfferRangePoint(
                    title = valuePilotNextText("Maximum", "Máximo"),
                    currency = range.currency,
                    amount = maximum,
                    basis = range.unitBasis,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                )
            }
            Text(
                if (isDesignFixture) {
                    valuePilotNextText(
                        "These illustrative amounts are not linked to a current store or offer.",
                        "Estos importes ilustrativos no están vinculados a una tienda ni oferta actual."
                    )
                } else {
                    valuePilotNextText(
                        "Only recent offers with the same unit are summarized. A published price does not confirm stock or availability.",
                        "Resume solo ofertas recientes con la misma unidad. El precio publicado no confirma stock ni disponibilidad."
                    )
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfferRangeTrack(medianPosition: Float) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(medianPosition)
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Box(
            modifier = Modifier
                .offset(x = (maxWidth - 12.dp) * medianPosition)
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun OfferRangePoint(
    title: String,
    currency: String,
    amount: String,
    basis: String,
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "$currency $amount",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "/ $basis",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProductDetailStickyActions(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    areaLabel: String,
    isDesignFixture: Boolean,
    onAddToBasket: () -> Unit,
    onOpenGoogleImages: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDesignFixture) {
                    PrimaryDiscoveryAction(
                        label = valuePilotNextText("View on Google Images", "Ver en Google Images"),
                        onClick = onOpenGoogleImages,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ProductDetailGoogleImagesAction(
                        match = match,
                        areaLabel = areaLabel,
                        onClick = onOpenGoogleImages
                    )
                    PrimaryDiscoveryAction(
                        label = valuePilotNextText("Add to your private basket", "Añadir a tu canasta privada"),
                        onClick = onAddToBasket,
                        enabled = !match.sourceLabel.isNullOrBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailGoogleImagesAction(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    areaLabel: String,
    onClick: () -> Unit
) {
    val query = GoogleImagesHandoff.queryFor(match, areaLabel)
    val accessibilityDescription = query?.let {
        valuePilotNextText(
            "View on Google Images. Search: $it",
            "Ver en Google Images. Búsqueda: $it"
        )
    } ?: valuePilotNextText(
        "View on Google Images: ${match.displayName}",
        "Ver en Google Images: ${match.displayName}"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .semantics {
                contentDescription = accessibilityDescription
            },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_vp_image_search),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CompactEvidenceNotice(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
        )
    }
}

@Composable
private fun PrimaryDiscoveryAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun OfferEvidenceCard(
    offer: ArgentinaBackendContract.SearchOffer,
    onOpenMapContext: () -> Unit
) {
    val language = LocalValuePilotNextLanguage.current
    val fresh = offer.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    val latitude = offer.storeLatitude.toBigDecimalOrNull()
    val longitude = offer.storeLongitude.toBigDecimalOrNull()
    val validCoordinates =
        latitude != null && latitude >= java.math.BigDecimal("-90") && latitude <= java.math.BigDecimal("90") &&
            longitude != null && longitude >= java.math.BigDecimal("-180") && longitude <= java.math.BigDecimal("180")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(
                        if (fresh) {
                            valuePilotNextText("RECENT PUBLISHED PRICE", "PRECIO PUBLICADO RECIENTE")
                        } else {
                            valuePilotNextText("PRICE WITHOUT CURRENT VALIDITY", "PRECIO SIN VIGENCIA ACTUAL")
                        }
                    )
                    Text(
                        valuePilotNextLocalizedFactText(
                            language,
                            offer.listPrice.currency + " " + offer.listPrice.amount
                        ),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OfferFreshnessPill(offer, fresh)
            }
            Text(offer.storeName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            offer.quantity?.let {
                Text(
                    valuePilotNextText("Package · ", "Envase · ") +
                        valuePilotNextLocalizedFactText(language, it.amount + " " + it.unit),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text(
                valuePilotNextText("Package quantity not provided", "Cantidad del envase no informada"),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            offer.unitPrice?.let { unit ->
                val basis = offer.unitPriceBasis?.takeIf(String::isNotBlank)
                Text(
                    valuePilotNextText("Unit price  ", "Precio unitario  ") +
                        valuePilotNextLocalizedFactText(
                            language,
                            unit.currency + " " + unit.amount + (basis?.let { " / $it" } ?: "")
                        ),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.secondary
                )
            } ?: Text(
                valuePilotNextText("Unit price not provided", "Precio unitario no informado"),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${offer.provenance.provider} · " +
                    valuePilotNextText("published", "publicación") +
                    " ${offer.releaseDate}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                valuePilotNextText(
                    "A published price does not confirm stock or availability.",
                    "El precio publicado no confirma stock ni disponibilidad."
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (validCoordinates) {
                TextButton(onClick = onOpenMapContext, modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)) {
                    Text(
                        valuePilotNextText("View store on the map  ↗", "Ver sucursal en el mapa  ↗"),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferFreshnessPill(
    offer: ArgentinaBackendContract.SearchOffer,
    fresh: Boolean
) {
    EvidencePill(
        if (fresh) {
            valuePilotNextText("RECENT", "RECIENTE")
        } else {
            valuePilotNextText("NOT CURRENT", "NO ACTUAL")
        },
        if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        if (fresh) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    )
}
