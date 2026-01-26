package dev.dimension.flare.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.direction
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.isLightTheme
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

internal const val TAG_URL = "url"
private val lightLinkColor = Color(0xff0066cc)
private val darkLinkColor = Color(0xff99c3ff)

@Composable
public fun RichText(
    text: UiRichText,
    modifier: Modifier = Modifier,
    layoutDirection: LayoutDirection = text.direction,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    textStyle: TextStyle = PlatformTextStyle.current,
    linkStyle: TextStyle =
        textStyle.copy(
            color = if (isLightTheme()) lightLinkColor else darkLinkColor,
            textDecoration = TextDecoration.None,
        ),
    imageHeaders: ImmutableMap<String, String>? = null,
) {
    val h1 = PlatformTheme.typography.h1
    val h2 = PlatformTheme.typography.h2
    val h3 = PlatformTheme.typography.h3
    val h4 = PlatformTheme.typography.h4
    val h5 = PlatformTheme.typography.h5
    val h6 = PlatformTheme.typography.h6
    val contentColor = PlatformContentColor.current
    val uriHandler = LocalUriHandler.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
    ) {
        CompositionLocalProvider(
            PlatformTextStyle provides PlatformTextStyle.current.merge(textStyle),
        ) {
            val state =
                remember(text, textStyle, linkStyle, contentColor) {
                    RichTextState(
                        richText = text,
                        styleData =
                            StyleData(
                                style = textStyle,
                                linkStyle = linkStyle,
                                h1 = h1,
                                h2 = h2,
                                h3 = h3,
                                h4 = h4,
                                h5 = h5,
                                h6 = h6,
                                contentColor = contentColor,
                            ),
                    )
                }

            androidx.compose.foundation.layout.Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(with(LocalDensity.current) { PlatformTextStyle.current.lineHeight.toDp() / 2 }),
            ) {
                val inlineContentMap =
                    state.inlineContent
                        .mapValues { (_, value) ->
                            renderInlineContent(value, LocalDensity.current)
                        }.toImmutableMap()

                state.contents.forEach { content ->
                    when (content) {
                        is RichTextContent.Text -> {
                            PlatformText(
                                modifier =
                                    Modifier.pointerInput(Unit) {
                                        awaitEachGesture {
                                            val change = awaitFirstDown()
                                            val annotation =
                                                layoutResult?.getOffsetForPosition(change.position)?.let {
                                                    content.content
                                                        .getStringAnnotations(start = it, end = it)
                                                        .firstOrNull()
                                                }
                                            if (annotation != null && annotation.tag == TAG_URL) {
                                                if (change.pressed != change.previousPressed) change.consume()
                                                val up =
                                                    waitForUpOrCancellation()?.also {
                                                        if (it.pressed != it.previousPressed) it.consume()
                                                    }
                                                if (up != null) {
                                                    uriHandler.openUri(annotation.item)
                                                }
                                            }
                                        }
                                    },
                                maxLines = maxLines,
                                color = color,
                                fontSize = fontSize,
                                fontStyle = fontStyle,
                                fontWeight = fontWeight,
                                fontFamily = fontFamily,
                                letterSpacing = letterSpacing,
                                textDecoration = textDecoration,
                                textAlign = textAlign,
                                lineHeight = lineHeight,
                                overflow = overflow,
                                softWrap = softWrap,
                                text = content.content,
                                onTextLayout = {
                                    layoutResult = it
                                    onTextLayout.invoke(it)
                                },
                                inlineContent = inlineContentMap,
                            )
                        }
                        is RichTextContent.BlockImage -> {
                            SubcomposeNetworkImage(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(PlatformTheme.shapes.medium)
                                        .let {
                                            if (content.href.isNullOrEmpty()) {
                                                it
                                            } else {
                                                it.clickable {
                                                    uriHandler.openUri(content.href)
                                                }
                                            }
                                        },
                                model = content.url,
                                contentDescription = content.url,
                                customHeaders = imageHeaders,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun renderInlineContent(
    content: BuildContentAnnotatedStringContext.InlineType,
    density: Density,
): InlineTextContent {
    var size by remember {
        mutableStateOf<IntSize?>(
            null,
            structuralEqualityPolicy(),
        )
    }

    with(density) {
        when (content) {
            is BuildContentAnnotatedStringContext.InlineType.Emoji -> {
                val placeholder =
                    Placeholder(
                        width = size?.width?.toSp() ?: 1.em,
                        height = 1.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    )
                return InlineTextContent(placeholder) { altText ->
                    Layout(
                        content = {
                            EmojiImage(
                                uri = altText,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    ) { measurables, constraints ->
                        val actualConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
                        val contentPlaceable =
                            measurables.singleOrNull()?.measure(actualConstraints)
                                ?: return@Layout layout(0, 0) {}
                        if (contentPlaceable.width > (size?.width ?: 0)) {
                            size = IntSize(contentPlaceable.width, contentPlaceable.height)
                        }
                        layout(contentPlaceable.width, contentPlaceable.height) {
                            contentPlaceable.place(0, 0)
                        }
                    }
                }
            }
        }
    }
}
