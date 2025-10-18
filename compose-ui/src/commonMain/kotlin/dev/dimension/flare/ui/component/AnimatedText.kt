package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.status.LocalIsScrollingInProgress
import dev.dimension.flare.ui.model.Digit
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun AnimatedNumber(
    digits: ImmutableList<Digit>,
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
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = PlatformTextStyle.current,
) {
    if (LocalIsScrollingInProgress.current) {
        Row(
            modifier = modifier,
        ) {
            digits
                .fastForEach { digit ->
                    PlatformText(
                        digit.digitString,
                        color = color,
                        fontSize = fontSize,
                        fontStyle = fontStyle,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        letterSpacing = 0.sp,
                        textDecoration = textDecoration,
                        textAlign = textAlign,
                        lineHeight = lineHeight,
                        overflow = overflow,
                        softWrap = softWrap,
                        maxLines = maxLines,
                        minLines = minLines,
                        inlineContent = inlineContent,
                        onTextLayout = onTextLayout,
                        style = style,
                    )
                }
        }
    } else {
        Row(
            modifier =
                modifier
                    .animateContentSize(),
        ) {
            digits
                .fastForEach { digit ->
                    AnimatedContent(
                        targetState = digit,
                        transitionSpec = {
                            if (targetState > initialState) {
                                fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { -it }
                            } else {
                                fadeIn() + slideInVertically { -it } togetherWith fadeOut() + slideOutVertically { it }
                            }.using(SizeTransform(clip = false))
                        },
                    ) { digit ->
                        PlatformText(
                            digit.digitString,
                            color = color,
                            fontSize = fontSize,
                            fontStyle = fontStyle,
                            fontWeight = fontWeight,
                            fontFamily = fontFamily,
                            letterSpacing = 0.sp,
                            textDecoration = textDecoration,
                            textAlign = textAlign,
                            lineHeight = lineHeight,
                            overflow = overflow,
                            softWrap = softWrap,
                            maxLines = maxLines,
                            minLines = minLines,
                            inlineContent = inlineContent,
                            onTextLayout = onTextLayout,
                            style = style,
                        )
                    }
                }
        }
    }
}

private operator fun Digit.compareTo(other: Digit): Int = fullNumber.compareTo(other.fullNumber)
