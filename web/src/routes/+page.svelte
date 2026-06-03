<script lang="ts">
	import { createHomeTimelineWithTabsPresenterController } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import type {
		IconType,
		TimelineTabItemV2,
		UiStrings,
		UiText,
	} from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import HomeTimelineTabPanel from '$lib/components/home/HomeTimelineTabPanel.svelte';
	import TimelineLoadingPlaceholderList from '$lib/components/UiTimeline/TimelineLoadingPlaceholderList.svelte';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';

	const homeTimeline = useRetainedPresenter(
		'home:timeline-tabs',
		() => createHomeTimelineWithTabsPresenterController(),
		{ ttlMs: Infinity }
	);
	let selectedTabId = $state<string | null>(null);
	const tabs = $derived(
		homeTimeline.tabState.type === 'Success' ? homeTimeline.tabState.data : []
	);
	const selectedTab = $derived(
		tabs.find((tab) => tab.id === selectedTabId) ?? tabs[0] ?? null
	);

	$effect(() => {
		if (tabs.length === 0) {
			selectedTabId = null;
			return;
		}

		if (!selectedTabId || !tabs.some((tab) => tab.id === selectedTabId)) {
			selectedTabId = tabs[0].id;
		}
	});

	function tabTitle(title: UiText): string {
		if (title.type === 'Raw') return title.string;
		return localizedUiString(title.string);
	}

	function localizedUiString(value: UiStrings): string {
		switch (value) {
			case 'Home':
				return 'Home';
			case 'Notifications':
				return 'Notifications';
			case 'Discover':
				return 'Discover';
			case 'Me':
				return 'Me';
			case 'Settings':
				return 'Settings';
			case 'MastodonLocal':
				return 'Local';
			case 'MastodonPublic':
				return 'Public';
			case 'Featured':
				return 'Featured';
			case 'Bookmark':
				return 'Bookmarks';
			case 'Favourite':
				return 'Favorites';
			case 'List':
				return 'List';
			case 'Feeds':
				return 'Feeds';
			case 'DirectMessage':
				return 'Messages';
			case 'Rss':
				return 'RSS';
			case 'Antenna':
				return 'Antenna';
			case 'MixedTimeline':
				return 'Mixed';
			case 'Social':
				return 'Social';
			case 'Liked':
				return 'Liked';
			case 'AllRssFeeds':
				return 'All feeds';
			case 'Posts':
				return 'Posts';
			case 'Channel':
				return 'Channel';
			case 'Default':
				return 'Default';
			case 'Login':
				return 'Login';
			case 'Verify':
				return 'Verify';
			case 'Cancel':
				return 'Cancel';
			case 'Next':
				return 'Next';
			case 'Username':
				return 'Username';
			case 'Password':
				return 'Password';
			case 'Otp':
				return 'OTP';
			case 'OAuthLogin':
				return 'OAuth';
			case 'PasswordLogin':
				return 'Password';
			case 'QrConnect':
				return 'QR connect';
			case 'CredentialImport':
				return 'Credential import';
			case 'ExternalSigner':
				return 'External signer';
			case 'WebCookieLogin':
				return 'Web cookie';
			case 'NostrLoginAccount':
				return 'Nostr';
		}
	}

	function tabIconName(tab: TimelineTabItemV2): string {
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
</script>

<div class="home-page">
	<AppTopBar>
		{#snippet start()}
			<div class="tabs tabs-border home-tabs" role="tablist" aria-label="Home timelines">
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
			<button
				class="btn btn-ghost btn-square btn-sm rounded-box"
				type="button"
				aria-label="Edit tabs"
				title="Edit tabs"
			>
				<FaIcon name="Sliders" size={16} />
			</button>
		{/snippet}
	</AppTopBar>

	<section class="home-content">
		{#if homeTimeline.tabState.type === 'Error'}
			<div class="home-state">
				<div class="alert alert-error">
					<span>{homeTimeline.tabState.message ?? 'Unable to load tabs'}</span>
				</div>
			</div>
		{:else if homeTimeline.tabState.type === 'Success' && tabs.length === 0}
			<div class="home-empty">No timelines</div>
		{:else if selectedTab}
			{#key selectedTab.id}
				<HomeTimelineTabPanel tab={selectedTab} />
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
		min-width: 0;
		overflow-x: auto;
		overflow-y: hidden;
		scrollbar-width: none;
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
