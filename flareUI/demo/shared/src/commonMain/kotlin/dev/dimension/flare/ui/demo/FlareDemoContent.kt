@file:OptIn(dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.entryProvider
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.demo.resources.DemoRes
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Row
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.foundation.VerticalAlignment
import dev.dimension.flare.ui.lazy.LazyColumn
import dev.dimension.flare.ui.lazy.LazyRow
import dev.dimension.flare.ui.navigation.NavigationDisplay
import dev.dimension.flare.ui.resources.moko.ResourceImage
import dev.dimension.flare.ui.resources.moko.imageResource
import dev.dimension.flare.ui.resources.moko.pluralStringResource
import dev.dimension.flare.ui.resources.moko.stringResource

/** Navigation-driven catalog shared by every platform host. */
@Composable
@FlareUiComposable
public fun FlareDemoContent() {
    val backStack = remember { mutableStateListOf<DemoRoute>(DemoCatalog) }
    val demoEntryProvider =
        remember(backStack) {
            entryProvider<DemoRoute> {
                entry<DemoCatalog> {
                    CatalogScreen(
                        onOpenResources = { backStack.pushFromCatalog(DemoResources) },
                        onOpenLazyLayouts = { backStack.pushFromCatalog(DemoLazyLayouts) },
                    )
                }
                entry<DemoResources> {
                    ResourcesScreen(onBack = backStack::popOne)
                }
                entry<DemoLazyLayouts> {
                    LazyLayoutsScreen(onBack = backStack::popOne)
                }
            }
        }

    NavigationDisplay(
        backStack = backStack,
        modifier = FlareModifier(testTag = "demo-navigation").fillMaxSize(),
        onBack = { request -> request.applyTo(backStack) },
        entryProvider = demoEntryProvider,
    )
}

@Composable
@FlareUiComposable
private fun CatalogScreen(
    onOpenResources: () -> Unit,
    onOpenLazyLayouts: () -> Unit,
) {
    Column(
        modifier = FlareModifier(testTag = "demo-catalog").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        ResourceImage(
            image = imageResource(DemoRes.images.flare_mark),
            contentDescription = stringResource(DemoRes.strings.flare_mark_description),
            modifier =
                FlareModifier(testTag = "demo-catalog-image")
                    .width(DEMO_IMAGE_SIZE)
                    .height(DEMO_IMAGE_SIZE),
        )
        Text(
            text = stringResource(DemoRes.strings.catalog_title),
            modifier = FlareModifier(testTag = "demo-catalog-title"),
        )
        Text(stringResource(DemoRes.strings.catalog_description))
        NativeButton(
            label = stringResource(DemoRes.strings.open_resources_feature),
            modifier = FlareModifier(testTag = "demo-open-resources").fillMaxWidth(),
            onClick = onOpenResources,
        )
        Text(stringResource(DemoRes.strings.resources_feature_description))
        NativeButton(
            label = stringResource(DemoRes.strings.open_lazy_layouts_feature),
            modifier = FlareModifier(testTag = "demo-open-lazy-layouts").fillMaxWidth(),
            onClick = onOpenLazyLayouts,
        )
        Text(stringResource(DemoRes.strings.lazy_layouts_feature_description))
    }
}

@Composable
@FlareUiComposable
private fun ResourcesScreen(onBack: () -> Unit) {
    var count by remember { mutableIntStateOf(0) }

    Column(
        modifier = FlareModifier(testTag = "demo-resources").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        BackToCatalogButton(onBack)
        ResourceImage(
            image = imageResource(DemoRes.images.flare_mark),
            contentDescription = stringResource(DemoRes.strings.flare_mark_description),
            modifier =
                FlareModifier(testTag = "demo-image")
                    .width(DEMO_IMAGE_SIZE)
                    .height(DEMO_IMAGE_SIZE),
        )
        Text(
            text = stringResource(DemoRes.strings.demo_title),
            modifier = FlareModifier(testTag = "demo-title"),
        )
        Text(stringResource(DemoRes.strings.demo_description))
        Text(
            text = stringResource(DemoRes.strings.count_format, count),
            modifier = FlareModifier(testTag = "demo-count"),
        )
        Text(
            text = pluralStringResource(DemoRes.plurals.update_count, count, count),
            modifier = FlareModifier(testTag = "demo-updates"),
        )
        Row(
            modifier = FlareModifier(testTag = "demo-actions"),
            spacing = DEMO_ITEM_SPACING,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            NativeButton(
                label = stringResource(DemoRes.strings.increment),
                modifier = FlareModifier(testTag = "demo-increment"),
                onClick = { count += 1 },
            )
            NativeButton(
                label = stringResource(DemoRes.strings.reset),
                modifier = FlareModifier(testTag = "demo-reset"),
                enabled = count != 0,
                onClick = { count = 0 },
            )
        }
    }
}

@Composable
@FlareUiComposable
private fun LazyLayoutsScreen(onBack: () -> Unit) {
    Column(
        modifier = FlareModifier(testTag = "demo-lazy-layouts").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        BackToCatalogButton(onBack)
        Text(stringResource(DemoRes.strings.lazy_row_title))
        LazyRow(
            modifier =
                FlareModifier(testTag = "demo-lazy-row")
                    .fillMaxWidth()
                    .height(DEMO_LAZY_ROW_HEIGHT),
            spacing = DEMO_LAZY_ITEM_SPACING,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            items(
                count = DEMO_CARD_COUNT,
                key = { index -> "card-$index" },
                contentType = { "card" },
            ) { index ->
                Text(
                    text = stringResource(DemoRes.strings.lazy_card_format, index),
                    modifier =
                        FlareModifier(testTag = "demo-lazy-row-item-$index")
                            .width(DEMO_CARD_WIDTH)
                            .height(DEMO_CARD_HEIGHT),
                )
            }
        }
        Text(stringResource(DemoRes.strings.lazy_column_title))
        LazyColumn(
            modifier =
                FlareModifier(testTag = "demo-lazy-column")
                    .fillMaxWidth()
                    .height(DEMO_LAZY_COLUMN_HEIGHT),
            spacing = DEMO_LAZY_ITEM_SPACING,
        ) {
            items(
                count = DEMO_LAZY_ITEM_COUNT,
                key = { index -> index },
                contentType = { "item" },
            ) { index ->
                Text(
                    text = stringResource(DemoRes.strings.lazy_item_format, index),
                    modifier =
                        FlareModifier(testTag = "demo-lazy-column-item-$index")
                            .height(DEMO_ITEM_HEIGHT),
                )
            }
        }
    }
}

@Composable
@FlareUiComposable
private fun BackToCatalogButton(onBack: () -> Unit) {
    NativeButton(
        label = stringResource(DemoRes.strings.back_to_catalog),
        modifier = FlareModifier(testTag = "demo-back"),
        onClick = onBack,
    )
}

private fun MutableList<DemoRoute>.pop(popCount: Int) {
    require(popCount > 0) { "A catalog back request must pop at least one entry." }
    repeat(popCount) {
        if (size > 1) removeAt(lastIndex)
    }
}

private fun MutableList<DemoRoute>.popOne() {
    pop(1)
}

private fun MutableList<DemoRoute>.pushFromCatalog(route: DemoRoute) {
    if (size == 1 && firstOrNull() == DemoCatalog) add(route)
}

private sealed interface DemoRoute

private data object DemoCatalog : DemoRoute

private data object DemoResources : DemoRoute

private data object DemoLazyLayouts : DemoRoute

private const val DEMO_ITEM_SPACING: Float = 12f
private const val DEMO_IMAGE_SIZE: Float = 64f
private const val DEMO_LAZY_ITEM_SPACING: Float = 6f
private const val DEMO_LAZY_ROW_HEIGHT: Float = 80f
private const val DEMO_LAZY_COLUMN_HEIGHT: Float = 240f
private const val DEMO_CARD_WIDTH: Float = 96f
private const val DEMO_CARD_HEIGHT: Float = 56f
private const val DEMO_ITEM_HEIGHT: Float = 36f
private const val DEMO_CARD_COUNT: Int = 50
private const val DEMO_LAZY_ITEM_COUNT: Int = 10_000
