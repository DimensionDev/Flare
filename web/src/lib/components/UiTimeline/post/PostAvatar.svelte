<script lang="ts">
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiProfile } from '@flare/web-presenters/timeline.svelte';
	import { initials } from './postUtils';

	let {
		user,
		placement,
		avatarShape,
	}: {
		user: UiProfile;
		placement: 'side' | 'inline' | 'quote';
		avatarShape: TimelineAppearance['avatarShape'];
	} = $props();

	const deepLink = useDeepLink();
	const avatarClickable = $derived(deepLink.canPerformClickEvent(user.clickEvent));

	function performAvatarClick(event: MouseEvent): void {
		if (!avatarClickable) return;

		event.stopPropagation();
		deepLink.performClickEvent(user.clickEvent);
	}
</script>

{#snippet AvatarFrame()}
	<div
		class:rounded-box={avatarShape === 'SQUARE'}
		class:rounded-full={avatarShape === 'CIRCLE'}
		class="avatar-frame"
	>
		{#if user.avatar}
			<img src={user.avatar} alt="" loading="lazy" />
		{:else}
			<span>{initials(user.name.innerText)}</span>
		{/if}
	</div>
{/snippet}

{#if avatarClickable}
	<button
		class={`avatar avatar-button avatar-${placement}`}
		type="button"
		aria-label={user.name.innerText || user.handle.canonical}
		onclick={performAvatarClick}
	>
		{@render AvatarFrame()}
	</button>
{:else}
	<div
		class={`avatar avatar-${placement}`}
		aria-hidden="true"
	>
		{@render AvatarFrame()}
	</div>
{/if}

<style>
	.avatar {
		display: inline-flex;
		width: var(--avatar-size);
		height: var(--avatar-size);
		flex: 0 0 auto;
		border: 0;
		background: transparent;
		cursor: default;
		padding: 0;
	}

	.avatar-button {
		cursor: pointer;
	}

	.avatar-frame {
		display: grid;
		width: 100%;
		height: 100%;
		place-items: center;
		overflow: hidden;
		background: linear-gradient(135deg, var(--post-primary-avatar), var(--post-secondary-avatar));
		color: var(--post-text-subtle);
		font-weight: 800;
	}

	.avatar-inline {
		--avatar-size: 2.75rem;
	}

	.avatar-quote {
		--avatar-size: 1.25rem;
	}

	.avatar-frame img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
