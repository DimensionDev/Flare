<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useDeepLink } from '$lib/deeplink/deepLink.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import PostAvatar from './PostAvatar.svelte';
	import RichText from '$lib/components/RichText.svelte';
	import { platformIcon, translationLabel, visibilityIcon, visibilityLabel } from './postUtils';

	let {
		post,
		appearance,
		sideAvatarVisible,
		quoteHeader,
	}: {
		post: UiTimelineV2Post;
		appearance: TimelineAppearance;
		sideAvatarVisible: boolean;
		quoteHeader: boolean;
	} = $props();

	const deepLink = useDeepLink();
	const userClickable = $derived(
		post.user ? deepLink.canPerformClickEvent(post.user.clickEvent) : false
	);

	function performUserClick(event: MouseEvent): void {
		if (!post.user || !userClickable) return;

		event.stopPropagation();
		deepLink.performClickEvent(post.user.clickEvent);
	}
</script>

{#if post.user ||
	post.visibility ||
	(appearance.showTranslateButton && post.translationDisplayState !== 'Hidden') ||
	appearance.showPlatformLogo}
	<header
		class:inline-avatar={!sideAvatarVisible && post.user !== null}
		class:quote-header={quoteHeader}
		class:full-width={!quoteHeader && appearance.fullWidthPost}
		class="post-header"
	>
		{#if !sideAvatarVisible && post.user}
			<PostAvatar
				user={post.user}
				placement={quoteHeader ? 'quote' : 'inline'}
				avatarShape={appearance.avatarShape}
			/>
		{/if}

		{#if post.user}
			{#if userClickable}
				<button class="identity-line identity-button" type="button" onclick={performUserClick}>
					<span class="name-row">
						<RichText text={post.user.name} className="post-user-name" maxLines={1} />
					</span>
					<span class="handle">{post.user.handle.canonical}</span>
				</button>
			{:else}
				<div class="identity-line">
					<span class="name-row">
						<RichText text={post.user.name} className="post-user-name" maxLines={1} />
					</span>
					<span class="handle">{post.user.handle.canonical}</span>
				</div>
			{/if}
		{/if}

		<div class="post-meta">
			{#if post.visibility}
				<span class="meta-icon" title={visibilityLabel(post.visibility)}>
					<FaIcon name={visibilityIcon(post.visibility)} size={14} />
				</span>
			{/if}
			{#if appearance.showTranslateButton && post.translationDisplayState !== 'Hidden'}
					<span class="meta-icon translation-meta" title={translationLabel(post.translationDisplayState)}>
						<FaIcon name="Translate" size={16} />
						{#if post.translationDisplayState === 'Translating'}
							<span class="loading loading-spinner loading-xs" aria-hidden="true"></span>
						{:else if post.translationDisplayState === 'Failed'}
							<FaIcon name="CircleExclamation" size={12} />
						{/if}
				</span>
			{/if}
			{#if appearance.showPlatformLogo}
				<span class="meta-icon" title={post.platformType}>
					<FaIcon name={platformIcon(post.platformType)} size={14} />
				</span>
			{/if}
			<time datetime={post.createdAt.full} title={post.createdAt.full}>
				{appearance.absoluteTimestamp ? post.createdAt.absolute : post.createdAt.relative}
			</time>
		</div>
	</header>
{/if}

<style>
	.post-header {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: var(--post-gap);
	}

	.post-header.inline-avatar {
		align-items: center;
		gap: var(--post-avatar-gap);
	}

	.identity-line {
		display: flex;
		min-width: 0;
		flex: 1 1 auto;
		align-items: center;
		gap: 0.25rem;
		white-space: nowrap;
		width: 0;
	}

	.identity-button {
		border: 0;
		background: transparent;
		color: inherit;
		cursor: pointer;
		font: inherit;
		padding: 0;
		text-align: left;
	}

	.name-row {
		display: inline-flex;
		min-width: 0;
		flex: 0 1 auto;
		align-items: center;
		gap: 0.25rem;
	}

	.identity-line :global(.post-user-name),
	.handle {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.identity-line :global(.post-user-name) {
		flex: 0 1 auto;
		min-width: 0;
		font-size: 0.95rem;
		font-weight: 400;
		line-height: 1.2;
	}

	.identity-line :global(.post-user-name .rt-block),
	.identity-line :global(.post-user-name p) {
		display: inline;
		margin: 0;
	}

	.handle {
		flex: 1 1 auto;
		min-width: 0;
		color: var(--post-text-muted);
		font-size: 0.82rem;
		line-height: 1.2;
	}

	.post-header:not(.full-width) .name-row {
		max-width: 56%;
	}

	.post-header.full-width {
		align-items: center;
	}

	.post-header.full-width .identity-line {
		display: grid;
		gap: 0.05rem;
		align-items: center;
		white-space: normal;
	}

	.post-header.full-width .name-row {
		width: fit-content;
		max-width: 100%;
	}

	.post-header.full-width .handle {
		width: 100%;
	}

	.post-meta {
		display: flex;
		flex: 0 0 auto;
		align-items: center;
		justify-content: flex-end;
		gap: var(--post-gap);
		color: var(--post-text-weak);
		font-size: 0.78rem;
		line-height: 1;
	}

	.post-meta time {
		white-space: nowrap;
	}

	.meta-icon,
	.translation-meta {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		line-height: 1;
	}

	@media (max-width: 520px) {
		.post-header {
			gap: var(--post-gap);
		}

		.post-meta {
			max-width: 38%;
		}

		.post-header:not(.full-width) .name-row {
			max-width: 48%;
		}
	}
</style>
