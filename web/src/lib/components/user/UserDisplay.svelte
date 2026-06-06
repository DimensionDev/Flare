<script lang="ts">
	import RichText from '$lib/components/RichText.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import {
		useEnvironmentSettings,
		type TimelineAppearance,
	} from '$lib/environment/environmentSettings.svelte';
	import type { UiProfile } from '@flare/web-presenters/timeline.svelte';

	type UserDisplayVariant = 'standalone' | 'compact' | 'sidebar' | 'account';

	let {
		user,
		variant = 'standalone',
		clickable = true,
		avatarShape,
	}: {
		user: UiProfile;
		variant?: UserDisplayVariant;
		clickable?: boolean;
		avatarShape?: TimelineAppearance['avatarShape'];
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const deepLink = useDeepLink();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const resolvedAvatarShape = $derived(
		avatarShape ??
			(timelineAppearanceState.type === 'Success'
				? timelineAppearanceState.data.avatarShape
				: 'CIRCLE')
	);
	const handleText = $derived(user.handle.canonical || user.handle.raw);
	const labelText = $derived(user.name.innerText || handleText);
	const userClickable = $derived(clickable && deepLink.canPerformClickEvent(user.clickEvent));

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

	function initials(value: string): string {
		const trimmed = value.trim();
		if (!trimmed) return '?';

		return Array.from(trimmed).slice(0, 2).join('').toUpperCase();
	}
</script>

{#snippet UserDisplayContent()}
	<div
		class:square={resolvedAvatarShape === 'SQUARE'}
		class="user-avatar"
		aria-hidden="true"
	>
		{#if user.avatar?.url}
			<img src={user.avatar?.url} alt="" loading="lazy" />
		{:else}
			<span>{initials(user.name.innerText || user.handleWithoutAtAndHost || handleText)}</span>
		{/if}
	</div>

	<div class="user-copy">
		<div class="name-line">
			<RichText text={user.name} className="user-display-name" maxLines={1} />
		</div>
		<div class="handle-line">
			<span>{handleText}</span>
		</div>
	</div>
{/snippet}

{#if userClickable}
	<button
		class={`user-display clickable ${variant}`}
		type="button"
		aria-label={labelText}
		onclick={performUserClick}
		onkeydown={performUserKeydown}
	>
		{@render UserDisplayContent()}
	</button>
{:else}
	<div class={`user-display ${variant}`} aria-label={labelText}>
		{@render UserDisplayContent()}
	</div>
{/if}

<style>
	.user-display {
		--user-display-padding-x: 1rem;
		--user-display-padding-y: 0.5rem;
		--user-display-gap: 0.5rem;
		--user-display-avatar-size: 2.75rem;
		--user-display-name-size: 0.96rem;
		--user-display-handle-size: 0.78rem;
		--user-display-name-weight: 400;
		--user-display-avatar-radius: 999px;
		--user-display-square-radius: var(--radius-box);
		--user-display-muted: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		--user-display-primary-avatar: color-mix(in oklab, var(--color-primary) 16%, transparent);
		--user-display-secondary-avatar: color-mix(in oklab, var(--color-secondary) 16%, transparent);
		display: grid;
		grid-template-columns: var(--user-display-avatar-size) minmax(0, 1fr);
		align-items: center;
		border: 0;
		gap: var(--user-display-gap);
		min-width: 0;
		background: var(--user-display-bg, transparent);
		color: var(--color-base-content);
		font: inherit;
		padding: var(--user-display-padding-y) var(--user-display-padding-x);
		text-align: left;
	}

	.user-display.compact {
		--user-display-padding-x: 0.75rem;
	}

	.user-display.sidebar {
		--user-display-padding-x: 0.25rem;
		--user-display-padding-y: 0.5rem;
		--user-display-gap: 0.625rem;
		--user-display-avatar-size: 2rem;
		--user-display-name-size: 0.875rem;
		--user-display-handle-size: 0.75rem;
		--user-display-name-weight: 600;
	}

	.user-display.account {
		--user-display-padding-x: 0;
		--user-display-padding-y: 0;
		--user-display-gap: 0.75rem;
		--user-display-avatar-size: 2.15rem;
		--user-display-name-size: 0.96rem;
		--user-display-handle-size: 0.79rem;
		--user-display-name-weight: 650;
	}

	.user-display.clickable {
		cursor: pointer;
	}

	.user-avatar {
		display: grid;
		width: var(--user-display-avatar-size);
		height: var(--user-display-avatar-size);
		place-items: center;
		overflow: hidden;
		border-radius: var(--user-display-avatar-radius);
		background: linear-gradient(
			135deg,
			var(--user-display-primary-avatar),
			var(--user-display-secondary-avatar)
		);
		color: color-mix(in oklab, var(--color-base-content) 72%, transparent);
		font-size: 0.85rem;
		font-weight: 800;
	}

	.user-avatar.square {
		border-radius: var(--user-display-square-radius);
	}

	.user-avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.user-copy {
		display: grid;
		min-width: 0;
		gap: 0.08rem;
	}

	.name-line,
	.handle-line {
		display: flex;
		min-width: 0;
		align-items: center;
	}

	.name-line {
		font-size: var(--user-display-name-size);
		font-weight: var(--user-display-name-weight);
		line-height: 1.28;
	}

	.handle-line {
		color: var(--user-display-muted);
		font-size: var(--user-display-handle-size);
		line-height: 1.25;
	}

	.name-line :global(.user-display-name) {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.name-line :global(.user-display-name),
	.name-line :global(.user-display-name p),
	.name-line :global(.user-display-name .rt-block),
	.handle-line span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.name-line :global(.user-display-name),
	.name-line :global(.user-display-name p),
	.name-line :global(.user-display-name .rt-block) {
		display: inline;
		margin: 0;
	}

	.handle-line span {
		min-width: 0;
	}

	@media (max-width: 520px) {
		.user-display {
			--user-display-padding-x: 0.85rem;
		}

		.user-display.account {
			--user-display-padding-x: 0;
		}
	}
</style>
