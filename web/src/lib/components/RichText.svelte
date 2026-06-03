<script lang="ts">
	import type { UiRichText } from '@flare/web-presenters/timeline.svelte';

	let {
		text,
		className = '',
		maxLines = null,
	}: {
		text: UiRichText;
		className?: string;
		maxLines?: number | null;
	} = $props();

	const classes = $derived(
		`${className} rich-text${maxLines !== null ? ' max-lines' : ''}`
	);
</script>

<div
	class={classes}
	dir={text.isRtl ? 'rtl' : 'auto'}
	style={maxLines !== null ? `--rich-text-max-lines: ${maxLines};` : undefined}
>{@html text.platformText}</div>

<style>
	.warning-text {
		font-size: 0.96rem;
		font-weight: 400;
		line-height: 1.5;
	}

	.rich-text {
		min-width: 0;
		max-width: 100%;
		overflow-wrap: anywhere;
		white-space: pre-wrap;
		word-break: break-word;
	}

	.rich-text.max-lines {
		display: -webkit-box;
		overflow: hidden;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: var(--rich-text-max-lines);
		line-clamp: var(--rich-text-max-lines);
	}

	.rich-text.max-lines :global(.rt-block),
	.rich-text.max-lines :global(p) {
		display: inline;
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
		color: var(--post-primary, var(--color-primary));
		text-decoration: none;
	}

	.rich-text :global(a:hover) {
		text-decoration: underline;
	}

	.rich-text :global(.rt-inline-image) {
		max-width: 1.4em;
		max-height: 1.4em;
		vertical-align: -0.25em;
	}

	.rich-text :global(.rt-block-image img) {
		max-width: 100%;
		height: auto;
	}
</style>
