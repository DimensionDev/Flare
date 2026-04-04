/*
 * Copyright (c) 2010 Arc90 Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.dimension.flare.readability

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Configuration options for [isProbablyReaderable].
 */
public data class ReaderableOptions(
    public val minScore: Double = 20.0,
    public val minContentLength: Int = 140,
    public val visibilityChecker: (Element) -> Boolean = ::isNodeVisible,
)

/**
 * Checks whether a node is visible.
 *
 * In a KMP environment without a browser, we check the `style` attribute
 * for `display:none` and the `hidden` / `aria-hidden` attributes.
 */
public fun isNodeVisible(node: Element): Boolean {
    val style = node.attr("style")
    if (style.isNotEmpty() && Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE).containsMatchIn(style)) {
        return false
    }
    if (node.hasAttr("hidden")) return false
    if (node.hasAttr("aria-hidden") && node.attr("aria-hidden") == "true") {
        val className = node.className()
        return className.contains("fallback-image")
    }
    return true
}

/**
 * Mimics JS `textContent` — concatenates all text node values without
 * adding any separators (unlike ksoup's `Element.text()` which
 * normalizes whitespace and adds spaces at element boundaries).
 */
private fun textContent(node: Node): String {
    val sb = StringBuilder()
    fun collect(n: Node) {
        if (n is TextNode) {
            sb.append(n.getWholeText())
        } else if (n is DataNode) {
            sb.append(n.getWholeData())
        } else {
            for (child in n.childNodes()) {
                collect(child)
            }
        }
    }
    collect(node)
    return sb.toString()
}

/**
 * Decides whether or not the document is reader-able without parsing the whole thing.
 *
 * @param doc the parsed [Document] to check
 * @param options configuration for the check
 * @return whether Readability.parse() will likely succeed at returning an article
 */
public fun isProbablyReaderable(doc: Document, options: ReaderableOptions = ReaderableOptions()): Boolean {
    val nodes = mutableSetOf<Element>()

    // Get <p>, <pre>, <article> nodes
    nodes.addAll(doc.select("p, pre, article"))

    // Get <div> nodes which have <br> node(s) and add their parents
    val brNodes = doc.select("div > br")
    for (br in brNodes) {
        val parent = br.parent()
        if (parent != null) {
            nodes.add(parent)
        }
    }

    var score = 0.0

    for (node in nodes) {
        if (!options.visibilityChecker(node)) continue

        val matchString = node.className() + " " + node.id()
        if (RegExps.unlikelyCandidates.containsMatchIn(matchString) &&
            !RegExps.okMaybeItsACandidate.containsMatchIn(matchString)
        ) {
            continue
        }

        if (node.`is`("li p")) continue

        val textContentLength = textContent(node).trim().length
        if (textContentLength < options.minContentLength) continue

        score += kotlin.math.sqrt((textContentLength - options.minContentLength).toDouble())

        if (score > options.minScore) {
            return true
        }
    }
    return false
}

/**
 * Convenience overload that parses HTML first.
 *
 * @param html raw HTML string
 * @param options configuration for the check
 * @return whether Readability.parse() will likely succeed
 */
public fun isProbablyReaderable(html: String, options: ReaderableOptions = ReaderableOptions()): Boolean {
    val doc = Ksoup.parse(html)
    return isProbablyReaderable(doc, options)
}
