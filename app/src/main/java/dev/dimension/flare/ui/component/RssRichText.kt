package dev.dimension.flare.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.halilibo.richtext.ui.BlockQuote
import com.halilibo.richtext.ui.CodeBlock
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.FormattedList
import com.halilibo.richtext.ui.Heading
import com.halilibo.richtext.ui.ListType
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.RichTextStringStyle
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat
import dev.dimension.flare.common.AppDeepLink
import kotlinx.collections.immutable.ImmutableMap

private val lightLinkColor = Color(0xff0066cc)
private val darkLinkColor = Color(0xff99c3ff)

@Composable
fun RssRichText(
    element: Element,
    modifier: Modifier = Modifier,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    imageHeader: ImmutableMap<String, String>? = null,
) {
    val uriHandler = LocalUriHandler.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5
    val context =
        remember(element, isLightTheme) {
            RenderContext(
                RichTextStyle(
                    stringStyle =
                        RichTextStringStyle(
                            linkStyle =
                                TextLinkStyles(
                                    textStyle
                                        .copy(
                                            color = if (isLightTheme) lightLinkColor else darkLinkColor,
                                            textDecoration = TextDecoration.None,
                                        ).toSpanStyle(),
                                ),
                        ),
                    codeBlockStyle =
                        CodeBlockStyle(
                            modifier =
                                Modifier
                                    .background(Color.LightGray.copy(alpha = .5f))
                                    .fillMaxWidth(),
                        ),
                ),
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
                imageHeader = imageHeader,
                onMediaClick = {
                    uriHandler.openUri(
                        AppDeepLink.RawImage(it),
                    )
                },
            )
        }
    ProvideTextStyle(value = textStyle) {
        CompositionLocalProvider(
            LocalLayoutDirection provides layoutDirection,
        ) {
            RichText(
                modifier = modifier,
                style = context.style,
            ) {
                RenderElement(context, element)
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RichTextScope.RenderElement(
    context: RenderContext,
    element: Element,
) {
    val name = element.tagName().lowercase()
    when (name) {
        "header" -> {
            // ignore
        }
        "code" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Code) {
                    append(element.text())
                }
            }
        }

        "blockquote" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            BlockQuote {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "strong" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Bold) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
        }

        "em" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Italic) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
        }

        "body" -> {
            element.childNodes().forEach {
                RenderNode(context, it)
            }
        }

        "small" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Subscript) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
        }

        "del", "s" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Strikethrough) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
        }

        "a" -> {
            val href = element.attribute("href")?.value
            if (!href.isNullOrEmpty()) {
                with(context.builder) {
                    withFormat(
                        RichTextString.Format.Link(destination = href),
                    ) {
                        element.childNodes().forEach {
                            RenderNode(context, it)
                        }
                    }
                }
            } else {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
            }
        }

        "u" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Underline) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
        }

        "img" -> {
            val src = element.attribute("src")?.value
            val alt = element.attribute("alt")?.value
            if (!src.isNullOrEmpty()) {
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
                SubcomposeNetworkImage(
                    src,
                    contentDescription = alt,
                    modifier =
                        Modifier
                            .clickable {
                                context.onMediaClick(src)
                            }.fillMaxSize(),
                    customHeaders = context.imageHeader,
                )
            } else {
                context.builder.append(alt.orEmpty())
            }
        }

        "pre" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            CodeBlock(element.text())
        }

        "span" -> {
            element.childNodes().forEach {
                RenderNode(context, it)
            }
        }

        "ul" -> {
            val items = element.children().filter { it.hasText() }
            if (items.isNotEmpty()) {
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
                FormattedList(
                    ListType.Unordered,
                    items = items,
                ) {
                    RenderNode(context, it)
                    with(context) {
                        RenderTextAndReset(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }

        "h1" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(1) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "h2" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(2) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "h3" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(3) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "h4" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(4) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "h5" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(5) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "h6" -> {
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
            Heading(6) {
                element.childNodes().forEach {
                    RenderNode(context, it)
                }
                with(context) {
                    RenderTextAndReset(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    )
                }
            }
        }

        "figcaption" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Subscript) {
                    element.childNodes().forEach {
                        RenderNode(context, it)
                    }
                }
            }
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }

        "p", "div" -> {
            element.childNodes().forEach {
                RenderNode(context, it)
            }
            with(context) {
                RenderTextAndReset(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }

        else -> {
            element.childNodes().forEach {
                RenderNode(context, it)
            }
        }
    }
}

@Composable
private fun RichTextScope.RenderNode(
    context: RenderContext,
    node: Node,
) {
    when (node) {
        is Element -> RenderElement(context, node)
        is TextNode -> {
            context.builder.append(node.text())
        }
        else -> Unit
    }
}

private class RenderContext(
    val style: RichTextStyle,
    val overflow: TextOverflow,
    val softWrap: Boolean,
    val maxLines: Int,
    val imageHeader: ImmutableMap<String, String>?,
    val onMediaClick: (String) -> Unit,
) {
    var builder = RichTextString.Builder()
        private set

    fun getTextAndReset(): RichTextString? {
        val result = builder.toRichTextString()
        builder = RichTextString.Builder()
        return result.takeIf { it.text.isNotEmpty() && it.text.isNotBlank() }
    }

    @Composable
    fun RichTextScope.RenderTextAndReset(modifier: Modifier = Modifier) {
        getTextAndReset()?.let {
            Text(
                text = it,
                modifier = modifier,
                overflow = overflow,
                softWrap = softWrap,
                maxLines = maxLines,
            )
        }
    }
}
