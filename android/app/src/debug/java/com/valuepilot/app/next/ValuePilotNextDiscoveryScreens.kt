package com.valuepilot.app.next

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
                Eyebrow("PRECIOS UNITARIOS NORMALIZADOS")
                EvidencePill(
                    areaLabel.uppercase(),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Decidí mejor en cada compra.",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 23.sp,
                        lineHeight = 29.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Compará precios publicados y costo del recorrido. Cada dato conserva su evidencia.",
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
                eyebrow = "TU CANASTA PRIVADA",
                title = if (basketCount == 0) "Canasta lista para empezar" else "$basketCount productos guardados",
                detail = if (basketCount == 0) {
                    "Sumá identidades elegidas. Los precios se confirman por separado."
                } else {
                    "Revisá tus productos y consultá una comparación cuando haya evidencia disponible."
                },
                actionLabel = "Ver canasta",
                onClick = onBasket
            )
        }
        item(key = "home-trip-planner") {
            HomeFeatureCard(
                eyebrow = "PLANIFICADOR DE RECORRIDOS",
                title = "Planificá tu recorrido de compra.",
                detail = "Ingresá distancia, combustible, peajes y estacionamiento.",
                actionLabel = "Calcular recorrido",
                onClick = onTripPlanner
            )
        }
        item(key = "home-quick-tools") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stack = stacksHomeQuickActions(maxWidth, LocalDensity.current.fontScale)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("HERRAMIENTAS")
                    if (stack) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeQuickAction("Verificar precio", onGoodPriceCheck, Modifier.fillMaxWidth())
                            HomeQuickAction("Comparar productos", onCompare, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeQuickAction("Verificar precio", onGoodPriceCheck, Modifier.weight(1f))
                            HomeQuickAction("Comparar productos", onCompare, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item(key = "home-principle") {
            EvidenceFootnote("Un precio publicado no confirma stock, disponibilidad ni ahorro verificado.")
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
                Text("Buscar productos", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface)
                Text("Nombre, marca o código de barras", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Buscar  →", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.primary)
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
    onLoadMore: () -> Unit,
    onOpenMatch: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onAddToBasket: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit,
    onOpenGoogleImages: (PracticalShoppingSearchIdentityPresentation.Match, String) -> Unit,
    onShowDesignFixture: () -> Unit
) {
    ValuePilotDiscoveryColumn {
        item(key = "search-intro") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow("BUSCAR PRODUCTOS")
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
                        "Probá una búsqueda",
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
                        Eyebrow("MUESTRA AISLADA · SOLO PREVIEW")
                        Text(
                            "Vista de producto sin precio ni tienda.",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        CompactTextAction(
                            if (showDesignFixture) "Muestra visible" else "Ver muestra de diseño",
                            onClick = onShowDesignFixture,
                        )
                    }
                }
            }
        }

        val presentation = state.presentation
        val matches = presentation?.matches.orEmpty()
        if (showDesignFixture) {
            item(key = "layout-fixture-label") {
                EvidenceNotice("MUESTRA LOCAL · no proviene de Search. Sin precio, tienda ni disponibilidad.")
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
                    isDesignFixture = false,
                    onClick = { onOpenMatch(match) },
                    onAddToBasket = { onAddToBasket(match) },
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
private fun SearchProductField(
    query: String,
    enabled: Boolean,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val submitEnabled = enabled && query.isNotBlank() &&
        query.length <= ArgentinaBackendContract.MAX_QUERY_CHARS
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
                    .semantics { contentDescription = "Buscar un producto por nombre o marca" },
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
                                "Nombre, marca o producto…",
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
                        .semantics { contentDescription = "Borrar búsqueda" }
                        .padding(horizontal = 5.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = { if (submitEnabled) onSubmit() },
                enabled = submitEnabled,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Buscar productos" },
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
        count.toString() + if (count == 1) " identidad" else " identidades",
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
                        ArgentinaLiveSearchSurfaceMode.AUTHORIZATION_UNAVAILABLE -> "Live Search no está autorizado en esta vista previa."
                        ArgentinaLiveSearchSurfaceMode.STALE_RELEASE -> "La búsqueda necesita una versión actualizada."
                        ArgentinaLiveSearchSurfaceMode.TEMPORARILY_UNAVAILABLE -> "La búsqueda no está disponible por ahora."
                        ArgentinaLiveSearchSurfaceMode.TIMEOUT -> "La búsqueda superó el tiempo de espera."
                        ArgentinaLiveSearchSurfaceMode.LOADING -> "Buscando productos…"
                        ArgentinaLiveSearchSurfaceMode.NO_MATCHES -> "No hay una identidad coincidente en esta búsqueda."
                        else -> state.statusMessage
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "No se muestran precios, tiendas ni disponibilidad sin evidencia de la fuente.",
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
    onOpenGoogleImages: () -> Unit
) {
    val currentOffer = match.qualifiedOffers.firstOrNull {
        it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Ver detalles de ${match.displayName}" },
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
                SearchMatchPill(if (isDesignFixture) "MUESTRA DE DISEÑO" else localizedMatchLabel(match.matchLabel))
                match.packageQuantityLabel?.let { EvidencePill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
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
                    "La fuente no confirma un precio actual.",
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
                    if (unitPrice == null) "PRECIO PUBLICADO" else "PRECIO UNITARIO NORMALIZADO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.45.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unitPrice == null) {
                    Text(
                        "${offer.listPrice.currency} ${offer.listPrice.amount}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${unitPrice.currency} ${unitPrice.amount}",
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
                        "PRECIO PUBLICADO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.35.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${offer.listPrice.currency} ${offer.listPrice.amount}",
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Buscar fotos de ${match.displayName} en Google Images" },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Ver en Google Images", style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Agregar ${match.displayName} a tu canasta privada" },
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("＋  Agregar", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp), maxLines = 1)
        }
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
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        listOfNotNull(match.brand, match.packageQuantityLabel)
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
    Text(
        offer.listPrice.currency + " " + offer.listPrice.amount,
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
    ValuePilotDiscoveryColumn {
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
                    CompactEvidenceNotice("MUESTRA LOCAL · no proviene de Search ni se guardará en tus datos.")
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
                            EvidencePill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        EvidencePill(
                            if (isDesignFixture) "CABA · MUESTRA" else "${areaLabel.uppercase()} · ARGENTINA",
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
                            Text("VISTA DE DISEÑO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.secondary)
                        } else {
                            Text(localizedMatchLabel(match.matchLabel), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.secondary)
                            match.sourceLabel?.let {
                                Text("· $it", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        if (isDesignFixture) "La muestra no incluye marca, precio ni tipo de envase." else "La identidad no confirma precio, stock ni disponibilidad.",
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
                                    "Sin fotos en el catálogo oficial",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Revisá el envase real en la web antes de elegir.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        PrimaryDiscoveryAction(
                            label = "Ver fotos en Google Images  ↗",
                            onClick = { onOpenGoogleImages(match, areaLabel) }
                        )
                        Text(
                            "Google controla los resultados; ValuePilot no descarga ni verifica imágenes.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item(key = "product-detail-offers-title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow("PRECIOS Y TIENDAS")
                    Text(
                        if (match.qualifiedOffers.isEmpty()) "Precio actual no informado" else "Precios relevados",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (match.qualifiedOffers.isEmpty()) {
                item(key = "product-detail-no-offer") {
                    CompactEvidenceNotice(
                        if (isDesignFixture) "Muestra de diseño: no hay precio ni tienda asociados."
                        else "No hay evidencia de precio actual asociada a esta identidad."
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
                        "La muestra no se puede agregar a la canasta ni se guarda en tus datos.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item(key = "product-detail-add") {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PrimaryDiscoveryAction(
                            label = "Añadir a tu canasta privada",
                            onClick = { onAddToBasket(match) },
                            enabled = !match.sourceLabel.isNullOrBlank()
                        )
                        Text(
                            "Se guarda la identidad con fuente. El precio se vuelve a revisar antes de decidir.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
private fun PrimaryDiscoveryAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
                    Eyebrow(if (fresh) "PRECIO PUBLICADO RECIENTE" else "PRECIO SIN VIGENCIA ACTUAL")
                    Text(
                        offer.listPrice.currency + " " + offer.listPrice.amount,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OfferFreshnessPill(offer, fresh)
            }
            Text(offer.storeName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            offer.quantity?.let {
                Text(
                    "Envase · " + it.amount + " " + it.unit,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text("Cantidad del envase no informada", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            offer.unitPrice?.let { unit ->
                val basis = offer.unitPriceBasis?.takeIf(String::isNotBlank)
                Text(
                    "Precio unitario  " + unit.currency + " " + unit.amount + (basis?.let { " / $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.secondary
                )
            } ?: Text("Precio unitario no informado", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${offer.provenance.provider} · publicación ${offer.releaseDate}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "El precio publicado no confirma stock ni disponibilidad.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (validCoordinates) {
                TextButton(onClick = onOpenMapContext, modifier = Modifier.heightIn(min = ValuePilotSpacing.minimumTouchTarget)) {
                    Text("Ver sucursal en el mapa  ↗", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp))
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
        if (fresh) "RECIENTE" else "NO ACTUAL",
        if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        if (fresh) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    )
}
