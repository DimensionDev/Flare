package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.ui.BlockQuote
import com.halilibo.richtext.ui.CodeBlock
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.WithStyle
import com.halilibo.richtext.ui.currentRichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.RichTextStringStyle
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat
import moe.tlaster.ktml.dom.Comment
import moe.tlaster.ktml.dom.Doctype
import moe.tlaster.ktml.dom.Element
import moe.tlaster.ktml.dom.Node
import moe.tlaster.ktml.dom.Text

@Composable
fun HtmlText2(
    element: Element,
    modifier: Modifier = Modifier,
    layoutDirection: LayoutDirection = LocalLayoutDirection.current,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val handler = LocalUriHandler.current
    val context =
        remember(element) {
            RenderContext(
                RichTextStyle(
                    stringStyle =
                        RichTextStringStyle(
                            linkStyle =
                                SpanStyle(
                                    color = primaryColor,
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
                onLinkClick = {
                    handler.openUri(it)
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
                with(element) {
                    RenderElement(context)
                    with(context) {
                        RenderTextAndReset()
                    }
                }
            }
        }
    }
}

private val blockElementName =
    listOf(
        "center",
        "blockquote",
        "search",
        "body",
        "pre",
    )

context (RichTextScope, Element)
@Composable
private fun RenderElement(context: RenderContext) {
    // check if element is a block element
    if (name in blockElementName) {
        with(context) {
            RenderTextAndReset()
        }
    }
    when (name.lowercase()) {
        "center" -> {
            CenterRichText(
                modifier = Modifier.fillMaxWidth(),
                style = context.style,
            ) {
                children.forEach {
                    with(it) {
                        RenderNode(context)
                    }
                }
                with(context) {
                    RenderTextAndReset()
                }
            }
        }

        "code" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Code) {
                    append(innerText)
                }
            }
        }

        "blockquote" -> {
            BlockQuote {
                children.forEach {
                    with(it) {
                        RenderNode(context)
                    }
                }
                with(context) {
                    RenderTextAndReset()
                }
            }
        }

        "search" -> {
            OutlinedTextField2(
                state =
                    rememberSaveable(
                        saver = TextFieldState.Saver,
                    ) {
                        TextFieldState(innerText)
                    },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = {
                        context.onLinkClick.invoke("https://www.google.com/search?q=$innerText")
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        "strong" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Bold) {
                    children.forEach {
                        with(it) {
                            RenderNode(context)
                        }
                    }
                }
            }
        }

        "fn" -> {
            // TODO: Add fn style
            children.forEach {
                with(it) {
                    RenderNode(context)
                }
            }
        }

        "em" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Italic) {
                    children.forEach {
                        with(it) {
                            RenderNode(context)
                        }
                    }
                }
            }
        }

        "body" -> {
            children.forEach {
                with(it) {
                    RenderNode(context)
                }
            }
            with(context) {
                RenderTextAndReset()
            }
        }

        "small" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Subscript) {
                    children.forEach {
                        with(it) {
                            RenderNode(context)
                        }
                    }
                }
            }
        }

        "s" -> {
            with(context.builder) {
                withFormat(RichTextString.Format.Strikethrough) {
                    children.forEach {
                        with(it) {
                            RenderNode(context)
                        }
                    }
                }
            }
        }

        "a" -> {
            val href = attributes["href"]
            if (!href.isNullOrEmpty()) {
                with(context.builder) {
                    withFormat(
                        RichTextString.Format.Link(onClick = {
                            context.onLinkClick.invoke(href)
                        }),
                    ) {
                        children.forEach {
                            with(it) {
                                RenderNode(context)
                            }
                        }
                    }
                }
            } else {
                children.forEach {
                    with(it) {
                        RenderNode(context)
                    }
                }
            }
        }

        "img" -> {
            val src = attributes["src"]
            val alt = attributes["alt"]
            if (!src.isNullOrEmpty()) {
                context.builder.appendInlineContent(
                    "image",
                    InlineContent(
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ) {
                        EmojiImage(
                            uri = src,
                            modifier =
                                Modifier
                                    .height(LocalTextStyle.current.fontSize.value.dp),
                        )
                    },
                )
            } else {
                context.builder.append(alt.orEmpty())
            }
        }

        "pre" -> {
            CodeBlock(innerText)
        }

        "span" -> {
            children.forEach {
                with(it) {
                    RenderNode(context)
                }
            }
        }

        else -> {
            children.forEach {
                with(it) {
                    RenderNode(context)
                }
            }
            with(context) {
                RenderTextAndReset()
            }
        }
    }
}

@Composable
fun CenterRichText(
    modifier: Modifier = Modifier,
    style: RichTextStyle? = null,
    children: @Composable RichTextScope.() -> Unit,
) {
    with(RichTextScope) {
        WithStyle(style) {
            val resolvedStyle = currentRichTextStyle.resolveDefaults()
            val blockSpacing =
                with(LocalDensity.current) {
                    resolvedStyle.paragraphSpacing!!.toDp()
                }

            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(blockSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                children()
            }
        }
    }
}

context (RichTextScope, Node)
@Composable
private fun RenderNode(context: RenderContext) {
    when (this@Node) {
        is Element -> RenderElement(context)
        is Text -> {
            context.builder.append(this@Node.text)
        }

        is Comment -> Unit
        is Doctype -> Unit
    }
}

private class RenderContext(
    val style: RichTextStyle,
    val overflow: TextOverflow,
    val softWrap: Boolean,
    val maxLines: Int,
    val onLinkClick: (String) -> Unit,
) {
    var builder = RichTextString.Builder()
        private set

    fun getTextAndReset(): RichTextString? {
        val result = builder.toRichTextString()
        builder = RichTextString.Builder()
        return result.takeIf { it.text.isNotEmpty() }
    }
}

context (RenderContext, RichTextScope)
@Composable
private fun RenderTextAndReset(modifier: Modifier = Modifier) {
    val text =
        remember<RichTextString?> {
            getTextAndReset()
        }
    text?.let {
        Text(
            text = it,
            modifier = modifier,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
        )
    }
}
