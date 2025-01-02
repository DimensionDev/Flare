package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.fleeksoft.ksoup.nodes.Element
import com.konyaco.fluent.FluentTheme
import com.konyaco.fluent.LocalTextStyle
import com.konyaco.fluent.ProvideTextStyle
import com.konyaco.fluent.component.Text
import dev.dimension.flare.ui.render.UiRichText

private const val ID_IMAGE = "image"
private val lightLinkColor = Color(0xff0066cc)
private val darkLinkColor = Color(0xff99c3ff)

@Composable
fun RichText(
    data: UiRichText,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    linkStyle: TextStyle =
        textStyle.copy(
            color = if (FluentTheme.colors.darkMode) darkLinkColor else lightLinkColor,
        ),
) {
    HtmlText(
        element = data.data,
        modifier = modifier,
        maxLines = maxLines,
        textStyle = textStyle,
        linkStyle = linkStyle,
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
        layoutDirection = data.direction,
    )
}

@Composable
fun HtmlText(
    element: Element,
    modifier: Modifier = Modifier,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    linkStyle: TextStyle =
        textStyle.copy(
            color = if (FluentTheme.colors.darkMode) darkLinkColor else lightLinkColor,
        ),
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
    ) {
        ProvideTextStyle(value = textStyle) {
            RenderContent(
                modifier = modifier,
                element = element,
                maxLines = maxLines,
                textStyle = textStyle,
                linkStyle = linkStyle,
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
            )
        }
    }
}

@OptIn(ExperimentalUnitApi::class)
@Composable
private fun RenderContent(
    element: Element,
    textStyle: TextStyle,
    linkStyle: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    val value =
        remember(element, textStyle, linkStyle) {
            buildContentAnnotatedString(
                element = element,
                textStyle = textStyle,
                linkStyle = linkStyle,
                imageId = ID_IMAGE,
            )
        }
    if (value.text.isNotEmpty() && value.text.isNotBlank()) {
        Text(
            modifier = modifier,
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
