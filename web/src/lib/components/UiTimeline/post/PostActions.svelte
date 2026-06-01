<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { ActionMenu } from '@flare/web-presenters/timeline.svelte';
	import { actionLabel } from './postUtils';

	let {
		actions,
		appearance,
	}: {
		actions: ActionMenu[];
		appearance: TimelineAppearance;
	} = $props();
</script>

<div class={`actions actions-${appearance.postActionStyle.toLowerCase()}`}>
	{#each actions as action}
		{@render ActionControl(action, appearance)}
	{/each}
</div>

{#snippet ActionControl(action: ActionMenu, appearance: TimelineAppearance)}
	{#if action.type === 'Item'}
		<button type="button" title={actionLabel(action)} aria-label={actionLabel(action)}>
			<FaIcon name={action.icon} size={15} />
			{#if appearance.showNumbers && action.count}
				<span>{action.count.humanized}</span>
			{/if}
		</button>
	{:else if action.type === 'Group'}
		<details class="action-menu">
			<summary title={actionLabel(action.displayItem)} aria-label={actionLabel(action.displayItem)}>
				<FaIcon name={action.displayItem.icon} size={15} />
				{#if appearance.showNumbers && action.displayItem.count}
					<span>{action.displayItem.count.humanized}</span>
				{/if}
			</summary>
			<div class="action-menu-panel">
				{#each action.actions as child}
					{@render ActionMenuEntry(child, appearance)}
				{/each}
			</div>
		</details>
	{/if}
{/snippet}

{#snippet ActionMenuEntry(action: ActionMenu, appearance: TimelineAppearance)}
	{#if action.type === 'Divider'}
		<div class="action-menu-divider"></div>
	{:else if action.type === 'Item'}
		<button
			class:destructive={action.color === 'Red'}
			class="action-menu-item"
			type="button"
			title={actionLabel(action)}
		>
			<FaIcon name={action.icon} size={14} />
			<span>{actionLabel(action)}</span>
			{#if appearance.showNumbers && action.count}
				<small>{action.count.humanized}</small>
			{/if}
		</button>
	{:else if action.type === 'Group'}
		{#each action.actions as child}
			{@render ActionMenuEntry(child, appearance)}
		{/each}
	{/if}
{/snippet}

<style>
	.actions {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		min-height: 1.9rem;
		padding-top: 0;
		color: var(--post-text-muted);
	}

	.actions-rightaligned {
		justify-content: flex-end;
	}

	.actions-stretch {
		justify-content: space-between;
	}

	.actions-leftaligned > :last-child {
		margin-left: auto;
	}

	.action-menu {
		position: relative;
	}

	.actions button,
	.actions summary {
		display: inline-flex;
		min-width: 2rem;
		align-items: center;
		justify-content: center;
		gap: 0.3rem;
		border: 0;
		border-radius: 0.5rem;
		background: transparent;
		color: inherit;
		cursor: pointer;
		font-size: 0.78rem;
		font-weight: 700;
		padding: 0.3rem 0.4rem;
	}

	.actions summary {
		list-style: none;
	}

	.actions summary::-webkit-details-marker {
		display: none;
	}

	.actions button:hover,
	.actions summary:hover,
	.action-menu[open] summary {
		background: var(--post-bg-soft);
		color: var(--post-text);
	}

	.action-menu-panel {
		position: absolute;
		right: 0;
		top: calc(100% + 0.35rem);
		z-index: 20;
		display: grid;
		min-width: 12rem;
		border: 1px solid var(--post-border);
		border-radius: 0.5rem;
		background: var(--post-bg);
		box-shadow: 0 0.75rem 2rem color-mix(in oklab, black 16%, transparent);
		padding: 0.35rem;
	}

	.action-menu-item {
		justify-content: flex-start;
		width: 100%;
		min-width: 0;
		gap: 0.5rem;
		padding: 0.45rem 0.55rem;
		text-align: left;
	}

	.action-menu-item span {
		flex: 1 1 auto;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.action-menu-item small {
		color: var(--post-text-weak);
		font-size: 0.72rem;
	}

	.action-menu-item.destructive {
		color: var(--post-error);
	}

	.action-menu-divider {
		height: 1px;
		margin: 0.3rem 0.2rem;
		background: var(--post-border);
	}

	@media (max-width: 520px) {
		.actions button,
		.actions summary {
			min-width: 1.7rem;
			padding-inline: 0.25rem;
		}
	}
</style>
