<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import PostAvatar from './PostAvatar.svelte';
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
</script>

{#if post.user ||
	post.visibility ||
	(appearance.showTranslateButton && post.translationDisplayState !== 'Hidden') ||
	appearance.showPlatformLogo}
	<header
		class:inline-avatar={!sideAvatarVisible && post.user !== null}
		class:quote-header={quoteHeader}
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
			<div class="identity-line">
				<strong>{post.user.name.innerText}</strong>
				{#if post.user.mark.includes('Verified')}
					<span class="verified" title="Verified">
						<FaIcon name="Check" size={11} />
					</span>
				{/if}
				<span class="handle">{post.user.handle.raw}</span>
			</div>
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
						<span class="progress-ring" aria-hidden="true"></span>
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

	.identity-line strong,
	.handle {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.identity-line strong {
		flex: 0 1 auto;
		min-width: 0;
		max-width: 56%;
		font-size: 0.95rem;
		line-height: 1.2;
	}

	.handle {
		flex: 1 1 auto;
		min-width: 0;
		color: var(--post-text-muted);
		font-size: 0.82rem;
		line-height: 1.2;
	}

	.verified {
		display: inline-grid;
		width: 1rem;
		height: 1rem;
		flex: 0 0 auto;
		place-items: center;
		border-radius: 999px;
		background: var(--post-primary);
		color: var(--post-primary-content);
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

	.progress-ring {
		width: 0.75rem;
		height: 0.75rem;
		border: 1.5px solid currentColor;
		border-right-color: transparent;
		border-radius: 999px;
		animation: progress-spin 0.8s linear infinite;
	}

	@keyframes progress-spin {
		to {
			rotate: 360deg;
		}
	}

	@media (max-width: 520px) {
		.post-header {
			gap: var(--post-gap);
		}

		.post-meta {
			max-width: 38%;
		}

		.identity-line strong {
			max-width: 48%;
		}
	}
</style>
