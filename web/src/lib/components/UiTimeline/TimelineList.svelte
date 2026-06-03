<script lang="ts">
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { TimelineDisplayMode } from '$lib/environment/environmentSettings.svelte';
	import type { MicroBlogKey, PagingState, UiTimelineV2 } from '@flare/web-presenters/timeline.svelte';
	import PostLoadingPlaceholder from './post/PostLoadingPlaceholder.svelte';
	import { defaultTimelineAppearance } from './post/postUtils';
	import TimelineItem from './TimelineItem.svelte';
	import TimelineLoadingPlaceholderList from './TimelineLoadingPlaceholderList.svelte';

	let {
		listState,
		detailStatusKey = null,
	}: {
		listState: PagingState<UiTimelineV2>;
		detailStatusKey?: MicroBlogKey | null;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const contentPadding = 16;
	const cardItemGap = 8;
	const displayMode = $derived<TimelineDisplayMode>(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data.timelineDisplayMode
			: defaultTimelineAppearance.timelineDisplayMode
	);
	const galleryMode = $derived(displayMode === 'Gallery');
	const cardStyleItems = $derived(!galleryMode && displayMode !== 'Plain');
	const itemIndexes = $derived(
		listState.type === 'Success'
			? Array.from({ length: listState.itemCount }, (_, index) => index)
			: []
	);

	let requestedListKey = '';
	let requestedVisibleIndexes = new Set<number>();

	$effect(() => {
		const key = listState.type === 'Success' ? `success:${listState.itemCount}` : listState.type;
		if (key === requestedListKey) return;

		requestedListKey = key;
		requestedVisibleIndexes = new Set<number>();
	});

	function requestItem(index: number) {
		if (galleryMode || listState.type !== 'Success' || requestedVisibleIndexes.has(index)) {
			return;
		}

		requestedVisibleIndexes.add(index);
		listState.get(index);
	}

	function loadWhenVisible(node: HTMLElement, index: number) {
		let currentIndex = index;
		let observer: IntersectionObserver | null = null;

		if (typeof IntersectionObserver === 'undefined') {
			queueMicrotask(() => requestItem(currentIndex));
			return {
				update(nextIndex: number) {
					currentIndex = nextIndex;
					queueMicrotask(() => requestItem(currentIndex));
				},
			};
		}

		observer = new IntersectionObserver(
			(entries) => {
				if (entries.some((entry) => entry.isIntersecting)) {
					requestItem(currentIndex);
				}
			},
			{
				root: null,
				threshold: 0,
			}
		);
		observer.observe(node);

		return {
			update(nextIndex: number) {
				currentIndex = nextIndex;
			},
			destroy() {
				observer?.disconnect();
			},
		};
	}
</script>

<div
	class:card-mode={cardStyleItems}
	class:plain-mode={!cardStyleItems}
	class="timeline-list"
	style={`--timeline-content-padding: ${contentPadding}px; --timeline-card-gap: ${cardItemGap}px;`}
>
	{#if listState.type === 'Loading'}
		<TimelineLoadingPlaceholderList />
	{:else if listState.type === 'Error'}
		<div class="timeline-list-state">
			<div class="alert alert-error">
				<span>{listState.message ?? 'Unable to load timeline'}</span>
			</div>
		</div>
	{:else if listState.type === 'Empty'}
		<div class="timeline-list-empty">
			<span>Empty</span>
		</div>
	{:else if galleryMode}
		<div class="timeline-gallery-slot"></div>
	{:else}
		<div class="timeline-list-body">
			{#if listState.isRefreshing}
				<progress class="progress progress-primary timeline-progress"></progress>
			{/if}

			{#each itemIndexes as index (index)}
				{@const item = listState.peek(index)}
				<article
					class:card-row={cardStyleItems}
					class:plain-row={!cardStyleItems}
					class="timeline-row"
					use:loadWhenVisible={index}
				>
					<div class:rounded-box={cardStyleItems} class="timeline-row-content bg-base-100">
						{#if item}
							<TimelineItem {item} {detailStatusKey} />
						{:else}
							<PostLoadingPlaceholder />
						{/if}
					</div>

					{#if !cardStyleItems && index < listState.itemCount - 1}
						<div class="divider timeline-row-divider"></div>
					{/if}
				</article>
			{/each}

			{#if listState.appendState.type === 'Loading'}
				<div class="timeline-append-state">
					<progress class="progress progress-primary timeline-append-progress"></progress>
				</div>
			{:else if listState.appendState.type === 'Error'}
				<div class="timeline-append-state">
					<div class="alert alert-error timeline-append-error">
						<span>{listState.appendState.message ?? 'Unable to load more posts'}</span>
						<button class="btn btn-sm" type="button" onclick={() => listState.retry()}>Retry</button>
					</div>
				</div>
			{:else if listState.appendState.endOfPaginationReached}
				<div class="timeline-append-end">No more posts</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.timeline-list {
		min-height: 100%;
		min-width: 0;
	}

	.timeline-list.card-mode {
		background: var(--color-base-200);
	}

	.timeline-list.plain-mode {
		background: var(--color-base-100);
	}

	.timeline-list-state {
		display: grid;
		min-width: 0;
		gap: 0.5rem;
		padding: var(--timeline-content-padding);
	}

	.timeline-list-empty {
		display: grid;
		min-height: 12rem;
		place-items: center;
		padding: var(--timeline-content-padding);
		color: color-mix(in oklab, var(--color-base-content) 56%, transparent);
		font-size: 0.9rem;
	}

	.timeline-gallery-slot {
		min-height: 100%;
		min-width: 0;
		background: var(--color-base-100);
	}

	.timeline-list-body {
		position: relative;
		min-width: 0;
		min-height: 100%;
	}

	.card-mode .timeline-list-body {
		display: grid;
		gap: var(--timeline-card-gap);
		padding: var(--timeline-content-padding);
		background: var(--color-base-200);
	}

	.plain-mode .timeline-list-body {
		background: var(--color-base-100);
	}

	.timeline-progress {
		position: sticky;
		top: 0;
		z-index: 2;
		display: block;
		width: 100%;
		height: 0.15rem;
	}

	.timeline-row {
		min-width: 0;
	}

	.timeline-row-content {
		min-width: 0;
		width: 100%;
		overflow: hidden;
	}

	.timeline-row-divider {
		--divider-m: 0;
	}

	.timeline-append-state {
		min-width: 0;
		padding: 0.5rem 0;
	}

	.plain-mode .timeline-append-state {
		padding-inline: var(--timeline-content-padding);
	}

	.timeline-append-progress {
		display: block;
		width: 100%;
		height: 0.15rem;
	}

	.timeline-append-error {
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
	}

	.timeline-append-end {
		min-width: 0;
		padding: 0.5rem var(--timeline-content-padding);
		text-align: center;
		color: color-mix(in oklab, var(--color-base-content) 56%, transparent);
		font-size: 0.9rem;
	}
</style>
