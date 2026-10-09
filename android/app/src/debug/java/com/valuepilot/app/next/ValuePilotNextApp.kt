package com.valuepilot.app.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.valuepilot.app.AppPrimaryTab
import com.valuepilot.app.AppRoute
import com.valuepilot.app.AppShellIntent
import com.valuepilot.app.AppShellReducer
import com.valuepilot.app.AppShellState
import com.valuepilot.app.R

private data class PrimaryDestination(
    val tab: AppPrimaryTab,
    val label: String,
    val iconResource: Int
)

private val primaryDestinations = listOf(
    PrimaryDestination(AppPrimaryTab.HOME, "Home", R.drawable.ic_vp_home),
    PrimaryDestination(AppPrimaryTab.SEARCH, "Search", R.drawable.ic_vp_search),
    PrimaryDestination(AppPrimaryTab.BASKET, "Basket", R.drawable.ic_vp_basket),
    PrimaryDestination(AppPrimaryTab.SAVED, "Saved", R.drawable.ic_vp_saved)
)

private val appShellStateSaver = listSaver<AppShellState, String>(
    save = { state ->
        listOf(
            state.selectedPrimaryTab.name,
            state.route.name,
            state.compareReturnTab?.name.orEmpty(),
            state.canNavigateBack.toString()
        )
    },
    restore = { saved ->
        val selectedTab =
            AppPrimaryTab.entries.firstOrNull { it.name == saved.getOrNull(0) }
                ?: AppPrimaryTab.HOME
        val route =
            AppRoute.entries.firstOrNull { it.name == saved.getOrNull(1) }
                ?: AppShellReducer.routeFor(selectedTab)
        val returnTab =
            AppPrimaryTab.entries.firstOrNull { it.name == saved.getOrNull(2) }

        AppShellState(
            selectedPrimaryTab = selectedTab,
            route = route,
            compareReturnTab = returnTab,
            canNavigateBack = saved.getOrNull(3)?.toBooleanStrictOrNull() ?: false
        )
    }
)

