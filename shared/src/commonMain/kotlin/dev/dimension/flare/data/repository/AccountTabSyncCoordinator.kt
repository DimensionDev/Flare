package dev.dimension.flare.data.repository

import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class AccountTabSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val timelineResolver: TimelineResolver,
) {
    init {
        coroutineScope.launch {
            removeTabsForDeletedAccounts()
        }
        coroutineScope.launch {
            accountRepository.onAdded.collect { account ->
                addDefaultTabs(account)
            }
        }
        coroutineScope.launch {
            accountRepository.onRemoved.collect { accountKey ->
                removeTabsForAccount(accountKey)
            }
        }
    }

    private suspend fun removeTabsForDeletedAccounts() {
        val existingAccounts =
            accountRepository.allAccounts
                .first()
                .mapTo(linkedSetOf()) { it.accountKey }
        settingsRepository.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForExistingAccounts(existingAccounts) }
                        .distinctBy { it.id },
            )
        }
    }

    private suspend fun addDefaultTabs(account: UiAccount) {
        val defaultSlots =
            (accountRepository.getOrCreateDataSource(account) as? TimelineTabConfigurationDataSource)
                ?.defaultTabs
                .orEmpty()
        if (defaultSlots.isEmpty()) {
            return
        }
        settingsRepository.updateTabSettingsV2 {
            val newSlots =
                (homeSlots + defaultSlots)
                    .distinctBy { it.id }
            val newSettings = copy(homeSlots = newSlots)
            if (newSettings == this) {
                this
            } else {
                newSettings
            }
        }
    }

    private suspend fun removeTabsForAccount(accountKey: MicroBlogKey) {
        settingsRepository.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForRemovedAccount(accountKey) }
                        .distinctBy { it.id },
            )
        }
    }

    private fun TimelineSlot.cleanupForExistingAccounts(accountKeys: Set<MicroBlogKey>): TimelineSlot? =
        cleanupAccountSlots { accountKey ->
            accountKey == null || accountKey in accountKeys
        }

    private fun TimelineSlot.cleanupForRemovedAccount(accountKey: MicroBlogKey): TimelineSlot? =
        cleanupAccountSlots { slotAccountKey ->
            slotAccountKey == null || slotAccountKey != accountKey
        }

    private fun TimelineSlot.cleanupAccountSlots(shouldKeep: (MicroBlogKey?) -> Boolean): TimelineSlot? =
        when (val slotContent = content) {
            is TimelineSlotContent.Source -> {
                if (shouldKeep(timelineResolver.resolveAccountKey(this))) {
                    this
                } else {
                    null
                }
            }

            is TimelineSlotContent.Group -> {
                val sanitizedChildren =
                    slotContent.children
                        .mapNotNull { it.cleanupAccountSlots(shouldKeep) }
                        .distinctBy { it.id }
                if (sanitizedChildren.isEmpty()) {
                    null
                } else if (sanitizedChildren == slotContent.children) {
                    this
                } else {
                    copy(content = slotContent.copy(children = sanitizedChildren))
                }
            }
        }
}

internal fun TabSettings.sanitizeDuplicateTabKeys(): TabSettings {
    val sanitizedTabs =
        mainTabs
            .mapNotNull { it.sanitizeDuplicateTabKeys() }
            .distinctBy { it.key }
    return if (sanitizedTabs == mainTabs) {
        this
    } else {
        copy(mainTabs = sanitizedTabs)
    }
}

private fun TimelineTabItem.sanitizeDuplicateTabKeys(): TimelineTabItem? =
    when (this) {
        is MixedTimelineTabItem -> {
            val sanitizedSubTabs =
                subTimelineTabItem
                    .mapNotNull { it.sanitizeDuplicateTabKeys() }
                    .distinctBy { it.key }
                    .toImmutableList()
            if (sanitizedSubTabs.isEmpty()) {
                null
            } else if (sanitizedSubTabs == subTimelineTabItem) {
                this
            } else {
                copy(subTimelineTabItem = sanitizedSubTabs)
            }
        }

        else -> {
            this
        }
    }
