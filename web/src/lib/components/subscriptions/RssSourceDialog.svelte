<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { untrack } from 'svelte';
	import {
		createEditRssSourcePresenter,
		type CheckRssSourcePresenterStateRssState,
		type RssDisplayMode,
		type SubscriptionType,
		type UiRssSource,
	} from '@flare/web-presenters/editRssSource.svelte';

	const publicRssHubServers = [
		'https://rsshub.rssforever.com',
		'https://hub.slarker.me',
		'https://rsshub.pseudoyu.com',
	];

	type SelectOption<T extends string> = {
		label: string;
		value: T;
	};

	let {
		source,
		onClose,
	}: {
		source: UiRssSource | null;
		onClose: () => void;
	} = $props();

	const initialSource = untrack(() => source);
	const editor = createEditRssSourcePresenter(initialSource?.id ?? null);
	let url = $state(initialSource?.url ?? '');
	let title = $state(initialSource?.title ?? '');
	let displayMode = $state<RssDisplayMode>(initialSource?.displayMode ?? 'FULL_CONTENT');
	let rssHubServer = $state('');
	let selectedUrls = $state<Set<string>>(new Set());
	let selectedTypes = $state<Set<SubscriptionType>>(new Set());
	const checkState = $derived(editor.checkState);
	const rssState = $derived(checkState.type === 'Success' ? checkState.data : null);
	const rssHubCheckState = $derived(editor.rssHubCheckState);
	const rssHubFeed = $derived(
		rssHubCheckState?.type === 'Success' && rssHubCheckState.data.type === 'RssFeed'
			? rssHubCheckState.data
			: null
	);
	const titleFeed = $derived(
		rssState?.type === 'RssFeed' ? rssState : rssHubFeed
	);
	const showDisplayMode = $derived(rssState?.type !== 'SubscriptionInstance');
	const canSave = $derived(canSaveCurrent());

	const displayModeOptions = $derived<SelectOption<RssDisplayMode>[]>([
		{ label: m.rssSourcesFullContent(), value: 'FULL_CONTENT' },
		{ label: m.rssSourcesOpenInBrowser(), value: 'OPEN_IN_BROWSER' },
		{ label: m.rssSourcesDescriptionOnly(), value: 'DESCRIPTION_ONLY' },
	]);

	$effect(() => {
		const value = url.trim();
		const timer = window.setTimeout(() => {
			editor.checkUrl(value);
		}, 666);
		return () => window.clearTimeout(timer);
	});

	$effect(() => {
		if (title || !titleFeed) return;
		title = titleFeed.title;
	});

	$effect(() => {
		const value = rssHubServer.trim();
		const timer = window.setTimeout(() => {
			editor.checkRssHubServer(value);
		}, 666);
		return () => window.clearTimeout(timer);
	});

	$effect(() => {
		if (rssState?.type === 'RssSources' && selectedUrls.size === 0) {
			selectedUrls = new Set(rssState.sources.map((item) => item.url));
		}
	});

	$effect(() => {
		if (rssState?.type === 'SubscriptionInstance' && selectedTypes.size === 0) {
			selectedTypes = new Set(rssState.availableTimelines);
		}
	});

	function selectValue<T extends string>(event: Event): T {
		return (event.currentTarget as HTMLSelectElement).value as T;
	}

	function toggleUrl(value: string): void {
		const next = new Set(selectedUrls);
		if (next.has(value)) {
			next.delete(value);
		} else {
			next.add(value);
		}
		selectedUrls = next;
	}

	function toggleType(value: SubscriptionType): void {
		const next = new Set(selectedTypes);
		if (next.has(value)) {
			next.delete(value);
		} else {
			next.add(value);
		}
		selectedTypes = next;
	}

	function timelineLabel(value: SubscriptionType): string {
		switch (value) {
			case 'MASTODON_TRENDS':
				return m.mastodonTrendingStatuses();
			case 'MASTODON_PUBLIC':
				return m.mastodonFederatedTimeline();
			case 'MASTODON_LOCAL':
				return m.mastodonLocalTimeline();
			default:
				return value;
		}
	}

	function canSaveCurrent(): boolean {
		if (!rssState) return false;
		switch (rssState.type) {
			case 'RssFeed':
				return true;
			case 'RssHub':
				return rssHubFeed !== null;
			case 'RssSources':
				return selectedUrls.size > 0;
			case 'SubscriptionInstance':
				return selectedTypes.size > 0;
		}
	}

	function save(): void {
		if (!rssState || !canSave) return;
		switch (rssState.type) {
			case 'RssFeed':
				editor.saveRssFeed(title, displayMode);
				break;
			case 'RssHub':
				editor.saveRssHub(title, displayMode);
				break;
			case 'RssSources':
				editor.saveSourcesByUrl(Array.from(selectedUrls).join(','), displayMode);
				break;
			case 'SubscriptionInstance':
				editor.saveSubscriptionTypes(Array.from(selectedTypes).join(','));
				break;
		}
		onClose();
	}

	function stateIcon(state: CheckRssSourcePresenterStateRssState | null): 'Check' | 'ChevronDown' | null {
		if (!state) return null;
		switch (state.type) {
			case 'RssFeed':
			case 'SubscriptionInstance':
				return 'Check';
			case 'RssHub':
			case 'RssSources':
				return 'ChevronDown';
		}
	}
</script>

