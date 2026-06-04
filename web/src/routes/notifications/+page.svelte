<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import { tick } from 'svelte';
	import {
		createNotificationsPresenterController,
		type NotificationAccountItem,
		type NotificationFilter,
		type UiTimelineV2,
	} from '@flare/web-presenters/notifications.svelte';
	import type { PagingState as TimelinePagingState } from '@flare/web-presenters/timeline.svelte';

	const notifications = useRetainedPresenter(
		'notifications',
		() => createNotificationsPresenterController(),
		{ ttlMs: Infinity }
	);

	let accountsElement = $state<HTMLDivElement | null>(null);
	let accountsExpanded = $state(false);
	let accountsOverflowing = $state(false);
	const accounts = $derived(notifications.notifications);
	const filters = $derived(
		notifications.supportedNotificationFilters.type === 'Success'
			? notifications.supportedNotificationFilters.data
			: []
	);
	const hasMultipleAccounts = $derived(accounts.length > 1);
	const hasMultipleFilters = $derived(filters.length > 1);
	const refreshing = $derived(
		notifications.timeline.type === 'Success' && notifications.timeline.isRefreshing
	);

	function selectAccount(account: NotificationAccountItem): void {
		notifications.setAccountKey(account.profile.key);
	}

	function isSelectedAccount(account: NotificationAccountItem): boolean {
		return (
			notifications.selectedAccount?.key.id === account.profile.key.id &&
			notifications.selectedAccount?.key.host === account.profile.key.host
		);
	}

	function filterLabel(filter: NotificationFilter): string {
		switch (filter) {
			case 'All':
				return m.notificationFilterAll();
			case 'Mention':
				return m.notificationFilterMentions();
			case 'Comment':
				return m.notificationFilterComments();
			case 'Like':
				return m.notificationFilterLikes();
		}
	}

	$effect(() => {
		accounts.length;
		accountsExpanded;
		void tick().then(updateAccountsOverflow);
	});

	$effect(() => {
		if (!accountsElement) return;

		const observer = new ResizeObserver(() => {
			updateAccountsOverflow();
		});
		observer.observe(accountsElement);

		return () => {
			observer.disconnect();
		};
	});

	function updateAccountsOverflow(): void {
		if (!accountsElement) return;

		const accountItems = Array.from(
			accountsElement.querySelectorAll<HTMLElement>('.account-tab, .account-tab-skeleton')
		);
		const styles = getComputedStyle(accountsElement);
		const columnGap = Number.parseFloat(styles.columnGap || styles.gap || '0') || 0;
		const accountsWidth = accountItems.reduce((total, item) => total + item.offsetWidth, 0);
		const totalWidth = accountsWidth + Math.max(0, accountItems.length - 1) * columnGap;
		const nextOverflowing = Math.ceil(totalWidth) > Math.floor(accountsElement.clientWidth);

		accountsOverflowing = nextOverflowing;
		if (!nextOverflowing && accountsExpanded) {
			accountsExpanded = false;
		}
	}
</script>

