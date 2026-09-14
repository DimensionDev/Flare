@file:OptIn(dev.dimension.compose.nativekit.navigation.ExperimentalNativeKitNavigation::class)

package dev.dimension.compose.nativekit.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.entryProvider
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.demo.resources.DemoRes
import dev.dimension.compose.nativekit.foundation.Column
import dev.dimension.compose.nativekit.foundation.HorizontalAlignment
import dev.dimension.compose.nativekit.foundation.NativeButton
import dev.dimension.compose.nativekit.foundation.Row
import dev.dimension.compose.nativekit.foundation.Text
import dev.dimension.compose.nativekit.foundation.VerticalAlignment
import dev.dimension.compose.nativekit.lazy.LazyColumn
import dev.dimension.compose.nativekit.lazy.LazyRow
import dev.dimension.compose.nativekit.navigation.NavigationDisplay
import dev.dimension.compose.nativekit.resources.moko.ResourceImage
import dev.dimension.compose.nativekit.resources.moko.imageResource
import dev.dimension.compose.nativekit.resources.moko.pluralStringResource
import dev.dimension.compose.nativekit.resources.moko.stringResource

/** Navigation-driven catalog shared by every platform host. */
@Composable
@NativeKitComposable
public fun NativeKitDemoContent() {
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
        modifier = NativeKitModifier(testTag = "demo-navigation").fillMaxSize(),
        onBack = { request -> request.applyTo(backStack) },
        entryProvider = demoEntryProvider,
    )
}

@Composable
@NativeKitComposable
private fun CatalogScreen(
    onOpenResources: () -> Unit,
    onOpenLazyLayouts: () -> Unit,
) {
    Column(
        modifier = NativeKitModifier(testTag = "demo-catalog").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        ResourceImage(
            image = imageResource(DemoRes.images.nativekit_mark),
            contentDescription = stringResource(DemoRes.strings.nativekit_mark_description),
            modifier =
                NativeKitModifier(testTag = "demo-catalog-image")
                    .width(DEMO_IMAGE_SIZE)
                    .height(DEMO_IMAGE_SIZE),
        )
        Text(
            text = stringResource(DemoRes.strings.catalog_title),
            modifier = NativeKitModifier(testTag = "demo-catalog-title"),
        )
        Text(stringResource(DemoRes.strings.catalog_description))
        NativeButton(
            label = stringResource(DemoRes.strings.open_resources_feature),
            modifier = NativeKitModifier(testTag = "demo-open-resources").fillMaxWidth(),
            onClick = onOpenResources,
        )
        Text(stringResource(DemoRes.strings.resources_feature_description))
        NativeButton(
            label = stringResource(DemoRes.strings.open_lazy_layouts_feature),
            modifier = NativeKitModifier(testTag = "demo-open-lazy-layouts").fillMaxWidth(),
            onClick = onOpenLazyLayouts,
        )
        Text(stringResource(DemoRes.strings.lazy_layouts_feature_description))
    }
}

@Composable
@NativeKitComposable
private fun ResourcesScreen(onBack: () -> Unit) {
    var count by remember { mutableIntStateOf(0) }

    Column(
        modifier = NativeKitModifier(testTag = "demo-resources").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        BackToCatalogButton(onBack)
        ResourceImage(
            image = imageResource(DemoRes.images.nativekit_mark),
            contentDescription = stringResource(DemoRes.strings.nativekit_mark_description),
            modifier =
                NativeKitModifier(testTag = "demo-image")
                    .width(DEMO_IMAGE_SIZE)
                    .height(DEMO_IMAGE_SIZE),
        )
        Text(
            text = stringResource(DemoRes.strings.demo_title),
            modifier = NativeKitModifier(testTag = "demo-title"),
        )
        Text(stringResource(DemoRes.strings.demo_description))
        Text(
            text = stringResource(DemoRes.strings.count_format, count),
            modifier = NativeKitModifier(testTag = "demo-count"),
        )
        Text(
            text = pluralStringResource(DemoRes.plurals.update_count, count, count),
            modifier = NativeKitModifier(testTag = "demo-updates"),
        )
        Row(
            modifier = NativeKitModifier(testTag = "demo-actions"),
            spacing = DEMO_ITEM_SPACING,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            NativeButton(
                label = stringResource(DemoRes.strings.increment),
                modifier = NativeKitModifier(testTag = "demo-increment"),
                onClick = { count += 1 },
            )
            NativeButton(
                label = stringResource(DemoRes.strings.reset),
                modifier = NativeKitModifier(testTag = "demo-reset"),
                enabled = count != 0,
                onClick = { count = 0 },
            )
        }
    }
}

@Composable
@NativeKitComposable
private fun LazyLayoutsScreen(onBack: () -> Unit) {
    Column(
        modifier = NativeKitModifier(testTag = "demo-lazy-layouts").fillMaxWidth(),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        BackToCatalogButton(onBack)
        Text(stringResource(DemoRes.strings.lazy_row_title))
        LazyRow(
            modifier =
                NativeKitModifier(testTag = "demo-lazy-row")
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
                        NativeKitModifier(testTag = "demo-lazy-row-item-$index")
                            .width(DEMO_CARD_WIDTH)
                            .height(DEMO_CARD_HEIGHT),
                )
            }
        }
        Text(stringResource(DemoRes.strings.lazy_column_title))
        LazyColumn(
            modifier =
                NativeKitModifier(testTag = "demo-lazy-column")
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
                        NativeKitModifier(testTag = "demo-lazy-column-item-$index")
                            .height(DEMO_ITEM_HEIGHT),
                )
            }
        }
    }
}

@Composable
@NativeKitComposable
private fun BackToCatalogButton(onBack: () -> Unit) {
    NativeButton(
        label = stringResource(DemoRes.strings.back_to_catalog),
        modifier = NativeKitModifier(testTag = "demo-back"),
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
