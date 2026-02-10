package dev.dimension.flare.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for timeline filtering preferences.
 * Provides flows for hide reposts and hide replies settings.
 */
public interface TimelineFilterRepository {
    public val hideRepostsFlow: Flow<Boolean>
    public val hideRepliesFlow: Flow<Boolean>
}
