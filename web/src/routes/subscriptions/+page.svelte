<script lang="ts">
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import RssSourceDialog from '$lib/components/subscriptions/RssSourceDialog.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createRssSourcesPresenter,
		type RssDisplayMode,
		type SubscriptionType,
		type UiRssSource,
	} from '@flare/web-presenters/rssSources.svelte';

	type SelectOption<T extends string> = {
		label: string;
		value: T;
	};

	const subscriptions = createRssSourcesPresenter();
	let editing = $state<UiRssSource | null>(null);
	let formOpen = $state(false);

	const displayModeOptions = $derived<SelectOption<RssDisplayMode>[]>([
		{ label: m.rssSourcesFullContent(), value: 'FULL_CONTENT' },
		{ label: m.rssSourcesOpenInBrowser(), value: 'OPEN_IN_BROWSER' },
		{ label: m.rssSourcesDescriptionOnly(), value: 'DESCRIPTION_ONLY' },
	]);
	const typeOptions = $derived<SelectOption<SubscriptionType>[]>([
		{ label: 'RSS', value: 'RSS' },
		{ label: m.mastodonTrendingStatuses(), value: 'MASTODON_TRENDS' },
		{ label: m.mastodonFederatedTimeline(), value: 'MASTODON_PUBLIC' },
		{ label: m.mastodonLocalTimeline(), value: 'MASTODON_LOCAL' },
	]);

	function openAdd(): void {
		editing = null;
		formOpen = true;
	}

	function openEdit(source: UiRssSource): void {
		editing = source;
		formOpen = true;
	}

	function closeForm(): void {
		formOpen = false;
		editing = null;
	}

	function sourceTitle(source: UiRssSource): string {
		return source.title || source.url;
	}

	function typeLabel(value: SubscriptionType): string {
		return typeOptions.find((item) => item.value === value)?.label ?? value;
	}

	function displayModeLabel(value: RssDisplayMode): string {
		return displayModeOptions.find((item) => item.value === value)?.label ?? value;
	}
</script>

<svelte:head>
	<title>{m.rssSourcesTitle()} | Flare</title>
</svelte:head>

<div class="subscriptions-page bg-base-200">
	<AppTopBar title={m.rssSourcesTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}
		{#snippet end()}
			<button class="btn btn-primary btn-sm" type="button" onclick={openAdd}>
				<FaIcon name="Plus" size={13} />
				<span>{m.addRssSource()}</span>
			</button>
		{/snippet}
	</AppTopBar>

	<div class="page-content">
		<div class="list rounded-box border border-base-300 bg-base-100">
			{#if subscriptions.sources.length === 0}
				<div class="list-row empty-row">
					<FaIcon name="Rss" size={22} />
					<span>{m.emptyRssSources()}</span>
				</div>
			{:else}
				{#each subscriptions.sources as source (source.id)}
					<div class="list-row source-row">
						<span class="source-icon bg-base-200">
							{#if source.favIcon}
								<img src={source.favIcon} alt="" loading="lazy" />
							{:else}
								<FaIcon name="Rss" size={17} />
							{/if}
						</span>
						<span class="source-copy">
							<span class="source-title">{sourceTitle(source)}</span>
							<span class="source-url">{source.url}</span>
							<span class="source-tags">
								<span class="badge badge-ghost badge-sm">{typeLabel(source.type)}</span>
								<span class="badge badge-ghost badge-sm">{displayModeLabel(source.displayMode)}</span>
							</span>
						</span>
						<span class="row-actions">
							<button
								class="btn btn-ghost btn-square btn-sm"
								type="button"
								aria-label={m.editRssSource()}
								onclick={() => openEdit(source)}
							>
								<FaIcon name="PenToSquare" size={14} />
							</button>
							<button
								class="btn btn-ghost btn-square btn-sm text-error"
								type="button"
								aria-label={m.deleteRssSource()}
								onclick={() => subscriptions.delete(source.id)}
							>
								<FaIcon name="Delete" size={14} />
							</button>
						</span>
					</div>
				{/each}
			{/if}
		</div>
	</div>

	{#if formOpen}
		<RssSourceDialog source={editing} onClose={closeForm} />
	{/if}
</div>

<style>
	.subscriptions-page {
		min-height: 100vh;
	}

	.page-content {
		padding: 1rem;
	}

	.empty-row {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.6rem;
		min-height: 10rem;
		color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
	}

	.source-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.7rem;
		padding: 0.8rem 1rem;
	}

	.source-icon {
		display: grid;
		width: 2.25rem;
		height: 2.25rem;
		place-items: center;
		overflow: hidden;
		border-radius: var(--radius-box);
	}

	.source-icon img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.source-copy {
		display: grid;
		gap: 0.2rem;
		min-width: 0;
	}

	.source-title {
		font-weight: 650;
		overflow-wrap: anywhere;
	}

	.source-url {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.8rem;
		overflow-wrap: anywhere;
	}

	.source-tags,
	.row-actions {
		display: flex;
		align-items: center;
		gap: 0.45rem;
	}

	.source-tags {
		flex-wrap: wrap;
	}

	@media (max-width: 560px) {
		.source-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.row-actions {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