<div class="notifications-page">
	<AppTopBar title={hasMultipleAccounts ? undefined : m.homeTabNotificationsTitle()}>
		{#snippet start()}
			{#if hasMultipleAccounts}
				<div
					bind:this={accountsElement}
					class:account-tabs-expanded={accountsExpanded}
					class="tabs tabs-border account-tabs"
					role="tablist"
					aria-label={m.homeTabNotificationsTitle()}
				>
					{#each accounts as account (account.stableKey)}
						<button
							class:tab-active={isSelectedAccount(account)}
							class="tab account-tab"
							role="tab"
							type="button"
							aria-selected={isSelectedAccount(account)}
							onclick={() => selectAccount(account)}
						>
							<span class="indicator">
								{#if account.badge > 0}
									<span class="badge badge-primary badge-xs indicator-item"></span>
								{/if}
								<img class="account-avatar" src={account.profile.avatar} alt="" />
							</span>
							<span class="account-handle">{account.profile.handle.canonical}</span>
							{#if account.badge > 0}
								<span class="badge badge-sm">{account.badge}</span>
							{/if}
						</button>
					{/each}
				</div>
			{/if}
		{/snippet}

		{#snippet end()}
			{#if hasMultipleAccounts && (accountsOverflowing || accountsExpanded)}
				<button
					class:swap-active={accountsExpanded}
					class="btn btn-ghost btn-square btn-sm swap swap-rotate rounded-box"
					type="button"
					aria-label={accountsExpanded ? m.tabsCollapse() : m.tabsExpand()}
					aria-expanded={accountsExpanded}
					title={accountsExpanded ? m.tabsCollapse() : m.tabsExpand()}
					onclick={() => {
						accountsExpanded = !accountsExpanded;
					}}
				>
					<span class="swap-on">
						<FaIcon name="ChevronUp" size={14} />
					</span>
					<span class="swap-off">
						<FaIcon name="ChevronDown" size={14} />
					</span>
				</button>
			{/if}
		{/snippet}

		{#snippet bottom()}
			{#if refreshing}
				<progress class="progress progress-primary block h-0.5 w-full"></progress>
			{/if}

			{#if hasMultipleFilters}
				<div class="filter-bar border-t border-base-300 bg-base-100">
					<div class="join">
						{#each filters as filter (filter)}
							<button
								class:btn-primary={notifications.selectedFilter === filter}
								class:btn-ghost={notifications.selectedFilter !== filter}
								class="btn join-item btn-sm"
								type="button"
								onclick={() => notifications.setFilter(filter)}
							>
								{filterLabel(filter)}
							</button>
						{/each}
					</div>
				</div>
			{/if}
		{/snippet}
	</AppTopBar>

	<section class="notifications-content">
		<TimelineList listState={notifications.timeline as unknown as TimelinePagingState<UiTimelineV2>} />
	</section>
</div>

<style>
	.notifications-page {
		display: grid;
		min-height: 100vh;
		min-width: 0;
		grid-template-rows: auto minmax(0, 1fr);
		background: var(--color-base-200);
	}

	.notifications-content {
		display: grid;
		align-content: start;
		min-width: 0;
		gap: 0.75rem;
		padding: 0.75rem;
	}

	.filter-bar {
		display: flex;
		min-width: 0;
		overflow-x: auto;
		padding: 0.45rem 0.75rem;
		scrollbar-width: none;
	}

	.filter-bar::-webkit-scrollbar,
	.account-tabs::-webkit-scrollbar {
		display: none;
	}

	.account-tabs {
		flex: 1 1 auto;
		width: 100%;
		max-width: 100%;
		max-height: 2.75rem;
		min-width: 0;
		flex-wrap: nowrap;
		overflow-x: hidden;
		overflow-y: hidden;
		scrollbar-width: none;
	}

	.account-tabs-expanded {
		max-height: none;
		flex-wrap: wrap;
		overflow: visible;
	}

	:global(.notifications-page .app-top-bar) {
		align-items: flex-start;
	}

	:global(.notifications-page .app-top-bar-end) {
		align-items: flex-start;
		padding-top: 0.375rem;
	}

	.account-tab {
		display: inline-flex;
		flex: 0 0 auto;
		flex-wrap: nowrap;
		align-items: center;
		gap: 0.4rem;
		max-width: 15rem;
		min-height: 2.5rem;
		padding-inline: 0.75rem;
		white-space: nowrap;
	}

	.account-handle {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.account-tab :global(.indicator),
	.account-tab :global(.badge) {
		flex: 0 0 auto;
	}

	.account-avatar {
		width: 1.25rem;
		height: 1.25rem;
		flex: 0 0 auto;
		border-radius: 999px;
		object-fit: cover;
		background: var(--color-base-300);
	}

	:global(.notifications-page .timeline-list.card-mode),
	:global(.notifications-page .card-mode .timeline-list-body),
	:global(.notifications-page .timeline-loading-placeholder-list.card-mode),
	:global(.notifications-page .card-mode .timeline-placeholder-body) {
		background: transparent;
	}

	:global(.notifications-page .card-mode .timeline-list-body),
	:global(.notifications-page .card-mode .timeline-placeholder-body) {
		padding: 0;
	}
</style>
