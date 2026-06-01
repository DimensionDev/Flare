<script lang="ts">
	import type { UiTimelineV2PostEmojiReaction } from '@flare/web-presenters/timeline.svelte';

	let {
		reactions,
	}: {
		reactions: UiTimelineV2PostEmojiReaction[];
	} = $props();
</script>

<div class="reaction-strip" aria-label="Emoji reactions">
	{#each reactions as reaction}
		<button class:mine={reaction.me} class="reaction-chip" type="button">
			{#if reaction.isUnicode}
				<span>{reaction.name}</span>
			{:else if reaction.url}
				<img src={reaction.url} alt={reaction.name} loading="lazy" />
			{/if}
			<strong>{reaction.count.humanized}</strong>
		</button>
	{/each}
</div>

<style>
	.reaction-strip {
		display: flex;
		gap: 0.45rem;
		overflow-x: auto;
		padding-bottom: 0.05rem;
		scrollbar-width: none;
	}

	.reaction-strip::-webkit-scrollbar {
		display: none;
	}

	.reaction-chip {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		min-height: 2rem;
		border: 0;
		border-radius: 0.5rem;
		background: var(--post-bg-soft);
		color: color-mix(in oklab, var(--post-text) 82%, transparent);
		cursor: pointer;
		padding: 0.25rem 0.55rem;
		white-space: nowrap;
	}

	.reaction-chip.mine {
		background: var(--post-primary);
		color: var(--post-primary-content);
	}

	.reaction-chip img {
		width: 1.15rem;
		height: 1.15rem;
		object-fit: contain;
	}

	.reaction-chip strong {
		font-size: 0.78rem;
	}
</style>
