<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import TimelineView from '$lib/components/TimelineView.svelte';
	import {
		createHomeTimelineWithTabsPresenter,
		type IconType,
		type TimelineTabItemV2,
		type UiText,
	} from '@flare/web-presenters/homeTimelineWithTabs.svelte';

	const home = createHomeTimelineWithTabsPresenter();
	let selectedTabId = $state<string | null>(null);

	const tabs = $derived(
		home.tabState.type === 'Success' ? home.tabState.data.filter((tab) => tab.enabled) : []
	);
	const selectedTab = $derived(
		tabs.find((tab) => tab.id === selectedTabId) ?? tabs[0] ?? null
	);

	$effect(() => {
		if (tabs.length === 0) {
			selectedTabId = null;
			return;
		}
		if (!tabs.some((tab) => tab.id === selectedTabId)) {
			selectedTabId = tabs[0].id;
		}
	});

	const localizedText: Partial<Record<string, string>> = {
		Home: 'Home',
		Notifications: 'Notifications',
		Discover: 'Discover',
		Me: 'Me',
		Settings: 'Settings',
		MastodonLocal: 'Local',
		MastodonPublic: 'Federated',
		Featured: 'Featured',
		Bookmark: 'Bookmarks',
		Favourite: 'Favourites',
		List: 'List',
		Feeds: 'Feeds',
		DirectMessage: 'Messages',
		Rss: 'RSS',
		Antenna: 'Antenna',
		MixedTimeline: 'Mixed',
		Social: 'Social',
		Liked: 'Liked',
		AllRssFeeds: 'All feeds',
		Posts: 'Posts',
		Channel: 'Channel',
		Default: 'Default',
	};

	function textLabel(text: UiText): string {
		return text.type === 'Raw' ? text.string : (localizedText[text.string] ?? text.string);
	}

	function iconName(icon: IconType): string {
		switch (icon.type) {
			case 'Material':
			case 'Mixed':
				return icon.icon;
			case 'FavIcon':
				return 'Rss';
			case 'Avatar':
				return 'Profile';
			case 'Url':
				return 'World';
		}
	}

	function tabLabel(tab: TimelineTabItemV2): string {
		return textLabel(tab.title);
	}

	const sidebarItems = [
		{ icon: 'Home', label: 'Home', active: true },
		{ icon: 'Notification', label: 'Notifications', active: false },
		{ icon: 'Search', label: 'Search', active: false },
		{ icon: 'Settings', label: 'Settings', active: false, bottom: true },
	];
</script>

<svelte:head>
	<title>Flare</title>
</svelte:head>

