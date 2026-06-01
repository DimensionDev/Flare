<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import { m } from '$lib/paraglide/messages.js';
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

	const componentId = $props.id();

	function altPopoverToken(index: number): string {
		return `post-media-alt-${componentId.replace(/[^a-zA-Z0-9_-]/g, '-')}-${index}`;
	}

	function altPopoverId(index: number): string {
		return `${altPopoverToken(index)}-popover`;
	}

	function altAnchorName(index: number): string {
		return `--${altPopoverToken(index)}-anchor`;
	}
</script>

<div
	class={`media-grid media-count-${Math.min(media.length, 9)}`}
	class:quote-media={quoteMedia}
	class:single-actual-ratio={media.length === 1 && appearance.expandMediaSize}
	style={media.length === 1 ? `--single-media-ratio: ${mediaAspect(media[0])};` : undefined}
>
	{#each media.slice(0, 9) as item, index}
		{@const altText = mediaAlt(item)}
		<figure class={`media-item media-${item.type.toLowerCase()}`}>
			{#if item.type === 'Audio'}
				{#if item.previewUrl}
					<img src={item.previewUrl} alt="" loading="lazy" />
				{/if}
				<figcaption class="audio-caption">
					<FaIcon name="Messages" size={18} />
					<span>{m.mediaAudio()}</span>
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
				<span class="media-badge">{m.mediaGif()}</span>
			{/if}

			{#if altText}
				{@const popoverId = altPopoverId(index)}
				{@const anchorName = altAnchorName(index)}
				<button
					class="badge badge-sm rounded-box border-0 alt-badge"
					type="button"
					title={altText}
					aria-label={altText}
					aria-describedby={popoverId}
					aria-haspopup="dialog"
					popovertarget={popoverId}
					style={`anchor-name: ${anchorName};`}
					onclick={(event) => event.stopPropagation()}
				>
					{m.mediaAlt()}
				</button>
				<div
					class="dropdown-content alt-popover rounded-box border border-base-300 bg-base-100 p-3 text-sm leading-relaxed text-base-content shadow-sm"
					popover="auto"
					role="dialog"
					id={popoverId}
					tabindex="-1"
					aria-label={m.mediaAlt()}
					style={`position-anchor: ${anchorName};`}
					onclick={(event) => event.stopPropagation()}
					onkeydown={(event) => event.stopPropagation()}
				>
					{altText}
				</div>
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
		border-radius: var(--post-corner-radius);
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
		border-radius: var(--post-small-corner-radius);
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
		height: auto;
		min-height: 0;
		padding: 0.32rem 0.38rem;
		letter-spacing: 0;
	}

	.alt-popover {
		position-area: top span-left;
		z-index: 20;
		width: max-content;
		max-width: min(18rem, calc(100vw - 2rem));
		white-space: normal;
	}
</style>
