@file:OptIn(ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import dev.dimension.flare.ui.FlareUiComposable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

public class NavigationModelTest {
    @Test
    public fun acceptsEntryProviderAndTypedMetadataDsl() {
        val provider: (TestRoute) -> NavEntry<TestRoute> =
            entryProvider<TestRoute> {
                entry<TestHome> { TestFlareScreen() }
                entry<TestEditor>(
                    metadata =
                        metadata {
                            put(
                                NavigationPresentationMetadata,
                                NavigationPresentation.Sheet,
                            )
                        },
                ) { TestFlareScreen() }
            }

        val resolved =
            resolveNavigationEntries(
                listOf(provider(TestHome), provider(TestEditor)),
            )

        assertEquals(NavigationPresentation.Page, resolved.first().presentation)
        assertEquals(NavigationPresentation.Sheet, resolved.last().presentation)
    }

    @Test
    public fun acceptsAFlareTargetDecorator() {
        val decorators: List<NavEntryDecorator<TestRoute>> =
            listOf(
                NavEntryDecorator(
                    decorate = { entry ->
                        TestFlareScreen()
                        entry.Content()
                    },
                ),
            )

        assertEquals(1, decorators.size)
    }

    @Test
    public fun resolvesDefaultAndExplicitPresentations() {
        val resolved =
            resolveNavigationEntries(
                listOf(
                    entry("home"),
                    entry("editor", NavigationPresentation.Sheet),
                    entry("confirm", NavigationPresentation.Dialog),
                ),
            )

        assertEquals(
            listOf(
                NavigationPresentation.Page,
                NavigationPresentation.Sheet,
                NavigationPresentation.Dialog,
            ),
            resolved.map(ResolvedNavigationEntry::presentation),
        )
    }

    @Test
    public fun rejectsEveryInvalidStackShape() {
        assertFailsWith<IllegalArgumentException> {
            resolveNavigationEntries(emptyList<NavEntry<String>>())
        }
        assertFailsWith<IllegalArgumentException> {
            resolveNavigationEntries(listOf(entry("dialog", NavigationPresentation.Dialog)))
        }
        assertFailsWith<IllegalArgumentException> {
            resolveNavigationEntries(
                listOf(
                    entry("home"),
                    entry("dialog", NavigationPresentation.Dialog),
                    entry("detail"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            resolveNavigationEntries(
                listOf(
                    entry("home", contentKey = "shared"),
                    entry("detail", contentKey = "shared"),
                ),
            )
        }
    }

    @Test
    public fun rejectsWrongPresentationMetadataType() {
        val invalid =
            NavEntry(
                key = "home",
                contentKey = "home",
                metadata = mapOf(NavigationPresentationMetadata.toString() to "Page"),
            ) {}

        val error =
            assertFailsWith<IllegalArgumentException> {
                resolveNavigationEntries(listOf(invalid))
            }

        assertTrue(error.message.orEmpty().contains("NavigationPresentation"))
    }

    @Test
    public fun rejectsPresentationChangesForARetainedIdentity() {
        val previous = resolveNavigationEntries(listOf(entry("home"), entry("editor")))
        val current =
            resolveNavigationEntries(
                listOf(entry("home"), entry("editor", NavigationPresentation.Sheet)),
            )

        assertFailsWith<IllegalArgumentException> {
            validateStablePresentations(previous, current)
        }
    }
}

private fun entry(
    key: String,
    presentation: NavigationPresentation = NavigationPresentation.Page,
    contentKey: Any = key,
): NavEntry<String> =
    NavEntry(
        key = key,
        contentKey = contentKey,
        metadata =
            if (presentation == NavigationPresentation.Page) {
                emptyMap()
            } else {
                mapOf(NavigationPresentationMetadata.toString() to presentation)
            },
    ) {}

private sealed interface TestRoute

private data object TestHome : TestRoute

private data object TestEditor : TestRoute

@Composable
@FlareUiComposable
private fun TestFlareScreen() = Unit
