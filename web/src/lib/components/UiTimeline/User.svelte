<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import type { UiTimelineV2 } from '@flare/web-presenters/timeline.svelte';
	import UiTimelineMessage from './Message.svelte';
	import { actionLabel } from './post/postUtils';
	import UserSummary from './user/UserSummary.svelte';

	type UiTimelineUser = Extract<UiTimelineV2, { type: 'User' }>;

	let {
		user,
	}: {
		user: UiTimelineUser;
	} = $props();

	const deepLink = useDeepLink();
</script>

<article class="timeline-user">
	{#if user.message}
		<div class="user-message">
			<UiTimelineMessage message={user.message} variant="postTop" />
		</div>
	{/if}

	<UserSummary user={user.value} />

	{#if user.button.length > 0}
		<div class="user-actions" aria-label={m.userActionsAriaLabel()}>
			{#each user.button as action (action.updateKey)}
				<button
					class:btn-error={action.color === 'Red'}
					class:btn-outline={action.color !== 'PrimaryColor'}
					class:btn-primary={action.color === 'PrimaryColor'}
					class="btn btn-sm rounded-box user-action"
					type="button"
					onclick={() => deepLink.performClickEvent(action.clickEvent)}
				>
					{#if action.icon}
						<FaIcon name={action.icon} size={13} />
					{/if}
					<span>{actionLabel(action)}</span>
					{#if action.count}
						<span class="action-count">{action.count.humanized}</span>
					{/if}
				</button>
			{/each}
		</div>
	{/if}
</article>

<style>
	.timeline-user {
		--user-item-bg: var(--color-base-100);
		--user-item-text: var(--color-base-content);
		--user-item-muted: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		min-width: 0;
		background: var(--user-item-bg);
		color: var(--user-item-text);
	}

	.user-message {
		padding: 0.5rem 1rem 0;
		color: var(--user-item-muted);
	}

	.user-actions {
		display: flex;
		min-width: 0;
		flex-wrap: wrap;
		gap: 0.5rem;
		padding: 0 1rem 0.5rem calc(1rem + 2.75rem + 0.5rem);
	}

	.user-action {
		min-height: 2rem;
		gap: 0.35rem;
		font-size: 0.78rem;
		font-weight: 600;
		line-height: 1;
		padding: 0 0.75rem;
	}

	.action-count {
		color: inherit;
		opacity: 0.72;
	}

	@media (max-width: 520px) {
		.user-message {
			padding-right: 0.85rem;
			padding-left: 0.85rem;
		}

		.user-actions {
			padding-right: 0.85rem;
			padding-left: calc(0.85rem + 2.75rem + 0.5rem);
		}
	}
</style>
