<script lang="ts">
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { UiProfile } from '@flare/web-presenters/timeline.svelte';
	import PostRichText from '../post/PostRichText.svelte';
	import { defaultTimelineAppearance, initials } from '../post/postUtils';

	type UserSummaryVariant = 'standalone' | 'compact';

	let {
		user,
		variant = 'standalone',
	}: {
		user: UiProfile;
		variant?: UserSummaryVariant;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const deepLink = useDeepLink();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const appearance = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data
			: defaultTimelineAppearance
	);
	const avatarSquare = $derived(appearance.avatarShape === 'SQUARE');
	const handleText = $derived(user.handle.raw || user.handle.canonical || user.handleWithoutAt);
	const userClickable = $derived(deepLink.canPerformClickEvent(user.clickEvent));

	function performUserClick(event: MouseEvent): void {
		if (!userClickable) return;

		event.stopPropagation();
		deepLink.performClickEvent(user.clickEvent);
	}

	function performUserKeydown(event: KeyboardEvent): void {
		if (!userClickable || event.defaultPrevented || event.key !== 'Enter') return;
		if (event.target !== event.currentTarget) return;

		event.preventDefault();
		event.stopPropagation();
		deepLink.performClickEvent(user.clickEvent);
	}
</script>

<div
	class:clickable={userClickable}
	class={`user-summary ${variant}`}
	role="link"
	aria-disabled={userClickable ? undefined : true}
	aria-label={user.name.innerText || handleText}
	tabindex={userClickable ? 0 : undefined}
	onclick={performUserClick}
	onkeydown={performUserKeydown}
>
	<div class:avatar-square={avatarSquare} class="user-avatar" aria-hidden="true">
		{#if user.avatar}
			<img src={user.avatar} alt="" loading="lazy" />
		{:else}
			<span>{initials(user.name.innerText || user.handleWithoutAtAndHost)}</span>
		{/if}
	</div>

	<div class="user-content">
		<header class="user-header">
			<div class="identity">
				<div class="name-line">
					<PostRichText text={user.name} className="user-name" />
				</div>
				<div class="handle-line">
					<span class="handle">{handleText}</span>
				</div>
			</div>
		</header>
	</div>
</div>

<style>
	.user-summary {
		--user-padding-x: 1rem;
		--user-padding-y: 0.5rem;
		--user-gap: 0.5rem;
		--user-content-gap: 0.25rem;
		--user-avatar-size: 2.75rem;
		--user-corner-radius: var(--radius-box);
		--user-avatar-circle-radius: 999px;
		--user-bg: var(--color-base-100);
		--user-text: var(--color-base-content);
		--user-text-muted: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		--user-primary-avatar: color-mix(in oklab, var(--color-primary) 16%, transparent);
		--user-secondary-avatar: color-mix(in oklab, var(--color-secondary) 16%, transparent);
		display: grid;
		grid-template-columns: var(--user-avatar-size) minmax(0, 1fr);
		gap: var(--user-gap);
		background: var(--user-bg);
		color: var(--user-text);
		padding: var(--user-padding-y) var(--user-padding-x);
	}

	.user-summary.compact {
		--user-padding-x: 0.75rem;
		--user-avatar-size: 2.75rem;
	}

	.user-summary.clickable {
		cursor: pointer;
	}

	.user-avatar {
		display: grid;
		width: var(--user-avatar-size);
		height: var(--user-avatar-size);
		place-items: center;
		overflow: hidden;
		border-radius: var(--user-avatar-circle-radius);
		background: linear-gradient(135deg, var(--user-primary-avatar), var(--user-secondary-avatar));
		color: color-mix(in oklab, var(--color-base-content) 72%, transparent);
		font-size: 0.85rem;
		font-weight: 800;
	}

	.user-avatar.avatar-square {
		border-radius: var(--user-corner-radius);
	}

	.user-avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.user-content {
		display: grid;
		min-width: 0;
		gap: var(--user-content-gap);
	}

	.user-header {
		min-width: 0;
	}

	.identity {
		display: grid;
		min-width: 0;
		gap: 0.08rem;
	}

	.name-line,
	.handle-line {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: 0.25rem;
	}

	.name-line {
		color: var(--user-text);
		font-size: 0.96rem;
		font-weight: 400;
		line-height: 1.28;
	}

	.handle-line {
		color: var(--user-text-muted);
		font-size: 0.78rem;
		line-height: 1.25;
	}

	.name-line :global(.user-name) {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.name-line :global(.user-name),
	.name-line :global(.user-name p),
	.name-line :global(.user-name .rt-block) {
		display: inline;
		margin: 0;
	}

	.handle {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	@media (max-width: 520px) {
		.user-summary {
			--user-padding-x: 0.85rem;
		}
	}
</style>
