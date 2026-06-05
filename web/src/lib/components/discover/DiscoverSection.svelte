<script lang="ts">
	import type { Snippet } from 'svelte';
	import { m } from '$lib/paraglide/messages.js';

type SectionState =
	| { type: 'Loading' }
	| { type: 'Error'; message: string | null }
	| { type: 'Empty' }
	| { type: 'Success'; itemCount?: number };

	let {
		title,
		state,
		children,
	}: {
		title: string;
		state: SectionState;
		children: Snippet;
	} = $props();
</script>

{#if state.type !== 'Empty' && !(state.type === 'Success' && state.itemCount === 0)}
	<section class="discover-section">
		<div class="section-heading">
			<h2>{title}</h2>
		</div>
		{#if state.type === 'Error'}
			<div class="alert alert-error rounded-box">
				<span>{state.message ?? m.timelineUnableToLoadTimeline()}</span>
			</div>
		{:else}
			{@render children()}
		{/if}
	</section>
{/if}

<style>
	.discover-section {
		display: grid;
		min-width: 0;
		gap: 0.5rem;
	}

	.section-heading {
		display: flex;
		min-height: 2rem;
		align-items: center;
		padding: 0 0.25rem;
	}

	.section-heading h2 {
		font-size: 0.92rem;
		font-weight: 700;
		line-height: 1.25;
	}

</style>
