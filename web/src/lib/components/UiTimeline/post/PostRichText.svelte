<script lang="ts">
	import type { UiRichText } from '@flare/web-presenters/timeline.svelte';

	let {
		text,
		className = '',
	}: {
		text: UiRichText;
		className?: string;
	} = $props();
</script>

<div class={`${className} rich-text`} dir={text.isRtl ? 'rtl' : 'auto'}>{@html text.platformText}</div>

<style>
	.warning-text {
		font-size: 0.96rem;
		font-weight: 400;
		line-height: 1.5;
	}

	.rich-text {
		white-space: pre-wrap;
	}

	.rich-text :global(.rt-block),
	.rich-text :global(p) {
		margin: 0 0 0.72em;
	}

	.rich-text :global(.rt-block:last-child),
	.rich-text :global(p:last-child) {
		margin-bottom: 0;
	}

	.rich-text :global(a) {
		color: var(--post-primary);
		text-decoration: none;
	}

	.rich-text :global(a:hover) {
		text-decoration: underline;
	}
</style>
