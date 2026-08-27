package dev.dimension.flare.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.dimension.flare.ui.resources.moko.ResourceImage
import dev.dimension.flare.ui.resources.moko.imageResource
import dev.dimension.flare.ui.resources.moko.pluralStringResource
import dev.dimension.flare.ui.resources.moko.stringResource

/** The complete demo screen shared by every platform host. */
@Composable
@FlareUiComposable
public fun FlareDemoContent() {
    var count by remember { mutableIntStateOf(0) }

    Column(
        modifier = FlareModifier(testTag = "demo-content"),
        spacing = DEMO_ITEM_SPACING,
        horizontalAlignment = HorizontalAlignment.Start,
    ) {
        ResourceImage(
            image = imageResource(DemoRes.images.flare_mark),
            contentDescription = stringResource(DemoRes.strings.flare_mark_description),
            modifier = FlareModifier(testTag = "demo-image"),
        )
        Text(stringResource(DemoRes.strings.demo_title))
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
        val lazyItemOffset = count
        LazyColumn(
            modifier =
                FlareModifier(testTag = "demo-lazy-column")
                    .fillMaxWidth()
                    .height(DEMO_LAZY_COLUMN_HEIGHT),
            spacing = DEMO_LAZY_ITEM_SPACING,
        ) {
            items(
                count = DEMO_LAZY_ITEM_COUNT + lazyItemOffset,
                key = { index -> index - lazyItemOffset },
                contentType = { "item" },
            ) { index ->
                val value = index - lazyItemOffset
                Text(
                    text = stringResource(DemoRes.strings.lazy_item_format, value),
                    modifier =
                        FlareModifier(testTag = "demo-lazy-column-item-$value")
                            .height(DEMO_ITEM_HEIGHT),
                )
            }
        }
    }
}

private const val DEMO_ITEM_SPACING: Float = 12f
private const val DEMO_LAZY_ITEM_SPACING: Float = 6f
private const val DEMO_LAZY_ROW_HEIGHT: Float = 80f
private const val DEMO_LAZY_COLUMN_HEIGHT: Float = 240f
private const val DEMO_CARD_WIDTH: Float = 96f
private const val DEMO_CARD_HEIGHT: Float = 56f
private const val DEMO_ITEM_HEIGHT: Float = 36f
private const val DEMO_CARD_COUNT: Int = 50
private const val DEMO_LAZY_ITEM_COUNT: Int = 10_000
