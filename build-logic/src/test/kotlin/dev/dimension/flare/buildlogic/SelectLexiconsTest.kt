package dev.dimension.flare.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectLexiconsTest {
    @Test
    fun retainsWholeSchemasAndTransitiveReferencesIncludingCycles() {
        val root = """{"id":"app.test.root","defs":{"main":{"type":"ref","ref":"app.test.model#view"}}}"""
        val model = """{"id":"app.test.model","defs":{"view":{"type":"union","refs":["#detail","app.test.root"]},"detail":{"type":"object","properties":{"ref":{"type":"ref","ref":"app.test.shared"}}},"unused":{"type":"string"}}}"""
        val shared = """{"id":"app.test.shared","defs":{"main":{"type":"string"}}}"""
        val unrelated = """{"id":"app.test.unrelated","defs":{"main":{"type":"string"}}}"""

        val result = selectLexiconSchemas(listOf(root, model, shared, unrelated), listOf("app.test.root"))

        assertEquals(setOf("app.test.root", "app.test.model", "app.test.shared"), result.keys)
        assertEquals(model, result["app.test.model"])
    }

    @Test
    fun doesNotTreatKnownValuesAsModelDependencies() {
        val schema = """{"id":"app.test.root","defs":{"main":{"type":"string","knownValues":["tools.unused.defs#reason"]}}}"""
        assertEquals(mapOf("app.test.root" to schema), selectLexiconSchemas(listOf(schema), listOf("app.test.root")))
    }

    @Test
    fun rejectsMissingRootsAndReferencedSchemas() {
        val schema = """{"id":"app.test.root","defs":{"main":{"type":"ref","ref":"app.test.missing"}}}"""
        for (root in listOf("app.test.root", "app.test.missing")) {
            val error = assertFailsWith<IllegalArgumentException> {
                selectLexiconSchemas(listOf(schema), listOf(root))
            }
            assertTrue(error.message.orEmpty().contains("app.test.missing"))
        }
    }

    @Test
    fun rejectsMissingDefinitions() {
        val schema = """{"id":"app.test.root","defs":{"main":{"type":"ref","ref":"#missing"}}}"""
        val error = assertFailsWith<IllegalArgumentException> {
            selectLexiconSchemas(listOf(schema), listOf("app.test.root"))
        }
        assertTrue(error.message.orEmpty().contains("#missing"))
    }
}
