<script lang="ts">
	import { page } from '$app/state';
	import { createHomeTabsPresenter } from '@flare/web-presenters/homeTabs.svelte';
	import type { HomeTabsPresenterStateHomeTabs } from '@flare/web-presenters/homeTabs.svelte';
	import DeepLinkProvider from '$lib/components/deeplink/DeepLinkProvider.svelte';
	import EnvironmentSettingsProvider from '$lib/components/environment/EnvironmentSettingsProvider.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import ThemeController from '$lib/components/environment/ThemeController.svelte';
	import favicon from '$lib/assets/favicon.svg';
	import '../app.css';

	let { children } = $props();

	type AppTab = HomeTabsPresenterStateHomeTabs | 'Settings';

	const fallbackTabs: HomeTabsPresenterStateHomeTabs[] = ['Home', 'Discover'];
	const homeTabs = createHomeTabsPresenter();
	const presenterTabs = $derived(
		homeTabs.tabs.type === 'Success'
			? homeTabs.tabs.data
			: ([] as HomeTabsPresenterStateHomeTabs[])
	);
	const navigationTabs = $derived(presenterTabs.length > 0 ? presenterTabs : fallbackTabs);
	const activeTab = $derived(tabForPath(page.url.pathname));
	const pageTitle = $derived(activeTab ? tabTitle(activeTab) : 'Flare');

	function tabForPath(pathname: string): AppTab | null {
		if (pathname === '/') return 'Home';
		if (pathname === '/discover' || pathname.startsWith('/discover/')) return 'Discover';
		if (pathname === '/notifications' || pathname.startsWith('/notifications/')) return 'Notifications';
		if (pathname === '/settings' || pathname.startsWith('/settings/')) return 'Settings';
		return null;
	}

	function tabTitle(tab: AppTab): string {
		switch (tab) {
			case 'Home':
				return 'Home';
			case 'Notifications':
				return 'Notifications';
			case 'Discover':
				return 'Discover';
			case 'Settings':
				return 'Settings';
		}
	}

	function tabIcon(tab: AppTab): string {
		switch (tab) {
			case 'Home':
				return 'Home';
			case 'Notifications':
				return 'Notification';
			case 'Discover':
				return 'Search';
			case 'Settings':
				return 'Settings';
		}
	}

	function tabHref(tab: AppTab): string {
		switch (tab) {
			case 'Home':
				return '/';
			case 'Notifications':
				return '/notifications';
			case 'Discover':
				return '/discover';
			case 'Settings':
				return '/settings';
		}
	}

	function isTabActive(tab: AppTab): boolean {
		return activeTab === tab;
	}
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>{pageTitle === 'Flare' ? 'Flare' : `${pageTitle} | Flare`}</title>
</svelte:head>

<EnvironmentSettingsProvider>
	<DeepLinkProvider>
		<ThemeController />
		<main class="app-shell bg-base-100 text-base-content">
			<nav class="app-sidebar border-r border-base-300 bg-base-100" aria-label="Main">
				<div class="sidebar-brand">
					<div class="brand-mark rounded-box bg-primary text-primary-content">F</div>
					<div class="brand-copy">
						<strong>Flare</strong>
					</div>
				</div>

				<ul class="menu menu-lg w-full gap-1 p-2">
					{#each navigationTabs as tab (tab)}
						<li>
							<a
								class:menu-active={isTabActive(tab)}
								href={tabHref(tab)}
								aria-current={isTabActive(tab) ? 'page' : undefined}
							>
								<FaIcon name={tabIcon(tab)} size={18} />
								<span>{tabTitle(tab)}</span>
							</a>
						</li>
					{/each}
				</ul>

				<ul class="menu menu-lg sidebar-settings w-full p-2">
					<li>
						<a
							class:menu-active={isTabActive('Settings')}
							href="/settings"
							aria-current={isTabActive('Settings') ? 'page' : undefined}
						>
							<FaIcon name="Settings" size={18} />
							<span>Settings</span>
						</a>
					</li>
				</ul>
			</nav>

			<section class="app-content min-w-0 bg-base-100" aria-label={pageTitle}>
				<div class="content-stage">
					{@render children()}
				</div>
			</section>

			<nav class="dock mobile-dock border-t border-base-300 bg-base-100" aria-label="Main">
				{#each navigationTabs as tab (tab)}
					<a
						class:dock-active={isTabActive(tab)}
						href={tabHref(tab)}
						aria-current={isTabActive(tab) ? 'page' : undefined}
					>
						<FaIcon name={tabIcon(tab)} size={18} />
						<span class="dock-label">{tabTitle(tab)}</span>
					</a>
				{/each}
				<a
					class:dock-active={isTabActive('Settings')}
					href="/settings"
					aria-current={isTabActive('Settings') ? 'page' : undefined}
				>
					<FaIcon name="Settings" size={18} />
					<span class="dock-label">Settings</span>
				</a>
			</nav>
		</main>
	</DeepLinkProvider>
</EnvironmentSettingsProvider>

<style>
	.app-shell {
		display: grid;
		grid-template-columns: 15rem minmax(0, 1fr);
		min-height: 100vh;
	}

	.app-sidebar {
		position: sticky;
		top: 0;
		display: flex;
		height: 100vh;
		min-width: 0;
		flex-direction: column;
	}

	.sidebar-brand {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		min-width: 0;
		padding: 1rem;
	}

	.brand-mark {
		display: grid;
		width: 2.25rem;
		height: 2.25rem;
		flex: 0 0 auto;
		place-items: center;
		font-size: 1rem;
		font-weight: 700;
	}

	.brand-copy {
		min-width: 0;
	}

	.brand-copy strong {
		display: block;
		overflow: hidden;
		font-size: 0.95rem;
		font-weight: 600;
		line-height: 1.2;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.sidebar-settings {
		margin-top: auto;
	}

	.app-content {
		min-height: 100vh;
	}

	.content-stage {
		min-width: 0;
		padding: 1rem;
	}

	.mobile-dock {
		display: none;
	}

	@media (max-width: 760px) {
		.app-shell {
			grid-template-columns: minmax(0, 1fr);
			padding-bottom: 4rem;
		}

		.app-sidebar {
			display: none;
		}

		.app-content {
			min-height: calc(100vh - 4rem);
		}

		.mobile-dock {
			display: flex;
		}
	}
</style>
