<script lang="ts">
	import { tick } from 'svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import PostActions from './post/PostActions.svelte';
	import PostAvatar from './post/PostAvatar.svelte';
	import PostCard from './post/PostCard.svelte';
	import PostHeader from './post/PostHeader.svelte';
	import PostMediaGrid from './post/PostMediaGrid.svelte';
	import PostPoll from './post/PostPoll.svelte';
	import PostQuote from './post/PostQuote.svelte';
	import PostReactions from './post/PostReactions.svelte';
	import PostRichText from './post/PostRichText.svelte';
	import PostTopMessage from './post/PostTopMessage.svelte';
	import { defaultTimelineAppearance, hasActionControls, postKey } from './post/postUtils';

	let {
		post,
		isQuote = false,
		forceHideActions = false,
	}: {
		post: UiTimelineV2Post;
		isQuote?: boolean;
		forceHideActions?: boolean;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const appearance = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data
			: defaultTimelineAppearance
	);

	// svelte-ignore state_referenced_locally -- reset logic below handles prop changes.
	let lastPostKey = $state(postKey(post));
	let contentExpanded = $state(false);
	let bodyExpanded = $state(false);
	let mediaExpanded = $state(false);
	let sensitiveRevealed = $state(false);
	let selectedPollOptions = $state<number[]>([]);
	let bodyTextElement = $state<HTMLElement | null>(null);
	let bodyOverflows = $state(false);

	$effect(() => {
		const nextKey = postKey(post);
		if (lastPostKey !== nextKey) {
			lastPostKey = nextKey;
			contentExpanded = false;
			bodyExpanded = false;
			mediaExpanded = false;
			sensitiveRevealed = false;
			selectedPollOptions = [];
		}
	});

	const showSideAvatar = $derived(!appearance.fullWidthPost && !isQuote && post.user !== null);
	const hasContentWarning = $derived(Boolean(post.contentWarning && !post.contentWarning.isEmpty));
	const contentVisible = $derived(!hasContentWarning || contentExpanded);
	const lineLimit = $derived(Math.max(appearance.lineLimit || 5, 1));
	const shouldClampBody = $derived(
		!bodyExpanded &&
			!isQuote &&
			!post.shouldExpandTextByDefault &&
			lineLimit > 0 &&
			!post.content.isEmpty &&
			contentVisible
	);
	const shouldShowBodyExpand = $derived(shouldClampBody && bodyOverflows);
	const shouldShowMediaGrid = $derived(post.images.length > 0 && (appearance.showMedia || mediaExpanded));
	const hideSensitiveMedia = $derived(
		shouldShowMediaGrid && post.sensitive && !appearance.showSensitiveContent && !sensitiveRevealed
	);
	const visibleCard = $derived(
		post.card && appearance.showLinkPreview && post.images.length === 0 && post.quote.length === 0
			? post.card
			: null
	);
	const visibleActions = $derived(
		!forceHideActions &&
			!isQuote &&
			appearance.postActionStyle !== 'Hidden' &&
			hasActionControls(post.actions)
	);

	$effect(() => {
		const element = bodyTextElement;
		const shouldMeasure = shouldClampBody;
		postKey(post);
		lineLimit;

		if (!element || !shouldMeasure) {
			bodyOverflows = false;
			return;
		}

		tick().then(() => {
			if (bodyTextElement !== element || !shouldMeasure) return;
			bodyOverflows = element.scrollHeight > element.clientHeight + 1;
		});
	});

	function togglePollOption(index: number, multiple: boolean): void {
		selectedPollOptions = multiple
			? selectedPollOptions.includes(index)
				? selectedPollOptions.filter((item) => item !== index)
				: [...selectedPollOptions, index]
			: [index];
	}
</script>

<article
	class:quote={isQuote}
	class:card-mode={!isQuote && appearance.timelineDisplayMode !== 'Plain'}
	class:full-width={!showSideAvatar}
	class="timeline-post"
