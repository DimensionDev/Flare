package dev.dimension.flare.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronDown
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusSummaryComponent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/** Keeps the media subtree mounted while the standard sheet moves between its two anchors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPostSheet(
    post: UiTimelineV2.Post?,
    quotes: ImmutableList<UiTimelineV2.Post>,
    visible: Boolean,
    uriHandler: UriHandler,
    content: @Composable (peekHeight: Dp) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            enabledValues = setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
        )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var summaryHeight by remember { mutableStateOf(80.dp) }
    var handleHeight by remember { mutableStateOf(48.dp) }
    val showSheet = visible && post != null
    val expanded =
        showSheet &&
            (sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded)
    val collapse: () -> Unit = { scope.launch { sheetState.partialExpand() } }
    LaunchedEffect(showSheet) {
        if (!showSheet) sheetState.partialExpand()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expandedHeight = (maxHeight * 0.9f - handleHeight).coerceAtLeast(summaryHeight)
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (showSheet) summaryHeight + handleHeight else 0.dp,
            sheetSwipeEnabled = showSheet,
            sheetMaxWidth = Dp.Unspecified,
            sheetShape = if (expanded) BottomSheetDefaults.ExpandedShape else RectangleShape,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = Color.Transparent,
            sheetDragHandle =
                if (showSheet) {
                    { BottomSheetDefaults.DragHandle(Modifier.onSizeChanged { handleHeight = with(density) { it.height.toDp() } }) }
                } else {
                    null
                },
            sheetContent = {
                if (showSheet) {
                    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                        Box(Modifier.fillMaxWidth().height(expandedHeight)) {
                            if (!expanded) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { summaryHeight = with(density) { it.height.toDp() } },
                                ) {
                                    StatusSummaryComponent(
                                        item = post,
                                        modifier =
                                            Modifier
                                                .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                                                .windowInsetsPadding(
                                                    WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                                                ),
                                    )
                                }
                            }
                            if (expanded) {
                                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        IconButton(onClick = collapse, modifier = Modifier.align(Alignment.CenterEnd)) {
                                            FAIcon(FontAwesomeIcons.Solid.ChevronDown, stringResource(R.string.media_post_collapse))
                                        }
                                    }
                                    CommonStatusComponent(
                                        item = post,
                                        isDetail = true,
                                        showMedia = false,
                                        quotes = quotes,
                                        modifier =
                                            Modifier
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                                                .windowInsetsPadding(
                                                    WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                                                ),
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                content(if (showSheet) summaryHeight + handleHeight else 0.dp)
                if (expanded) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = collapse))
                }
            }
        }
    }
    BackHandler(enabled = expanded, onBack = collapse)
}
