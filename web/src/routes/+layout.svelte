<script lang="ts">
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { createHomeTabsPresenter } from '@flare/web-presenters/homeTabs.svelte';
	import { createNotificationBadgePresenter } from '@flare/web-presenters/notificationBadge.svelte';
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
	import { initializeFirebaseAnalytics } from '$lib/firebase/firebase';
	import logo from '$lib/assets/logo.svg';
	import ComposeDialog from '$lib/components/compose/ComposeDialog.svelte';
	import { onMount } from 'svelte';
	import '../app.css';

	let { children } = $props();

	type AppTab = HomeTabsPresenterStateHomeTabs | 'Profile' | 'Settings' | 'Search' | 'Compose';

	const fallbackTabs: HomeTabsPresenterStateHomeTabs[] = ['Home', 'Discover'];
	const homeTabs = createHomeTabsPresenter();
	const notificationBadge = createNotificationBadgePresenter();
	let selectedSecondaryTimeline = $state<SecondaryTimelineTabItemV2 | null>(null);
	let composeOverlayOpen = $state(false);
	let composeOverlayUrl = $state('/compose');
	let backgroundSnapshotElement = $state<HTMLElement | null>(null);
	let backgroundSnapshotHost = $state<HTMLDivElement | null>(null);
	let shallowPath = $state<string | null>(null);
	const presenterTabs = $derived(
		homeTabs.tabs.type === 'Success'
			? homeTabs.tabs.data
			: ([] as HomeTabsPresenterStateHomeTabs[])
	);
	const navigationTabs = $derived(presenterTabs.length > 0 ? presenterTabs : fallbackTabs);
	const visiblePathname = $derived(shallowPath ?? page.url.pathname);
	const activeTab = $derived(tabForPath(visiblePathname));
	const pageTitle = $derived(
		selectedSecondaryTimeline ? timelineTabTitle(selectedSecondaryTimeline) : activeTab ? tabTitle(activeTab) : 'Flare'
	);

	$effect(() => {
		const host = backgroundSnapshotHost;
		const snapshot = backgroundSnapshotElement;
		if (!host) return;

		host.replaceChildren();
		if (!composeOverlayOpen || !snapshot) return;
		host.replaceChildren(...Array.from(snapshot.childNodes).map((node) => node.cloneNode(true)));
	});

	$effect(() => {
		page.url.pathname;
		if (composeOverlayOpen) return;
		selectedSecondaryTimeline = null;
	});

	onMount(() => {
		void initializeFirebaseAnalytics().catch((error: unknown) => {
			console.warn('Failed to initialize Firebase Analytics.', error);
		});

		const handlePopState = () => {
			composeOverlayOpen = false;
			backgroundSnapshotElement = null;
			shallowPath = null;
		};
		const handleDocumentClick = (event: MouseEvent) => {
			if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
				return;
			}
			if (!(event.target instanceof Element)) return;

			const anchor = event.target.closest<HTMLAnchorElement>('a[href]');
			const href = anchor?.getAttribute('href');
			if (!href || anchor?.target || !isComposeRouteUrl(href)) return;

			event.preventDefault();
			openComposeUrl(href);
		};
		const handleComposeRoute = (event: Event) => {
			const routeUrl = (event as CustomEvent<string>).detail;
			if (!isComposeRouteUrl(routeUrl)) return;
			event.preventDefault();
			openComposeUrl(routeUrl);
		};

		addEventListener('popstate', handlePopState);
		document.addEventListener('click', handleDocumentClick, true);
		addEventListener('flare:compose-route', handleComposeRoute);
		return () => {
			removeEventListener('popstate', handlePopState);
			document.removeEventListener('click', handleDocumentClick, true);
			removeEventListener('flare:compose-route', handleComposeRoute);
		};
	});

	function tabForPath(pathname: string): AppTab | null {
		if (pathname === '/') return 'Home';
		if (pathname === '/search' || pathname.startsWith('/search/')) return 'Search';
		if (pathname === '/compose') return 'Compose';
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
			case 'Search':
				return m.searchTitle();
			case 'Profile':
				return m.profileTitle();
			case 'Settings':
				return m.settingsTitle();
			case 'Compose':
				return 'Compose';
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
			case 'Search':
				return 'Search';
			case 'Profile':
				return 'Profile';
			case 'Settings':
				return 'Settings';
			case 'Compose':
				return 'Edit';
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
			case 'Search':
				return '/search';
			case 'Profile':
				return '/profile';
			case 'Settings':
				return '/settings';
			case 'Compose':
				return '/compose';
		}
	}

	function isTabActive(tab: AppTab): boolean {
		return activeTab === tab;
	}

	function badgeLabel(count: number): string {
		return count > 99 ? '99+' : `${count}`;
	}

	function openCompose(event: MouseEvent): void {
		if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
			return;
		}
		if (page.url.pathname === '/compose' && composeOverlayOpen) return;

		event.preventDefault();
		openComposeUrl('/compose');
	}

	function openComposeUrl(routeUrl: string): void {
		if (!composeOverlayOpen) {
			captureBackgroundSnapshot();
		}
		history.pushState({ composeOverlay: true }, '', routeUrl);
		composeOverlayUrl = routeUrl;
		shallowPath = '/compose';
		composeOverlayOpen = true;
	}

	function closeComposeOverlay(): void {
		if (composeOverlayOpen && history.length > 1) {
			history.back();
			return;
		}
		void goto('/');
	}

	function captureBackgroundSnapshot(): void {
		const stage = document.querySelector<HTMLElement>('.content-stage');
		backgroundSnapshotElement = stage?.cloneNode(true) as HTMLElement | null;
	}

	function isComposeRouteUrl(routeUrl: string): boolean {
		try {
			const url = new URL(routeUrl, page.url.origin);
			return url.origin === page.url.origin && url.pathname === '/compose';
		} catch {
			return false;
		}
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
								<span class="nav-icon-indicator indicator">
									{#if tab === 'Notifications' && notificationBadge.count > 0}
										<span class="badge badge-primary indicator-item notification-badge">
											{badgeLabel(notificationBadge.count)}
										</span>
									{/if}
									<FaIcon name={tabIcon(tab)} size={18} />
								</span>
							</a>
						</li>
					{/each}
				</ul>

				<a
					class:btn-active={activeTab === 'Compose'}
					class="btn btn-primary btn-square compose-nav-button tooltip tooltip-right"
					href="/compose"
					aria-label={m.composeTitle()}
					aria-current={activeTab === 'Compose' ? 'page' : undefined}
					data-tip={m.composeTitle()}
					onclick={openCompose}
				>
					<FaIcon name="Edit" size={17} />
				</a>
			</nav>

				<section class="app-content min-w-0 bg-base-100" aria-label={pageTitle}>
					{#if composeOverlayOpen && backgroundSnapshotElement}
						<div class="content-stage background-snapshot-host" bind:this={backgroundSnapshotHost}></div>
					{:else}
						<div class="content-stage">
							{#if selectedSecondaryTimeline}
								{#key selectedSecondaryTimeline.id}
									<HomeTimelineTabPanel tab={selectedSecondaryTimeline as unknown as TimelineTabItemV2} />
								{/key}
							{:else}
								{@render children()}
							{/if}
						</div>
					{/if}
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
						<span class="nav-icon-indicator indicator">
							{#if tab === 'Notifications' && notificationBadge.count > 0}
								<span class="badge badge-primary indicator-item notification-badge">
									{badgeLabel(notificationBadge.count)}
								</span>
							{/if}
							<FaIcon name={tabIcon(tab)} size={18} />
						</span>
					</a>
				{/each}
				<a
					class:dock-active={activeTab === 'Compose'}
					href="/compose"
					aria-label={m.composeTitle()}
					aria-current={activeTab === 'Compose' ? 'page' : undefined}
					onclick={openCompose}
				>
					<FaIcon name="Edit" size={18} />
				</a>
				<a
					class:dock-active={isTabActive('Settings')}
					href="/settings"
					aria-label={m.settingsTitle()}
					aria-current={isTabActive('Settings') ? 'page' : undefined}
				>
					<FaIcon name="Settings" size={18} />
				</a>
			</nav>

			{#if composeOverlayOpen}
				<ComposeDialog routeUrl={composeOverlayUrl} onClose={closeComposeOverlay} />
			{/if}
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
		--app-secondary-width: 21rem;
		display: grid;
		grid-template-columns:
			var(--app-sidebar-width)
			minmax(0, var(--app-content-width))
			var(--app-secondary-width);
		justify-content: center;
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
		overflow: visible;
	}

	.app-sidebar :global(.menu a.active-primary) {
		background: color-mix(in oklab, var(--color-primary) 12%, transparent);
		color: var(--color-primary);
	}

	.nav-icon-indicator {
		display: grid;
		width: 1.5rem;
		height: 1.5rem;
		place-items: center;
	}

	.compose-nav-button {
		width: 2.75rem;
		height: 2.75rem;
		min-height: 2.75rem;
		margin-top: 0.35rem;
		border-radius: var(--radius-box);
	}

	.notification-badge {
		min-width: 1rem;
		height: 1rem;
		padding-inline: 0.2rem;
		border-width: 0;
		font-size: 0.58rem;
		line-height: 1;
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

	.background-snapshot-host {
		pointer-events: none;
		user-select: none;
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
