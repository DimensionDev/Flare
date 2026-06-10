<script lang="ts">
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { createSecondaryTabsPresenter } from '@flare/web-presenters/secondaryTabs.svelte';
	import type {
		SecondaryTabsPresenterTab,
		UiTimelineTabItem,
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
		onTimelineSelected: (tab: UiTimelineTabItem) => void;
	} = $props();

	let searchQuery = $state('');
	const secondaryTabs = createSecondaryTabsPresenter();
	const appVersion = __FLARE_APP_VERSION__;
	const secondaryItems = $derived(
		secondaryTabs.items.type === 'Success' ? secondaryTabs.items.data : []
	);
	const projectFooterLinks = $derived([
		{
			label: m.settingsAboutSourceCode(),
			href: 'https://github.com/DimensionDev/Flare',
			icon: 'Github',
		},
		{
			label: m.settingsAboutTelegram(),
			href: 'https://t.me/+VZ63fqNQXIA0MzVl',
			icon: 'Telegram',
		},
		{
			label: m.settingsAboutDiscord(),
			href: 'https://discord.gg/De9NhXBryT',
			icon: 'Discord',
		},
		{
			label: m.settingsPrivacyPolicy(),
			href: 'https://legal.mask.io/maskbook/',
			icon: 'Lock',
		},
	]);
	const downloadFooterLinks = $derived([
		{
			label: 'App Store',
			href: 'https://apps.apple.com/us/app/flare-social-network-client/id6476077738',
			icon: 'Apple',
		},
		{
			label: 'Google Play',
			href: 'https://play.google.com/store/apps/details?id=dev.dimension.flare',
			icon: 'GooglePlay',
		},
		{
			label: 'F-Droid',
			href: 'https://f-droid.org/packages/dev.dimension.flare',
			icon: 'Android',
		},
		{
			label: 'AppImage',
			href: 'https://github.com/DimensionDev/Flare/releases/latest',
			icon: 'Github',
		},
	]);

	function secondaryTabKey(tab: SecondaryTabsPresenterTab): string {
		if (tab.timelineTabItem) return `timeline:${tab.timelineTabItem.key}`;
		return tab.href ? `route:${tab.href}` : `route:${tab.title}:${tab.icon}`;
	}

	function isHrefActive(href: string): boolean {
		return page.url.pathname === href || page.url.pathname.startsWith(`${href}/`);
	}

	function submitSearch(): void {
		const next = searchQuery.trim();
		const params = new URLSearchParams();
		if (next) params.set('q', next);
		const queryString = params.toString();
		void goto(queryString ? `/search?${queryString}` : '/search');
	}
</script>

<aside class="secondary-sidebar border-l border-base-300 bg-base-300" aria-label={m.secondarySidebarAriaLabel()}>
	<div class="secondary-scroll flex flex-col gap-4 pb-4 pl-4 pr-3">
		<form
			class="secondary-search"
			role="search"
			onsubmit={(event) => {
				event.preventDefault();
				submitSearch();
			}}
		>
			<label class="input input-bordered input-sm rounded-box">
				<FaIcon name="Search" size={14} />
				<input
					bind:value={searchQuery}
					type="search"
					placeholder={m.discoverSearchPlaceholder()}
					aria-label={m.discoverSearchPlaceholder()}
				/>
			</label>
		</form>

		<div class="card bg-base-100 shadow-sm">
			<div class="card-body px-2 pb-2 pt-4">
				{#if secondaryTabs.items.type === 'Loading'}
					<div class="secondary-loading p-2">
						<div class="skeleton h-8 w-32"></div>
						<div class="skeleton h-10 w-full"></div>
						<div class="skeleton h-10 w-full"></div>
					</div>
				{:else if secondaryTabs.items.type === 'Success'}
					{#if secondaryItems.length === 0}
						<a class="btn btn-primary btn-sm w-full" href="/login">
							<FaIcon name="Follow" size={16} />
							<span>{m.loginButton()}</span>
						</a>
					{:else}
						{#each secondaryItems as item (item.__webPresenterRef)}
							<div class="collapse collapse-arrow secondary-account">
								<input type="checkbox" aria-label={m.secondaryToggleAccountShortcuts()} />
								<div class="collapse-title secondary-account-title">
									{#if item.user.type === 'Success'}
										<UserDisplay user={item.user.data} variant="sidebar" clickable={false} />
									{:else}
										<div class="secondary-user">
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
			</div>
		</div>

		<div class="card bg-base-100 shadow-sm">
			<div class="card-body p-2">
				<ul class="menu menu-sm w-full gap-1 p-0">
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
		</div>

		<footer class="secondary-footer">
			<nav class="footer-links" aria-label={m.settingsAboutTitle()}>
				{#each projectFooterLinks as link (link.href)}
					<a href={link.href} target="_blank" rel="noreferrer">
						<FaIcon name={link.icon} size={12} />
						<span>{link.label}</span>
					</a>
				{/each}
			</nav>
			<nav class="footer-links" aria-label={m.secondaryFooterDownloadApp()}>
				{#each downloadFooterLinks as link (link.href)}
					<a href={link.href} target="_blank" rel="noreferrer">
						<FaIcon name={link.icon} size={12} />
						<span>{link.label}</span>
					</a>
				{/each}
			</nav>
			{#if appVersion}
				<p>{m.secondaryFooterVersion({ version: appVersion })}</p>
			{/if}
		</footer>
	</div>
</aside>

<style>
	.secondary-sidebar {
		position: sticky;
		top: 0;
		width: min(var(--app-secondary-width, 24rem), 100%);
		height: 100vh;
		min-width: 0;
		align-self: start;
		justify-self: start;
	}

	.secondary-scroll {
		height: 100%;
		overflow: auto;
	}

	.secondary-search {
		display: flex;
		min-height: 3.5rem;
		min-width: 0;
		align-items: center;
	}

	.secondary-search label {
		display: flex;
		width: 100%;
		align-items: center;
		gap: 0.5rem;
		background: var(--color-base-100);
	}

	.secondary-search input {
		min-width: 0;
	}

	.secondary-footer {
		display: grid;
		gap: 0.6rem;
		padding: 0.25rem 0.25rem 0.5rem;
		color: color-mix(in oklab, var(--color-base-content) 56%, transparent);
		font-size: 0.74rem;
		line-height: 1.25;
	}

	.footer-links {
		display: flex;
		flex-wrap: wrap;
		gap: 0.35rem 0.75rem;
	}

	.footer-links a {
		display: inline-flex;
		min-width: 0;
		align-items: center;
		gap: 0.3rem;
		color: inherit;
		text-decoration: none;
	}

	.footer-links a:hover {
		color: var(--color-base-content);
		text-decoration: underline;
		text-underline-offset: 0.15rem;
	}

	.secondary-footer p {
		margin: 0;
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
		color: color-mix(in oklab, var(--color-base-content) 44%, transparent);
	}

	.secondary-loading {
		display: grid;
		gap: 0.75rem;
	}

	.secondary-account {
		border-radius: 0;
		margin: 0;
	}

	.secondary-account-title {
		min-height: 0;
		padding: 0.25rem 0.5rem;
	}

	.secondary-account-title :global(.user-display.sidebar) {
		padding: 0;
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
