<script lang="ts">
	import { page } from '$app/state';
	import { createHomeTabsPresenter } from '@flare/web-presenters/homeTabs.svelte';
	import type { HomeTabsPresenterStateHomeTabs } from '@flare/web-presenters/homeTabs.svelte';
	import type { TimelineTabItemV2 as SecondaryTimelineTabItemV2 } from '@flare/web-presenters/secondaryTabs.svelte';
	import DeepLinkProvider from '$lib/components/deeplink/DeepLinkProvider.svelte';
	import EnvironmentSettingsProvider from '$lib/components/environment/EnvironmentSettingsProvider.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import HomeTimelineTabPanel from '$lib/components/home/HomeTimelineTabPanel.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import SecondarySidebar from '$lib/components/secondary/SecondarySidebar.svelte';
	import { timelineTabTitle } from '$lib/components/secondary/secondaryTabs';
	import ThemeController from '$lib/components/environment/ThemeController.svelte';
	import type { TimelineTabItemV2 } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import favicon from '$lib/assets/favicon.svg';
	import logo from '$lib/assets/logo.svg';
	import '../app.css';

	let { children } = $props();

	type AppTab = HomeTabsPresenterStateHomeTabs | 'Profile' | 'Settings';

	const fallbackTabs: HomeTabsPresenterStateHomeTabs[] = ['Home', 'Discover'];
	const homeTabs = createHomeTabsPresenter();
	let selectedSecondaryTimeline = $state<SecondaryTimelineTabItemV2 | null>(null);
	const presenterTabs = $derived(
		homeTabs.tabs.type === 'Success'
			? homeTabs.tabs.data
			: ([] as HomeTabsPresenterStateHomeTabs[])
	);
	const navigationTabs = $derived(presenterTabs.length > 0 ? presenterTabs : fallbackTabs);
	const activeTab = $derived(tabForPath(page.url.pathname));
	const pageTitle = $derived(
		selectedSecondaryTimeline ? timelineTabTitle(selectedSecondaryTimeline) : activeTab ? tabTitle(activeTab) : 'Flare'
	);

	$effect(() => {
		page.url.pathname;
		selectedSecondaryTimeline = null;
	});

	function tabForPath(pathname: string): AppTab | null {
		if (pathname === '/') return 'Home';
		if (pathname === '/discover' || pathname.startsWith('/discover/')) return 'Discover';
		if (pathname === '/notifications' || pathname.startsWith('/notifications/')) return 'Notifications';
		if (isProfilePath(pathname)) return 'Profile';
		if (pathname === '/settings' || pathname.startsWith('/settings/')) return 'Settings';
		return null;
	}

	function isProfilePath(pathname: string): boolean {
		return (
			pathname === '/profile' ||
			pathname.startsWith('/profile/') ||
			/^\/[^/]+\/profile(?:\/|$)/.test(pathname)
		);
	}

	function tabTitle(tab: AppTab): string {
		switch (tab) {
			case 'Home':
				return m.homeTabHomeTitle();
			case 'Notifications':
				return m.homeTabNotificationsTitle();
			case 'Discover':
				return m.homeTabDiscoverTitle();
			case 'Profile':
				return m.profileTitle();
			case 'Settings':
				return m.settingsTitle();
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
			case 'Profile':
				return 'Profile';
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
			case 'Profile':
				return '/profile';
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
		<main class="app-shell bg-base-300 text-base-content">
			<nav class="app-sidebar bg-base-300" aria-label={m.navigationMainAriaLabel()}>
				<div class="sidebar-brand">
					<img class="brand-mark" src={logo} alt="Flare" />
				</div>

				<ul class="menu menu-lg w-full items-center gap-2 p-2">
					{#each navigationTabs as tab (tab)}
						<li>
							<a
								class:active-primary={isTabActive(tab)}
								class="tooltip tooltip-right rounded-box"
								href={tabHref(tab)}
								aria-label={tabTitle(tab)}
								aria-current={isTabActive(tab) ? 'page' : undefined}
								data-tip={tabTitle(tab)}
							>
								<FaIcon name={tabIcon(tab)} size={18} />
							</a>
						</li>
					{/each}
				</ul>
			</nav>

			<section class="app-content min-w-0 bg-base-100" aria-label={pageTitle}>
				<div class="content-stage">
					{#if selectedSecondaryTimeline}
						{#key selectedSecondaryTimeline.id}
							<HomeTimelineTabPanel tab={selectedSecondaryTimeline as unknown as TimelineTabItemV2} />
						{/key}
					{:else}
						{@render children()}
					{/if}
				</div>
			</section>

			<SecondarySidebar
				selectedTimelineKey={selectedSecondaryTimeline?.key ?? null}
				onTimelineSelected={(tab) => {
					selectedSecondaryTimeline = tab;
				}}
			/>

			<nav class="dock mobile-dock border-t border-base-300 bg-base-300" aria-label={m.navigationMainAriaLabel()}>
				{#each navigationTabs as tab (tab)}
					<a
						class:dock-active={isTabActive(tab)}
						href={tabHref(tab)}
						aria-label={tabTitle(tab)}
						aria-current={isTabActive(tab) ? 'page' : undefined}
					>
						<FaIcon name={tabIcon(tab)} size={18} />
					</a>
				{/each}
				<a
					class:dock-active={isTabActive('Settings')}
					href="/settings"
					aria-label={m.settingsTitle()}
					aria-current={isTabActive('Settings') ? 'page' : undefined}
				>
					<FaIcon name="Settings" size={18} />
				</a>
			</nav>
		</main>
	</DeepLinkProvider>
</EnvironmentSettingsProvider>

<style>
	:global(body) {
		background: var(--color-base-300);
	}

	.app-shell {
		--app-content-width: 640px;
		--app-sidebar-width: 4.5rem;
		--app-secondary-width: 15rem;
		display: grid;
		grid-template-columns:
			minmax(var(--app-sidebar-width), 1fr)
			minmax(0, var(--app-content-width))
			minmax(var(--app-secondary-width), 1fr);
		width: 100%;
		min-height: 100vh;
		background: var(--color-base-300);
	}

	.app-sidebar {
		position: sticky;
		top: 0;
		display: flex;
		width: var(--app-sidebar-width);
		height: 100vh;
		align-self: start;
		justify-self: end;
		min-width: 0;
		flex-direction: column;
		align-items: center;
		overflow-x: hidden;
		overflow-y: auto;
	}

	.sidebar-brand {
		display: flex;
		align-items: center;
		justify-content: center;
		min-width: 0;
		padding: 1rem 0.75rem;
	}

	.brand-mark {
		width: 2.25rem;
		height: 2.25rem;
		flex: 0 0 auto;
		object-fit: contain;
	}

	.app-sidebar :global(.menu li) {
		display: grid;
		place-items: center;
	}

	.app-sidebar :global(.menu a) {
		display: grid;
		width: 2.75rem;
		height: 2.75rem;
		place-items: center;
		justify-content: center;
		align-content: center;
		padding: 0;
		color: color-mix(in oklab, var(--color-base-content) 72%, transparent);
		overflow: hidden;
	}

	.app-sidebar :global(.menu a.active-primary) {
		background: color-mix(in oklab, var(--color-primary) 12%, transparent);
		color: var(--color-primary);
	}

	.app-content {
		min-height: 100vh;
		background: var(--color-base-100);
	}

	.content-stage {
		width: 100%;
		min-height: 100%;
		min-width: 0;
		background: var(--color-base-100);
	}

	.mobile-dock {
		display: none;
	}

	@media (max-width: 760px) {
		.app-shell {
			grid-template-columns: minmax(0, 1fr);
			width: 100%;
			min-height: 100vh;
			margin-inline: 0;
			padding-bottom: 4rem;
		}

		.app-sidebar {
			display: none;
		}

		.app-content {
			min-height: calc(100vh - 4rem);
		}

		.content-stage {
			min-height: calc(100vh - 4rem);
		}

		.mobile-dock {
			display: flex;
		}
	}
</style>
