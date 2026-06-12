<script lang="ts">
	import { createHomeTimelineWithTabsPresenterController } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import { tick } from 'svelte';
	import type {
		IconType,
		UiTimelineTabItem,
		UiText,
	} from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import HomeTimelineTabPanel from '$lib/components/home/HomeTimelineTabPanel.svelte';
	import { localizedUiString } from '$lib/i18n/uiStrings';
	import { m } from '$lib/paraglide/messages.js';
	import TimelineLoadingPlaceholderList from '$lib/components/UiTimeline/TimelineLoadingPlaceholderList.svelte';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import {
		useEnvironmentSettings,
		type TimelineAppearance,
	} from '$lib/environment/environmentSettings.svelte';

	const homeTimeline = useRetainedPresenter(
		'home:timeline-tabs',
		() => createHomeTimelineWithTabsPresenterController(),
		{ ttlMs: Infinity }
	);
	const environmentSettings = useEnvironmentSettings();
	let selectedTabId = $state<string | null>(null);
	let tabsElement = $state<HTMLDivElement | null>(null);
	let tabsExpanded = $state(false);
	let tabsOverflowing = $state(false);
	let selectedTimelineRefreshing = $state(false);
	let refreshRequestId = $state(0);
	const tabs = $derived(
		homeTimeline.tabState.type === 'Success' ? homeTimeline.tabState.data : []
	);
	const selectedTab = $derived(
		selectedTabId ? (tabs.find((tab) => tab.id === selectedTabId) ?? null) : null
	);
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const timelineAppearance = $derived<TimelineAppearance | null>(
		timelineAppearanceState.type === 'Success' ? timelineAppearanceState.data : null
	);
	const selectedTabAppearance = $derived<TimelineAppearance | null>(
		selectedTab && timelineAppearance
			? homeTimeline.resolveAppearance(selectedTab, timelineAppearance)
			: timelineAppearance
	);

	$effect(() => {
		if (tabs.length === 0) {
			selectedTabId = null;
			selectedTimelineRefreshing = false;
			return;
		}

		if (!selectedTabId || !tabs.some((tab) => tab.id === selectedTabId)) {
			selectedTabId = tabs[0].id;
			selectedTimelineRefreshing = false;
		}
	});

	$effect(() => {
		selectedTabId;
		selectedTimelineRefreshing = false;
	});

	$effect(() => {
		tabs.length;
		tabsExpanded;
		void tick().then(updateTabsOverflow);
	});

	$effect(() => {
		if (!tabsElement) return;

		const observer = new ResizeObserver(() => {
			updateTabsOverflow();
		});
		observer.observe(tabsElement);

		return () => {
			observer.disconnect();
		};
	});

	function updateTabsOverflow(): void {
		if (!tabsElement) return;

		const tabItems = Array.from(
			tabsElement.querySelectorAll<HTMLElement>('.home-tab, .home-tab-skeleton')
		);
		const styles = getComputedStyle(tabsElement);
		const columnGap = Number.parseFloat(styles.columnGap || styles.gap || '0') || 0;
		const tabsWidth = tabItems.reduce((total, item) => total + item.offsetWidth, 0);
		const totalWidth = tabsWidth + Math.max(0, tabItems.length - 1) * columnGap;
		const nextOverflowing = Math.ceil(totalWidth) > Math.floor(tabsElement.clientWidth);

		tabsOverflowing = nextOverflowing;
		if (!nextOverflowing && tabsExpanded) {
			tabsExpanded = false;
		}
	}

	function tabTitle(title: UiText): string {
		if (title.type === 'Raw') return title.string;
		return localizedUiString(title.string);
	}

	function tabIconName(tab: UiTimelineTabItem): string {
		switch (tab.icon.type) {
			case 'Material':
				return tab.icon.icon;
			case 'Mixed':
				return tab.icon.icon;
			default:
				return 'List';
		}
	}

	function tabImageSource(icon: IconType): string | null {
		switch (icon.type) {
			case 'Url':
				return icon.url;
			case 'FavIcon':
				return `https://icons.duckduckgo.com/ip3/${icon.host}.ico`;
			default:
				return null;
		}
	}

	function refreshSelectedTimeline(): void {
		refreshRequestId += 1;
	}
</script>