>
	{#if post.message && !isQuote}
		<PostTopMessage message={post.message} sideAvatarVisible={showSideAvatar} />
	{/if}

	<div class="post-grid" class:with-avatar={showSideAvatar}>
		{#if showSideAvatar && post.user}
			<PostAvatar user={post.user} placement="side" avatarShape={appearance.avatarShape} />
		{/if}

		<div class="post-content">
			<PostHeader post={post} {appearance} sideAvatarVisible={showSideAvatar} quoteHeader={isQuote} />

			<div class="content-stack">
				{#if post.replyToHandle}
					<div class="reply-row">
						<FaIcon name="Reply" size={12} />
						<span>Reply to {post.replyToHandle}</span>
					</div>
				{/if}

				{#if hasContentWarning && post.contentWarning}
					<div class="content-warning">
						<PostRichText text={post.contentWarning} className="warning-text" />
						<button class="text-button" type="button" onclick={() => (contentExpanded = !contentExpanded)}>
							{contentExpanded ? 'Show less' : 'Show more'}
						</button>
					</div>
				{/if}

				{#if contentVisible && !post.content.isEmpty}
					<div
						bind:this={bodyTextElement}
						class:clamped={shouldClampBody}
						class="post-text"
						style={`--line-limit: ${lineLimit};`}
					>
						<PostRichText text={post.content} className="rich-body" />
					</div>
					{#if shouldShowBodyExpand}
						<button class="text-button" type="button" onclick={() => (bodyExpanded = true)}>
							Show more
						</button>
					{/if}
				{/if}

				{#if post.poll}
					<PostPoll
						poll={post.poll}
						selectedOptions={selectedPollOptions}
						onToggleOption={togglePollOption}
					/>
				{/if}

				{#if post.images.length > 0}
					{#if shouldShowMediaGrid}
						<div class="media-shell">
							<PostMediaGrid media={post.images} {appearance} quoteMedia={isQuote} />
							{#if hideSensitiveMedia}
								<button
									class="sensitive-overlay"
									type="button"
									onclick={() => (sensitiveRevealed = true)}
								>
									<FaIcon name="Image" size={18} />
									<span>Sensitive media</span>
									<small>Show</small>
								</button>
							{:else if post.sensitive && !appearance.showSensitiveContent}
								<button
									class="hide-sensitive-button"
									type="button"
									title="Hide sensitive media"
									onclick={() => (sensitiveRevealed = false)}
								>
									<FaIcon name="Image" size={13} />
								</button>
							{/if}
						</div>
					{:else}
						<button class="show-media-button" type="button" onclick={() => (mediaExpanded = true)}>
							<FaIcon name="Image" size={13} />
							<span>Show media</span>
						</button>
					{/if}
				{/if}

				{#if visibleCard}
					<PostCard card={visibleCard} {appearance} />
				{/if}

				{#if post.quote.length > 0 && !isQuote}
					<div class="quote-box">
						{#each post.quote as quote, index (postKey(quote))}
							<div class="quote-item">
								<PostQuote {quote} {appearance} />
							</div>
							{#if index < post.quote.length - 1}
								<div class="quote-divider"></div>
							{/if}
						{/each}
					</div>
				{/if}

				{#if post.sourceChannel && !isQuote}
					<div class="source-channel">
						<FaIcon name="Channel" size={12} />
						<span>{post.sourceChannel.name}</span>
					</div>
				{/if}

				{#if post.emojiReactions.length > 0 && !isQuote}
					<PostReactions reactions={post.emojiReactions} />
				{/if}

				{#if visibleActions}
					<PostActions actions={post.actions} {appearance} />
				{/if}
			</div>
		</div>
	</div>
</article>

<style>
	.timeline-post {
		--post-padding-x: 1rem;
		--post-padding-y: 0.75rem;
		--post-padding-bottom: 0.25rem;
		--post-gap: 0.25rem;
		--post-avatar-gap: 0.5rem;
		--avatar-size: 2.75rem;
		--top-message-icon-size: 0.9375rem;
		--post-bg: var(--color-base-100);
		--post-bg-soft: var(--color-base-200);
		--post-bg-muted: color-mix(in oklab, var(--color-base-200) 72%, transparent);
		--post-border: var(--color-base-300);
		--post-border-soft: color-mix(in oklab, var(--color-base-300) 70%, transparent);
		--post-text: var(--color-base-content);
		--post-text-weak: color-mix(in oklab, var(--color-base-content) 52%, transparent);
		--post-text-muted: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		--post-text-subtle: color-mix(in oklab, var(--color-base-content) 72%, transparent);
		--post-text-readable: color-mix(in oklab, var(--color-base-content) 90%, transparent);
		--post-primary: var(--color-primary);
		--post-primary-content: var(--color-primary-content);
		--post-primary-soft: color-mix(in oklab, var(--color-primary) 12%, transparent);
		--post-primary-avatar: color-mix(in oklab, var(--color-primary) 16%, transparent);
		--post-secondary-avatar: color-mix(in oklab, var(--color-secondary) 16%, transparent);
		--post-error: var(--color-error);
		background: var(--post-bg);
		color: var(--post-text);
		padding: var(--post-padding-y) var(--post-padding-x) var(--post-padding-bottom);
	}

	.timeline-post.card-mode {
		border: 1px solid var(--post-border);
		border-radius: 0.5rem;
		background: var(--post-bg);
		box-shadow: 0 1px 0 var(--post-border-soft);
	}

	.timeline-post.quote {
		--post-padding-x: 0;
		--post-padding-y: 0;
		--post-padding-bottom: 0;
		--avatar-size: 2rem;
		border: 0;
		background: transparent;
		box-shadow: none;
	}

	.post-grid {
		display: grid;
		min-width: 0;
		gap: var(--post-gap) var(--post-avatar-gap);
	}

	.post-grid.with-avatar {
		grid-template-columns: var(--avatar-size) minmax(0, 1fr);
	}

	.post-content {
		min-width: 0;
	}

	.content-stack {
		display: grid;
		gap: var(--post-gap);
		margin-top: var(--post-gap);
	}

	.reply-row,
	.source-channel {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: 0.35rem;
		color: var(--post-text-muted);
		font-size: 0.8rem;
	}

	.content-warning {
		display: grid;
		width: fit-content;
		max-width: 100%;
		gap: 0.4rem;
		border: 1px solid var(--post-border);
		border-radius: 0.5rem;
		background: var(--post-bg-muted);
		padding: 0.55rem 0.65rem;
	}

	.text-button {
		width: fit-content;
		border: 0;
		background: transparent;
		color: var(--post-primary);
		cursor: pointer;
		font-size: 0.84rem;
		font-weight: 700;
		padding: 0;
	}

	.text-button:hover {
		text-decoration: underline;
	}

	.post-text {
		color: var(--post-text-readable);
		font-size: 0.96rem;
		line-height: 1.5;
		overflow-wrap: anywhere;
	}

	.post-text.clamped {
		display: -webkit-box;
		overflow: hidden;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: var(--line-limit);
		line-clamp: var(--line-limit);
	}

	.show-media-button {
		display: inline-flex;
		width: fit-content;
		align-items: center;
		gap: 0.38rem;
		border: 1px solid var(--post-border);
		border-radius: 0.5rem;
		background: var(--post-bg-soft);
		color: var(--post-text-subtle);
		cursor: pointer;
		font-size: 0.82rem;
		font-weight: 700;
		padding: 0.45rem 0.6rem;
	}

	.media-shell {
		position: relative;
		overflow: hidden;
		border-radius: 0.5rem;
		background: var(--post-bg-soft);
	}

	.sensitive-overlay {
		position: absolute;
		inset: 0;
		display: grid;
		place-items: center;
		align-content: center;
		gap: 0.3rem;
		border: 0;
		background: color-mix(in oklab, var(--post-bg) 54%, transparent);
		backdrop-filter: blur(18px);
		color: var(--post-text);
		cursor: pointer;
		font-weight: 750;
	}

	.sensitive-overlay small {
		border-radius: 999px;
		background: var(--post-primary);
		color: var(--post-primary-content);
		font-size: 0.74rem;
		padding: 0.18rem 0.55rem;
	}

	.hide-sensitive-button {
		position: absolute;
		top: 0.55rem;
		left: 0.55rem;
		border: 0;
		border-radius: 0.4rem;
		background: hsl(0 0% 0% / 0.68);
		color: white;
		cursor: pointer;
		font-size: 0.7rem;
		font-weight: 800;
		line-height: 1;
		padding: 0.32rem;
	}

	.quote-box {
		overflow: hidden;
		border: 1px solid var(--post-border);
		border-radius: 1rem;
	}

	.quote-item {
		padding: 0.5rem;
	}

	.quote-divider {
		height: 1px;
		background: var(--post-border);
	}

	@media (max-width: 520px) {
		.timeline-post {
			--post-padding-x: 0.85rem;
		}
	}
</style>
