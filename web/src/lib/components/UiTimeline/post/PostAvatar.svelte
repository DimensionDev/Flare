<script lang="ts">
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
</script>

<div
	class:avatar-square={avatarShape === 'SQUARE'}
	class={`avatar avatar-${placement}`}
	aria-hidden="true"
>
	{#if user.avatar}
		<img src={user.avatar} alt="" loading="lazy" />
	{:else}
		<span>{initials(user.name.innerText)}</span>
	{/if}
</div>

<style>
	.avatar {
		display: grid;
		width: var(--avatar-size);
		height: var(--avatar-size);
		flex: 0 0 auto;
		place-items: center;
		overflow: hidden;
		border-radius: 999px;
		background: linear-gradient(135deg, var(--post-primary-avatar), var(--post-secondary-avatar));
		color: var(--post-text-subtle);
		font-weight: 800;
	}

	.avatar-inline {
		--avatar-size: 2.55rem;
	}

	.avatar-quote {
		--avatar-size: 1.25rem;
	}

	.avatar.avatar-square {
		border-radius: 0.5rem;
	}

	.avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
