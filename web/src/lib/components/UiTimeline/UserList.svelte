<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import type { UiTimelineV2 } from '@flare/web-presenters/timeline.svelte';
	import UiTimelineMessage from './Message.svelte';
	import UiTimelinePost from './Post.svelte';

	type UiTimelineUserList = Extract<UiTimelineV2, { type: 'UserList' }>;

	let {
		userList,
	}: {
		userList: UiTimelineUserList;
	} = $props();
</script>

<article class="timeline-user-list">
	{#if userList.message}
		<div class="user-list-message">
			<UiTimelineMessage message={userList.message} variant="postTop" />
		</div>
	{/if}

	{#if userList.users.length > 0}
		<div class="user-list-scroll" aria-label={m.usersAriaLabel()}>
			{#each userList.users as user (user.key.id)}
				<section
					class="user-list-card rounded-box border border-base-300"
					aria-label={user.name.innerText || user.handle.canonical}
				>
					<UserDisplay {user} variant="compact" />
				</section>
			{/each}
		</div>
	{/if}

	{#if userList.post}
		<section class="user-list-post rounded-box border border-base-300" aria-label={m.relatedPostAriaLabel()}>
			<UiTimelinePost post={userList.post} isQuote forceHideActions />
		</section>
	{/if}
</article>

<style>
	.timeline-user-list {
		--user-list-padding-x: 1rem;
		--user-list-padding-y: 0.5rem;
		--user-list-gap: 0.5rem;
		--user-list-post-padding: 0.5rem;
		--user-list-bg: var(--color-base-100);
		--user-list-text: var(--color-base-content);
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		min-width: 0;
		gap: var(--user-list-gap);
		background: var(--user-list-bg);
		padding: var(--user-list-padding-y) var(--user-list-padding-x);
	}

	.user-list-message {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
	}

	.user-list-scroll {
		display: flex;
		min-width: 0;
		gap: var(--user-list-gap);
		overflow-x: auto;
		overscroll-behavior-x: contain;
		padding-bottom: 0.12rem;
		scrollbar-width: thin;
	}

	.user-list-card {
		width: min(16rem, calc(100vw - 4rem));
		min-width: min(16rem, calc(100vw - 4rem));
		overflow: hidden;
		background: var(--user-list-bg);
	}

	.user-list-post {
		overflow: hidden;
		background: var(--user-list-bg);
		padding: var(--user-list-post-padding);
	}

	@media (max-width: 520px) {
		.timeline-user-list {
			--user-list-padding-x: 0.85rem;
		}
	}
</style>
