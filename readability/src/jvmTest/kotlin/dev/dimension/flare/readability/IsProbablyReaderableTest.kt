package dev.dimension.flare.readability

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsProbablyReaderableTest {

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
            error("Cannot find test-pages directory")
        }

        private data class TestPage(
            val dir: String,
            val source: String,
            val expectedMetadata: JsonObject,
        )

        private val testPages: List<TestPage> by lazy {
            val dir = testPagesDir()
            dir.listFiles()!!
                .filter { it.isDirectory }
                .sortedBy { it.name }
                .map { pageDir ->
                    TestPage(
                        dir = pageDir.name,
                        source = pageDir.resolve("source.html").readText().trim(),
                        expectedMetadata = Json.parseToJsonElement(
                            pageDir.resolve("expected-metadata.json").readText().trim()
                        ).jsonObject,
                    )
                }
        }
    }

    // ── Test all 130 fixtures for readerable flag ────────────────────────

    @Test
    fun testAllPagesReaderable() {
        val failures = mutableListOf<String>()

        for (testPage in testPages) {
            val meta = testPage.expectedMetadata
            val expectedValue = meta["readerable"]
            if (expectedValue == null || expectedValue is JsonNull) continue

            val expected = expectedValue.jsonPrimitive.content.toBoolean()
            val doc = Ksoup.parse(testPage.source, TEST_URI)
            val actual = isProbablyReaderable(doc)

            if (actual != expected) {
                failures.add("[${testPage.dir}] expected readerable=$expected, got=$actual")
            }
        }

        if (failures.isNotEmpty()) {
            kotlin.test.fail(
                "isProbablyReaderable failures (${failures.size}):\n" +
                    failures.joinToString("\n") { "  $it" }
            )
        }
    }

    // ── Unit tests matching the JS test suite ────────────────────────────

    private fun makeDoc(source: String) = Ksoup.parse(source)

    // content lengths:
    // verySmallDoc: "hello there" => 11 chars
    // smallDoc: "hello there " * 11 => 132 chars
    // largeDoc: "hello there " * 12 => 144 chars
    // veryLargeDoc: "hello there " * 50 => 600 chars

    private val verySmallDoc = makeDoc("<html><p id=\"main\">hello there</p></html>")
    private val smallDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(11)}</p></html>")
    private val largeDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(12)}</p></html>")
    private val veryLargeDoc = makeDoc("<html><p id=\"main\">${"hello there ".repeat(50)}</p></html>")

    @Test
    fun `should only declare large documents as readerable when default options`() {
        assertFalse(isProbablyReaderable(verySmallDoc), "very small doc")
        assertFalse(isProbablyReaderable(smallDoc), "small doc")
        assertFalse(isProbablyReaderable(largeDoc), "large doc")
        assertTrue(isProbablyReaderable(veryLargeDoc), "very large doc")
    }

    @Test
    fun `should declare small and large documents as readerable when lower minContentLength`() {
        val options = ReaderableOptions(minContentLength = 120, minScore = 0.0)
        assertFalse(isProbablyReaderable(verySmallDoc, options), "very small doc")
        assertTrue(isProbablyReaderable(smallDoc, options), "small doc")
        assertTrue(isProbablyReaderable(largeDoc, options), "large doc")
        assertTrue(isProbablyReaderable(veryLargeDoc, options), "very large doc")
    }

    @Test
    fun `should only declare largest document as readerable when higher minContentLength`() {
        val options = ReaderableOptions(minContentLength = 200, minScore = 0.0)
        assertFalse(isProbablyReaderable(verySmallDoc, options), "very small doc")
        assertFalse(isProbablyReaderable(smallDoc, options), "small doc")
        assertFalse(isProbablyReaderable(largeDoc, options), "large doc")
        assertTrue(isProbablyReaderable(veryLargeDoc, options), "very large doc")
    }

    @Test
    fun `should declare small and large documents as readerable when lower minScore`() {
        val options = ReaderableOptions(minContentLength = 0, minScore = 4.0)
        assertFalse(isProbablyReaderable(verySmallDoc, options), "very small doc")
        assertTrue(isProbablyReaderable(smallDoc, options), "small doc")
        assertTrue(isProbablyReaderable(largeDoc, options), "large doc")
        assertTrue(isProbablyReaderable(veryLargeDoc, options), "very large doc")
    }

    @Test
    fun `should declare large documents as readerable when higher minScore`() {
        val options = ReaderableOptions(minContentLength = 0, minScore = 11.5)
        assertFalse(isProbablyReaderable(verySmallDoc, options), "very small doc")
        assertFalse(isProbablyReaderable(smallDoc, options), "small doc")
        assertTrue(isProbablyReaderable(largeDoc, options), "large doc")
        assertTrue(isProbablyReaderable(veryLargeDoc, options), "very large doc")
    }

    @Test
    fun `should use visibility checker - not visible`() {
        var called = false
        val options = ReaderableOptions(
            visibilityChecker = {
                called = true
                false
            },
        )
        assertFalse(isProbablyReaderable(veryLargeDoc, options))
        assertTrue(called, "visibilityChecker should have been called")
    }

    @Test
    fun `should use visibility checker - visible`() {
        var called = false
        val options = ReaderableOptions(
            visibilityChecker = {
                called = true
                true
            },
        )
        assertTrue(isProbablyReaderable(veryLargeDoc, options))
        assertTrue(called, "visibilityChecker should have been called")
    }
}
