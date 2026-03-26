package dev.dimension.flare.data.repository

import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.spec
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class AccountTabSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
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
        settingsRepository.updateTabSettings {
            cleanupForExistingAccounts(existingAccounts)
        }
    }

    private suspend fun addDefaultTabs(account: UiAccount) {
        val defaultTabs = account.platformType.spec.defaultTimelineTabs(account.accountKey)
        if (defaultTabs.isEmpty()) {
            return
        }
        settingsRepository.updateTabSettings {
            val newTabs =
                (mainTabs + defaultTabs)
                    .distinctBy { it.key }
            if (newTabs == mainTabs) {
                this
            } else {
                copy(mainTabs = newTabs)
            }
        }
    }

    private suspend fun removeTabsForAccount(accountKey: MicroBlogKey) {
        settingsRepository.updateTabSettings {
            cleanupForExistingAccounts(setOf(accountKey), retainAccounts = false)
        }
    }

    private fun TabSettings.cleanupForExistingAccounts(
        accountKeys: Set<MicroBlogKey>,
        retainAccounts: Boolean = true,
    ): TabSettings {
        val newTabs =
            mainTabs
                .mapNotNull { tab ->
                    tab.cleanup(accountKeys, retainAccounts)
                }
        return if (newTabs == mainTabs) {
            this
        } else {
            copy(mainTabs = newTabs)
        }
    }

    private fun TimelineTabItem.cleanup(
        accountKeys: Set<MicroBlogKey>,
        retainAccounts: Boolean,
    ): TimelineTabItem? =
        when (this) {
            is MixedTimelineTabItem -> {
                val cleanedSubTabs =
                    subTimelineTabItem
                        .mapNotNull {
                            it.cleanup(accountKeys, retainAccounts)
                        }.toImmutableList()
                if (cleanedSubTabs.isEmpty()) {
                    null
                } else if (cleanedSubTabs == subTimelineTabItem) {
                    this
                } else {
                    copy(subTimelineTabItem = cleanedSubTabs)
                }
            }

            else -> {
                val accountKey = (account as? AccountType.Specific)?.accountKey ?: return this
                val shouldRetain = accountKey in accountKeys
                if (shouldRetain == retainAccounts) {
                    this
                } else {
                    null
                }
            }
        }
}