<main class="app-shell">
	<aside class="rail" aria-label="Primary navigation">
		{#each sidebarItems as item}
			<button
				class:active={item.active}
				class:bottom={item.bottom}
				class="rail-button"
				type="button"
				title={item.label}
				aria-label={item.label}
			>
				<FaIcon name={item.icon} size={20} />
			</button>
		{/each}
	</aside>

	<section class="home" aria-label="Home timeline">
		<header class="topbar">
			<div class="tab-strip" aria-label="Timeline tabs">
				{#if home.tabState.type === 'Success'}
					{#each tabs as tab (tab.id)}
						<button
							class:active={selectedTab?.id === tab.id}
							class="tab-chip"
							type="button"
							title={tabLabel(tab)}
							onclick={() => (selectedTabId = tab.id)}
						>
							<FaIcon name={iconName(tab.icon)} size={15} />
							<span>{tabLabel(tab)}</span>
						</button>
					{/each}
				{:else if home.tabState.type === 'Error'}
					<div class="topbar-state">{home.tabState.message ?? 'Failed to load tabs'}</div>
				{:else}
					<div class="topbar-state">Loading</div>
				{/if}
			</div>
			<button class="icon-button" type="button" title="Tab settings" aria-label="Tab settings">
				<FaIcon name="Sliders" size={17} />
			</button>
		</header>

		<div class="timeline-frame">
			{#if selectedTab}
				{#key selectedTab.id}
					<TimelineView tab={selectedTab} />
				{/key}
			{:else if home.tabState.type === 'Success'}
				<div class="empty-state">No timeline tabs</div>
			{:else if home.tabState.type === 'Error'}
				<div class="empty-state">{home.tabState.message ?? 'Failed to load home'}</div>
			{:else}
				<div class="empty-state">Loading</div>
			{/if}
		</div>
	</section>
</main>

<style>
	:global(body) {
		margin: 0;
		font-family:
			Inter,
			ui-sans-serif,
			system-ui,
			-apple-system,
			BlinkMacSystemFont,
			'Segoe UI',
			sans-serif;
		background: #f3f5f8;
		color: #17202c;
	}

	:global(button) {
		font: inherit;
	}

	.app-shell {
		display: grid;
		grid-template-columns: 72px minmax(0, 1fr);
		min-height: 100vh;
		background: #f3f5f8;
	}

	.home {
		min-width: 0;
		display: grid;
		grid-template-rows: auto minmax(0, 1fr);
	}

	.topbar {
		position: sticky;
		top: 0;
		z-index: 5;
		display: grid;
		grid-template-columns: minmax(0, 1fr) 44px;
		gap: 12px;
		align-items: center;
		min-height: 64px;
		border-bottom: 1px solid #d9dee8;
		background: rgb(255 255 255 / 92%);
		backdrop-filter: blur(12px);
		padding: 10px 18px;
		box-sizing: border-box;
	}

	.tab-strip {
		min-width: 0;
		display: flex;
		gap: 8px;
		overflow-x: auto;
		scrollbar-width: none;
	}

	.tab-strip::-webkit-scrollbar {
		display: none;
	}

	.tab-chip,
	.icon-button,
	.rail-button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border: 0;
		cursor: pointer;
	}

	.tab-chip {
		flex: 0 0 auto;
		height: 38px;
		max-width: 220px;
		gap: 8px;
		border-radius: 999px;
		background: #eef2f7;
		color: #344054;
		padding: 0 14px;
	}

	.tab-chip.active {
		background: #17202c;
		color: #ffffff;
	}

	.tab-chip span {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 0.92rem;
		font-weight: 650;
	}

	.topbar-state {
		display: inline-flex;
		align-items: center;
		height: 38px;
		color: #667085;
		font-size: 0.92rem;
	}

	.icon-button {
		width: 40px;
		height: 40px;
		border-radius: 50%;
		background: #eef2f7;
		color: #344054;
	}

	.icon-button:hover,
	.rail-button:hover {
		background: #e3e8ef;
	}

	.timeline-frame {
		min-width: 0;
		width: min(100%, 760px);
		min-height: calc(100vh - 64px);
		margin: 0 auto;
		border-right: 1px solid #d9dee8;
		border-left: 1px solid #d9dee8;
		background: #ffffff;
	}

	.empty-state {
		display: grid;
		min-height: 240px;
		place-items: center;
		color: #667085;
	}

	.rail {
		position: sticky;
		top: 0;
		height: 100vh;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		border-right: 1px solid #d9dee8;
		background: #ffffff;
		padding: 14px 10px;
		box-sizing: border-box;
	}

	.rail-button {
		width: 48px;
		height: 48px;
		border-radius: 50%;
		background: transparent;
		color: #475467;
	}

	.rail-button.active {
		background: #17202c;
		color: #ffffff;
	}

	.rail-button.bottom {
		margin-top: auto;
	}

	@media (max-width: 720px) {
		.app-shell {
			grid-template-columns: 58px minmax(0, 1fr);
		}

		.topbar {
			min-height: 58px;
			padding: 9px 12px;
		}

		.tab-chip {
			height: 36px;
			max-width: 170px;
			padding: 0 12px;
		}

		.timeline-frame {
			width: 100%;
			border-left: 0;
		}

		.rail {
			padding: 10px 6px;
		}

		.rail-button {
			width: 44px;
			height: 44px;
		}
	}
</style>
