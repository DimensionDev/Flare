<script lang="ts">
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import PostCard from './PostCard.svelte';
	import PostHeader from './PostHeader.svelte';
	import PostMediaGrid from './PostMediaGrid.svelte';
	import PostRichText from './PostRichText.svelte';

	let {
		quote,
		appearance,
	}: {
		quote: UiTimelineV2Post;
		appearance: TimelineAppearance;
	} = $props();
</script>

<div class="quote-content">
	<PostHeader post={quote} {appearance} sideAvatarVisible={false} quoteHeader={true} />
	{#if !quote.content.isEmpty}
		<div class="quote-text">
			<PostRichText text={quote.content} className="rich-body" />
		</div>
	{/if}
	{#if quote.images.length > 0}
		<PostMediaGrid media={quote.images} {appearance} quoteMedia={true} />
	{/if}
	{#if quote.card && appearance.showLinkPreview && quote.images.length === 0}
		<PostCard card={quote.card} {appearance} />
	{/if}
</div>

<style>
	.quote-content {
		display: grid;
		gap: var(--post-gap);
	}

	.quote-text {
		color: var(--post-text-readable);
		font-size: 0.86rem;
		line-height: 1.42;
		overflow-wrap: anywhere;
	}
</style>
