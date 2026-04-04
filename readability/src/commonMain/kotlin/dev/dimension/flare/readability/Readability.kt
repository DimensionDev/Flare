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

/*
 * This code is heavily based on Arc90's readability.js (1.7.1) script
 * available at: http://code.google.com/p/arc90labs-readability
 *
 * Kotlin Multiplatform port using fleeksoft/ksoup.
 */

package dev.dimension.flare.readability

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.Elements
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Readability — extracts the main content from an HTML document.
 *
 * Kotlin Multiplatform port of Mozilla's Readability.js.
 *
 * @param html the raw HTML string to parse
 * @param url  the URL of the document (used for resolving relative URIs)
 * @param options configuration options
 */
public class Readability(
    html: String,
    private val url: String = "",
    private val options: ReadabilityOptions = ReadabilityOptions(),
) {
    // ── flags ────────────────────────────────────────────────────────────
    private companion object {
        private const val FLAG_STRIP_UNLIKELYS = 0x1
        private const val FLAG_WEIGHT_CLASSES = 0x2
        private const val FLAG_CLEAN_CONDITIONALLY = 0x4

        // Element tags to score by default.
        private val DEFAULT_TAGS_TO_SCORE = setOf(
            "SECTION", "H2", "H3", "H4", "H5", "H6", "P", "TD", "PRE"
        )

        private val DIV_TO_P_ELEMS = setOf(
            "BLOCKQUOTE", "DL", "DIV", "IMG", "OL", "P", "PRE", "TABLE", "UL"
        )

        private val ALTER_TO_DIV_EXCEPTIONS = setOf(
            "DIV", "ARTICLE", "SECTION", "P", "OL", "UL"
        )

        private val PRESENTATIONAL_ATTRIBUTES = listOf(
            "align", "background", "bgcolor", "border", "cellpadding",
            "cellspacing", "frame", "hspace", "rules", "style", "valign", "vspace"
        )

        private val DEPRECATED_SIZE_ATTRIBUTE_ELEMS = setOf(
            "TABLE", "TH", "TD", "HR", "PRE"
        )

        private val PHRASING_ELEMS = setOf(
            "ABBR", "AUDIO", "B", "BDO", "BR", "BUTTON", "CITE", "CODE", "DATA",
            "DATALIST", "DFN", "EM", "EMBED", "I", "IMG", "INPUT", "KBD", "LABEL",
            "MARK", "MATH", "METER", "NOSCRIPT", "OBJECT", "OUTPUT", "PROGRESS",
            "Q", "RUBY", "SAMP", "SCRIPT", "SELECT", "SMALL", "SPAN", "STRONG",
            "SUB", "SUP", "TEXTAREA", "TIME", "VAR", "WBR"
        )

        private val CLASSES_TO_PRESERVE = listOf("page")

        private val UNLIKELY_ROLES = setOf(
            "menu", "menubar", "complementary", "navigation",
            "alert", "alertdialog", "dialog"
        )

        private val HTML_ESCAPE_MAP = mapOf(
            "lt" to "<",
            "gt" to ">",
            "amp" to "&",
            "quot" to "\"",
            "apos" to "'"
        )
    }

    // ── instance state ───────────────────────────────────────────────────
    // Normalize \r\n and \r to \n before parsing, matching the HTML spec's
    // input-stream preprocessing step (browsers do this automatically).
    private val doc: Document = Ksoup.parse(html.replace("\r\n", "\n").replace("\r", "\n"), url)
    private var articleTitle: String = ""
    private var articleByline: String? = null
    private var articleDir: String? = null
    private var articleLang: String? = null
    private var articleSiteName: String? = null

    private data class Attempt(val articleContent: Element, val textLength: Int)
    private val attempts = mutableListOf<Attempt>()
    private var metadata = ArticleMetadata()

    private var flags = FLAG_STRIP_UNLIKELYS or FLAG_WEIGHT_CLASSES or FLAG_CLEAN_CONDITIONALLY

    private val classesToPreserve = CLASSES_TO_PRESERVE + options.classesToPreserve
    private val serializer: (Element) -> String = options.serializer ?: { it.html() }
    private val allowedVideoRegex: Regex = options.allowedVideoRegex ?: RegExps.videos
    private val linkDensityModifier: Double = options.linkDensityModifier

    // External maps for properties that JS attaches directly to DOM nodes.
    // In ksoup we cannot add arbitrary properties to Element instances.
    private val contentScores = HashMap<Element, Double>()
    private val readabilityDataTables = HashMap<Element, Boolean>()

    // ── logging ──────────────────────────────────────────────────────────
    private fun log(vararg args: Any?) {
        if (options.debug) {
            println("Reader: (Readability) " + args.joinToString(" "))
        }
    }

    // ── helpers for contentScore map (replaces node.readability) ────────
    private fun hasContentScore(node: Element): Boolean = contentScores.containsKey(node)

    private fun getContentScore(node: Element): Double = contentScores[node] ?: 0.0

    private fun setContentScore(node: Element, score: Double) {
        contentScores[node] = score
    }

    private fun addContentScore(node: Element, delta: Double) {
        contentScores[node] = (contentScores[node] ?: 0.0) + delta
    }

    // ── helpers for readabilityDataTable map ────────────────────────────
    private fun isReadabilityDataTable(table: Element): Boolean =
        readabilityDataTables[table] == true

    // ── flag management ─────────────────────────────────────────────────
    private fun flagIsActive(flag: Int): Boolean = (flags and flag) > 0

    private fun removeFlag(flag: Int) {
        flags = flags and flag.inv()
    }

    // ── utility functions ───────────────────────────────────────────────

    /**
     * Remove nodes from a list, optionally filtered by [filterFn].
     * Iterates in reverse to avoid index shifting issues.
     */
    private fun removeNodes(nodes: List<Element>, filterFn: ((Element) -> Boolean)? = null) {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            if (node.parent() != null) {
                if (filterFn == null || filterFn(node)) {
                    node.remove()
                }
            }
        }
    }

    /**
     * Remove nodes from a list of generic [Node], optionally filtered by [filterFn].
     */
    private fun removeChildNodes(nodes: List<Node>, filterFn: ((Node) -> Boolean)? = null) {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            if (node.parent() != null) {
                if (filterFn == null || filterFn(node)) {
                    node.remove()
                }
            }
        }
    }

    /**
     * Replace the tag of each node in [nodes] with [newTagName].
     */
    private fun replaceNodeTags(nodes: List<Element>, newTagName: String) {
        for (node in nodes) {
            setNodeTag(node, newTagName)
        }
    }

    /**
     * Get all descendant elements with any of the given [tagNames].
     */
    private fun getAllNodesWithTag(node: Element, tagNames: List<String>): List<Element> {
        return node.select(tagNames.joinToString(",")).filter { it !== node }
    }

    /**
     * Clean classes from [node] and descendants, keeping only those in [classesToPreserve].
     */
    private fun cleanClasses(node: Element) {
        val className = (node.attr("class"))
            .split(Regex("\\s+"))
            .filter { it in classesToPreserve }
            .joinToString(" ")

        if (className.isNotEmpty()) {
            node.attr("class", className)
        } else {
            node.removeAttr("class")
        }

        var child = node.firstElementChild()
        while (child != null) {
            cleanClasses(child)
            child = child.nextElementSibling()
        }
    }

    /**
     * Tests whether a string is a valid URL.
     */
    private fun isUrl(str: String): Boolean {
        return str.startsWith("http://") || str.startsWith("https://")
    }

    /**
     * Convert relative URIs to absolute URIs within [articleContent].
     */
    private fun fixRelativeUris(articleContent: Element) {
        // Get base URI, accounting for <base> element
        val documentURI = url
        val baseURI = run {
            val baseElements = doc.getElementsByTag("base")
            val href = baseElements.firstOrNull()?.attr("href") ?: ""
            if (href.isNotEmpty()) {
                try {
                    resolveUrl(documentURI, href)
                } catch (_: Exception) {
                    documentURI
                }
            } else {
                documentURI
            }
        }

        fun toAbsoluteURI(uri: String): String {
            if (baseURI == documentURI && uri.startsWith("#")) {
                return uri
            }
            return try {
                resolveUrl(baseURI, uri)
            } catch (_: Exception) {
                uri
            }
        }

        val links = getAllNodesWithTag(articleContent, listOf("a"))
        for (link in links) {
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                if (href.startsWith("javascript:")) {
                    if (link.childNodes().size == 1 && link.childNode(0) is TextNode) {
                        val text = TextNode(textContent(link))
                        link.replaceWith(text)
                    } else {
                        val container = Element("span")
                        while (link.childNodes().isNotEmpty()) {
                            container.appendChild(link.childNode(0))
                        }
                        link.replaceWith(container)
                    }
                } else {
                    link.attr("href", toAbsoluteURI(href))
                }
            }
        }

        val medias = getAllNodesWithTag(articleContent, listOf("img", "picture", "figure", "video", "audio", "source"))
        for (media in medias) {
            val src = media.attr("src")
            val poster = media.attr("poster")
            val srcset = media.attr("srcset")

            if (src.isNotEmpty()) {
                media.attr("src", toAbsoluteURI(src))
            }
            if (poster.isNotEmpty()) {
                media.attr("poster", toAbsoluteURI(poster))
            }
            if (srcset.isNotEmpty()) {
                val newSrcset = RegExps.srcsetUrl.replace(srcset) { matchResult ->
                    val p1 = matchResult.groupValues[1]
                    val p2 = matchResult.groupValues[2]
                    val p3 = matchResult.groupValues[3]
                    toAbsoluteURI(p1) + p2 + p3
                }
                media.attr("srcset", newSrcset)
            }
        }
    }

    /**
     * URL resolution that mimics the behavior of `new URL(relative, base).href` in JavaScript.
     * Handles absolute URIs, protocol-relative URLs, absolute paths, relative paths,
     * special schemes (mailto:, about:, data:, tel:, blob:, ftp:, etc.), and
     * normalizes `.` and `..` path segments.
     */
    private fun resolveUrl(base: String, relative: String): String {
        // Already absolute URL
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return normalizeUrlPath(relative)
        }

        // Check for other absolute URI schemes (mailto:, tel:, data:, about:, blob:, ftp:, etc.)
        // An absolute URI has a scheme followed by ':'
        val schemeMatch = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").find(relative)
        if (schemeMatch != null) {
            // For file: URLs, normalize legacy Windows drive letter | to :
            // per WHATWG URL spec (e.g. file:///C|/path -> file:///C:/path)
            if (relative.startsWith("file:")) {
                return relative.replace(Regex("^(file:///[a-zA-Z])\\|"), "$1:")
            }
            return relative
        }

        if (base.isEmpty()) return relative

        // Protocol-relative URL (//host/path)
        if (relative.startsWith("//")) {
            val schemeEnd = base.indexOf("://")
            val scheme = if (schemeEnd != -1) base.substring(0, schemeEnd) else "http"
            return normalizeUrlPath("$scheme:$relative")
        }

        // Fragment-only — append to base URL (strip existing fragment from base first)
        if (relative.startsWith("#")) {
            val baseWithoutFragment = base.substringBefore('#')
            return baseWithoutFragment + relative
        }

        val schemeEnd = base.indexOf("://")
        if (schemeEnd == -1) return relative

        val afterScheme = schemeEnd + 3
        val slashIdx = base.indexOf('/', afterScheme)
        val qIdx = base.indexOf('?', afterScheme)
        val fIdx = base.indexOf('#', afterScheme)
        val hostEnd = listOf(slashIdx, qIdx, fIdx).filter { it != -1 }.minOrNull() ?: -1
        val origin = if (hostEnd == -1) base else base.substring(0, hostEnd)

        if (relative.startsWith("/")) {
            // Absolute path
            return normalizeUrlPath(origin + relative)
        }

        // Relative path — resolve against base directory
        val basePath = if (hostEnd == -1) "$base/" else base.substring(0, base.lastIndexOf('/') + 1)
        return normalizeUrlPath(basePath + relative)
    }

    /**
     * Normalize `.` and `..` segments in the path portion of a URL.
     * Also ensures bare authority URLs (like `http://example.com`) get a trailing slash,
     * lowercases the hostname (per WHATWG URL standard), and handles the case where
     * query/fragment follows the hostname directly with no path.
     */
    private fun normalizeUrlPath(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return url

        val afterScheme = schemeEnd + 3

        // Find the end of the host: the first of '/', '?', or '#' after the scheme
        val slashIdx = url.indexOf('/', afterScheme)
        val queryIdx = url.indexOf('?', afterScheme)
        val fragmentIdx = url.indexOf('#', afterScheme)

        val hostEnd = listOf(slashIdx, queryIdx, fragmentIdx)
            .filter { it != -1 }
            .minOrNull()

        if (hostEnd == null) {
            // Bare authority: http://example.com -> http://example.com/
            val scheme = url.substring(0, afterScheme)
            val host = url.substring(afterScheme).lowercase()
            return "$scheme$host/"
        }

        // If no path (host followed directly by ? or #), insert "/" before the remainder
        if (url[hostEnd] != '/') {
            val scheme = url.substring(0, afterScheme)
            val host = url.substring(afterScheme, hostEnd).lowercase()
            val rest = url.substring(hostEnd)
            return normalizeUrlPath("$scheme$host/$rest")
        }

        val scheme = url.substring(0, afterScheme)
        val host = url.substring(afterScheme, hostEnd).lowercase()
        val prefix = "$scheme$host"
        val pathAndRest = url.substring(hostEnd)

        // Separate path from query/fragment
        val pQueryStart = pathAndRest.indexOf('?')
        val pFragmentStart = pathAndRest.indexOf('#')
        val pathEnd = when {
            pQueryStart != -1 && pFragmentStart != -1 -> minOf(pQueryStart, pFragmentStart)
            pQueryStart != -1 -> pQueryStart
            pFragmentStart != -1 -> pFragmentStart
            else -> pathAndRest.length
        }

        val path = pathAndRest.substring(0, pathEnd)
        val suffix = pathAndRest.substring(pathEnd)

        // Normalize path segments
        val segments = path.split('/')
        val normalized = mutableListOf<String>()
        for (segment in segments) {
            when (segment) {
                "." -> { /* skip */ }
                ".." -> {
                    // Don't pop past root
                    if (normalized.size > 1) {
                        normalized.removeAt(normalized.size - 1)
                    }
                }
                else -> normalized.add(segment)
            }
        }

        val normalizedPath = normalized.joinToString("/")
        return prefix + normalizedPath + suffix
    }

    private fun simplifyNestedElements(articleContent: Element) {
        var node: Element? = articleContent

        while (node != null) {
            if (node.parent() != null &&
                node.tagName().uppercase() in listOf("DIV", "SECTION") &&
                !(node.id().isNotEmpty() && node.id().startsWith("readability"))
            ) {
                if (isElementWithoutContent(node)) {
                    node = removeAndGetNext(node)
                    continue
                } else if (hasSingleTagInsideElement(node, "DIV") ||
                    hasSingleTagInsideElement(node, "SECTION")
                ) {
                    val child = node.children()[0]
                    // Copy all attributes from parent to child
                    for (attr in node.attributes().toList()) {
                        child.attr(attr.key, attr.value)
                    }
                    node.replaceWith(child)
                    node = child
                    continue
                }
            }
            node = getNextNode(node)
        }
    }

    // ── _postProcessContent ─────────────────────────────────────────────

    private fun postProcessContent(articleContent: Element) {
        fixRelativeUris(articleContent)
        simplifyNestedElements(articleContent)
        if (!options.keepClasses) {
            cleanClasses(articleContent)
        }
    }

    // ── _nextNode ───────────────────────────────────────────────────────

    /**
     * Finds the next element node, skipping whitespace text nodes.
     */
    private fun nextNode(node: Node?): Element? {
        var next: Node? = node
        while (next != null && next !is Element && (next !is TextNode || RegExps.whitespace.matches(next.text()))) {
            next = next.nextSibling()
        }
        return next as? Element
    }

    // ── _replaceBrs ─────────────────────────────────────────────────────

    private fun replaceBrs(elem: Element) {
        val brs = getAllNodesWithTag(elem, listOf("br")).toList()
        for (br in brs) {
            var next: Node? = br.nextSibling()
            var replaced = false

            // If we find a <br> chain, remove the <br>s until we hit another node
            // or non-whitespace.
            while (true) {
                val nextElem = nextNode(next) ?: break
                if (nextElem.tagName().uppercase() != "BR") break
                replaced = true
                val brSibling = nextElem.nextSibling()
                nextElem.remove()
                next = brSibling
            }

            if (replaced) {
                val p = Element("p")
                br.replaceWith(p)

                next = p.nextSibling()
                while (next != null) {
                    // If we've hit another <br><br>, we're done adding children to this <p>.
                    if (next is Element && next.tagName().uppercase() == "BR") {
                        val nextElem = nextNode(next.nextSibling())
                        if (nextElem != null && nextElem.tagName().uppercase() == "BR") {
                            break
                        }
                    }

                    if (!isPhrasingContent(next)) {
                        break
                    }

                    val sibling = next.nextSibling()
                    p.appendChild(next)
                    next = sibling
                }

                while (p.childNodes().isNotEmpty() && isWhitespace(p.childNodes().last())) {
                    p.childNodes().last().remove()
                }

                if (p.parent() != null && p.parent()!!.tagName().uppercase() == "P") {
                    setNodeTag(p.parent() as Element, "DIV")
                }
            }
        }
    }

    // ── _setNodeTag ─────────────────────────────────────────────────────

    /**
     * Replace the tag of [node] with [tag], preserving children and attributes.
     * Returns the replacement element.
     */
    private fun setNodeTag(node: Element, tag: String): Element {
        log("_setNodeTag", node.tagName(), tag)
        // In ksoup, we use tagName() setter
        val replacement = Element(tag)
        // Move children
        while (node.childNodes().isNotEmpty()) {
            replacement.appendChild(node.childNode(0))
        }
        // Copy attributes
        for (attr in node.attributes().toList()) {
            replacement.attr(attr.key, attr.value)
        }
        // Transfer content score if present
        val score = contentScores[node]
        if (score != null) {
            contentScores[replacement] = score
            contentScores.remove(node)
        }
        // Transfer data table flag if present
        val dataTable = readabilityDataTables[node]
        if (dataTable != null) {
            readabilityDataTables[replacement] = dataTable
            readabilityDataTables.remove(node)
        }

        node.replaceWith(replacement)
        return replacement
    }

    // ── _getArticleTitle ────────────────────────────────────────────────

    private fun getArticleTitle(): String {
        var curTitle = ""
        var origTitle = ""

        try {
            curTitle = doc.title().trim()
            origTitle = curTitle

            if (curTitle.isEmpty()) {
                val titleElements = doc.getElementsByTag("title")
                if (titleElements.isNotEmpty()) {
                    curTitle = getInnerText(titleElements[0])
                    origTitle = curTitle
                }
            }
        } catch (_: Exception) {
            // ignore exceptions setting the title
        }

        var titleHadHierarchicalSeparators = false

        fun wordCount(str: String): Int = str.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

        val titleSeparators = """\|\-\u2013\u2014\\\/>\u00BB"""
        val separatorRegex = Regex("\\s[$titleSeparators]\\s")

        if (separatorRegex.containsMatchIn(curTitle)) {
            titleHadHierarchicalSeparators = Regex("\\s[\\\\/>\\u00BB]\\s").containsMatchIn(curTitle)
            val allSeparators = separatorRegex.findAll(origTitle).toList()
            if (allSeparators.isNotEmpty()) {
                curTitle = origTitle.substring(0, allSeparators.last().range.first)
            }

            if (wordCount(curTitle) < 3) {
                curTitle = origTitle.replace(Regex("^[^$titleSeparators]*[$titleSeparators]", RegexOption.IGNORE_CASE), "")
            }
        } else if (curTitle.contains(": ")) {
            val headings = getAllNodesWithTag(doc, listOf("h1", "h2"))
            val trimmedTitle = curTitle.trim()
            val match = headings.any { heading ->
                heading.text().trim() == trimmedTitle
            }

            if (!match) {
                curTitle = origTitle.substring(origTitle.lastIndexOf(":") + 1)

                if (wordCount(curTitle) < 3) {
                    curTitle = origTitle.substring(origTitle.indexOf(":") + 1)
                } else if (wordCount(origTitle.substring(0, origTitle.indexOf(":"))) > 5) {
                    curTitle = origTitle
                }
            }
        } else if (curTitle.length > 150 || curTitle.length < 15) {
            val hOnes = doc.getElementsByTag("h1")
            if (hOnes.size == 1) {
                curTitle = getInnerText(hOnes[0])
            }
        }

        curTitle = curTitle.trim().replace(RegExps.normalize, " ")

        val curTitleWordCount = wordCount(curTitle)
        if (curTitleWordCount <= 4 &&
            (!titleHadHierarchicalSeparators ||
                curTitleWordCount != wordCount(origTitle.replace(Regex("\\s[$titleSeparators]\\s"), "")) - 1)
        ) {
            curTitle = origTitle
        }

        return curTitle
    }

    // ── _prepDocument ───────────────────────────────────────────────────

    private fun prepDocument() {
        // Remove all style tags in head
        removeNodes(getAllNodesWithTag(doc, listOf("style")))

        val body = doc.body()
        replaceBrs(body)

        replaceNodeTags(getAllNodesWithTag(doc, listOf("font")), "SPAN")
    }

    // ── _prepArticle ────────────────────────────────────────────────────

    private fun prepArticle(articleContent: Element) {
        cleanStyles(articleContent)

        // Check for data tables before we continue
        markDataTables(articleContent)

        fixLazyImages(articleContent)

        // Clean out junk from the article content
        cleanConditionally(articleContent, "form")
        cleanConditionally(articleContent, "fieldset")
        clean(articleContent, "object")
        clean(articleContent, "embed")
        clean(articleContent, "footer")
        clean(articleContent, "link")
        clean(articleContent, "aside")

        // Clean out elements with little content that have "share" in their id/class
        val shareElementThreshold = ReadabilityOptions.DEFAULT_CHAR_THRESHOLD
        for (topCandidate in articleContent.children().toList()) {
            cleanMatchedNodes(topCandidate) { node, matchString ->
                RegExps.shareElements.containsMatchIn(matchString) &&
                    node.text().length < shareElementThreshold
            }
        }

        clean(articleContent, "iframe")
        clean(articleContent, "input")
        clean(articleContent, "textarea")
        clean(articleContent, "select")
        clean(articleContent, "button")
        cleanHeaders(articleContent)

        // Do these last as the previous stuff may have removed junk
        cleanConditionally(articleContent, "table")
        cleanConditionally(articleContent, "ul")
        cleanConditionally(articleContent, "div")

        // replace H1 with H2 as H1 should be only title that is displayed separately
        replaceNodeTags(getAllNodesWithTag(articleContent, listOf("h1")), "h2")

        // Remove extra paragraphs
        removeNodes(getAllNodesWithTag(articleContent, listOf("p"))) { paragraph ->
            val contentElementCount = getAllNodesWithTag(paragraph, listOf("img", "embed", "object", "iframe")).size
            contentElementCount == 0 && getInnerText(paragraph, false).isEmpty()
        }

        for (br in getAllNodesWithTag(articleContent, listOf("br")).toList()) {
            val next = nextNode(br.nextSibling())
            if (next != null && next.tagName().uppercase() == "P") {
                br.remove()
            }
        }

        // Remove single-cell tables
        for (table in getAllNodesWithTag(articleContent, listOf("table")).toList()) {
            val tbody = if (hasSingleTagInsideElement(table, "TBODY")) {
                table.firstElementChild()!!
            } else {
                table
            }
            if (hasSingleTagInsideElement(tbody, "TR")) {
                val row = tbody.firstElementChild()!!
                if (hasSingleTagInsideElement(row, "TD")) {
                    var cell = row.firstElementChild()!!
                    val newTag = if (cell.childNodes().all { isPhrasingContent(it) }) "P" else "DIV"
                    cell = setNodeTag(cell, newTag)
                    table.replaceWith(cell)
                }
            }
        }
    }

    // ── _initializeNode ─────────────────────────────────────────────────

    private fun initializeNode(node: Element) {
        var score = 0.0
        when (node.tagName().uppercase()) {
            "DIV" -> score += 5
            "PRE", "TD", "BLOCKQUOTE" -> score += 3
            "ADDRESS", "OL", "UL", "DL", "DD", "DT", "LI", "FORM" -> score -= 3
            "H1", "H2", "H3", "H4", "H5", "H6", "TH" -> score -= 5
        }
        score += getClassWeight(node)
        setContentScore(node, score)
    }

    // ── _removeAndGetNext ───────────────────────────────────────────────

    private fun removeAndGetNext(node: Element): Element? {
        val nextNode = getNextNode(node, ignoreSelfAndKids = true)
        node.remove()
        return nextNode
    }

    // ── _getNextNode ────────────────────────────────────────────────────

    /**
     * Depth-first traversal. Returns the next node to visit.
     * If [ignoreSelfAndKids] is true, skips this node's children.
     */
    private fun getNextNode(node: Element, ignoreSelfAndKids: Boolean = false): Element? {
        if (!ignoreSelfAndKids) {
            val firstChild = node.firstElementChild()
            if (firstChild != null) return firstChild
        }
        val nextSibling = node.nextElementSibling()
        if (nextSibling != null) return nextSibling

        // Move up parent chain and find a sibling
        var current: Element? = node
        while (current != null) {
            val parent = current.parent()
            current = if (parent is Element) parent else null
            if (current?.nextElementSibling() != null) {
                return current.nextElementSibling()
            }
        }
        return null
    }

    // ── _textSimilarity ─────────────────────────────────────────────────

    private fun textSimilarity(textA: String, textB: String): Double {
        val tokensA = textA.lowercase().split(RegExps.tokenize).filter { it.isNotEmpty() }
        val tokensB = textB.lowercase().split(RegExps.tokenize).filter { it.isNotEmpty() }
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val uniqTokensB = tokensB.filter { it !in tokensA }
        val distanceB = uniqTokensB.joinToString(" ").length.toDouble() / tokensB.joinToString(" ").length.toDouble()
        return 1.0 - distanceB
    }

    // ── _isValidByline ──────────────────────────────────────────────────

    /**
     * Mimics browser DOM's `textContent` property: recursively concatenates
     * all descendant TextNode values, ignoring element boundaries (no extra
     * newlines for `<br>` or block elements).
     */
    private fun textContent(node: Node): String {
        val sb = StringBuilder()
        fun collect(n: Node) {
            if (n is TextNode) {
                sb.append(n.getWholeText())
            } else {
                for (child in n.childNodes()) {
                    collect(child)
                }
            }
        }
        collect(node)
        return sb.toString()
    }

    private fun isValidByline(node: Element, matchString: String): Boolean {
        val rel = node.attr("rel")
        val itemprop = node.attr("itemprop")
        val bylineLength = textContent(node).trim().length

        return (rel == "author" ||
            (itemprop.isNotEmpty() && itemprop.contains("author")) ||
            RegExps.byline.containsMatchIn(matchString)) &&
            bylineLength > 0 &&
            bylineLength < 100
    }

    // ── _getNodeAncestors ───────────────────────────────────────────────

    private fun getNodeAncestors(node: Element, maxDepth: Int = 0): List<Element> {
        val ancestors = mutableListOf<Element>()
        var current: Element? = node
        var i = 0
        while (true) {
            val parent = current?.parent() ?: break
            ancestors.add(parent)
            if (maxDepth > 0 && ++i == maxDepth) break
            current = parent
        }
        return ancestors
    }

    // ── _isElementWithoutContent ────────────────────────────────────────

    private fun isElementWithoutContent(node: Element): Boolean {
        return node.text().trim().isEmpty() &&
            (node.children().isEmpty() ||
                node.children().size == node.getElementsByTag("br").size + node.getElementsByTag("hr").size)
    }

    // ── _hasChildBlockElement ───────────────────────────────────────────

    private fun hasChildBlockElement(element: Element): Boolean {
        return element.childNodes().any { node ->
            (node is Element && node.tagName().uppercase() in DIV_TO_P_ELEMS) ||
                (node is Element && hasChildBlockElement(node))
        }
    }

    // ── _isPhrasingContent ──────────────────────────────────────────────

    private fun isPhrasingContent(node: Node): Boolean {
        if (node is TextNode) return true
        if (node !is Element) return false
        val tag = node.tagName().uppercase()
        return tag in PHRASING_ELEMS ||
            ((tag == "A" || tag == "DEL" || tag == "INS") &&
                node.childNodes().all { isPhrasingContent(it) })
    }

    // ── _isWhitespace ───────────────────────────────────────────────────

    private fun isWhitespace(node: Node): Boolean {
        return (node is TextNode && node.text().trim().isEmpty()) ||
            (node is Element && node.tagName().uppercase() == "BR")
    }

    // ── _getInnerText ───────────────────────────────────────────────────

    private fun getInnerText(e: Element, normalizeSpaces: Boolean = true): String {
        val textContent = e.text().trim()
        return if (normalizeSpaces) {
            textContent.replace(RegExps.normalize, " ")
        } else {
            textContent
        }
    }

    // ── _getCharCount ───────────────────────────────────────────────────

    private fun getCharCount(e: Element, s: String = ","): Int {
        return getInnerText(e).split(s).size - 1
    }

    // ── _hasSingleTagInsideElement ──────────────────────────────────────

    private fun hasSingleTagInsideElement(element: Element, tag: String): Boolean {
        if (element.children().size != 1 || element.children()[0].tagName().uppercase() != tag.uppercase()) {
            return false
        }
        return !element.childNodes().any { node ->
            node is TextNode && RegExps.hasContent.containsMatchIn(node.text())
        }
    }

    // ── _isSingleImage ──────────────────────────────────────────────────

    private fun isSingleImage(node: Element): Boolean {
        var current: Element? = node
        while (current != null) {
            if (current.tagName().uppercase() == "IMG") return true
            if (current.children().size != 1 || current.text().trim().isNotEmpty()) return false
            current = current.children().firstOrNull()
        }
        return false
    }

    // ── _isProbablyVisible ──────────────────────────────────────────────

    private fun isProbablyVisible(node: Element): Boolean {
        // In ksoup we don't have computed styles, so check the style attribute directly
        val style = node.attr("style")
        if (style.isNotEmpty()) {
            if (Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE).containsMatchIn(style)) return false
            if (Regex("visibility\\s*:\\s*hidden", RegexOption.IGNORE_CASE).containsMatchIn(style)) return false
        }
        if (node.hasAttr("hidden")) return false
        if (node.hasAttr("aria-hidden") && node.attr("aria-hidden") == "true") {
            val className = node.className()
            return className.contains("fallback-image")
        }
        return true
    }

    // ── _headerDuplicatesTitle ──────────────────────────────────────────

    private fun headerDuplicatesTitle(node: Element): Boolean {
        val tag = node.tagName().uppercase()
        if (tag != "H1" && tag != "H2") return false
        val heading = getInnerText(node, false)
        log("Evaluating similarity of header:", heading, articleTitle)
        return textSimilarity(articleTitle, heading) > 0.75
    }

    // ── _hasAncestorTag ─────────────────────────────────────────────────

    private fun hasAncestorTag(
        node: Element,
        tagName: String,
        maxDepth: Int = 3,
        filterFn: ((Element) -> Boolean)? = null
    ): Boolean {
        val upperTag = tagName.uppercase()
        var depth = 0
        var current: Element? = node
        while (true) {
            val parent = current?.parent() ?: return false
            if (maxDepth > 0 && depth > maxDepth) return false
            if (parent.tagName().uppercase() == upperTag && (filterFn == null || filterFn(parent))) {
                return true
            }
            current = parent
            depth++
        }
    }

    // ── _cleanStyles ────────────────────────────────────────────────────

    private fun cleanStyles(e: Element) {
        if (e.tagName().lowercase() == "svg") return

        for (attr in PRESENTATIONAL_ATTRIBUTES) {
            e.removeAttr(attr)
        }

        if (e.tagName().uppercase() in DEPRECATED_SIZE_ATTRIBUTE_ELEMS) {
            e.removeAttr("width")
            e.removeAttr("height")
        }

        var cur = e.firstElementChild()
        while (cur != null) {
            cleanStyles(cur)
            cur = cur.nextElementSibling()
        }
    }

    // ── _getLinkDensity ─────────────────────────────────────────────────

    private fun getLinkDensity(element: Element): Double {
        val textLength = getInnerText(element).length
        if (textLength == 0) return 0.0

        var linkLength = 0.0
        for (linkNode in element.getElementsByTag("a")) {
            val href = linkNode.attr("href")
            val coefficient = if (href.isNotEmpty() && RegExps.hashUrl.containsMatchIn(href)) 0.3 else 1.0
            linkLength += getInnerText(linkNode).length * coefficient
        }

        return linkLength / textLength
    }

    // ── _getClassWeight ─────────────────────────────────────────────────

    private fun getClassWeight(e: Element): Double {
        if (!flagIsActive(FLAG_WEIGHT_CLASSES)) return 0.0

        var weight = 0.0

        val className = e.className()
        if (className.isNotEmpty()) {
            if (RegExps.negative.containsMatchIn(className)) weight -= 25
            if (RegExps.positive.containsMatchIn(className)) weight += 25
        }

        val id = e.id()
        if (id.isNotEmpty()) {
            if (RegExps.negative.containsMatchIn(id)) weight -= 25
            if (RegExps.positive.containsMatchIn(id)) weight += 25
        }

        return weight
    }

    // ── _getTextDensity ─────────────────────────────────────────────────

    private fun getTextDensity(e: Element, tags: List<String>): Double {
        val textLength = getInnerText(e, true).length
        if (textLength == 0) return 0.0
        var childrenLength = 0
        val children = getAllNodesWithTag(e, tags)
        for (child in children) {
            childrenLength += getInnerText(child, true).length
        }
        return childrenLength.toDouble() / textLength.toDouble()
    }

    // ── _clean ──────────────────────────────────────────────────────────

    private fun clean(e: Element, tag: String) {
        val isEmbed = tag in listOf("object", "embed", "iframe")

        removeNodes(getAllNodesWithTag(e, listOf(tag))) { element ->
            if (isEmbed) {
                for (attr in element.attributes().toList()) {
                    if (allowedVideoRegex.containsMatchIn(attr.value)) {
                        return@removeNodes false
                    }
                }
                if (element.tagName().lowercase() == "object" && allowedVideoRegex.containsMatchIn(element.html())) {
                    return@removeNodes false
                }
            }
            true
        }
    }

    // ── _cleanConditionally ─────────────────────────────────────────────

    private fun cleanConditionally(e: Element, tag: String) {
        if (!flagIsActive(FLAG_CLEAN_CONDITIONALLY)) return

        removeNodes(getAllNodesWithTag(e, listOf(tag))) { node ->
            val isDataTable: (Element) -> Boolean = { t -> readabilityDataTables[t] == true }

            var isList = tag == "ul" || tag == "ol"
            if (!isList) {
                var listLength = 0
                val listNodes = getAllNodesWithTag(node, listOf("ul", "ol"))
                for (list in listNodes) {
                    listLength += getInnerText(list).length
                }
                val nodeText = getInnerText(node)
                isList = nodeText.isNotEmpty() && listLength.toDouble() / nodeText.length.toDouble() > 0.9
            }

            if (tag == "table" && isDataTable(node)) {
                return@removeNodes false
            }

            if (hasAncestorTag(node, "table", -1, isDataTable)) {
                return@removeNodes false
            }

            if (hasAncestorTag(node, "code")) {
                return@removeNodes false
            }

            // Keep element if it has data tables
            if (node.getElementsByTag("table").any { readabilityDataTables[it] == true }) {
                return@removeNodes false
            }

            val weight = getClassWeight(node)

            log("Cleaning Conditionally", node.tagName())

            val contentScore = 0.0

            if (weight + contentScore < 0) {
                return@removeNodes true
            }

            if (getCharCount(node, ",") < 10) {
                val p = node.getElementsByTag("p").size
                val img = node.getElementsByTag("img").size
                val li = node.getElementsByTag("li").size - 100
                val input = node.getElementsByTag("input").size
                val headingDensity = getTextDensity(node, listOf("h1", "h2", "h3", "h4", "h5", "h6"))

                var embedCount = 0
                val embeds = getAllNodesWithTag(node, listOf("object", "embed", "iframe"))

                for (embed in embeds) {
                    for (attr in embed.attributes().toList()) {
                        if (allowedVideoRegex.containsMatchIn(attr.value)) {
                            return@removeNodes false
                        }
                    }
                    if (embed.tagName().lowercase() == "object" && allowedVideoRegex.containsMatchIn(embed.html())) {
                        return@removeNodes false
                    }
                    embedCount++
                }

                val innerText = getInnerText(node)

                if (RegExps.adWords.containsMatchIn(innerText) || RegExps.loadingWords.containsMatchIn(innerText)) {
                    return@removeNodes true
                }

                val contentLength = innerText.length
                val linkDensity = getLinkDensity(node)
                val textishTags = listOf("SPAN", "LI", "TD") + DIV_TO_P_ELEMS.toList()
                val textDensity = getTextDensity(node, textishTags)
                val isFigureChild = hasAncestorTag(node, "figure")

                // Apply shadiness checks
                fun shouldRemoveNode(): Boolean {
                    if (!isFigureChild && img > 1 && p.toDouble() / img.toDouble() < 0.5) {
                        log("Bad p to img ratio")
                        return true
                    }
                    if (!isList && li > p) {
                        log("Too many li's outside of a list")
                        return true
                    }
                    if (input > p / 3) {
                        log("Too many inputs per p")
                        return true
                    }
                    if (!isList && !isFigureChild && headingDensity < 0.9 &&
                        contentLength < 25 && (img == 0 || img > 2) && linkDensity > 0
                    ) {
                        log("Suspiciously short")
                        return true
                    }
                    if (!isList && weight < 25 && linkDensity > 0.2 + linkDensityModifier) {
                        log("Low weight and a little linky")
                        return true
                    }
                    if (weight >= 25 && linkDensity > 0.5 + linkDensityModifier) {
                        log("High weight and mostly links")
                        return true
                    }
                    if ((embedCount == 1 && contentLength < 75) || embedCount > 1) {
                        log("Suspicious embed")
                        return true
                    }
                    if (img == 0 && textDensity == 0.0) {
                        log("No useful content")
                        return true
                    }
                    return false
                }

                var haveToRemove = shouldRemoveNode()

                // Allow simple lists of images to remain in pages
                if (isList && haveToRemove) {
                    for (child in node.children()) {
                        if (child.children().size > 1) {
                            return@removeNodes haveToRemove
                        }
                    }
                    val liCount = node.getElementsByTag("li").size
                    if (img == liCount) {
                        return@removeNodes false
                    }
                }
                return@removeNodes haveToRemove
            }
            false
        }
    }

    // ── _cleanMatchedNodes ──────────────────────────────────────────────

    private fun cleanMatchedNodes(e: Element, filter: (Element, String) -> Boolean) {
        val endOfSearchMarkerNode = getNextNode(e, ignoreSelfAndKids = true)
        var next = getNextNode(e)
        while (next != null && next != endOfSearchMarkerNode) {
            if (filter(next, next.className() + " " + next.id())) {
                next = removeAndGetNext(next)
            } else {
                next = getNextNode(next)
            }
        }
    }

    // ── _cleanHeaders ───────────────────────────────────────────────────

    private fun cleanHeaders(e: Element) {
        val headingNodes = getAllNodesWithTag(e, listOf("h1", "h2"))
        removeNodes(headingNodes) { node ->
            val shouldRemove = getClassWeight(node) < 0
            if (shouldRemove) {
                log("Removing header with low class weight:", node.tagName())
            }
            shouldRemove
        }
    }

    // ── _getRowAndColumnCount ───────────────────────────────────────────

    private data class TableSize(val rows: Int, val columns: Int)

    private fun getRowAndColumnCount(table: Element): TableSize {
        var rows = 0
        var columns = 0
        val trs = table.getElementsByTag("tr")
        for (tr in trs) {
            val rowspanAttr = tr.attr("rowspan")
            val rowspan = if (rowspanAttr.isNotEmpty()) rowspanAttr.toIntOrNull() ?: 0 else 0
            rows += if (rowspan > 0) rowspan else 1

            var columnsInThisRow = 0
            val cells = tr.getElementsByTag("td")
            for (cell in cells) {
                val colspanAttr = cell.attr("colspan")
                val colspan = if (colspanAttr.isNotEmpty()) colspanAttr.toIntOrNull() ?: 0 else 0
                columnsInThisRow += if (colspan > 0) colspan else 1
            }
            columns = maxOf(columns, columnsInThisRow)
        }
        return TableSize(rows, columns)
    }

    // ── _markDataTables ─────────────────────────────────────────────────

    private fun markDataTables(root: Element) {
        val tables = root.getElementsByTag("table")
        for (table in tables) {
            val role = table.attr("role")
            if (role == "presentation") {
                readabilityDataTables[table] = false
                continue
            }
            val datatable = table.attr("datatable")
            if (datatable == "0") {
                readabilityDataTables[table] = false
                continue
            }
            val summary = table.attr("summary")
            if (summary.isNotEmpty()) {
                readabilityDataTables[table] = true
                continue
            }

            val caption = table.getElementsByTag("caption").firstOrNull()
            if (caption != null && caption.childNodes().isNotEmpty()) {
                readabilityDataTables[table] = true
                continue
            }

            val dataTableDescendants = listOf("col", "colgroup", "tfoot", "thead", "th")
            if (dataTableDescendants.any { table.getElementsByTag(it).isNotEmpty() }) {
                log("Data table because found data-y descendant")
                readabilityDataTables[table] = true
                continue
            }

            // Nested tables indicate a layout table
            // Note: ksoup's getElementsByTag includes the element itself, so we
            // must filter it out to match the browser DOM's getElementsByTagName
            // which only returns descendants.
            if (table.getElementsByTag("table").any { it !== table }) {
                readabilityDataTables[table] = false
                continue
            }

            val sizeInfo = getRowAndColumnCount(table)
            if (sizeInfo.columns == 1 || sizeInfo.rows == 1) {
                readabilityDataTables[table] = false
                continue
            }
            if (sizeInfo.rows >= 10 || sizeInfo.columns > 4) {
                readabilityDataTables[table] = true
                continue
            }
            readabilityDataTables[table] = sizeInfo.rows * sizeInfo.columns > 10
        }
    }

    // ── _fixLazyImages ──────────────────────────────────────────────────

    private fun fixLazyImages(root: Element) {
        val elems = getAllNodesWithTag(root, listOf("img", "picture", "figure"))
        for (elem in elems) {
            val src = elem.attr("src")
            // Check if src is base64 data URI
            if (src.isNotEmpty() && RegExps.b64DataUrl.containsMatchIn(src)) {
                val parts = RegExps.b64DataUrl.find(src)
                if (parts != null && parts.groupValues[1] == "image/svg+xml") {
                    continue
                }

                var srcCouldBeRemoved = false
                for (attr in elem.attributes().toList()) {
                    if (attr.key == "src") continue
                    if (Regex("\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE).containsMatchIn(attr.value)) {
                        srcCouldBeRemoved = true
                        break
                    }
                }

                if (srcCouldBeRemoved && parts != null) {
                    val b64starts = parts.value.length
                    val b64length = src.length - b64starts
                    if (b64length < 133) {
                        elem.removeAttr("src")
                    }
                }
            }

            val srcset = elem.attr("srcset")
            if ((elem.attr("src").isNotEmpty() || (srcset.isNotEmpty() && srcset != "null")) &&
                !elem.className().lowercase().contains("lazy")
            ) {
                continue
            }

            // Collect attributes to copy first to avoid concurrent modification
            val attrsToCopy = mutableListOf<Pair<String, String>>()
            for (attr in elem.attributes().toList()) {
                if (attr.key == "src" || attr.key == "srcset" || attr.key == "alt") continue
                var copyTo: String? = null
                if (Regex("\\.(jpg|jpeg|png|webp)\\s+\\d").containsMatchIn(attr.value)) {
                    copyTo = "srcset"
                } else if (Regex("^\\s*\\S+\\.(jpg|jpeg|png|webp)\\S*\\s*$").containsMatchIn(attr.value)) {
                    copyTo = "src"
                }
                if (copyTo != null) {
                    attrsToCopy.add(copyTo to attr.value)
                }
            }
            for ((copyTo, value) in attrsToCopy) {
                val tag = elem.tagName().uppercase()
                if (tag == "IMG" || tag == "PICTURE") {
                    elem.attr(copyTo, value)
                } else if (tag == "FIGURE" && getAllNodesWithTag(elem, listOf("img", "picture")).isEmpty()) {
                    val img = Element("img")
                    img.attr(copyTo, value)
                    elem.appendChild(img)
                }
            }
        }
    }

    // ── _unwrapNoscriptImages ───────────────────────────────────────────

    private fun unwrapNoscriptImages(doc: Document) {
        val imgs = doc.getElementsByTag("img").toList()
        for (img in imgs) {
            var hasImageAttr = false
            for (attr in img.attributes().toList()) {
                when (attr.key) {
                    "src", "srcset", "data-src", "data-srcset" -> {
                        hasImageAttr = true
                        break
                    }
                }
                if (Regex("\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE).containsMatchIn(attr.value)) {
                    hasImageAttr = true
                    break
                }
            }
            if (!hasImageAttr) {
                img.remove()
            }
        }

        val noscripts = doc.getElementsByTag("noscript").toList()
        for (noscript in noscripts) {
            if (!isSingleImage(noscript)) continue

            val tmp = Element("div")
            tmp.html(noscript.html())

            val prevElement = noscript.previousElementSibling()
            if (prevElement != null && isSingleImage(prevElement)) {
                val prevImg = if (prevElement.tagName().uppercase() != "IMG") {
                    prevElement.getElementsByTag("img").firstOrNull() ?: continue
                } else {
                    prevElement
                }

                val newImg = tmp.getElementsByTag("img").firstOrNull() ?: continue

                for (attr in prevImg.attributes().toList()) {
                    if (attr.value.isEmpty()) continue
                    if (attr.key == "src" || attr.key == "srcset" ||
                        Regex("\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE).containsMatchIn(attr.value)
                    ) {
                        if (newImg.attr(attr.key) == attr.value) continue
                        var attrName = attr.key
                        if (newImg.hasAttr(attrName)) {
                            attrName = "data-old-$attrName"
                        }
                        newImg.attr(attrName, attr.value)
                    }
                }

                val replacement = tmp.firstElementChild()
                if (replacement != null) {
                    prevElement.replaceWith(replacement)
                }
            }
        }
    }

    // ── _removeScripts ──────────────────────────────────────────────────

    private fun removeScripts(doc: Document) {
        removeNodes(getAllNodesWithTag(doc, listOf("script", "noscript")))
    }

    // ── _removeCommentNodes ─────────────────────────────────────────────

    /**
     * Recursively remove all HTML comment nodes from the document.
     * The JS reference implementation's JSDOMParser discards comments during
     * parsing; ksoup preserves them, so we need to strip them explicitly.
     */
    private fun removeCommentNodes(node: Node) {
        val children = node.childNodes().toList()
        for (child in children) {
            if (child is Comment) {
                child.remove()
            } else if (child.childNodeSize() > 0) {
                removeCommentNodes(child)
            }
        }
    }

    // ── _unescapeHtmlEntities ───────────────────────────────────────────

    private fun unescapeHtmlEntities(str: String?): String? {
        if (str == null) return null

        var result = str.replace(Regex("&(quot|amp|apos|lt|gt);")) { matchResult ->
            HTML_ESCAPE_MAP[matchResult.groupValues[1]] ?: matchResult.value
        }

        result = result.replace(Regex("&#(?:x([0-9a-fA-F]+)|([0-9]+));")) { matchResult ->
            val hex = matchResult.groupValues[1]
            val numStr = matchResult.groupValues[2]
            var num = if (hex.isNotEmpty()) hex.toLongOrNull(16) ?: 0xFFFD.toLong() else numStr.toLongOrNull(10) ?: 0xFFFD.toLong()
            if (num == 0L || num > 0x10FFFF || (num in 0xD800..0xDFFF)) {
                num = 0xFFFD
            }
            // Use Char for BMP, or surrogate pairs for supplementary characters
            val codePoint = num.toInt()
            if (codePoint <= 0xFFFF) {
                codePoint.toChar().toString()
            } else {
                val high = ((codePoint - 0x10000) shr 10) + 0xD800
                val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
                "${high.toChar()}${low.toChar()}"
            }
        }

        return result
    }

    // ── _getJSONLD ──────────────────────────────────────────────────────

    private fun getJSONLD(doc: Document): ArticleMetadata {
        val scripts = getAllNodesWithTag(doc, listOf("script"))
        var metadata: ArticleMetadata? = null

        for (jsonLdElement in scripts) {
            if (metadata != null) break
            if (jsonLdElement.attr("type") != "application/ld+json") continue

            try {
                val content = jsonLdElement.data()
                    .replace(Regex("^\\s*<!\\[CDATA\\[|\\]\\]>\\s*$"), "")

                val json = Json { ignoreUnknownKeys = true; isLenient = true }
                val parsed = json.parseToJsonElement(content)

                var articleObj: JsonObject? = null

                when (parsed) {
                    is JsonArray -> {
                        articleObj = parsed.jsonArray.firstOrNull { element ->
                            val type = (element as? JsonObject)?.get("@type")
                            type is JsonPrimitive && RegExps.jsonLdArticleTypes.containsMatchIn(type.content)
                        }?.jsonObject
                    }
                    is JsonObject -> {
                        articleObj = parsed.jsonObject
                    }
                    else -> continue
                }

                if (articleObj == null) continue

                // Check @context
                val context = articleObj["@context"]
                val schemaDotOrgRegex = Regex("^https?://schema\\.org/?$")
                val contextMatches = when {
                    context is JsonPrimitive && context.isString ->
                        schemaDotOrgRegex.containsMatchIn(context.content)
                    context is JsonObject -> {
                        val vocab = context["@vocab"]
                        vocab is JsonPrimitive && vocab.isString && schemaDotOrgRegex.containsMatchIn(vocab.content)
                    }
                    else -> false
                }
                if (!contextMatches) continue

                // If no @type, check @graph
                val type = articleObj["@type"]
                if (type == null || (type is JsonPrimitive && type.content.isEmpty())) {
                    val graph = articleObj["@graph"]
                    if (graph is JsonArray) {
                        articleObj = graph.firstOrNull { element ->
                            val t = (element as? JsonObject)?.get("@type")
                            t is JsonPrimitive && RegExps.jsonLdArticleTypes.containsMatchIn(t.content)
                        }?.jsonObject
                    }
                }

                if (articleObj == null) continue

                val typeValue = articleObj["@type"]
                if (typeValue == null || !(typeValue is JsonPrimitive && RegExps.jsonLdArticleTypes.containsMatchIn(typeValue.content))) {
                    continue
                }

                val meta = ArticleMetadata()

                val name = articleObj["name"]?.jsonPrimitiveOrNull()?.content?.trim()
                val headline = articleObj["headline"]?.jsonPrimitiveOrNull()?.content?.trim()

                if (name != null && headline != null && name != headline) {
                    val title = getArticleTitle()
                    val nameMatches = textSimilarity(name, title) > 0.75
                    val headlineMatches = textSimilarity(headline, title) > 0.75
                    meta.title = if (headlineMatches && !nameMatches) headline else name
                } else if (name != null) {
                    meta.title = name
                } else if (headline != null) {
                    meta.title = headline
                }

                val author = articleObj["author"]
                if (author is JsonObject) {
                    val authorName = author["name"]?.jsonPrimitiveOrNull()?.content?.trim()
                    if (!authorName.isNullOrEmpty()) meta.byline = authorName
                } else if (author is JsonArray) {
                    val authorNames = author.mapNotNull { el ->
                        (el as? JsonObject)?.get("name")?.jsonPrimitiveOrNull()?.content?.trim()
                            ?.ifEmpty { null }
                    }
                    if (authorNames.isNotEmpty()) {
                        meta.byline = authorNames.joinToString(", ")
                    }
                }

                val description = articleObj["description"]?.jsonPrimitiveOrNull()?.content?.trim()
                if (description != null) meta.excerpt = description

                val publisher = articleObj["publisher"]
                if (publisher is JsonObject) {
                    val publisherName = publisher["name"]?.jsonPrimitiveOrNull()?.content?.trim()
                    if (publisherName != null) meta.siteName = publisherName
                }

                val datePublished = articleObj["datePublished"]?.jsonPrimitiveOrNull()?.content?.trim()
                if (datePublished != null) meta.publishedTime = datePublished

                metadata = meta
            } catch (e: Exception) {
                log(e.message ?: "JSON-LD parsing error")
            }
        }

        return metadata ?: ArticleMetadata()
    }

    /**
     * Extension to safely get a JsonPrimitive or null.
     */
    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? {
        return this as? JsonPrimitive
    }

    // ── _getArticleMetadata ─────────────────────────────────────────────

    private fun getArticleMetadata(jsonld: ArticleMetadata): ArticleMetadata {
        val values = mutableMapOf<String, String>()
        val metaElements = doc.getElementsByTag("meta")

        val propertyPattern = Regex(
            "\\s*(article|dc|dcterm|og|twitter)\\s*:\\s*(author|creator|description|published_time|title|site_name)\\s*",
            RegexOption.IGNORE_CASE
        )
        val namePattern = Regex(
            "^\\s*(?:(dc|dcterm|og|twitter|parsely|weibo:(article|webpage))\\s*[-.:])?(author|creator|pub-date|description|title|site_name)\\s*$",
            RegexOption.IGNORE_CASE
        )

        for (element in metaElements) {
            val elementName = element.attr("name")
            val elementProperty = element.attr("property")
            val content = element.attr("content")
            if (content.isEmpty()) continue

            var name: String? = null

            if (elementProperty.isNotEmpty()) {
                val matches = propertyPattern.find(elementProperty)
                if (matches != null) {
                    name = matches.value.lowercase().replace(Regex("\\s"), "")
                    values[name] = content.trim()
                }
            }
            if (name == null && elementName.isNotEmpty() && namePattern.containsMatchIn(elementName)) {
                name = elementName.lowercase().replace(Regex("\\s"), "").replace(".", ":")
                values[name] = content.trim()
            }
        }

        val meta = ArticleMetadata()

        // get title
        meta.title = jsonld.title
            ?: values["dc:title"]
            ?: values["dcterm:title"]
            ?: values["og:title"]
            ?: values["weibo:article:title"]
            ?: values["weibo:webpage:title"]
            ?: values["title"]
            ?: values["twitter:title"]
            ?: values["parsely-title"]

        if (meta.title == null) {
            meta.title = getArticleTitle()
        }

        val articleAuthor = values["article:author"]?.let { author ->
            if (!isUrl(author)) author else null
        }

        // get author
        meta.byline = jsonld.byline
            ?: values["dc:creator"]
            ?: values["dcterm:creator"]
            ?: values["author"]
            ?: values["parsely-author"]
            ?: articleAuthor

        // get description
        meta.excerpt = jsonld.excerpt
            ?: values["dc:description"]
            ?: values["dcterm:description"]
            ?: values["og:description"]
            ?: values["weibo:article:description"]
            ?: values["weibo:webpage:description"]
            ?: values["description"]
            ?: values["twitter:description"]

        // get site name
        meta.siteName = jsonld.siteName ?: values["og:site_name"]

        // get article published time
        meta.publishedTime = jsonld.publishedTime
            ?: values["article:published_time"]
            ?: values["parsely-pub-date"]

        // Unescape HTML entities in metadata
        meta.title = unescapeHtmlEntities(meta.title)
        meta.byline = unescapeHtmlEntities(meta.byline)
        meta.excerpt = unescapeHtmlEntities(meta.excerpt)
        meta.siteName = unescapeHtmlEntities(meta.siteName)
        meta.publishedTime = unescapeHtmlEntities(meta.publishedTime)

        return meta
    }

    // ── _grabArticle ────────────────────────────────────────────────────

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun grabArticle(page: Element? = null): Element? {
        log("**** grabArticle ****")
        val isPaging = page != null
        val body = page ?: doc.body()

        val pageCacheHtml = body.html()

        while (true) {
            log("Starting grabArticle loop")
            val stripUnlikelyCandidates = flagIsActive(FLAG_STRIP_UNLIKELYS)

            val elementsToScore = mutableListOf<Element>()
            var node: Element? = doc.documentElement()

            var shouldRemoveTitleHeader = true

            while (node != null) {
                val tag = node.tagName().uppercase()

                if (tag == "HTML") {
                    articleLang = node.attr("lang").ifEmpty { null }
                }

                val matchString = node.className() + " " + node.id()

                if (!isProbablyVisible(node)) {
                    log("Removing hidden node - $matchString")
                    node = removeAndGetNext(node)
                    continue
                }

                // Remove aria-modal dialogs
                if (node.attr("aria-modal") == "true" && node.attr("role") == "dialog") {
                    node = removeAndGetNext(node)
                    continue
                }

                // Check for byline
                if (articleByline.isNullOrEmpty() && metadata.byline.isNullOrEmpty() && isValidByline(node, matchString)) {
                    val endOfSearchMarkerNode = getNextNode(node, ignoreSelfAndKids = true)
                    var nextForByline = getNextNode(node)
                    var itemPropNameNode: Element? = null
                    while (nextForByline != null && nextForByline != endOfSearchMarkerNode) {
                        val itemprop = nextForByline.attr("itemprop")
                        if (itemprop.isNotEmpty() && itemprop.contains("name")) {
                            itemPropNameNode = nextForByline
                            break
                        }
                        nextForByline = getNextNode(nextForByline)
                    }
                    articleByline = textContent(itemPropNameNode ?: node).trim()
                    node = removeAndGetNext(node)
                    continue
                }

                if (shouldRemoveTitleHeader && headerDuplicatesTitle(node)) {
                    log("Removing header: ", node.text().trim(), articleTitle.trim())
                    shouldRemoveTitleHeader = false
                    node = removeAndGetNext(node)
                    continue
                }

                // Remove unlikely candidates
                if (stripUnlikelyCandidates) {
                    if (RegExps.unlikelyCandidates.containsMatchIn(matchString) &&
                        !RegExps.okMaybeItsACandidate.containsMatchIn(matchString) &&
                        !hasAncestorTag(node, "table") &&
                        !hasAncestorTag(node, "code") &&
                        tag != "BODY" &&
                        tag != "A"
                    ) {
                        log("Removing unlikely candidate - $matchString")
                        node = removeAndGetNext(node)
                        continue
                    }

                    if (node.attr("role") in UNLIKELY_ROLES) {
                        log("Removing content with role ${node.attr("role")} - $matchString")
                        node = removeAndGetNext(node)
                        continue
                    }
                }

                // Remove empty content elements
                if (tag in listOf("DIV", "SECTION", "HEADER", "H1", "H2", "H3", "H4", "H5", "H6") &&
                    isElementWithoutContent(node)
                ) {
                    node = removeAndGetNext(node)
                    continue
                }

                if (tag in DEFAULT_TAGS_TO_SCORE) {
                    elementsToScore.add(node)
                }

                // Turn all divs that don't have children block level elements into p's
                if (tag == "DIV") {
                    // Put phrasing content into paragraphs
                    var childNode: Node? = node.childNodes().firstOrNull()
                    while (childNode != null) {
                        var nextSibling: Node? = childNode.nextSibling()
                        if (isPhrasingContent(childNode)) {
                            // Collect all consecutive phrasing content
                            val phrasingNodes = mutableListOf<Node>()
                            do {
                                nextSibling = childNode?.nextSibling()
                                if (childNode != null) phrasingNodes.add(childNode)
                                childNode = nextSibling
                            } while (childNode != null && isPhrasingContent(childNode))

                            // Trim leading whitespace
                            while (phrasingNodes.isNotEmpty() && isWhitespace(phrasingNodes.first())) {
                                phrasingNodes.removeFirst().remove()
                            }
                            // Trim trailing whitespace
                            while (phrasingNodes.isNotEmpty() && isWhitespace(phrasingNodes.last())) {
                                phrasingNodes.removeLast().remove()
                            }

                            if (phrasingNodes.isNotEmpty()) {
                                val p = Element("p")
                                // Insert <p> before the next non-phrasing node
                                if (nextSibling != null) {
                                    nextSibling.before(p)
                                } else {
                                    node.appendChild(p)
                                }
                                for (pNode in phrasingNodes) {
                                    p.appendChild(pNode)
                                }
                            }
                        }
                        childNode = nextSibling
                    }

                    if (hasSingleTagInsideElement(node, "P") && getLinkDensity(node) < 0.25) {
                        val newNode = node.children()[0]
                        node.replaceWith(newNode)
                        node = newNode
                        elementsToScore.add(node)
                    } else if (!hasChildBlockElement(node)) {
                        node = setNodeTag(node, "P")
                        elementsToScore.add(node)
                    }
                }
                node = getNextNode(node)
            }

            // Loop through all paragraphs, and assign a score to them
            val candidates = mutableListOf<Element>()
            for (elementToScore in elementsToScore) {
                if (elementToScore.parent() == null) continue

                val innerText = getInnerText(elementToScore)
                if (innerText.length < 25) continue

                val ancestors = getNodeAncestors(elementToScore, 5)
                if (ancestors.isEmpty()) continue

                var contentScore = 1.0
                contentScore += innerText.split(RegExps.commas).size
                contentScore += minOf((innerText.length / 100).toDouble(), 3.0)

                for ((level, ancestor) in ancestors.withIndex()) {
                    val ancestorTag = ancestor.tagName()
                    if (ancestorTag.isEmpty() || ancestor.parent() == null) continue

                    if (!hasContentScore(ancestor)) {
                        initializeNode(ancestor)
                        candidates.add(ancestor)
                    }

                    val scoreDivider = when (level) {
                        0 -> 1.0
                        1 -> 2.0
                        else -> (level * 3).toDouble()
                    }
                    addContentScore(ancestor, contentScore / scoreDivider)
                }
            }

            // Find the one with the highest score
            val topCandidates = mutableListOf<Element>()
            for (candidate in candidates) {
                val candidateScore = getContentScore(candidate) * (1 - getLinkDensity(candidate))
                setContentScore(candidate, candidateScore)

                log("Candidate:", candidate.tagName(), "with score $candidateScore")

                for (t in 0 until options.nbTopCandidates) {
                    val aTopCandidate = topCandidates.getOrNull(t)
                    if (aTopCandidate == null || candidateScore > getContentScore(aTopCandidate)) {
                        topCandidates.add(t, candidate)
                        if (topCandidates.size > options.nbTopCandidates) {
                            topCandidates.removeAt(topCandidates.size - 1)
                        }
                        break
                    }
                }
            }

            var topCandidate: Element? = topCandidates.firstOrNull()
            var neededToCreateTopCandidate = false
            var parentOfTopCandidate: Element?

            if (topCandidate == null || topCandidate.tagName().uppercase() == "BODY") {
                topCandidate = Element("DIV")
                neededToCreateTopCandidate = true
                while (body.childNodes().isNotEmpty()) {
                    topCandidate.appendChild(body.childNode(0))
                }
                body.appendChild(topCandidate)
                initializeNode(topCandidate)
            } else {
                // Find a better top candidate node
                val alternativeCandidateAncestors = mutableListOf<List<Element>>()
                for (i in 1 until topCandidates.size) {
                    val score = getContentScore(topCandidates[i])
                    val topScore = getContentScore(topCandidate)
                    if (topScore > 0 && score / topScore >= 0.75) {
                        alternativeCandidateAncestors.add(getNodeAncestors(topCandidates[i]))
                    }
                }

                val MINIMUM_TOPCANDIDATES = 3
                if (alternativeCandidateAncestors.size >= MINIMUM_TOPCANDIDATES) {
                    parentOfTopCandidate = topCandidate.parent()
                    while (parentOfTopCandidate != null && parentOfTopCandidate.tagName().uppercase() != "BODY") {
                        var listsContainingThisAncestor = 0
                        for (ancestorIndex in alternativeCandidateAncestors.indices) {
                            if (listsContainingThisAncestor >= MINIMUM_TOPCANDIDATES) break
                            if (alternativeCandidateAncestors[ancestorIndex].contains(parentOfTopCandidate)) {
                                listsContainingThisAncestor++
                            }
                        }
                        if (listsContainingThisAncestor >= MINIMUM_TOPCANDIDATES) {
                            topCandidate = parentOfTopCandidate
                            break
                        }
                        parentOfTopCandidate = parentOfTopCandidate.parent()
                    }
                }

                if (!hasContentScore(topCandidate!!)) {
                    initializeNode(topCandidate)
                }

                // Check if there's a better parent
                parentOfTopCandidate = topCandidate.parent()
                var lastScore = getContentScore(topCandidate)
                val scoreThreshold = lastScore / 3
                while (parentOfTopCandidate != null && parentOfTopCandidate.tagName().uppercase() != "BODY") {
                    if (!hasContentScore(parentOfTopCandidate)) {
                        parentOfTopCandidate = parentOfTopCandidate.parent()
                        continue
                    }
                    val parentScore = getContentScore(parentOfTopCandidate)
                    if (parentScore < scoreThreshold) break
                    if (parentScore > lastScore) {
                        topCandidate = parentOfTopCandidate
                        break
                    }
                    lastScore = getContentScore(parentOfTopCandidate)
                    parentOfTopCandidate = parentOfTopCandidate.parent()
                }

                // If the top candidate is the only child, use parent instead
                parentOfTopCandidate = topCandidate!!.parent()
                while (parentOfTopCandidate != null &&
                    parentOfTopCandidate.tagName().uppercase() != "BODY" &&
                    parentOfTopCandidate.children().size == 1
                ) {
                    topCandidate = parentOfTopCandidate
                    parentOfTopCandidate = topCandidate.parent()
                }
                if (!hasContentScore(topCandidate)) {
                    initializeNode(topCandidate)
                }
            }

            // Now that we have the top candidate, look through its siblings
            val articleContent = Element("DIV")
            if (isPaging) {
                articleContent.attr("id", "readability-content")
            }

            val siblingScoreThreshold = maxOf(10.0, getContentScore(topCandidate) * 0.2)
            parentOfTopCandidate = topCandidate.parent()
            val siblings = parentOfTopCandidate?.children()?.toMutableList() ?: mutableListOf()

            var s = 0
            while (s < siblings.size) {
                val sibling = siblings[s]
                var append = false

                log("Looking at sibling node:", sibling.tagName(),
                    if (hasContentScore(sibling)) "with score ${getContentScore(sibling)}" else "")

                if (sibling === topCandidate) {
                    append = true
                } else {
                    var contentBonus = 0.0

                    if (sibling.className() == topCandidate.className() && topCandidate.className().isNotEmpty()) {
                        contentBonus += getContentScore(topCandidate) * 0.2
                    }

                    if (hasContentScore(sibling) && getContentScore(sibling) + contentBonus >= siblingScoreThreshold) {
                        append = true
                    } else if (sibling.nodeName().uppercase() == "P") {
                        val linkDensity = getLinkDensity(sibling)
                        val nodeContent = getInnerText(sibling)
                        val nodeLength = nodeContent.length

                        if (nodeLength > 80 && linkDensity < 0.25) {
                            append = true
                        } else if (nodeLength < 80 && nodeLength > 0 && linkDensity == 0.0 &&
                            Regex("\\.( |$)").containsMatchIn(nodeContent)
                        ) {
                            append = true
                        }
                    }
                }

                if (append) {
                    log("Appending node:", sibling.tagName())

                    if (sibling.nodeName().uppercase() !in ALTER_TO_DIV_EXCEPTIONS) {
                        log("Altering sibling:", sibling.tagName(), "to div.")
                        val newSibling = setNodeTag(sibling, "DIV")
                        // After setNodeTag, the sibling reference is stale; update siblings list
                        siblings[s] = newSibling
                    }

                    articleContent.appendChild(siblings[s])
                    // Re-fetch siblings since appendChild moves the node
                    siblings.clear()
                    siblings.addAll(parentOfTopCandidate?.children()?.toList() ?: emptyList())
                    s -= 1
                }
                s++
            }

            if (options.debug) {
                log("Article content pre-prep: " + articleContent.html().take(200))
            }

            prepArticle(articleContent)

            if (options.debug) {
                log("Article content post-prep: " + articleContent.html().take(200))
            }

            if (neededToCreateTopCandidate) {
                topCandidate.attr("id", "readability-page-1")
                topCandidate.attr("class", "page")
            } else {
                val div = Element("DIV")
                div.attr("id", "readability-page-1")
                div.attr("class", "page")
                while (articleContent.childNodes().isNotEmpty()) {
                    div.appendChild(articleContent.childNode(0))
                }
                articleContent.appendChild(div)
            }

            if (options.debug) {
                log("Article content after paging: " + articleContent.html().take(200))
            }

            var parseSuccessful = true

            val textLength = getInnerText(articleContent, true).length
            if (textLength < options.charThreshold) {
                parseSuccessful = false
                body.html(pageCacheHtml)

                attempts.add(Attempt(articleContent, textLength))

                if (flagIsActive(FLAG_STRIP_UNLIKELYS)) {
                    removeFlag(FLAG_STRIP_UNLIKELYS)
                } else if (flagIsActive(FLAG_WEIGHT_CLASSES)) {
                    removeFlag(FLAG_WEIGHT_CLASSES)
                } else if (flagIsActive(FLAG_CLEAN_CONDITIONALLY)) {
                    removeFlag(FLAG_CLEAN_CONDITIONALLY)
                } else {
                    attempts.sortByDescending { it.textLength }
                    if (attempts[0].textLength == 0) {
                        return null
                    }
                    return attempts[0].articleContent.also { parseSuccessful = true }
                }
            }

            if (parseSuccessful) {
                val ancestors = mutableListOf<Element>()
                if (parentOfTopCandidate != null) ancestors.add(parentOfTopCandidate)
                ancestors.add(topCandidate)
                if (parentOfTopCandidate != null) {
                    ancestors.addAll(getNodeAncestors(parentOfTopCandidate))
                }

                for (ancestor in ancestors) {
                    val articleDirAttr = ancestor.attr("dir")
                    if (articleDirAttr.isNotEmpty()) {
                        articleDir = articleDirAttr
                        break
                    }
                }

                return articleContent
            }
        }
    }

    // ── parse ───────────────────────────────────────────────────────────

    /**
     * Run readability and extract the article.
     *
     * @return [Article] if extraction was successful, or `null` otherwise.
     */
    public fun parse(): Article? {
        // Avoid parsing too large documents
        if (options.maxElemsToParse > 0) {
            val numTags = doc.select("*").size
            if (numTags > options.maxElemsToParse) {
                throw IllegalArgumentException(
                    "Aborting parsing document; $numTags elements found"
                )
            }
        }

        // Unwrap image from noscript
        unwrapNoscriptImages(doc)

        // Extract JSON-LD metadata before removing scripts
        val jsonLd = if (options.disableJSONLD) ArticleMetadata() else getJSONLD(doc)

        // Remove script tags from the document
        removeScripts(doc)

        // Remove comment nodes from the document.
        // The JS reference implementation (JSDOMParser) discards comments during
        // parsing.  ksoup preserves them, so we strip them here to match.
        removeCommentNodes(doc)

        prepDocument()

        val meta = getArticleMetadata(jsonLd)
        metadata = meta
        articleTitle = meta.title ?: ""

        val articleContent = grabArticle() ?: return null

        log("Grabbed: " + articleContent.html().take(200))

        postProcessContent(articleContent)

        // If we haven't found an excerpt in the article's metadata, use the article's
        // first paragraph as the excerpt.
        if (meta.excerpt == null) {
            val paragraphs = articleContent.getElementsByTag("p")
            if (paragraphs.isNotEmpty()) {
                meta.excerpt = textContent(paragraphs[0]).trim()
            }
        }

        val textContent = articleContent.text()
        return Article(
            title = articleTitle,
            byline = meta.byline?.takeIf { it.isNotEmpty() } ?: articleByline,
            dir = articleDir,
            lang = articleLang,
            content = serializer(articleContent),
            textContent = textContent,
            length = textContent.length,
            excerpt = meta.excerpt,
            siteName = meta.siteName ?: articleSiteName,
            publishedTime = meta.publishedTime,
        )
    }

    // Helper extension: get document element
    private fun Document.documentElement(): Element? {
        return this.children().firstOrNull { it.tagName().equals("html", ignoreCase = true) }
            ?: this.body().parent()
            ?: this.body()
    }
}
