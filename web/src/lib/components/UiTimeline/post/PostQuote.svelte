<script lang="ts">
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import PostCard from './PostCard.svelte';
	import PostHeader from './PostHeader.svelte';
	import PostMediaGrid from './PostMediaGrid.svelte';
	import RichText from '$lib/components/RichText.svelte';
	import { shouldIgnorePostContainerClick } from './postUtils';

	let {
		quote,
		appearance,
	}: {
		quote: UiTimelineV2Post;
		appearance: TimelineAppearance;
	} = $props();

	const deepLink = useDeepLink();
	const quoteClickable = $derived(deepLink.canPerformClickEvent(quote.clickEvent));

	function performQuoteClick(event: MouseEvent): void {
		if (!quoteClickable || shouldIgnorePostContainerClick(event)) return;

		event.stopPropagation();
		deepLink.performClickEvent(quote.clickEvent);
	}

	function performQuoteKeydown(event: KeyboardEvent): void {
		if (!quoteClickable || event.defaultPrevented || event.key !== 'Enter') {
			return;
		}
		if (event.target !== event.currentTarget) return;

		event.preventDefault();
		event.stopPropagation();
		deepLink.performClickEvent(quote.clickEvent);
	}
</script>

<div
	class:clickable={quoteClickable}
	class="quote-content"
	role="link"
	aria-disabled={quoteClickable ? undefined : true}
	tabindex={quoteClickable ? 0 : undefined}
	onclick={performQuoteClick}
	onkeydown={performQuoteKeydown}
>
	<PostHeader post={quote} {appearance} sideAvatarVisible={false} quoteHeader={true} />
	{#if !quote.content.isEmpty}
		<div class="quote-text">
			<RichText text={quote.content} className="rich-body" />
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

	.quote-content.clickable {
		cursor: pointer;
	}

	.quote-text {
		color: var(--post-text-readable);
		font-size: 0.86rem;
		line-height: 1.42;
		overflow-wrap: anywhere;
	}
</style>