<div class="modal modal-open" role="dialog" aria-modal="true">
	<div class="modal-box max-w-2xl rounded-box">
		<h2 class="modal-title">{source ? m.editRssSource() : m.addRssSource()}</h2>
		<div class="form-grid">
			<label class="form-control">
				<span class="label-text">{m.rssSourcesUrlLabel()}</span>
				<div class="input input-bordered flex w-full items-center gap-2">
					<input class="grow" type="url" bind:value={url} />
					{#if checkState.type === 'Loading' && url.trim()}
						<span class="loading loading-spinner loading-sm"></span>
					{:else if checkState.type === 'Error'}
						<FaIcon name="CircleExclamation" size={18} />
					{:else if checkState.type === 'Success'}
						{@const icon = stateIcon(checkState.data)}
						{#if icon}
							<FaIcon name={icon} size={18} />
						{/if}
					{/if}
				</div>
				<span class="label-text-alt">{m.subscriptionUrlHint()}</span>
			</label>

			{#if rssState?.type === 'RssHub'}
				<label class="form-control">
					<span class="label-text">{m.rssSourcesRssHubHostLabel()}</span>
					<div class="input input-bordered flex w-full items-center gap-2">
						<input class="grow" type="url" bind:value={rssHubServer} />
						{#if rssHubCheckState?.type === 'Loading' && rssHubServer.trim()}
							<span class="loading loading-spinner loading-sm"></span>
						{:else if rssHubFeed}
							<FaIcon name="Check" size={18} />
						{:else if rssHubCheckState?.type === 'Error'}
							<FaIcon name="CircleExclamation" size={18} />
						{/if}
					</div>
					<span class="label-text-alt">{m.rssSourcesRssHubHostHint()}</span>
				</label>
				<div class="join join-vertical">
					{#each publicRssHubServers as server}
						<button class="btn join-item justify-start" type="button" onclick={() => (rssHubServer = server)}>
							{server}
						</button>
					{/each}
				</div>
			{/if}

			{#if titleFeed}
				<label class="form-control">
					<span class="label-text">{m.rssSourcesTitleLabel()}</span>
					<div class="input input-bordered flex w-full items-center gap-2">
						{#if titleFeed.icon}
							<img class="field-icon" src={titleFeed.icon} alt="" loading="lazy" />
						{/if}
						<input class="grow" type="text" bind:value={title} />
					</div>
				</label>
			{/if}

			{#if rssState?.type === 'RssSources'}
				<div class="section-title">{m.rssSourcesDiscoveredRssSources()}</div>
				<div class="list rounded-box border border-base-300 bg-base-100">
					{#each rssState.sources as discovered (discovered.url)}
						<label class="list-row option-row">
							<span class="source-icon bg-base-200">
								{#if discovered.favIcon}
									<img src={discovered.favIcon} alt="" loading="lazy" />
								{:else}
									<FaIcon name="Rss" size={16} />
								{/if}
							</span>
							<span class="source-copy">
								<span class="source-title">{discovered.title ?? discovered.url}</span>
								<span class="source-url">{discovered.url}</span>
							</span>
							<input
								class="checkbox checkbox-primary"
								type="checkbox"
								checked={selectedUrls.has(discovered.url)}
								onchange={() => toggleUrl(discovered.url)}
							/>
						</label>
					{/each}
				</div>
			{/if}

			{#if rssState?.type === 'SubscriptionInstance'}
				<div class="section-title">{m.mastodonAvailableTimelines()}</div>
				<div class="list rounded-box border border-base-300 bg-base-100">
					{#each rssState.availableTimelines as timeline (timeline)}
						<label class="list-row option-row">
							<span class="source-icon bg-base-200">
								{#if rssState.icon}
									<img src={rssState.icon} alt="" loading="lazy" />
								{:else}
									<FaIcon name="Mastodon" size={16} />
								{/if}
							</span>
							<span class="source-copy">
								<span class="source-title">{timelineLabel(timeline)}</span>
							</span>
							<input
								class="checkbox checkbox-primary"
								type="checkbox"
								checked={selectedTypes.has(timeline)}
								onchange={() => toggleType(timeline)}
							/>
						</label>
					{/each}
				</div>
			{/if}

			{#if (showDisplayMode && rssState && rssState.type !== 'RssHub') || rssHubFeed}
				<label class="form-control">
					<span class="label-text">{m.rssSourcesDisplayMode()}</span>
					<select
						class="select select-bordered w-full"
						value={displayMode}
						onchange={(event) => (displayMode = selectValue<RssDisplayMode>(event))}
					>
						{#each displayModeOptions as option}
							<option value={option.value}>{option.label}</option>
						{/each}
					</select>
				</label>
			{/if}
		</div>
		<div class="modal-action">
			<button class="btn btn-ghost" type="button" onclick={onClose}>{m.loginCancel()}</button>
			<button class="btn btn-primary" type="button" disabled={!canSave} onclick={save}>
				{m.rssSourcesSave()}
			</button>
		</div>
	</div>
	<button class="modal-backdrop" type="button" aria-label={m.loginCancel()} onclick={onClose}></button>
</div>

<style>
	.modal-title,
	.form-grid {
		display: grid;
		gap: 0.75rem;
	}

	.modal-title {
		margin: 0 0 1rem;
		font-size: 1.05rem;
		font-weight: 700;
	}

	.field-icon,
	.source-icon {
		display: grid;
		width: 1.75rem;
		height: 1.75rem;
		place-items: center;
		overflow: hidden;
		border-radius: var(--radius-box);
	}

	.field-icon,
	.source-icon img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.option-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4rem;
	}

	.source-copy {
		display: grid;
		gap: 0.15rem;
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

	.section-title {
		font-size: 0.82rem;
		font-weight: 650;
		color: color-mix(in oklab, var(--color-base-content) 72%, transparent);
	}
</style>
