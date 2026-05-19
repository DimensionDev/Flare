package dev.dimension.flare.data.repository

import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabProvider
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.model.tab.GroupSource
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSlotContent
import dev.dimension.flare.data.model.tab.normalizeSystemHomeMixedTimeline
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class AccountTabSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val appDataStore: AppDataStore,
    private val coroutineScope: CoroutineScope,
    private val timelineResolver: TimelineResolver,
    private val timelinePersistenceMapper: TimelinePersistenceMapper,
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
        appDataStore.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForExistingAccounts(existingAccounts) }
                        .normalizeSystemHomeMixedTimeline(
                            enabled = homeSlots.anySystemHomeMixedTimeline(),
                        ),
            )
        }
    }

    private suspend fun addDefaultTabs(account: UiAccount) {
        val service = accountRepository.getOrCreateDataSource(account)
        val defaultSlots =
            (service as? TimelineTabProvider)
                ?.defaultTimelineTabs
                ?.map(timelinePersistenceMapper::toSlot)
                ?: (service as? TimelineTabConfigurationDataSource)
                    ?.defaultTabs
                    .orEmpty()
        if (defaultSlots.isEmpty()) {
            return
        }
        appDataStore.updateTabSettingsV2 {
            val shouldEnableSystemHomeMixedTimeline =
                homeSlots.anySystemHomeMixedTimeline() ||
                    homeSlots.countNonSystemHomeTabs() < 2
            val newSlots =
                (homeSlots + defaultSlots)
                    .normalizeSystemHomeMixedTimeline(
                        enabled = shouldEnableSystemHomeMixedTimeline,
                    )
            val newSettings = copy(homeSlots = newSlots)
            if (newSettings == this) {
                this
            } else {
                newSettings
            }
        }
    }

    private suspend fun removeTabsForAccount(accountKey: MicroBlogKey) {
        appDataStore.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForRemovedAccount(accountKey) }
                        .normalizeSystemHomeMixedTimeline(
                            enabled = homeSlots.anySystemHomeMixedTimeline(),
                        ),
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

private fun List<TimelineSlot>.anySystemHomeMixedTimeline(): Boolean = any { it.isSystemHomeMixedTimeline() }

private fun List<TimelineSlot>.countNonSystemHomeTabs(): Int = count { !it.isSystemHomeMixedTimeline() }

private fun TimelineSlot.isSystemHomeMixedTimeline(): Boolean = (content as? TimelineSlotContent.Group)?.source == GroupSource.SystemHome
