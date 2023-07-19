package dev.dimension.flare.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList


internal sealed interface UiContentToken {
    val value: String
}

data class UiCashTagToken(
    override val value: String,
    val deeplink: String,
) : UiContentToken

data class UiHashTagToken(
    override val value: String,
    val deeplink: String,
) : UiContentToken

data class UiStringToken(
    override val value: String,
) : UiContentToken

data class UiUrlToken(
    override val value: String,
    val display: String? = null,
    val clickable: Boolean = true,
) : UiContentToken

data class UiUserNameToken(
    override val value: String,
    val deeplink: String,
) : UiContentToken

data class UiEmojiToken(
    override val value: String,
    val image: String,
) : UiContentToken

private const val ID_IMAGE = "image"

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun UiContentTokenText(
    tokens: ImmutableList<UiContentToken>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    linkStyle: TextStyle = textStyle.copy(MaterialTheme.colorScheme.primary),
) {
    val uriHandler = LocalUriHandler.current
    val text = remember(tokens, textStyle, linkStyle) {
        buildAnnotatedString { renderTokens(tokens, textStyle, linkStyle) }
    }
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val change = awaitFirstDown()
                val annotation =
                    layoutResult.value?.getOffsetForPosition(change.position)?.let {
                        text.getUrlAnnotations(start = it, end = it)
                            .firstOrNull()
                    }
                if (annotation != null) {
                    if (change.pressed != change.previousPressed) change.consume()
                    val up = waitForUpOrCancellation()?.also {
                        if (it.pressed != it.previousPressed) it.consume()
                    }
                    if (up != null) {
                        uriHandler.openUri(annotation.item.url)
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
        text = text,
        onTextLayout = {
            layoutResult.value = it
        },
        inlineContent = mapOf(
            ID_IMAGE to InlineTextContent(
                Placeholder(
                    width = LocalTextStyle.current.fontSize,
                    height = LocalTextStyle.current.fontSize,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) { target ->
                AsyncImage(
                    model = target,
                    contentDescription = target
                )
            },
        ),
    )
}

internal fun AnnotatedString.Builder.renderTokens(
    tokens: ImmutableList<UiContentToken>,
    textStyle: TextStyle,
    linkStyle: TextStyle,
) {
    tokens.forEach { renderToken(it, RenderContext(textStyle, linkStyle)) }
}

private fun AnnotatedString.Builder.renderToken(
    token: UiContentToken,
    context: RenderContext,
) {
    when (token) {
        is UiCashTagToken -> renderCashTag(token, context)
        is UiEmojiToken -> renderEmoji(token, context)
        is UiHashTagToken -> renderHashTag(token, context)
        is UiStringToken -> renderString(token, context)
        is UiUrlToken -> renderUrl(token, context)
        is UiUserNameToken -> renderUserName(token, context)
    }
}

private fun AnnotatedString.Builder.renderCashTag(
    token: UiCashTagToken,
    context: RenderContext,
) {
    renderLink(display = token.value, href = token.deeplink, context = context)
}

private fun AnnotatedString.Builder.renderEmoji(
    token: UiEmojiToken,
    context: RenderContext,
) {
    appendInlineContent(ID_IMAGE, token.image)
//    renderText(text = token.value, textStyle = context.textStyle)
}

private fun AnnotatedString.Builder.renderHashTag(
    token: UiHashTagToken,
    context: RenderContext,
) {
    renderLink(display = token.value, href = token.deeplink, context = context)
}

private fun AnnotatedString.Builder.renderString(
    token: UiStringToken,
    context: RenderContext,
) {
    renderText(text = token.value, textStyle = context.textStyle)
}

private fun AnnotatedString.Builder.renderUrl(
    token: UiUrlToken,
    context: RenderContext,
) {
    renderLink(
        display = token.display ?: token.value,
        href = token.value,
        clickable = token.clickable,
        context = context,
    )
}

private fun AnnotatedString.Builder.renderUserName(
    token: UiUserNameToken,
    context: RenderContext,
) {
    renderLink(display = token.value, href = token.deeplink, context = context)
}

@OptIn(ExperimentalTextApi::class)
private fun AnnotatedString.Builder.renderLink(
    display: String,
    href: String = display,
    clickable: Boolean = true,
    context: RenderContext,
) {
    if (clickable) {
        withAnnotation(UrlAnnotation(href)) {
            renderText(display, context.linkStyle)
        }
    } else {
        renderText(display, context.linkStyle)
    }
}

private fun AnnotatedString.Builder.renderText(
    text: String,
    textStyle: TextStyle,
) {
    withStyle(textStyle.toSpanStyle()) {
        append(text)
    }
}

private data class RenderContext(
    val textStyle: TextStyle,
    val linkStyle: TextStyle,
)
