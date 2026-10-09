package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    PremiumColumn {
        item(key = "home-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                Eyebrow("COMPRAS EN  ·  ${areaLabel.uppercase()}")
                Text(
                    "Decidí mejor en cada compra.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Compará productos, precios informados y costo del viaje; cada dato conserva su evidencia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EvidencePill(
                    "BUSCÁS CERCA  ·  $areaLabel",
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        item(key = "home-search") {
            SearchCommandCard(onClick = onSearch)
        }
        item(key = "home-basket") {
            ActionCard(
                eyebrow = "TU CANASTA PRIVADA",
                title = if (basketCount == 0) {
                    "Todavía no agregaste productos."
                } else {
                    basketCount.toString() + if (basketCount == 1) " producto seleccionado" else " productos seleccionados"
                },
                detail = if (basketCount == 0) {
                    "Guardá productos elegidos en este dispositivo. La identidad y el precio se mantienen separados."
                } else {
                    "Revisá los productos y pedí una comparación actual cuando haya evidencia disponible."
                },
                actionLabel = if (basketCount == 0) "Abrir canasta" else "Ver canasta",
                onClick = onBasket
            )
        }
        item(key = "home-trip-planner") {
            ActionCard(
                eyebrow = "PLANIFICADOR DE RECORRIDOS",
                title = "Calculá el costo de tu viaje.",
                detail = "Estimá combustible, peajes y estacionamiento con tus propios datos.",
                actionLabel = "Planear viaje",
                onClick = onTripPlanner
            )
        }
        item(key = "home-quick-tools") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stack = stacksHomeQuickActions(maxWidth, LocalDensity.current.fontScale)
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                    Eyebrow("HERRAMIENTAS")
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                            HomeQuickAction("Verificar precio", onGoodPriceCheck, Modifier.fillMaxWidth())
                            HomeQuickAction("Comparar productos", onCompare, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                            HomeQuickAction("Verificar precio", onGoodPriceCheck, Modifier.weight(1f))
                            HomeQuickAction("Comparar productos", onCompare, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item(key = "home-principle") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ValuePilotSpacing.small),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
            ) {
                Eyebrow("CRITERIO DE VALUEPILOT")
                Text(
                    "Lo desconocido permanece desconocido.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Un precio publicado no confirma stock ni un ahorro verificado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchCommandCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ValuePilotSpacing.large, vertical = ValuePilotSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vp_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                Text("Buscar productos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Nombre, marca o código de barras", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Buscar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HomeQuickAction(label: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ValuePilotSpacing.large, vertical = ValuePilotSpacing.medium),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun ValuePilotSearchScreen(
    query: String,
    state: ArgentinaLiveSearchSurfaceState,
    showDesignFixture: Boolean,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenMatch: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onOpenGoogleImages: (PracticalShoppingSearchIdentityPresentation.Match, String) -> Unit,
    onShowDesignFixture: () -> Unit
) {
    PremiumColumn {
        item(key = "search-intro") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                Eyebrow("COMPRAS EN  ·  ${state.areaLabel.uppercase()}")
                Text(
                    "Encontrá el producto exacto.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Compará identidades y precios publicados con su evidencia. La disponibilidad se mantiene desconocida.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "search-field") {
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Producto") },
                    placeholder = { Text("Yerba, aceite de oliva…") },
                    singleLine = true,
                    maxLines = 1,
                    enabled = state.mode != ArgentinaLiveSearchSurfaceMode.LOADING,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = MaterialTheme.shapes.large
                )
                PrimaryActionButton(
                    label = if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) "Buscando…" else "Buscar productos",
                    onClick = onSubmit,
                    enabled = query.trim().isNotEmpty() &&
                        query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS &&
                        state.mode != ArgentinaLiveSearchSurfaceMode.LOADING
                )
                Text(
                    "La búsqueda admite hasta " + ArgentinaBackendContract.MAX_QUERY_CHARS + " caracteres.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "search-state") {
            SearchStatePanel(state)
        }

        if (isSearchUnavailable(state.mode) &&
            state.presentation?.matches.isNullOrEmpty()
        ) {
            item(key = "layout-fixture-entry") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(ValuePilotSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
                    ) {
                        Eyebrow("MUESTRA AISLADA · SOLO PREVIEW")
                        Text(
                            "Revisá el diseño con un producto de ejemplo.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "No es un resultado real de Search y no incluye precios ni tiendas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onShowDesignFixture,
                            modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        ) {
                            Text(if (showDesignFixture) "Muestra visible" else "Ver muestra de diseño")
                        }
                    }
                }
            }
        }

        val presentation = state.presentation
        val matches = presentation?.matches.orEmpty()
        if (showDesignFixture) {
            item(key = "layout-fixture-label") {
                EvidenceNotice(
                    "MUESTRA LOCAL DE DISEÑO · no proviene de Search. Sin precio, tienda ni disponibilidad."
                )
            }
            item(key = "layout-fixture-product") {
                SearchResultCard(
                    match = ValuePilotNextLayoutFixture.match,
                    onClick = { onOpenMatch(ValuePilotNextLayoutFixture.match) },
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
                            SearchResultsCount(matches.size)
                            Text(
                                "Página " + ((presentation?.page ?: 0) + 1),
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
                            SearchResultsCount(matches.size)
                            Text(
                                "Página " + ((presentation?.page ?: 0) + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(matches, key = { it.identityKey }) { match ->
                SearchResultCard(
                    match = match,
                    onClick = { onOpenMatch(match) },
                    onOpenGoogleImages = { onOpenGoogleImages(match, state.areaLabel) }
                )
            }
            if (state.mode == ArgentinaLiveSearchSurfaceMode.LOADING) {
                item(key = "search-more-loading") {
                    LoadingRow("Cargando más identidades")
                }
            } else if (presentation?.hasMore == true && presentation.nextPage != null) {
                item(key = "search-load-more") {
                    SecondaryActionButton("Ver más resultados", onLoadMore)
                }
            }
            item(key = "search-caveat") {
                EvidenceFootnote(state.caveat)
            }
        }
    }
}

@Composable
private fun SearchResultsCount(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Eyebrow("PRODUCTOS ENCONTRADOS")
        Text(
            count.toString() + if (count == 1) " resultado" else " resultados",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (unavailable) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (unavailable) MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        Row(
            modifier = Modifier.padding(ValuePilotSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium),
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
            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                Text(
                    when (state.mode) {
                        ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE ->
                            "La búsqueda en vivo no está autorizada en esta vista previa."
                        ArgentinaLiveSearchSurfaceMode.STALE_RELEASE ->
                            "El servicio de búsqueda necesita una versión actualizada."
                        ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE ->
                            "La búsqueda no está disponible por ahora."
                        ArgentinaLiveSearchSurfaceMode.TIMEOUT ->
                            "La búsqueda superó el tiempo de espera."
                        ArgentinaLiveSearchSurfaceMode.LOADING -> "Buscando productos…"
                        else -> state.statusMessage
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    state.caveat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    onClick: () -> Unit,
    onOpenGoogleImages: () -> Unit
) {
    val currentOffer = match.qualifiedOffers.firstOrNull {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
            val stackActions = stacksHomeQuickActions(maxWidth, LocalDensity.current.fontScale)
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
            ) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                        SearchIdentitySummary(match)
                        currentOffer?.let { SearchPriceLabel(it, Modifier.align(Alignment.End)) }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium),
                        verticalAlignment = Alignment.Top
                    ) {
                        SearchIdentitySummary(match, Modifier.weight(1f))
                        currentOffer?.let { SearchPriceLabel(it) }
                    }
                }
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                        SearchMatchPill(match.matchLabel)
                        SearchFreshnessLabel(currentOffer != null)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchMatchPill(match.matchLabel)
                        SearchFreshnessLabel(currentOffer != null)
                    }
                }
                if (currentOffer != null) {
                    val unit = currentOffer.unitPrice?.let { value ->
                        val basis = currentOffer.unitPriceBasis?.takeIf(String::isNotBlank)
                        value.currency + " " + value.amount + (basis?.let { " / " + it } ?: "")
                    }
                    Text(
                        listOfNotNull(unit, currentOffer.storeName, "Disponibilidad desconocida").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (match.identityKey == ValuePilotNextLayoutFixture.IDENTITY_KEY) {
                            "Muestra local de interfaz. No es un resultado real de Search."
                        } else if (match.qualifiedOffers.isNotEmpty()) {
                            "La evidencia de precio no es lo bastante reciente para mostrarla como actual."
                        } else {
                            "No hay un precio actual ni una oferta de tienda asociados a esta identidad."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stackActions) {
                    Column {
                        TextButton(
                            onClick = onClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        ) {
                            Text("Ver detalles")
                        }
                        GoogleImagesAction(match, onOpenGoogleImages, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onClick,
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                        ) {
                            Text("Ver detalles")
                        }
                        GoogleImagesAction(match, onOpenGoogleImages, Modifier.weight(1.2f))
                    }
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
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ValuePilotSpacing.minimumTouchTarget)
            .semantics {
                contentDescription = "Ver fotos de ${match.displayName} en Google Images"
            }
    ) {
        Text("Ver en Google Images")
    }
}

@Composable
private fun SearchIdentitySummary(
    match: PracticalShoppingSearchIdentityPresentation.Match,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Text(
            match.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        listOfNotNull(match.brand, match.packageQuantityLabel)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}

@Composable
private fun SearchPriceLabel(
    offer: ArgentinaBackendContract.SearchOffer,
    modifier: Modifier = Modifier
) {
    Text(
        offer.listPrice.currency + " " + offer.listPrice.amount,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SearchMatchPill(matchLabel: String) {
    EvidencePill(
        localizedMatchLabel(matchLabel).uppercase(),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer
    )
}

private fun localizedMatchLabel(matchLabel: String): String = when (matchLabel.lowercase()) {
    "exact barcode" -> "código exacto"
    "exact name" -> "nombre exacto"
    "name match" -> "coincidencia de nombre"
    "name prefix" -> "inicio del nombre"
    "possible typo match" -> "posible error de escritura"
    else -> matchLabel
}

@Composable
private fun SearchFreshnessLabel(hasFreshPrice: Boolean) {
    Text(
        if (hasFreshPrice) "Precio publicado reciente" else "Solo identidad",
        style = MaterialTheme.typography.labelMedium,
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
    PremiumColumn {
        if (match == null) {
            item(key = "product-detail-unavailable") {
                if (isSearchLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = ValuePilotSpacing.section),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Restaurando la identidad seleccionada…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    EmptyStateCard(
                        eyebrow = "PRODUCTO SELECCIONADO",
                        title = "Esta selección ya no está en los resultados actuales.",
                        detail = "Volvé a buscar para actualizar su identidad y cualquier evidencia de precio.",
                        actionLabel = "Volver a Buscar",
                        onAction = onSearchAgain
                    )
                }
            }
        } else {
            if (isDesignFixture) {
                item(key = "product-detail-fixture-disclosure") {
                    EvidenceNotice(
                        "MUESTRA LOCAL DE DISEÑO · no proviene de Search y no se guardará en tus datos."
                    )
                }
            }
            item(key = "product-detail-identity") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                    Eyebrow(if (isDesignFixture) "VISTA PREVIA" else "DETALLE DEL PRODUCTO")
                    Text(match.displayName, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                    listOfNotNull(match.brand, match.packageQuantityLabel)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ")
                        ?.let {
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
                        if (compact) {
                            Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                if (isDesignFixture) {
                                    EvidencePill(
                                        "SOLO VISTA PREVIA · SIN DATOS REALES",
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    SearchMatchPill(match.matchLabel)
                                    match.sourceLabel?.let {
                                        EvidencePill(
                                            it.uppercase(),
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)) {
                                if (isDesignFixture) {
                                    EvidencePill(
                                        "SOLO VISTA PREVIA · SIN DATOS REALES",
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    SearchMatchPill(match.matchLabel)
                                    match.sourceLabel?.let {
                                        EvidencePill(
                                            it.uppercase(),
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "La identidad, la cantidad y el precio publicado son datos distintos. Lo que falta no se completa por inferencia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "product-detail-google-images") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
                ) {
                    Column(
                        modifier = Modifier.padding(ValuePilotSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.small)
                    ) {
                        Eyebrow("FOTOGRAFÍA DEL PRODUCTO")
                        Text(
                            "Sin imagen en el catálogo de ValuePilot.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Google controla los resultados. ValuePilot no descarga ni verifica esas imágenes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryActionButton(
                            label = "Ver en Google Images",
                            onClick = { onOpenGoogleImages(match, areaLabel) }
                        )
                    }
                }
            }
            item(key = "product-detail-offers-title") {
                SectionHeading(
                    "PRECIOS Y TIENDAS",
                    if (match.qualifiedOffers.isEmpty()) "Sin precio actual asociado" else "Precios informados por la fuente"
                )
            }
            if (match.qualifiedOffers.isEmpty()) {
                item(key = "product-detail-no-offer") {
                    EmptyStateCard(
                        eyebrow = if (isDesignFixture) "MUESTRA SIN DATOS" else "SOLO IDENTIDAD",
                        title = "Precio y tienda desconocidos.",
                        detail = if (isDesignFixture) {
                            "Esta muestra sirve solo para revisar la interfaz. No tiene datos de precio ni de tienda."
                        } else {
                            "La identidad del producto no incluye evidencia de precio publicada por una fuente."
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
                    EvidenceFootnote("Esta muestra no se puede añadir a la canasta ni se guarda en los datos privados.")
                }
            } else {
                item(key = "product-detail-add") {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    PrimaryActionButton(
                        label = "Añadir a tu canasta privada",
                        onClick = { onAddToBasket(match) },
                        enabled = !match.sourceLabel.isNullOrBlank()
                    )
                    EvidenceFootnote(
                        "Solo se guarda la identidad con una fuente explícita. La evidencia de precio de Search es temporal y se vuelve a revisar antes de decidir."
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferEvidenceCard(
    offer: ArgentinaBackendContract.SearchOffer,
    onOpenMapContext: () -> Unit
) {
    val fresh = offer.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    val latitude = offer.storeLatitude.toBigDecimalOrNull()
    val longitude = offer.storeLongitude.toBigDecimalOrNull()
    val validCoordinates =
        latitude != null && latitude >= java.math.BigDecimal("-90") && latitude <= java.math.BigDecimal("90") &&
            longitude != null && longitude >= java.math.BigDecimal("-180") && longitude <= java.math.BigDecimal("180")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = ValuePilotElevation.flat)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = usesCompactDiscoveryContent(maxWidth, LocalDensity.current.fontScale)
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.large),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
            ) {
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                        Eyebrow(if (fresh) "FRESH LISTED PRICE" else "NOT CURRENT")
                        Text(
                            offer.listPrice.currency + " " + offer.listPrice.amount,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OfferFreshnessPill(offer, fresh)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
                            Eyebrow(if (fresh) "FRESH LISTED PRICE" else "NOT CURRENT")
                            Text(
                                offer.listPrice.currency + " " + offer.listPrice.amount,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OfferFreshnessPill(offer, fresh)
                    }
                }
                Text(offer.storeName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                offer.quantity?.let {
                    Text(
                        "Package " + it.amount + " " + it.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text("Package quantity unknown", color = MaterialTheme.colorScheme.onSurfaceVariant)
                offer.unitPrice?.let { unit ->
                    val basis = offer.unitPriceBasis?.takeIf(String::isNotBlank)
                    Text(
                        "Unit price  " + unit.currency + " " + unit.amount + (basis?.let { " / " + it } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } ?: Text("Unit price not supplied by this offer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                EvidencePill(
                    "AVAILABILITY UNKNOWN",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    offer.provenance.provider + " · release " + offer.releaseDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "A published price does not confirm stock, pickup, delivery or a best-value ranking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (validCoordinates) {
                    TextButton(
                        onClick = onOpenMapContext,
                        modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)
                    ) {
                        Text("View source store context")
                    }
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
        offer.freshnessStatus.name.replace('_', ' '),
        if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        if (fresh) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    )
}
