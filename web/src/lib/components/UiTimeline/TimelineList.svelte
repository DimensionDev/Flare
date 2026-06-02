<script lang="ts">
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { TimelineDisplayMode } from '$lib/environment/environmentSettings.svelte';
	import type { PagingState, UiTimelineV2 } from '@flare/web-presenters/timeline.svelte';
	import PostLoadingPlaceholder from './post/PostLoadingPlaceholder.svelte';
	import TimelineItem from './TimelineItem.svelte';

	let {
		listState,
	}: {
		listState: PagingState<UiTimelineV2>;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const contentPadding = 16;
	const cardItemGap = 8;
	const loadingPlaceholderIndexes = Array.from({ length: 8 }, (_, index) => index);
	const displayMode = $derived<TimelineDisplayMode>(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data.timelineDisplayMode
			: 'Plain'
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
		<div class="timeline-list-state">
			{#each loadingPlaceholderIndexes as index (index)}
				<PostLoadingPlaceholder />
			{/each}
		</div>
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
							<TimelineItem {item} />
						{:else}
							<PostLoadingPlaceholder />
						{/if}
					</div>

					{#if !cardStyleItems && index < listState.itemCount - 1}
						<div class="divider timeline-row-divider"></div>
					{/if}
				</article>
			{/each}
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
</style>
