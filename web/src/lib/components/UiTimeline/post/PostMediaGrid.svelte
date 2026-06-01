<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiMedia } from '@flare/web-presenters/timeline.svelte';
	import { mediaAlt, mediaAspect, mediaPreview } from './postUtils';

	let {
		media,
		appearance,
		quoteMedia = false,
	}: {
		media: UiMedia[];
		appearance: TimelineAppearance;
		quoteMedia?: boolean;
	} = $props();
</script>

<div
	class={`media-grid media-count-${Math.min(media.length, 9)}`}
	class:quote-media={quoteMedia}
	class:single-actual-ratio={media.length === 1 && appearance.expandMediaSize}
	style={media.length === 1 ? `--single-media-ratio: ${mediaAspect(media[0])};` : undefined}
>
	{#each media.slice(0, 9) as item}
		<figure class={`media-item media-${item.type.toLowerCase()}`}>
			{#if item.type === 'Audio'}
				{#if item.previewUrl}
					<img src={item.previewUrl} alt="" loading="lazy" />
				{/if}
				<figcaption class="audio-caption">
					<FaIcon name="Messages" size={18} />
					<span>Audio</span>
					<small>{item.url}</small>
				</figcaption>
			{:else}
				<img src={mediaPreview(item)} alt={item.description ?? ''} loading="lazy" />
			{/if}

			{#if item.type === 'Video'}
				<span class="media-badge">
					<FaIcon name="Video" size={12} />
				</span>
			{:else if item.type === 'Gif'}
				<span class="media-badge">GIF</span>
			{/if}

			{#if mediaAlt(item)}
				<span class="alt-badge">ALT</span>
			{/if}
		</figure>
	{/each}
</div>

<style>
	.media-grid {
		display: grid;
		gap: 4px;
		overflow: hidden;
		border-radius: inherit;
		background: var(--post-bg-soft);
		aspect-ratio: 16 / 9;
	}

	.media-grid.quote-media {
		border-radius: 0.5rem;
	}

	.media-count-1.single-actual-ratio {
		aspect-ratio: var(--single-media-ratio);
	}

	.media-count-2 {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}

	.media-count-3 {
		grid-template-columns: repeat(2, minmax(0, 1fr));
		grid-template-rows: repeat(2, minmax(0, 1fr));
	}

	.media-count-3 .media-item:first-child {
		grid-row: span 2;
	}

	.media-count-4 {
		grid-template-columns: repeat(2, minmax(0, 1fr));
		grid-template-rows: repeat(2, minmax(0, 1fr));
	}

	.media-count-5,
	.media-count-6,
	.media-count-7,
	.media-count-8,
	.media-count-9 {
		grid-template-columns: repeat(3, minmax(0, 1fr));
		aspect-ratio: auto;
	}

	.media-count-5 .media-item,
	.media-count-6 .media-item,
	.media-count-7 .media-item,
	.media-count-8 .media-item,
	.media-count-9 .media-item {
		aspect-ratio: 1;
	}

	.media-item {
		position: relative;
		min-width: 0;
		min-height: 0;
		overflow: hidden;
		background: var(--post-border);
	}

	.media-item img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.media-audio img {
		filter: saturate(0.85) brightness(0.78);
	}

	.audio-caption {
		position: absolute;
		inset: 0;
		display: grid;
		align-content: end;
		gap: 0.15rem;
		background: linear-gradient(to top, hsl(0 0% 0% / 0.66), transparent 70%);
		color: white;
		padding: 0.75rem;
	}

	.audio-caption span,
	.audio-caption small {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.audio-caption span {
		font-size: 0.82rem;
		font-weight: 800;
	}

	.audio-caption small {
		font-size: 0.72rem;
		opacity: 0.78;
	}

	.media-badge,
	.alt-badge {
		position: absolute;
		border-radius: 0.4rem;
		background: hsl(0 0% 0% / 0.68);
		color: white;
		font-size: 0.7rem;
		font-weight: 800;
		line-height: 1;
	}

	.media-badge {
		left: 0.5rem;
		bottom: 0.5rem;
		display: flex;
		align-items: center;
		gap: 0.25rem;
		padding: 0.28rem 0.4rem;
	}

	.alt-badge {
		right: 0.5rem;
		bottom: 0.5rem;
		padding: 0.32rem 0.38rem;
		letter-spacing: 0;
	}
</style>
