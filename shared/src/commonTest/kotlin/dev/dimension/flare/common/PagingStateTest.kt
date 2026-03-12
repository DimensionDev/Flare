package dev.dimension.flare.common

import androidx.paging.LoadState
import androidx.paging.LoadStates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagingStateTest {
    @Test
    fun unresolvedEmptySnapshotIsNotResolvedEmpty() {
        val snapshot =
            createSnapshot(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false),
                sourceRefresh = LoadState.NotLoading(endOfPaginationReached = false),
                sourcePrepend = LoadState.NotLoading(endOfPaginationReached = false),
                sourceAppend = LoadState.NotLoading(endOfPaginationReached = false),
            )

        assertFalse(snapshot.isResolvedEmpty())
        assertTrue(snapshot.initialErrorOrNull() == null)
        assertFalse(snapshot.hasPendingRefresh())
    }

    @Test
    fun terminalEmptySnapshotIsResolvedEmpty() {
        val snapshot =
            createSnapshot(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true),
                sourceRefresh = LoadState.NotLoading(endOfPaginationReached = false),
                sourcePrepend = LoadState.NotLoading(endOfPaginationReached = true),
                sourceAppend = LoadState.NotLoading(endOfPaginationReached = true),
            )

        assertTrue(snapshot.isResolvedEmpty())
        assertNull(snapshot.initialErrorOrNull())
    }

    @Test
    fun refreshErrorIsExposedAsInitialError() {
        val error = IllegalStateException("boom")
        val snapshot =
            createSnapshot(
                refresh = LoadState.Error(error),
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false),
                sourceRefresh = LoadState.NotLoading(endOfPaginationReached = false),
                sourcePrepend = LoadState.NotLoading(endOfPaginationReached = false),
                sourceAppend = LoadState.NotLoading(endOfPaginationReached = false),
            )

        assertIs<IllegalStateException>(snapshot.initialErrorOrNull())
        assertFalse(snapshot.isResolvedEmpty())
    }

    @Test
    fun sourceRefreshLoadingCountsAsPendingRefresh() {
        val snapshot =
            createSnapshot(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = false),
                append = LoadState.NotLoading(endOfPaginationReached = false),
                sourceRefresh = LoadState.Loading,
                sourcePrepend = LoadState.NotLoading(endOfPaginationReached = false),
                sourceAppend = LoadState.NotLoading(endOfPaginationReached = false),
            )

        assertTrue(snapshot.hasPendingRefresh())
        assertFalse(snapshot.isResolvedEmpty())
    }
}

private fun createSnapshot(
    refresh: LoadState,
    prepend: LoadState,
    append: LoadState,
    sourceRefresh: LoadState,
    sourcePrepend: LoadState,
    sourceAppend: LoadState,
    mediator: LoadStates? = null,
): PagingSnapshot =
    PagingSnapshot(
        itemCount = 0,
        refresh = refresh,
        prepend = prepend,
        append = append,
        source =
            LoadStates(
                refresh = sourceRefresh,
                prepend = sourcePrepend,
                append = sourceAppend,
            ),
        mediator = mediator,
    )
