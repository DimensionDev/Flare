<script lang="ts">
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { UiTimelineV2PostEmojiReaction } from '@flare/web-presenters/timeline.svelte';

	let {
		reactions,
	}: {
		reactions: UiTimelineV2PostEmojiReaction[];
	} = $props();

	const deepLink = useDeepLink();
</script>

<div class="reaction-strip" aria-label="Emoji reactions">
	{#each reactions as reaction}
		<button
			class:btn-primary={reaction.me}
			class:btn-soft={!reaction.me}
			class="btn btn-sm rounded-box reaction-chip"
			type="button"
			onclick={() => deepLink.performClickEvent(reaction.clickEvent)}
		>
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
		gap: 0.3rem;
		min-height: 2rem;
		padding: 0.25rem 0.55rem;
		white-space: nowrap;
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
