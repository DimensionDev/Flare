package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.ExperimentalCupertinoApi
import com.slapps.cupertino.LocalContentColor
import com.slapps.cupertino.ProvideTextStyle
import com.slapps.cupertino.theme.CupertinoTheme
import dev.dimension.flare.ui.theme.PlatformTheme

internal actual typealias PlatformDropdownMenuScope = ColumnScope

// internal actual interface PlatformDropdownMenuScope

// private data class PlatformDropdownMenuScopeImpl(
//    val delegate: CupertinoMenuScope
// ) : PlatformDropdownMenuScope

@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal actual fun PlatformDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable (PlatformDropdownMenuScope.() -> Unit),
) {
//    val sheetState =
//        rememberModalBottomSheetState(
//            initialDetent =
//                if (expanded) {
//                    SheetDetent.FullyExpanded
//                } else {
//                    SheetDetent.Hidden
//                },
//        )
//
//    LaunchedEffect(expanded) {
//        if (expanded) {
//            sheetState.animateTo(SheetDetent.FullyExpanded)
//        } else {
//            sheetState.animateTo(SheetDetent.Hidden)
//        }
//    }
//
//    ModalBottomSheet(
//        state = sheetState,
//        onDismiss = {
//            onDismissRequest.invoke()
//        },
//        content = {
//            Scrim(
//                enter = fadeIn(),
//                exit = fadeOut(),
//            )
//            val isCompact = !isBigScreen()
//
//            Box(
//                Modifier
//                    .fillMaxSize()
//                    .padding(top = 12.dp)
//                    .let { if (isCompact) it else it.padding(horizontal = 56.dp) }
//                    .displayCutoutPadding()
//                    .statusBarsPadding()
//                    .padding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal).asPaddingValues()),
//            ) {
//                Sheet(
//                    modifier =
//                        Modifier
//                            .shadow(4.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
//                            .widthIn(max = 640.dp)
//                            .fillMaxWidth(),
//                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
//                    backgroundColor = CupertinoTheme.colorScheme.systemGroupedBackground,
//                    contentColor = CupertinoTheme.colorScheme.label,
//                ) {
//                    Column(
//                        modifier = Modifier.padding(vertical = 16.dp),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement =
//                            androidx.compose.foundation.layout.Arrangement
//                                .spacedBy(16.dp),
//                    ) {
//                        DragIndication(
//                            modifier =
//                                Modifier
//                                    .background(CupertinoTheme.colorScheme.separator, RoundedCornerShape(100))
//                                    .width(32.dp)
//                                    .height(4.dp),
//                        )
//                        Column(
//                            modifier =
//                                Modifier
//                                    .padding(horizontal = screenHorizontalPadding)
//                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
//                                    .clip(PlatformTheme.shapes.listCardContainerShape)
//                                    .background(CupertinoTheme.colorScheme.systemGroupedBackground),
//                            verticalArrangement =
//                                androidx.compose.foundation.layout.Arrangement
//                                    .spacedBy(2.dp),
//                        ) {
//                            content.invoke(this)
//                        }
//                    }
//                }
//            }
//        },
//    )
//    CupertinoDropdownMenu(
//        expanded = expanded,
//        onDismissRequest = onDismissRequest,
//        modifier = modifier,
//        content = {
//            val scope = remember(this) { PlatformDropdownMenuScopeImpl(this) }
//            content.invoke(scope)
//        }
//    )
}

@Composable
internal actual fun PlatformDropdownMenuScope.PlatformDropdownMenuItem(
    text: @Composable (() -> Unit),
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
) {
    ProvideTextStyle(
        CupertinoTheme.typography.body,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides CupertinoTheme.colorScheme.label,
        ) {
            PlatformListItem(
                modifier =
                    modifier
                        .clip(shape = PlatformTheme.shapes.listCardItemShape)
                        .background(
                            CupertinoTheme.colorScheme.tertiarySystemBackground,
                        ).padding(vertical = 8.dp)
                        .clickable {
                            onClick.invoke()
                        },
                leadingContent = {
                    leadingIcon?.invoke()
                },
                headlineContent = {
                    text()
                },
                trailingContent = {
                    trailingIcon?.invoke()
                },
            )
        }
    }
//    (this as PlatformDropdownMenuScopeImpl).delegate.MenuAction(
//        modifier = modifier,
//        title = text,
//        onClick = onClick,
//        icon = {
//            leadingIcon?.invoke()
//        },
//    )
}
