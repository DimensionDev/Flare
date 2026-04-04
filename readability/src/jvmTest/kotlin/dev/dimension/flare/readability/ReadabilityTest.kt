package dev.dimension.flare.readability

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.DocumentType
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class ReadabilityTest {

    companion object {
        private const val TEST_URI = "http://fakehost/test/page.html"

        private fun testPagesDir(): File {
            val projectRoot = File(System.getProperty("user.dir"))
            val candidates = listOf(
                File(projectRoot, "src/commonTest/resources/test-pages"),
                File(projectRoot, "test/test-pages"),
            )
            for (candidate in candidates) {
                if (candidate.isDirectory) return candidate
            }
            error("Cannot find test-pages directory. Looked in: ${candidates.map { it.absolutePath }}")
        }

        val testPages: List<TestPage> by lazy {
            val dir = testPagesDir()
            dir.listFiles()!!
                .filter { it.isDirectory }
                .sortedBy { it.name }
                .map { pageDir ->
                    TestPage(
                        dir = pageDir.name,
                        source = pageDir.resolve("source.html").readText().trim(),
                        expectedContent = pageDir.resolve("expected.html").readText().trim(),
                        expectedMetadata = Json.parseToJsonElement(
                            pageDir.resolve("expected-metadata.json").readText().trim()
                        ).jsonObject,
                    )
                }
        }

        data class TestPage(
            val dir: String,
            val source: String,
            val expectedContent: String,
            val expectedMetadata: JsonObject,
        )

        // ── DOM comparison helpers ──────────────────────────────────────

        /**
         * Collapse whitespace the same way HTML does (but don't trim).
         */
        fun htmlTransform(str: String): String = str.replace(Regex("\\s+"), " ")

        /**
         * In-order traversal: go to firstChild, or nextSibling, or walk up.
         */
        private fun inOrderTraverse(fromNode: Node?): Node? {
            var node = fromNode ?: return null
            val first = node.childNodes().firstOrNull()
            if (first != null) return first
            while (true) {
                val next = node.nextSibling()
                if (next != null) return next
                node = node.parentNode() ?: return null
            }
        }

        /**
         * Skip empty text nodes (whitespace-only).
         */
        /**
         * Skip empty text nodes, comment nodes, doctype nodes, and data nodes.
         */
        private fun inOrderIgnoreEmptyTextNodes(fromNode: Node?): Node? {
            var node = inOrderTraverse(fromNode)
            while (node != null && (
                (node is TextNode && node.getWholeText().trim().isEmpty()) ||
                node is Comment ||
                node is DocumentType ||
                node is DataNode
            )) {
                node = inOrderTraverse(node)
            }
            return node
        }

        /**
         * Node description for error messages.
         */
        fun nodeStr(n: Node?): String {
            if (n == null) return "(no node)"
            if (n is TextNode) return "#text(${htmlTransform(n.getWholeText())})"
            if (n is Element) {
                var rv = n.tagName()
                if (n.id().isNotEmpty()) rv += "#${n.id()}"
                val cls = n.className()
                if (cls.isNotEmpty()) rv += ".($cls)"
                return rv
            }
            return "some other node type"
        }

        /**
         * Compare two DOMs in-order, node by node.
         * Returns null on success, or an error message on first mismatch.
         */
        fun compareDOMs(actualDoc: Document, expectedDoc: Document): String? {
            var actualNode: Node? = actualDoc.body()
            var expectedNode: Node? = expectedDoc.body()

            while (actualNode != null || expectedNode != null) {
                if (actualNode == null || expectedNode == null) {
                    return "DOM mismatch: actual=${nodeStr(actualNode)}, expected=${nodeStr(expectedNode)}"
                }

                val actualDesc = nodeStr(actualNode)
                val expectedDesc = nodeStr(expectedNode)

                // For text nodes: compare with whitespace collapsed AND trimmed
                // (the JS tests compare after htmlTransform which collapses whitespace,
                // and leading/trailing whitespace differences inside elements are not
                // meaningful in HTML rendering)
                if (actualNode is TextNode && expectedNode is TextNode) {
                    val actualText = htmlTransform(actualNode.getWholeText()).trim()
                    val expectedText = htmlTransform(expectedNode.getWholeText()).trim()
                    if (actualText != expectedText) {
                        return "Text mismatch: actual='$actualText', expected='$expectedText'"
                    }
                } else if (actualNode is Element && expectedNode is Element) {
                    // Compare tag names (case-insensitive for SVG elements like clipPath)
                    if (actualNode.tagName().lowercase() != expectedNode.tagName().lowercase()) {
                        return "Tag mismatch: actual=<${actualNode.tagName()}>, expected=<${expectedNode.tagName()}>"
                    }
                    // Compare attributes, normalizing boolean attributes and whitespace
                    fun normalizeAttrValue(key: String, value: String): String {
                        val trimmed = value.replace(Regex("\\s+"), " ").trim()
                        // Boolean attributes: "" and "attrname" are equivalent
                        if (trimmed.isEmpty() || trimmed.equals(key, ignoreCase = true)) {
                            return key // normalize to the attribute name
                        }
                        return trimmed
                    }
                    // Normalize attribute names: ksoup may normalize invalid chars
                    // (e.g., `"` → `_`) differently across parse/serialize cycles.
                    fun normalizeAttrKey(key: String): String =
                        key.replace(Regex("[^a-zA-Z0-9_:-]"), "_")
                    val actualAttrs = actualNode.attributes()
                        .map { val nk = normalizeAttrKey(it.key); "$nk=${normalizeAttrValue(nk, it.value)}" }.sorted()
                    val expectedAttrs = expectedNode.attributes()
                        .map { val nk = normalizeAttrKey(it.key); "$nk=${normalizeAttrValue(nk, it.value)}" }.sorted()
                    if (actualAttrs != expectedAttrs) {
                        return "Attribute mismatch on <${actualNode.tagName()}>: actual=$actualAttrs, expected=$expectedAttrs"
                    }
                } else {
                    // Different node types
                    if (actualDesc != expectedDesc) {
                        return "Node type mismatch: actual=$actualDesc, expected=$expectedDesc"
                    }
                }

                actualNode = inOrderIgnoreEmptyTextNodes(actualNode)
                expectedNode = inOrderIgnoreEmptyTextNodes(expectedNode)
            }
            return null
        }

        fun metadataString(meta: JsonObject, key: String): String? {
            val value = meta[key] ?: return null
            if (value is JsonNull) return null
            return value.jsonPrimitive.content
        }

        fun metadataBoolean(meta: JsonObject, key: String): Boolean? {
            val value = meta[key] ?: return null
            if (value is JsonNull) return null
            return value.jsonPrimitive.content.toBoolean()
        }
    }

    // ── Integration test: iterate over all 130 test page fixtures ────────

    @Test
    fun testAllPages() {
        val failures = mutableListOf<String>()

        for (testPage in testPages) {
            val result = try {
                Readability(
                    html = testPage.source,
                    url = TEST_URI,
                    options = ReadabilityOptions(classesToPreserve = listOf("caption")),
                ).parse()
            } catch (e: Exception) {
                failures.add("[${testPage.dir}] Parse threw exception: ${e.message}")
                continue
            }

            if (result == null) {
                failures.add("[${testPage.dir}] parse() returned null")
                continue
            }

            val meta = testPage.expectedMetadata
            val prefix = "[${testPage.dir}]"

            // Metadata checks
            val expectedTitle = metadataString(meta, "title")
            if (result.title != expectedTitle) {
                failures.add("$prefix Title mismatch: actual='${result.title}', expected='$expectedTitle'")
            }

            val expectedByline = metadataString(meta, "byline")
            if (result.byline != expectedByline) {
                failures.add("$prefix Byline mismatch: actual='${result.byline}', expected='$expectedByline'")
            }

            // Excerpt: normalize whitespace for comparison (expected may have newlines from JSON formatting)
            val expectedExcerpt = metadataString(meta, "excerpt")
            val normalizedActualExcerpt = result.excerpt?.let { htmlTransform(it).trim() }
            val normalizedExpectedExcerpt = expectedExcerpt?.let { htmlTransform(it).trim() }
            if (normalizedActualExcerpt != normalizedExpectedExcerpt) {
                failures.add("$prefix Excerpt mismatch: actual='$normalizedActualExcerpt', expected='$normalizedExpectedExcerpt'")
            }

            val expectedSiteName = metadataString(meta, "siteName")
            if (result.siteName != expectedSiteName) {
                failures.add("$prefix SiteName mismatch: actual='${result.siteName}', expected='$expectedSiteName'")
            }

            val expectedDir = metadataString(meta, "dir")
            if (expectedDir != null && result.dir != expectedDir) {
                failures.add("$prefix Dir mismatch: actual='${result.dir}', expected='$expectedDir'")
            }

            val expectedLang = metadataString(meta, "lang")
            if (expectedLang != null && result.lang != expectedLang) {
                failures.add("$prefix Lang mismatch: actual='${result.lang}', expected='$expectedLang'")
            }

            val expectedPublishedTime = metadataString(meta, "publishedTime")
            if (expectedPublishedTime != null && result.publishedTime != expectedPublishedTime) {
                failures.add("$prefix PublishedTime mismatch: actual='${result.publishedTime}', expected='$expectedPublishedTime'")
            }

            // Content comparison via DOM traversal
            val actualDoc = Ksoup.parse(result.content)
            val expectedDoc = Ksoup.parse(testPage.expectedContent)
            val contentError = compareDOMs(actualDoc, expectedDoc)
            if (contentError != null) {
                failures.add("$prefix Content: $contentError")
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "Readability test failures (${failures.size}):\n" +
                    failures.joinToString("\n") { "  $it" }
            )
        }
    }
}