<div class="home-page">
	<AppTopBar>
		{#snippet start()}
			<div
				bind:this={tabsElement}
				class:home-tabs-expanded={tabsExpanded}
				class="tabs tabs-border home-tabs"
				role="tablist"
				aria-label={m.homeTabHomeTitle()}
			>
				{#if homeTimeline.tabState.type === 'Loading'}
					<div class="tab home-tab-skeleton">
						<div class="skeleton h-5 w-24"></div>
					</div>
				{:else if homeTimeline.tabState.type === 'Success'}
					{#each tabs as tab (tab.key)}
						{@const imageSource = tabImageSource(tab.icon)}
						<button
							class:tab-active={selectedTab?.id === tab.id}
							class="tab home-tab"
							role="tab"
							type="button"
							aria-selected={selectedTab?.id === tab.id}
							disabled={!tab.enabled}
							onclick={() => {
								selectedTabId = tab.id;
							}}
						>
							{#if imageSource}
								<img class="home-tab-icon" src={imageSource} alt="" loading="lazy" />
							{:else}
								<FaIcon name={tabIconName(tab)} size={15} />
							{/if}
							<span>{tabTitle(tab.title)}</span>
						</button>
					{/each}
				{/if}
			</div>
		{/snippet}

		{#snippet end()}
			{#if tabsOverflowing || tabsExpanded}
				<button
					class:swap-active={tabsExpanded}
					class="btn btn-ghost btn-square btn-sm swap swap-rotate rounded-box"
					type="button"
					aria-label={tabsExpanded ? m.tabsCollapse() : m.tabsExpand()}
					aria-expanded={tabsExpanded}
					title={tabsExpanded ? m.tabsCollapse() : m.tabsExpand()}
					onclick={() => {
						tabsExpanded = !tabsExpanded;
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

			<a
				class="btn btn-ghost btn-square btn-sm rounded-box"
				href="/tabs"
				aria-label={m.tabSettingsTitle()}
				title={m.tabSettingsTitle()}
			>
				<FaIcon name="Sliders" size={16} />
			</a>

			<button
				class="btn btn-ghost btn-square btn-sm rounded-box"
				type="button"
				aria-label={m.timelineRefresh()}
				title={m.timelineRefresh()}
				disabled={!selectedTab || selectedTimelineRefreshing}
				onclick={refreshSelectedTimeline}
			>
				<FaIcon name="Refresh" size={16} />
			</button>
		{/snippet}

		{#snippet bottom()}
			{#if selectedTimelineRefreshing}
				<progress class="progress progress-primary block h-0.5 w-full"></progress>
			{/if}
		{/snippet}
	</AppTopBar>

	<section class="home-content">
		{#if homeTimeline.tabState.type === 'Error'}
			<div class="home-state">
				<div class="alert alert-error">
					<span>{homeTimeline.tabState.message ?? m.timelineUnableToLoadTabs()}</span>
				</div>
			</div>
		{:else if homeTimeline.tabState.type === 'Success' && tabs.length === 0}
			<div class="home-empty">{m.timelineNoTimelines()}</div>
		{:else if selectedTab}
			{#key selectedTab.id}
				<HomeTimelineTabPanel
					tab={selectedTab}
					appearance={selectedTabAppearance}
					{refreshRequestId}
					onRefreshingChange={(isRefreshing) => {
						selectedTimelineRefreshing = isRefreshing;
					}}
				/>
			{/key}
		{:else}
			<TimelineLoadingPlaceholderList />
		{/if}
	</section>
</div>

<style>
	.home-page {
		display: grid;
		grid-template-rows: auto minmax(0, 1fr);
		min-height: 100vh;
		min-width: 0;
	}

	.home-tabs {
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

	.home-tabs-expanded {
		max-height: none;
		flex-wrap: wrap;
		overflow: visible;
	}

	:global(.home-page .app-top-bar) {
		align-items: flex-start;
	}

	:global(.home-page .app-top-bar-end) {
		align-items: flex-start;
		padding-top: 0.375rem;
	}

	.home-tabs::-webkit-scrollbar {
		display: none;
	}

	.home-tab {
		gap: 0.4rem;
		min-width: max-content;
		height: 2.75rem;
		padding-inline: 0.75rem;
		font-weight: 400;
		letter-spacing: 0;
	}

	.home-tab :global(.fa-icon) {
		color: currentColor;
	}

	.home-tab-icon {
		width: 1rem;
		height: 1rem;
		border-radius: 999px;
		object-fit: cover;
	}

	.home-tab-skeleton {
		height: 2.75rem;
	}

	.home-content {
		min-height: 0;
		min-width: 0;
	}

	.home-state {
		display: grid;
		gap: 0.5rem;
		min-width: 0;
		padding: 0.75rem 0;
	}

	.home-empty {
		display: grid;
		min-height: 12rem;
		place-items: center;
		color: color-mix(in oklab, var(--color-base-content) 56%, transparent);
		font-size: 0.9rem;
	}

	@media (max-width: 760px) {
		.home-page {
			min-height: calc(100vh - 4rem);
		}
	}
</style>
