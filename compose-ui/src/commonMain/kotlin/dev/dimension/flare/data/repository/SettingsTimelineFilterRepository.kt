package dev.dimension.flare.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SettingsTimelineFilterRepository(
    private val settingsRepository: SettingsRepository,
) : TimelineFilterRepository {
    override val hideRepostsFlow: Flow<Boolean> =
        settingsRepository.appearanceSettings.map { it.hideReposts }

    override val hideRepliesFlow: Flow<Boolean> =
        settingsRepository.appearanceSettings.map { it.hideReplies }
}
