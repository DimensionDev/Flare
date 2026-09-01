@file:OptIn(ExperimentalFlareNavigation::class)

package dev.dimension.flare.ui.navigation

import androidx.navigation3.runtime.NavEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

public class NavigationPlanTest {
    @Test
    public fun animatesSingleSuffixUpdatesAndBatchesBulkOrReplacementUpdates() {
        val home = resolved(entry("home"))
        val detail = resolved(entry("detail"))
        val sheet = resolved(entry("sheet", NavigationPresentation.Sheet))
        val dialog = resolved(entry("dialog", NavigationPresentation.Dialog))

        assertIs<NavigationOperation.PushPage>(
            calculateNavigationPlan(listOf(home), listOf(home, detail)).single(),
        )
        assertIs<NavigationOperation.PresentOverlay>(
            calculateNavigationPlan(listOf(home, detail), listOf(home, detail, sheet)).single(),
        )
        assertIs<NavigationOperation.DismissOverlay>(
            calculateNavigationPlan(listOf(home, detail, sheet), listOf(home, detail)).single(),
        )
        assertIs<NavigationOperation.PopPage>(
            calculateNavigationPlan(listOf(home, detail), listOf(home)).single(),
        )

        val appendBatch = calculateNavigationPlan(listOf(home), listOf(home, detail, sheet, dialog)).single()
        assertIs<NavigationOperation.Reconstruct>(appendBatch)
        assertEquals(listOf("home", "detail", "sheet", "dialog"), appendBatch.targetStack.contentKeys())

        val removeBatch = calculateNavigationPlan(listOf(home, detail, sheet, dialog), listOf(home)).single()
        assertIs<NavigationOperation.Reconstruct>(removeBatch)
        assertEquals(listOf("home"), removeBatch.targetStack.contentKeys())

        val replacement = resolved(entry("replacement"))
        val replacementPlan = calculateNavigationPlan(listOf(home, detail), listOf(home, replacement))
        assertEquals(1, replacementPlan.size)
        assertIs<NavigationOperation.Reconstruct>(replacementPlan.single())
    }

    @Test
    public fun initialProjectionAndDirtyProjectionUseImmediateReconstruction() {
        val target = resolveNavigationEntries(listOf(entry("home"), entry("detail")))

        val initial = calculateNavigationPlan(emptyList(), target).single()
        val dirty = calculateNavigationPlan(target, target, forceReconstruction = true).single()

        assertIs<NavigationOperation.Reconstruct>(initial)
        assertIs<NavigationOperation.Reconstruct>(dirty)
        assertTrue(!initial.animated)
        assertTrue(!dirty.animated)
    }

    @Test
    public fun nextOperationAnimatesOneEntryAndBatchesADeepSuffix() {
        val home = resolved(entry("home"))
        val detail = resolved(entry("detail"))
        val editor = resolved(entry("editor"))
        val settings = resolved(entry("settings"))

        val push =
            assertIs<NavigationOperation.PushPage>(
                calculateNextNavigationOperation(
                    projectedStack = listOf(home),
                    declaredStack = listOf(home, detail),
                ),
            )
        assertEquals(listOf("home", "detail"), push.targetStack.contentKeys())

        val pop =
            assertIs<NavigationOperation.PopPage>(
                calculateNextNavigationOperation(
                    projectedStack = listOf(home, detail),
                    declaredStack = listOf(home),
                ),
            )
        assertEquals(listOf("home"), pop.targetStack.contentKeys())

        val batch =
            assertIs<NavigationOperation.Reconstruct>(
                calculateNextNavigationOperation(
                    projectedStack = listOf(home),
                    declaredStack = listOf(home, detail, editor, settings),
                ),
            )
        assertEquals(listOf("home", "detail", "editor", "settings"), batch.targetStack.contentKeys())
    }
}

private fun entry(
    key: String,
    presentation: NavigationPresentation = NavigationPresentation.Page,
): NavEntry<String> =
    NavEntry(
        key = key,
        contentKey = key,
        metadata =
            if (presentation == NavigationPresentation.Page) {
                emptyMap()
            } else {
                mapOf(NavigationPresentationMetadata.toString() to presentation)
            },
    ) {}

private fun resolved(entry: NavEntry<String>): ResolvedNavigationEntry =
    resolveNavigationEntries(
        if (entry.metadata.isEmpty()) {
            listOf(entry)
        } else {
            listOf(entry("root"), entry)
        },
    ).last()

private fun List<ResolvedNavigationEntry>.contentKeys(): List<Any> = map(ResolvedNavigationEntry::contentKey)
