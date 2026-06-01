<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import type { UiTimelineV2Message } from '@flare/web-presenters/timeline.svelte';
	import { messageText } from './postUtils';

	let {
		message,
		sideAvatarVisible,
	}: {
		message: UiTimelineV2Message;
		sideAvatarVisible: boolean;
	} = $props();
</script>

<div class:with-side-avatar={sideAvatarVisible} class="top-message">
	<FaIcon name={message.icon} size={15} />
	{#if message.user}
		<span class="top-message-label">{message.user.name.innerText}</span>
	{/if}
	<span class="top-message-label">{messageText(message)}</span>
</div>

<style>
	.top-message {
		display: flex;
		align-items: center;
		gap: var(--post-avatar-gap);
		margin: 0 0 var(--post-gap) 0;
		color: var(--post-text-muted);
		font-size: 0.78rem;
		font-weight: 600;
		line-height: 1.2;
		min-height: var(--top-message-icon-size);
	}

	.top-message.with-side-avatar {
		margin-left: calc(var(--avatar-size) - var(--top-message-icon-size));
	}

	.top-message-label {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
</style>
