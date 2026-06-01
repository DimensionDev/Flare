<script lang="ts">
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiCard } from '@flare/web-presenters/timeline.svelte';
	import { cardHost, mediaPreview } from './postUtils';

	let {
		card,
		appearance,
	}: {
		card: UiCard;
		appearance: TimelineAppearance;
	} = $props();
</script>

<a
	class:compat={appearance.compatLinkPreview}
	class="link-card"
	href={card.url}
	target="_blank"
	rel="noreferrer"
>
	{#if card.media}
		<div class="card-media">
			<img src={mediaPreview(card.media)} alt={card.media.description ?? ''} loading="lazy" />
		</div>
	{/if}
	<div class="card-copy">
		<strong>{card.title}</strong>
		{#if card.description}
			<span>{card.description}</span>
		{/if}
		<small>{cardHost(card.url)}</small>
	</div>
</a>

<style>
	.link-card {
		display: grid;
		overflow: hidden;
		border: 1px solid var(--post-border);
		border-radius: 0.5rem;
		background: var(--post-bg);
		color: inherit;
		text-decoration: none;
	}

	.link-card:hover strong {
		text-decoration: underline;
	}

	.link-card.compat {
		grid-template-columns: 4.5rem minmax(0, 1fr);
		min-height: 4.5rem;
	}

	.card-media {
		overflow: hidden;
		background: var(--post-bg-soft);
		aspect-ratio: 16 / 9;
	}

	.link-card.compat .card-media {
		aspect-ratio: auto;
	}

	.card-media img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.card-copy {
		display: grid;
		gap: 0.2rem;
		min-width: 0;
		padding: 0.65rem;
	}

	.card-copy strong,
	.card-copy span,
	.card-copy small {
		overflow: hidden;
		display: -webkit-box;
		-webkit-box-orient: vertical;
	}

	.card-copy strong {
		-webkit-line-clamp: 2;
		line-clamp: 2;
		font-size: 0.9rem;
		line-height: 1.3;
	}

	.card-copy span {
		-webkit-line-clamp: 2;
		line-clamp: 2;
		color: color-mix(in oklab, var(--post-text) 62%, transparent);
		font-size: 0.8rem;
		line-height: 1.35;
	}

	.card-copy small {
		-webkit-line-clamp: 1;
		line-clamp: 1;
		color: var(--post-text-weak);
		font-size: 0.72rem;
	}

	.link-card.compat .card-copy strong,
	.link-card.compat .card-copy span,
	.link-card.compat .card-copy small {
		-webkit-line-clamp: 1;
		line-clamp: 1;
	}

	@media (max-width: 520px) {
		.link-card:not(.compat) .card-media {
			aspect-ratio: 1.9;
		}
	}
</style>
