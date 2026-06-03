<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		subtitle,
		start,
		end,
		bottom,
		zIndex = 'z-10',
	}: {
		title?: string;
		subtitle?: string;
		start?: Snippet;
		end?: Snippet;
		bottom?: Snippet;
		zIndex?: string;
	} = $props();
</script>

<header class={`sticky top-0 ${zIndex} min-w-0 max-w-full border-b border-base-300 bg-base-100/95 backdrop-blur`}>
	<div class="navbar app-top-bar min-h-14 w-full min-w-0 max-w-full gap-2 px-3 py-1">
		<div class="navbar-start app-top-bar-start">
			{#if start}
				{@render start()}
			{/if}

			{#if title}
				<div class="app-top-bar-title">
					<h1>{title}</h1>
					{#if subtitle}
						<span>{subtitle}</span>
					{/if}
				</div>
			{/if}
		</div>

		{#if end}
			<div class="navbar-end app-top-bar-end">
				{@render end()}
			</div>
		{/if}
	</div>

	{#if bottom}
		{@render bottom()}
	{/if}
</header>

<style>
	.app-top-bar-start {
		flex: 1 1 auto;
		width: auto;
		min-width: 0;
		gap: 0.5rem;
	}

	.app-top-bar-end {
		flex: 0 0 auto;
		width: auto;
		gap: 0.35rem;
	}

	.app-top-bar-title {
		display: grid;
		gap: 0.12rem;
		min-width: 0;
		line-height: 1.15;
	}

	.app-top-bar-title h1,
	.app-top-bar-title span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.app-top-bar-title h1 {
		font-size: 1rem;
		font-weight: 650;
	}

	.app-top-bar-title span {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.75rem;
		font-weight: 500;
	}
</style>
