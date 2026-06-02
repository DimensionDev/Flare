<script lang="ts">
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { TimelineDisplayMode } from '$lib/environment/environmentSettings.svelte';
	import PostLoadingPlaceholder from './post/PostLoadingPlaceholder.svelte';
	import { defaultTimelineAppearance } from './post/postUtils';

	let {
		count = 8,
	}: {
		count?: number;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const contentPadding = 16;
	const cardItemGap = 8;
	const placeholderIndexes = $derived(Array.from({ length: count }, (_, index) => index));
	const displayMode = $derived<TimelineDisplayMode>(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data.timelineDisplayMode
			: defaultTimelineAppearance.timelineDisplayMode
	);
	const galleryMode = $derived(displayMode === 'Gallery');
	const cardStyleItems = $derived(!galleryMode && displayMode !== 'Plain');
</script>

<div
	class:card-mode={cardStyleItems}
	class:plain-mode={!cardStyleItems}
	class="timeline-loading-placeholder-list"
	style={`--timeline-content-padding: ${contentPadding}px; --timeline-card-gap: ${cardItemGap}px;`}
>
	<div class="timeline-placeholder-body">
		{#each placeholderIndexes as index (index)}
			<article class:card-row={cardStyleItems} class:plain-row={!cardStyleItems} class="timeline-placeholder-row">
				<div class:rounded-box={cardStyleItems} class="timeline-placeholder-content bg-base-100">
					<PostLoadingPlaceholder />
				</div>

				{#if !cardStyleItems && index < placeholderIndexes.length - 1}
					<div class="divider timeline-placeholder-divider"></div>
				{/if}
			</article>
		{/each}
	</div>
</div>

<style>
	.timeline-loading-placeholder-list {
		min-height: 100%;
		min-width: 0;
	}

	.timeline-loading-placeholder-list.card-mode {
		background: var(--color-base-200);
	}

	.timeline-loading-placeholder-list.plain-mode {
		background: var(--color-base-100);
	}

	.timeline-placeholder-body {
		min-width: 0;
		min-height: 100%;
	}

	.card-mode .timeline-placeholder-body {
		display: grid;
		gap: var(--timeline-card-gap);
		padding: var(--timeline-content-padding);
		background: var(--color-base-200);
	}

	.plain-mode .timeline-placeholder-body {
		background: var(--color-base-100);
	}

	.timeline-placeholder-row {
		min-width: 0;
	}

	.timeline-placeholder-content {
		min-width: 0;
		width: 100%;
		overflow: hidden;
	}

	.timeline-placeholder-divider {
		--divider-m: 0;
		margin: 0;
	}
</style>