@Composable
internal fun ValuePilotNextApp(
    reducedMotion: Boolean,
    onExitPreview: () -> Unit,
    onOpenComparison: () -> Unit
) {
    var shellState by rememberSaveable(stateSaver = appShellStateSaver) {
        mutableStateOf(AppShellState.initial())
    }

    androidx.activity.compose.BackHandler(enabled = shellState.canNavigateBack) {
        shellState = AppShellReducer.reduce(shellState, AppShellIntent.NavigateBack)
    }

    fun dispatch(intent: AppShellIntent) {
        val next = AppShellReducer.reduce(shellState, intent)
        shellState = next

        if (next.route == AppRoute.COMPARE) {
            onOpenComparison()
            shellState = AppShellReducer.reduce(next, AppShellIntent.NavigateBack)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 720.dp
            val safeInsets =
                if (expanded) {
                    WindowInsets.safeDrawing
                } else {
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    )
                }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeInsets)
            ) {
                if (expanded) {
                    ValuePilotNavigationRail(
                        selectedTab = shellState.selectedPrimaryTab,
                        onSelectTab = { tab ->
                            dispatch(AppShellIntent.SelectPrimary(tab))
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    ValuePilotNextHeader(onExitPreview = onExitPreview)

                    val enterDuration = ValuePilotMotion.durationMillis(
                        ValuePilotMotion.standardMillis,
                        reducedMotion
                    )
                    val exitDuration = ValuePilotMotion.durationMillis(
                        ValuePilotMotion.quickMillis,
                        reducedMotion
                    )

                    AnimatedContent(
                        targetState = shellState.route,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = tween(enterDuration)) +
                                    slideInHorizontally(
                                        initialOffsetX = { width -> width / 24 },
                                        animationSpec = tween(enterDuration)
                                    )
                                ) togetherWith (
                                fadeOut(animationSpec = tween(exitDuration)) +
                                    slideOutHorizontally(
                                        targetOffsetX = { width -> -width / 24 },
                                        animationSpec = tween(exitDuration)
                                    )
                                )
                        },
                        label = "ValuePilot destination"
                    ) { route ->
                        when (route) {
                            AppRoute.HOME -> ValuePilotHomeScreen(
                                onSearch = {
                                    dispatch(
                                        AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH)
                                    )
                                },
                                onCompare = {
                                    dispatch(AppShellIntent.OpenStandaloneCompare)
                                },
                                onExitPreview = onExitPreview
                            )

                            AppRoute.SEARCH -> MigrationStatusScreen(
                                title = "Search",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_search,
                                message = "The current interface connects Search to production-authorized offers for its configured area. Home's local sample planning flow stays separate. This Compose preview does not query or change offer data.",
                                onReturn = onExitPreview
                            )

                            AppRoute.BASKET -> MigrationStatusScreen(
                                title = "Basket",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_basket,
                                message = "Basket planning and trip-cost calculation remain in the current interface. This Compose preview does not read or change the existing basket state.",
                                onReturn = onExitPreview
                            )

                            AppRoute.SAVED -> MigrationStatusScreen(
                                title = "Saved",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_saved,
                                message = "Saved lists, private price observations, Staple Watch, and on-device price history remain in the current interface. This Compose preview does not access or change saved data.",
                                onReturn = onExitPreview
                            )

                            AppRoute.OBSERVED_PRICE_SAVED_SELECTION,
                            AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT -> MigrationStatusScreen(
                                title = "Private price observation",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_saved,
                                message = "The existing Saved flow keeps user-entered price observations on device. This Compose preview does not read or alter those records.",
                                onReturn = onExitPreview
                            )

                            AppRoute.STAPLE_WATCH_SETUP,
                            AppRoute.STAPLE_WATCH_POLICY -> MigrationStatusScreen(
                                title = "Staple Watch",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_saved,
                                message = "The existing Saved flow contains foreground Staple Watch setup and policy screens. This Compose preview does not run a Watch evaluation.",
                                onReturn = onExitPreview
                            )

                            AppRoute.COMPARE -> MigrationStatusScreen(
                                title = "Compare products",
                                status = "AVAILABLE IN CURRENT APP",
                                statusContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                statusContentColor = ValuePilotSemanticColors.ready(),
                                iconResource = R.drawable.ic_vp_basket,
                                message = "The current comparison screen still owns its package and price calculations. This Compose preview opens that existing screen instead of recreating the comparison engine.",
                                onReturn = onExitPreview
                            )
                        }
                    }

                    if (!expanded) {
                        ValuePilotNavigationBar(
                            selectedTab = shellState.selectedPrimaryTab,
                            onSelectTab = { tab ->
                                dispatch(AppShellIntent.SelectPrimary(tab))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuePilotNextHeader(onExitPreview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ValuePilotSpacing.xxLarge, vertical = ValuePilotSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "V",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ValuePilot",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "NEXT PREVIEW",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        TextButton(
            onClick = onExitPreview,
            modifier = Modifier.sizeIn(
                minWidth = ValuePilotSpacing.minimumTouchTarget,
                minHeight = ValuePilotSpacing.minimumTouchTarget
            ),
            contentPadding = PaddingValues(horizontal = ValuePilotSpacing.medium)
        ) {
            Text("Current app")
        }
    }
}

@Composable
private fun ValuePilotNavigationBar(
    selectedTab: AppPrimaryTab,
    onSelectTab: (AppPrimaryTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = ValuePilotElevation.flat
    ) {
        primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedTab == destination.tab,
                onClick = { onSelectTab(destination.tab) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = null
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ValuePilotNavigationRail(
    selectedTab: AppPrimaryTab,
    onSelectTab: (AppPrimaryTab) -> Unit
) {
    NavigationRail(
        modifier = Modifier
            .widthIn(min = 88.dp, max = 104.dp)
            .fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        primaryDestinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedTab == destination.tab,
                onClick = { onSelectTab(destination.tab) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconResource),
                        contentDescription = null
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ValuePilotHomeScreen(
    onSearch: () -> Unit,
    onCompare: () -> Unit,
    onExitPreview: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = ValuePilotSpacing.xxLarge,
                vertical = ValuePilotSpacing.xLarge
            ),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xxLarge)
        ) {
            item(key = "welcome") {
                Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
                    Text(
                        text = "VALUE, MADE CLEAR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Shop with the full picture.",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Compare what you get with what you pay, with the evidence kept in view.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "value-principles") {
                ValuePrinciplesCard()
            }

            item(key = "comparison") {
                ComparisonActionCard(onCompare = onCompare)
            }

            item(key = "migration-status") {
                PreviewStatusCard(
                    onSearch = onSearch,
                    onExitPreview = onExitPreview
                )
            }
        }
    }
}

@Composable
private fun ValuePrinciplesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = ValuePilotElevation.flat
        )
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.xxLarge),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)
        ) {
            StatusPill(
                text = "BUILT AROUND CLARITY",
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "A price means more when you see the amount behind it.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "ValuePilot keeps package quantity, unit price, and source quality together so a good-looking number does not tell the whole story.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ComparisonActionCard(onCompare: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = ValuePilotElevation.flat
        ),
        border = BorderStroke(
            ValuePilotSpacing.hairline,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(ValuePilotSpacing.xxLarge),
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
        ) {
            Text(
                text = "READY TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Compare products on your device.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "The existing comparison screen handles package amounts and exact price calculations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCompare,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(
                    horizontal = ValuePilotSpacing.xLarge,
                    vertical = ValuePilotSpacing.large
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Compare products", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PreviewStatusCard(
    onSearch: () -> Unit,
    onExitPreview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)) {
        Text(
            text = "CURRENT APP CAPABILITIES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = ValuePilotElevation.flat
            ),
            border = BorderStroke(
                ValuePilotSpacing.hairline,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(ValuePilotSpacing.xLarge),
                verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.medium)
            ) {
                CapabilityLine(
                    title = "Search",
                    detail = "Production-authorized Search is available for its configured area; this preview does not query it."
                )
                CapabilityLine(
                    title = "Home planning",
                    detail = "The current Home planner uses a separate local sample model; it is not production offer data."
                )
                CapabilityLine(
                    title = "Basket, trips, and Saved",
                    detail = "Existing screens retain basket planning, trip costs, saved lists, private price observations, and Staple Watch."
                )
                TextButton(
                    onClick = onSearch,
                    modifier = Modifier.heightIn(
                        min = ValuePilotSpacing.minimumTouchTarget
                    ),
                    contentPadding = PaddingValues(horizontal = ValuePilotSpacing.small)
                ) {
                    Text("Search migration status")
                }
                TextButton(
                    onClick = onExitPreview,
                    modifier = Modifier.heightIn(
                        min = ValuePilotSpacing.minimumTouchTarget
                    ),
                    contentPadding = PaddingValues(horizontal = ValuePilotSpacing.small)
                ) {
                    Text("Return to current interface")
                }
            }
        }
    }
}

@Composable
private fun CapabilityLine(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.xSmall)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MigrationStatusScreen(
    title: String,
    status: String,
    statusContainerColor: Color,
    statusContentColor: Color,
    iconResource: Int,
    message: String,
    onReturn: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(ValuePilotSpacing.xxLarge),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(ValuePilotSpacing.large)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconResource),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            StatusPill(
                text = status,
                containerColor = statusContainerColor,
                contentColor = statusContentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onReturn,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Return to current interface")
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = ValuePilotSpacing.medium,
                vertical = ValuePilotSpacing.small
            ),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 10.sp
        )
    }
}

@Preview(showSystemUi = true, name = "ValuePilot Next · Sunlit Intelligence")
@Composable
private fun ValuePilotNextPreview() {
    ValuePilotNextTheme {
        ValuePilotNextApp(
            reducedMotion = true,
            onExitPreview = {},
            onOpenComparison = {}
        )
    }
}
