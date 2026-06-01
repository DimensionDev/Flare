<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2 } from '@flare/web-presenters/timeline.svelte';
	import { defaultTimelineAppearance, translationLabel } from './post/postUtils';

	type UiTimelineFeed = Extract<UiTimelineV2, { type: 'Feed' }>;

	let {
		feed,
	}: {
		feed: UiTimelineFeed;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const deepLink = useDeepLink();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const appearance = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data
			: defaultTimelineAppearance
	);
	const descriptionText = $derived(feed.description?.trim() ? feed.description : null);
	const titleText = $derived(feed.title?.trim() ? feed.title : null);
	const timeText = $derived(
		appearance.absoluteTimestamp ? feed.createdAt.absolute : feed.createdAt.relative
	);
	const showTime = $derived(Boolean(timeText?.trim()));
	const mediaPreview = $derived(feed.media?.previewUrl || feed.media?.url || null);
	const feedClickable = $derived(deepLink.canPerformClickEvent(feed.clickEvent));

	function performFeedClick(event: MouseEvent): void {
		if (!feedClickable) return;

		event.stopPropagation();
		deepLink.performClickEvent(feed.clickEvent);
	}

	function performFeedKeydown(event: KeyboardEvent): void {
		if (!feedClickable || event.defaultPrevented || event.key !== 'Enter') return;
		if (event.target !== event.currentTarget) return;

		event.preventDefault();
		event.stopPropagation();
		deepLink.performClickEvent(feed.clickEvent);
	}
</script>

<div
	class:clickable={feedClickable}
	class="timeline-feed"
	role="link"
	aria-disabled={feedClickable ? undefined : true}
	tabindex={feedClickable ? 0 : undefined}
	onclick={performFeedClick}
	onkeydown={performFeedKeydown}
>
	<header class="feed-header">
		<div class="source-line">
			{#if feed.source.icon}
				<img class="source-icon" src={feed.source.icon} alt="" loading="lazy" />
			{/if}
			<span class="source-name">{feed.source.name}</span>
		</div>

		{#if (appearance.showTranslateButton && feed.translationDisplayState !== 'Hidden') || showTime}
			<div class="feed-meta">
				{#if appearance.showTranslateButton && feed.translationDisplayState !== 'Hidden'}
					<span class="meta-icon translation-meta" title={translationLabel(feed.translationDisplayState)}>
						<FaIcon name="Translate" size={16} />
						{#if feed.translationDisplayState === 'Translating'}
							<span class="loading loading-spinner loading-xs" aria-hidden="true"></span>
						{:else if feed.translationDisplayState === 'Failed'}
							<FaIcon name="CircleExclamation" size={12} />
						{/if}
					</span>
				{/if}
				{#if showTime}
					<time datetime={feed.createdAt.full} title={feed.createdAt.full}>
						{timeText}
					</time>
				{/if}
			</div>
		{/if}
	</header>

	{#if titleText}
		<h2 class="feed-title">{titleText}</h2>
	{/if}

	{#if descriptionText || mediaPreview}
		<div class:media-only={!descriptionText && mediaPreview} class="feed-body">
			{#if descriptionText}
				<p class="feed-description">{descriptionText}</p>
			{/if}
			{#if feed.media && mediaPreview}
				<img
					class="feed-media"
					src={mediaPreview}
					alt={feed.media.description ?? ''}
					loading="lazy"
				/>
			{/if}
		</div>
	{/if}
</div>

<style>
	.timeline-feed {
		--feed-padding-x: 1rem;
		--feed-padding-y: 0.5rem;
		--feed-main-gap: 0.25rem;
		--feed-content-gap: 0.5rem;
		--feed-media-size: 4.5rem;
		--feed-corner-radius: var(--radius-box);
		--feed-pill-radius: var(--radius-box);
		--feed-bg: var(--color-base-100);
		--feed-bg-soft: var(--color-base-200);
		--feed-text: var(--color-base-content);
		--feed-text-muted: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		--feed-text-weak: color-mix(in oklab, var(--color-base-content) 52%, transparent);
		display: grid;
		gap: var(--feed-main-gap);
		background: var(--feed-bg);
		color: var(--feed-text);
		padding: var(--feed-padding-y) var(--feed-padding-x);
		text-decoration: none;
	}

	.timeline-feed.clickable {
		cursor: pointer;
	}

	.feed-header {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: var(--feed-main-gap);
	}

	.source-line {
		display: flex;
		min-width: 0;
		flex: 1 1 auto;
		align-items: center;
		gap: var(--feed-content-gap);
	}

	.source-icon {
		width: 1.25rem;
		height: 1.25rem;
		flex: 0 0 auto;
		border-radius: var(--feed-corner-radius);
		object-fit: cover;
	}

	.source-name {
		min-width: 0;
		color: var(--feed-text);
		font-size: 0.82rem;
		line-height: 1.25;
		overflow-wrap: anywhere;
	}

	.feed-meta {
		display: inline-flex;
		flex: 0 0 auto;
		align-items: center;
		gap: 0.25rem;
		color: var(--feed-text-weak);
		font-size: 0.78rem;
		line-height: 1;
	}

	.feed-meta time {
		white-space: nowrap;
	}

	.meta-icon,
	.translation-meta {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		line-height: 1;
	}

	.feed-title {
		margin: 0;
		color: var(--feed-text);
		font-size: 0.96rem;
		font-weight: 400;
		line-height: 1.38;
		overflow-wrap: anywhere;
	}

	.feed-body {
		display: flex;
		min-width: 0;
		align-items: flex-start;
		gap: var(--feed-content-gap);
	}

	.feed-body.media-only {
		justify-content: flex-end;
	}

	.feed-description {
		display: -webkit-box;
		min-width: 0;
		flex: 1 1 auto;
		margin: 0;
		overflow: hidden;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 5;
		line-clamp: 5;
		color: var(--feed-text-muted);
		font-size: 0.78rem;
		line-height: 1.38;
		overflow-wrap: anywhere;
	}

	.feed-media {
		width: var(--feed-media-size);
		height: var(--feed-media-size);
		flex: 0 0 var(--feed-media-size);
		border-radius: var(--feed-corner-radius);
		background: var(--feed-bg-soft);
		object-fit: cover;
	}

	@media (max-width: 520px) {
		.timeline-feed {
			--feed-padding-x: 0.85rem;
			--feed-media-size: 4rem;
		}
	}
</style>
