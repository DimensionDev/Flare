<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import type { ActionMenu } from '@flare/web-presenters/timeline.svelte';
	import { actionLabel } from './postUtils';

	type ActionMenuActionItem = Extract<ActionMenu, { type: 'Item' }>;
	type ActionMenuActionColor = ActionMenuActionItem['color'];

	let {
		actions,
		appearance,
		detailActions = false,
	}: {
		actions: ActionMenu[];
		appearance: TimelineAppearance;
		detailActions?: boolean;
	} = $props();

	const deepLink = useDeepLink();
	const componentId = $props.id();

	function performAction(action: ActionMenuActionItem): void {
		deepLink.performClickEvent(action.clickEvent);
	}

	function performMenuAction(event: MouseEvent, action: ActionMenuActionItem): void {
		performAction(action);
		const menu = (event.currentTarget as HTMLElement).closest('[popover]') as HTMLElement | null;
		menu?.hidePopover?.();
	}

	function dropdownToken(index: number): string {
		return `post-action-${componentId.replace(/[^a-zA-Z0-9_-]/g, '-')}-${index}`;
	}

	function dropdownPopoverId(index: number): string {
		return `${dropdownToken(index)}-popover`;
	}

	function dropdownAnchorName(index: number): string {
		return `--${dropdownToken(index)}-anchor`;
	}

	function actionColorClass(color: ActionMenuActionColor): string | null {
		switch (color) {
			case 'PrimaryColor':
				return 'action-color-primary';
			case 'Red':
				return 'action-color-red';
			case 'ContentColor':
				return 'action-color-content';
			default:
				return null;
		}
	}
</script>

<div
	class:detail-actions={detailActions}
	class={`actions actions-${appearance.postActionStyle.toLowerCase()}`}
	aria-label={m.postActionsAriaLabel()}
>
	{#each actions as action, index}
		{@render ActionControl(action, appearance, index)}
	{/each}
</div>

{#snippet ActionControl(action: ActionMenu, appearance: TimelineAppearance, index: number)}
	{#if action.type === 'Item'}
		<button
			class={['btn btn-ghost btn-sm rounded-box action-button', actionColorClass(action.color)]}
			type="button"
			title={actionLabel(action)}
			aria-label={actionLabel(action)}
			onclick={() => performAction(action)}
		>
			<FaIcon name={action.icon} size={15} />
			{#if appearance.showNumbers && action.count}
				<span>{action.count.humanized}</span>
			{/if}
		</button>
	{:else if action.type === 'Group'}
		{@const popoverId = dropdownPopoverId(index)}
		{@const anchorName = dropdownAnchorName(index)}
		<button
			class={[
				'btn btn-ghost btn-sm rounded-box action-button',
				actionColorClass(action.displayItem.color),
			]}
			type="button"
			title={actionLabel(action.displayItem)}
			aria-label={actionLabel(action.displayItem)}
			popovertarget={popoverId}
			style={`anchor-name: ${anchorName};`}
		>
			<FaIcon name={action.displayItem.icon} size={15} />
			{#if appearance.showNumbers && action.displayItem.count}
				<span>{action.displayItem.count.humanized}</span>
			{/if}
		</button>
		<ul
			class="dropdown dropdown-end menu bg-base-100 rounded-box w-52 shadow-sm"
			popover="auto"
			id={popoverId}
			style={`position-anchor: ${anchorName};`}
		>
			{#each action.actions as child}
				{@render ActionMenuEntry(child, appearance)}
			{/each}
		</ul>
	{/if}
{/snippet}

{#snippet ActionMenuEntry(action: ActionMenu, appearance: TimelineAppearance)}
	{#if action.type === 'Divider'}
		<li class="action-menu-divider" aria-hidden="true"></li>
	{:else if action.type === 'Item'}
		<li>
			<button
				class={['rounded-box action-menu-button', actionColorClass(action.color)]}
				type="button"
				title={actionLabel(action)}
				onclick={(event) => performMenuAction(event, action)}
			>
				<FaIcon name={action.icon} size={14} />
				<span>{actionLabel(action)}</span>
				{#if appearance.showNumbers && action.count}
					<small>{action.count.humanized}</small>
				{/if}
			</button>
		</li>
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

	.actions.detail-actions {
		min-height: 2.25rem;
		color: var(--post-text-readable);
		font-size: 0.95rem;
	}

	.actions.detail-actions :global(.btn) {
		min-height: 2.25rem;
		height: 2.25rem;
	}

	.action-button,
	.action-menu-button {
		color: var(--action-color, inherit);
		--btn-fg: var(--action-color, currentColor);
	}

	.action-color-primary {
		--action-color: var(--post-primary);
	}

	.action-color-red {
		--action-color: var(--post-error);
	}

	.action-color-content {
		--action-color: var(--post-text-readable);
	}

	.actions-rightaligned {
		justify-content: flex-end;
	}

	.actions-stretch {
		justify-content: space-between;
	}

	.actions-leftaligned > button:last-of-type {
		margin-left: auto;
	}

	.dropdown button span {
		flex: 1 1 auto;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.dropdown button small {
		color: var(--post-text-weak);
		font-size: 0.72rem;
	}

	.action-menu-divider {
		margin: 0.3rem 0.2rem;
	}
</style>
