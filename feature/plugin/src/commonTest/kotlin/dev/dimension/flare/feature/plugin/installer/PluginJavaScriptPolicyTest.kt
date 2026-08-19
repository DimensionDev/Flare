package dev.dimension.flare.feature.plugin.installer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginJavaScriptPolicyTest {
    @Test
    fun detectsDynamicImportsSeparatedByComments() {
        assertTrue(containsDynamicImport("async function load() { return import /* hidden */ ('remote'); }"))
        assertTrue(containsDynamicImport("const value = `result: ${'$'}{import('remote')}`;"))
    }

    @Test
    fun ignoresImportTextInCommentsStringsTemplatesAndRegexes() {
        assertFalse(
            containsDynamicImport(
                """
                // import('comment')
                const one = "import('string')";
                const two = 'import /* text */ (string)';
                const three = `import('template text')`;
                const four = /import\s*\(/;
                """.trimIndent(),
            ),
        )
    }
}
