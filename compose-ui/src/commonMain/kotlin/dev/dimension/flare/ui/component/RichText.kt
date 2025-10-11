package dev.dimension.flare.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.direction
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.theme.isLightTheme

internal const val TAG_URL = "url"
private const val ID_IMAGE = "image"
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
    textStyle: TextStyle = PlatformTextStyle.current,
    linkStyle: TextStyle =
        textStyle.copy(
            color = if (isLightTheme()) lightLinkColor else darkLinkColor,
            textDecoration = TextDecoration.None,
        ),
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
    ) {
        CompositionLocalProvider(
            PlatformTextStyle provides PlatformTextStyle.current.merge(textStyle),
        ) {
            val value =
                remember(text, textStyle, linkStyle) {
                    buildContentAnnotatedString(
                        element = text.data,
                        textStyle = textStyle,
                        linkStyle = linkStyle,
                        imageId = ID_IMAGE,
                    )
                }

            PlatformText(
                modifier =
                    modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val change = awaitFirstDown()
                            val annotation =
                                layoutResult.value?.getOffsetForPosition(change.position)?.let {
                                    value
                                        .getStringAnnotations(start = it, end = it)
                                        .firstOrNull()
                                }
                            if (annotation != null) {
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
                text = value,
                onTextLayout = {
                    layoutResult.value = it
                },
                inlineContent =
                    mapOf(
                        ID_IMAGE to
                            InlineTextContent(
                                Placeholder(
                                    width = 1.em,
                                    height = 1.em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                                ),
                            ) { target ->
                                EmojiImage(
                                    uri = target,
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                )
                            },
                    ),
            )
        }
    }
}
