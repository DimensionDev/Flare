<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { UiTimelineV2, UiTimelineV2Message } from '@flare/web-presenters/timeline.svelte';
	import PostRichText from './post/PostRichText.svelte';
	import { messageText } from './post/postUtils';

	type UiTimelineMessage = Extract<UiTimelineV2, { type: 'Message' }>;
	type MessageData = UiTimelineMessage | UiTimelineV2Message;
	type MessageVariant = 'standalone' | 'postTop';

	let {
		message,
		variant = 'standalone',
		sideAvatarVisible = false,
	}: {
		message: MessageData;
		variant?: MessageVariant;
		sideAvatarVisible?: boolean;
	} = $props();

	const deepLink = useDeepLink();
	const text = $derived(messageText(message));
	const hasText = $derived(Boolean(text));
	const postTop = $derived(variant === 'postTop');
	const messageClickable = $derived(deepLink.canPerformClickEvent(message.clickEvent));

	function performMessageClick(event: MouseEvent): void {
		if (!messageClickable) return;

		event.stopPropagation();
		deepLink.performClickEvent(message.clickEvent);
	}

	function performMessageKeydown(event: KeyboardEvent): void {
		if (!messageClickable || event.defaultPrevented || event.key !== 'Enter') return;
		if (event.target !== event.currentTarget) return;

		event.preventDefault();
		event.stopPropagation();
		deepLink.performClickEvent(message.clickEvent);
	}
</script>

{#if hasText || message.user}
	<div
		class:clickable={messageClickable}
		class={`timeline-message ${postTop ? 'post-top' : ''} ${postTop && sideAvatarVisible ? 'with-side-avatar' : ''}`}
		role="link"
		aria-disabled={messageClickable ? undefined : true}
		tabindex={messageClickable ? 0 : undefined}
		onclick={performMessageClick}
		onkeydown={performMessageKeydown}
	>
		<div class="message-icon">
			<FaIcon name={message.icon} size={15} />
		</div>

		<div class="message-content">
			{#if message.user}
				<div class="message-user">
					<PostRichText text={message.user.name} className="message-user-name" />
				</div>
			{/if}
			{#if hasText}
				<span class="message-text">{text}</span>
			{/if}
		</div>
	</div>
{/if}

<style>
	.timeline-message {
		--message-padding-x: 1rem;
		--message-padding-y: 0.5rem;
		--message-gap: 0.5rem;
		--message-icon-size: 0.9375rem;
		--post-primary: var(--color-primary);
		display: flex;
		min-width: 0;
		align-items: center;
		gap: var(--message-gap);
		background: var(--color-base-100);
		color: var(--color-base-content);
		padding: var(--message-padding-y) var(--message-padding-x);
	}

	.timeline-message.post-top {
		--message-padding-x: 0;
		--message-padding-y: 0;
		--message-gap: var(--post-avatar-gap, 0.5rem);
		--message-icon-size: var(--top-message-icon-size, 0.9375rem);
		min-height: var(--message-icon-size);
		margin: 0 0 var(--post-top-message-gap, var(--post-gap, 0.25rem)) 0;
		background: transparent;
		color: var(--post-text-muted, color-mix(in oklab, var(--color-base-content) 58%, transparent));
		padding: 0;
	}

	.timeline-message.post-top.with-side-avatar {
		margin-left: calc(var(--avatar-size, 2.75rem) - var(--message-icon-size));
	}

	.timeline-message.clickable {
		cursor: pointer;
	}

	.message-icon {
		display: grid;
		width: var(--message-icon-size);
		height: var(--message-icon-size);
		flex: 0 0 var(--message-icon-size);
		place-items: center;
	}

	.message-content {
		display: flex;
		min-width: 0;
		flex: 1 1 auto;
		flex-wrap: wrap;
		align-items: baseline;
		column-gap: var(--message-gap);
		color: inherit;
		font-size: 0.96rem;
		font-weight: 400;
		line-height: 1.5;
		overflow-wrap: anywhere;
		row-gap: 0.15rem;
	}

	.timeline-message.post-top .message-content {
		flex-wrap: nowrap;
		align-items: center;
		font-size: 0.78rem;
		font-weight: 600;
		line-height: 1.2;
		row-gap: 0;
	}

	.message-user {
		min-width: 0;
		max-width: min(45%, 18rem);
		flex: 0 1 auto;
		overflow: hidden;
		color: inherit;
	}

	.timeline-message.post-top .message-user {
		max-width: min(45%, 18rem);
	}

	.message-text {
		min-width: 8rem;
		flex: 1 1 10rem;
		color: inherit;
	}

	.timeline-message.post-top .message-text {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.message-user :global(.rich-text) {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.message-user :global(.rich-text p),
	.message-user :global(.rich-text .rt-block) {
		display: inline;
		margin: 0;
	}

	@media (max-width: 520px) {
		.timeline-message {
			--message-padding-x: 0.85rem;
		}

		.message-user {
			max-width: 50%;
		}
	}
</style>
