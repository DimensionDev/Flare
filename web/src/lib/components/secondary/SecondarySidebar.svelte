<script lang="ts">
	import { page } from '$app/state';
	import { createSecondaryTabsPresenter } from '@flare/web-presenters/secondaryTabs.svelte';
	import type {
		SecondaryTabsPresenterTab,
		TimelineTabItemV2,
	} from '@flare/web-presenters/secondaryTabs.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { localizedUiString } from '$lib/i18n/uiStrings';
	import { m } from '$lib/paraglide/messages.js';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';

	let {
		selectedTimelineKey = null,
		onTimelineSelected,
	}: {
		selectedTimelineKey?: string | null;
		onTimelineSelected: (tab: TimelineTabItemV2) => void;
	} = $props();

	const secondaryTabs = createSecondaryTabsPresenter();
	const secondaryItems = $derived(
		secondaryTabs.items.type === 'Success' ? secondaryTabs.items.data : []
	);

	function secondaryTabKey(tab: SecondaryTabsPresenterTab): string {
		if (tab.timelineTabItem) return `timeline:${tab.timelineTabItem.key}`;
		return tab.href ? `route:${tab.href}` : `route:${tab.title}:${tab.icon}`;
	}

	function isHrefActive(href: string): boolean {
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}
</script>

<aside class="secondary-sidebar border-l border-base-300 bg-base-300" aria-label={m.secondarySidebarAriaLabel()}>
	<div class="secondary-scroll">
		{#if secondaryTabs.items.type === 'Loading'}
			<div class="secondary-loading p-4">
				<div class="skeleton h-8 w-32"></div>
				<div class="skeleton h-10 w-full"></div>
				<div class="skeleton h-10 w-full"></div>
			</div>
		{:else if secondaryTabs.items.type === 'Success'}
			{#if secondaryItems.length === 0}
				<div class="px-3 py-3">
					<a class="btn btn-primary btn-sm w-full" href="/login">
						<FaIcon name="Follow" size={16} />
						<span>{m.loginButton()}</span>
					</a>
				</div>
			{:else}
				{#each secondaryItems as item (item.__webPresenterRef)}
					<div class="collapse collapse-arrow secondary-account">
						<input type="checkbox" aria-label={m.secondaryToggleAccountShortcuts()} />
						<div class="collapse-title secondary-account-title">
							{#if item.user.type === 'Success'}
								<UserDisplay user={item.user.data} variant="sidebar" clickable={false} />
							{:else}
								<div class="secondary-user px-1 py-2">
									<div class="avatar placeholder">
										<div class="h-8 w-8 rounded-full bg-base-300"></div>
									</div>
									<div class="secondary-user-copy">
										<span>{m.settingsAccountsTitle()}</span>
									</div>
								</div>
							{/if}
						</div>

						<div class="collapse-content secondary-account-content">
							<ul class="menu menu-sm w-full gap-1 p-0">
								{#each item.tabs as tab (secondaryTabKey(tab))}
									<li class="w-full">
										{#if tab.timelineTabItem}
											<button
												class="w-full justify-start"
												type="button"
												class:menu-active={selectedTimelineKey === tab.timelineTabItem.key}
												onclick={() => {
													if (tab.timelineTabItem) {
														onTimelineSelected(tab.timelineTabItem);
													}
												}}
											>
												<FaIcon name={tab.icon} size={16} />
												<span>{localizedUiString(tab.title)}</span>
											</button>
										{:else if tab.href}
											<a
												class="w-full justify-start"
												class:menu-active={isHrefActive(tab.href)}
												href={tab.href}
												aria-current={isHrefActive(tab.href) ? 'page' : undefined}
											>
												<FaIcon name={tab.icon} size={16} />
												<span>{localizedUiString(tab.title)}</span>
											</a>
										{:else}
											<button class="w-full justify-start" type="button" disabled>
												<FaIcon name={tab.icon} size={16} />
												<span>{localizedUiString(tab.title)}</span>
											</button>
										{/if}
									</li>
								{/each}
							</ul>
						</div>
					</div>
				{/each}
			{/if}
		{/if}

		<div class="divider my-2"></div>
		<ul class="menu menu-sm w-full gap-1 px-3 pb-4">
			<li class="w-full">
				<button class="w-full justify-start" type="button" disabled>
					<FaIcon name="PenToSquare" size={16} />
					<span>{m.draftBoxTitle()}</span>
				</button>
			</li>
			<li class="w-full">
				<a
					class="w-full justify-start"
					class:menu-active={isHrefActive('/subscriptions')}
					href="/subscriptions"
					aria-current={isHrefActive('/subscriptions') ? 'page' : undefined}
				>
					<FaIcon name="Rss" size={16} />
					<span>{m.settingsRssManagementTitle()}</span>
				</a>
			</li>
			<li class="w-full">
				<a
					class="w-full justify-start"
					class:menu-active={isHrefActive('/history')}
					href="/history"
					aria-current={isHrefActive('/history') ? 'page' : undefined}
				>
					<FaIcon name="ClockRotateLeft" size={16} />
					<span>{m.settingsLocalHistoryTitle()}</span>
				</a>
			</li>
			<li class="w-full">
				<a
					class="w-full justify-start"
					class:menu-active={isHrefActive('/settings')}
					href="/settings"
					aria-current={isHrefActive('/settings') ? 'page' : undefined}
				>
					<FaIcon name="Settings" size={16} />
					<span>{m.settingsTitle()}</span>
				</a>
			</li>
		</ul>
	</div>
</aside>

<style>
	.secondary-sidebar {
		position: sticky;
		top: 0;
		width: min(15rem, 100%);
		height: 100vh;
		min-width: 0;
		align-self: start;
		justify-self: start;
	}

	.secondary-scroll {
		height: 100%;
		overflow: auto;
	}

	.secondary-loading {
		display: grid;
		gap: 0.75rem;
	}

	.secondary-account {
		border-radius: 0;
	}

	.secondary-account-title {
		min-height: 0;
		padding: 0.25rem 2.5rem 0.25rem 0.75rem;
	}

	.secondary-account-content {
		padding-inline: 0.75rem;
		padding-bottom: 0.75rem;
	}

	.secondary-sidebar :global(.menu),
	.secondary-sidebar :global(.menu li),
	.secondary-sidebar :global(.menu li > a),
	.secondary-sidebar :global(.menu li > button) {
		box-sizing: border-box;
		width: 100%;
		max-width: none;
	}

	.secondary-user {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: 0.625rem;
	}

	.secondary-user-copy {
		display: grid;
		min-width: 0;
		gap: 0.05rem;
	}

	.secondary-user-copy span {
		overflow: hidden;
		font-size: 0.875rem;
		font-weight: 600;
		line-height: 1.2;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	@media (max-width: 760px) {
		.secondary-sidebar {
			display: none;
		}
	}
</style>
