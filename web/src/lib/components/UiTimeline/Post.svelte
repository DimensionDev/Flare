<script lang="ts">
    import { tick } from "svelte";
    import FaIcon from "$lib/components/FaIcon.svelte";
    import { useDeepLink } from "$lib/deeplink/deepLink.svelte";
    import { useEnvironmentSettings } from "$lib/environment/environmentSettings.svelte";
    import { m } from "$lib/paraglide/messages.js";
    import type { UiTimelineV2Message, UiTimelineV2Post } from "@flare/web-presenters/timeline.svelte";
    import PostActions from "./post/PostActions.svelte";
    import PostAvatar from "./post/PostAvatar.svelte";
    import PostCard from "./post/PostCard.svelte";
    import PostHeader from "./post/PostHeader.svelte";
    import PostMediaGrid from "./post/PostMediaGrid.svelte";
    import PostPoll from "./post/PostPoll.svelte";
    import PostQuote from "./post/PostQuote.svelte";
    import PostReactions from "./post/PostReactions.svelte";
    import RichText from "$lib/components/RichText.svelte";
    import PostTopMessage from "./post/PostTopMessage.svelte";
    import UiTimelinePost from "./Post.svelte";
    import {
        defaultTimelineAppearance,
        hasActionControls,
        postKey,
        shouldIgnorePostContainerClick,
    } from "./post/postUtils";

    let {
        post,
        isQuote = false,
        forceHideActions = false,
        showParents = true,
        message = null,
        inlineParents = [],
        quotes = [],
        withLeadingPadding = false,
        isParent = false,
        isDetail = false,
        showMedia = true,
    }: {
        post: UiTimelineV2Post;
        isQuote?: boolean;
        forceHideActions?: boolean;
        showParents?: boolean;
        message?: UiTimelineV2Message | null;
        inlineParents?: UiTimelineV2Post[];
        quotes?: UiTimelineV2Post[];
        withLeadingPadding?: boolean;
        isParent?: boolean;
        isDetail?: boolean;
        showMedia?: boolean;
    } = $props();

    const environmentSettings = useEnvironmentSettings();
    const deepLink = useDeepLink();
    const timelineAppearanceState = $derived(
        environmentSettings.timelineAppearance(),
    );
    const appSettingsState = $derived(environmentSettings.appSettings());
    const showOriginalWithTranslation = $derived(
        appSettingsState.type === "Success"
            ? appSettingsState.data.translateConfig.showOriginalWithTranslation
            : false,
    );
    const appearance = $derived(
        timelineAppearanceState.type === "Success"
            ? timelineAppearanceState.data
            : defaultTimelineAppearance,
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

    const showSideAvatar = $derived(
        !isDetail &&
            (!appearance.fullWidthPost || withLeadingPadding) &&
            !isQuote &&
            post.user !== null,
    );
    const visibleContentWarnings = $derived(
        post.contentWarning
            ? post.translationDisplayState === "Translated" && post.contentWarning.translation
                ? showOriginalWithTranslation
                    ? [post.contentWarning.original, post.contentWarning.translation]
                    : [post.contentWarning.translation]
                : [post.contentWarning.original]
            : [],
    );
    const visibleContents = $derived(
        post.translationDisplayState === "Translated" && post.content.translation
            ? showOriginalWithTranslation
                ? [post.content.original, post.content.translation]
                : [post.content.translation]
            : [post.content.original],
    );
    const hasContentWarning = $derived(
        visibleContentWarnings.some((content) => !content.isEmpty),
    );
    const contentVisible = $derived(
        !hasContentWarning || contentExpanded || appearance.expandContentWarning,
    );
    const lineLimit = $derived(Math.max(appearance.lineLimit || 5, 1));
    const shouldExpandTextByDefault = $derived(
        !hasContentWarning &&
            visibleContents.reduce((length, content) => length + content.innerText.length, 0) <= 500,
    );
    const shouldClampBody = $derived(
        !bodyExpanded &&
            !isDetail &&
            !isQuote &&
            !shouldExpandTextByDefault &&
            lineLimit > 0 &&
            visibleContents.some((content) => !content.isEmpty) &&
            contentVisible,
    );
    const shouldShowBodyExpand = $derived(shouldClampBody && bodyOverflows);
    const shouldShowMediaGrid = $derived(
        showMedia && post.images.length > 0 && (appearance.showMedia || mediaExpanded),
    );
    const hideSensitiveMedia = $derived(
        shouldShowMediaGrid &&
            post.sensitive &&
            !appearance.showSensitiveContent &&
            !sensitiveRevealed,
    );
    const visibleCard = $derived(
        post.card &&
            appearance.showLinkPreview &&
            post.images.length === 0 &&
            quotes.length === 0
            ? post.card
            : null,
    );
    const visibleActions = $derived(
        !forceHideActions &&
            !isQuote &&
            (isDetail || appearance.postActionStyle !== "Hidden") &&
            hasActionControls(post.actions),
    );
    const postClickable = $derived(!isDetail && deepLink.canPerformClickEvent(post.clickEvent));

    $effect(() => {
        const element = bodyTextElement;
        const shouldMeasure = shouldClampBody;
        postKey(post);
        lineLimit;
        showOriginalWithTranslation;

        if (!element || !shouldMeasure) {
            bodyOverflows = false;
            return;
        }

        tick().then(() => {
            if (bodyTextElement !== element || !shouldMeasure) return;
            bodyOverflows = Array.from(element.querySelectorAll<HTMLElement>(".post-text-block"))
                .some((block) => block.scrollHeight > block.clientHeight + 1);
        });
    });

    function togglePollOption(index: number, multiple: boolean): void {
        selectedPollOptions = multiple
            ? selectedPollOptions.includes(index)
                ? selectedPollOptions.filter((item) => item !== index)
                : [...selectedPollOptions, index]
            : [index];
    }

    function performPostClick(event: MouseEvent): void {
        if (!postClickable || shouldIgnorePostContainerClick(event)) return;

        event.stopPropagation();
        deepLink.performClickEvent(post.clickEvent);
    }

    function performPostKeydown(event: KeyboardEvent): void {
        if (!postClickable || event.defaultPrevented || event.key !== "Enter") {
            return;
        }
        if (event.target !== event.currentTarget) return;

        event.preventDefault();
        event.stopPropagation();
        deepLink.performClickEvent(post.clickEvent);
    }
</script>

<div
    class:quote={isQuote}
    class:parent={isParent}
    class:detail={isDetail}
    class:full-width={!showSideAvatar}
    class:clickable={postClickable}
    class="timeline-post"
    role="link"
    aria-disabled={postClickable ? undefined : true}
    tabindex={postClickable ? 0 : undefined}
    onclick={performPostClick}
    onkeydown={performPostKeydown}
>
    {#if message && !isQuote}
        <PostTopMessage
            {message}
            sideAvatarVisible={showSideAvatar}
        />
    {/if}

    {#if showParents && !isQuote && inlineParents.length > 0}
        <div class="parent-stack">
            {#each inlineParents as parent (postKey(parent))}
                <div class="parent-container">
                    <UiTimelinePost
                        post={parent}
                        withLeadingPadding={true}
                        isParent={true}
                    />
                    <div class="divider divider-horizontal parent-thread-line" aria-hidden="true"></div>
                </div>
            {/each}
        </div>
    {/if}

    <div class="post-grid" class:with-avatar={showSideAvatar}>
        {#if showSideAvatar && post.user}
            <PostAvatar
                user={post.user}
                placement="side"
                avatarShape={appearance.avatarShape}
            />
        {/if}

        <div class="post-content">
            <PostHeader
                {post}
                {appearance}
                sideAvatarVisible={showSideAvatar}
                quoteHeader={isQuote}
                detailHeader={isDetail}
            />

            <div class="content-stack">
                {#if post.replyToHandle}
                    <div class="reply-row">
                        <FaIcon name="Reply" size={12} />
                        <span>{m.postReplyTo({ handle: post.replyToHandle })}</span>
                    </div>
                {/if}

                {#if hasContentWarning}
                    <div class="content-warning rounded-box border border-base-300">
                        {#each visibleContentWarnings as contentWarning}
                            {#if !contentWarning.isEmpty}
                                <RichText
                                    text={contentWarning}
                                    className="warning-text"
                                />
                            {/if}
                        {/each}
                        {#if !appearance.expandContentWarning}
                            <button
                                class="btn btn-link btn-xs h-auto min-h-0 rounded-box p-0 text-button"
                                type="button"
                                onclick={() => (contentExpanded = !contentExpanded)}
                            >
                                {contentExpanded ? m.postShowLess() : m.postShowMore()}
                            </button>
                        {/if}
                    </div>
                {/if}

                {#if contentVisible && visibleContents.some((content) => !content.isEmpty)}
                    <div
                        bind:this={bodyTextElement}
                        class="post-text"
                        style={`--line-limit: ${lineLimit};`}
                    >
                        {#each visibleContents as content}
                            {#if !content.isEmpty}
                                <div class:clamped={shouldClampBody} class="post-text-block">
                                    <RichText
                                        text={content}
                                        className="rich-body"
                                    />
                                </div>
                            {/if}
                        {/each}
                    </div>
                    {#if shouldShowBodyExpand}
                        <button
                            class="btn btn-link btn-xs h-auto min-h-0 rounded-box p-0 text-button"
                            type="button"
                            onclick={() => (bodyExpanded = true)}
                        >
                            {m.postShowMore()}
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

                {#if showMedia && post.images.length > 0}
                    {#if shouldShowMediaGrid}
                        <div class="media-shell">
                            <PostMediaGrid
                                media={post.images}
                                {appearance}
                                quoteMedia={isQuote}
                            />
                            {#if hideSensitiveMedia}
                                <button
                                    class="sensitive-overlay"
                                    type="button"
                                    onclick={() => (sensitiveRevealed = true)}
                                >
                                    <FaIcon name="Image" size={18} />
                                    <span>{m.postSensitiveMedia()}</span>
                                    <span class="btn btn-primary btn-xs rounded-box sensitive-overlay-action">
                                        {m.postShow()}
                                    </span>
                                </button>
                            {:else if post.sensitive && !appearance.showSensitiveContent}
                                <button
                                    class="btn btn-neutral btn-square btn-xs rounded-box hide-sensitive-button"
                                    type="button"
                                    title={m.postHideSensitiveMedia()}
                                    onclick={() => (sensitiveRevealed = false)}
                                >
                                    <FaIcon name="Image" size={13} />
                                </button>
                            {/if}
                        </div>
                    {:else}
                        <button
                            class="btn btn-soft btn-sm rounded-box show-media-button"
                            type="button"
                            onclick={() => (mediaExpanded = true)}
                        >
                            <FaIcon name="Image" size={13} />
                            <span>{m.postShowMedia()}</span>
                        </button>
                    {/if}
                {/if}

                {#if visibleCard}
                    <PostCard card={visibleCard} {appearance} />
                {/if}

                {#if quotes.length > 0 && !isQuote}
                    <div class="quote-box rounded-box border border-base-300">
                        {#each quotes as quote, index (postKey(quote))}
                            <div class="quote-item">
                                <PostQuote {quote} {appearance} {showOriginalWithTranslation} />
                            </div>
                            {#if index < quotes.length - 1}
                                <div class="divider quote-divider" aria-hidden="true"></div>
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

                {#if isDetail}
                    <time class="detail-time" datetime={post.createdAt.full} title={post.createdAt.full}>
                        {post.createdAt.full}
                    </time>
                {/if}

                {#if visibleActions}
                    <PostActions actions={post.actions} {appearance} detailActions={isDetail} />
                {/if}
            </div>
        </div>
    </div>
</div>

<style>
    .timeline-post {
        --post-padding-x: 1rem;
        --post-padding-y: 0.75rem;
        --post-padding-bottom: 0.25rem;
        --post-gap: 0.25rem;
        --post-avatar-gap: 0.5rem;
        --avatar-size: 2.75rem;
        --post-avatar-circle-radius: 999px;
        --top-message-icon-size: 0.9375rem;
        --post-top-message-gap: 0.5rem;
        --post-parent-gap: 0.25rem;
        --post-corner-radius: var(--radius-box);
        --post-small-corner-radius: var(--radius-box);
        --post-quote-padding: 0.5rem;
        --post-embedded-radius: var(--radius-box);
        --post-bg: var(--color-base-100);
        --post-bg-soft: var(--color-base-200);
        --post-bg-muted: color-mix(
            in oklab,
            var(--color-base-200) 72%,
            transparent
        );
        --post-text: var(--color-base-content);
        --post-text-weak: color-mix(
            in oklab,
            var(--color-base-content) 52%,
            transparent
        );
        --post-text-muted: color-mix(
            in oklab,
            var(--color-base-content) 58%,
            transparent
        );
        --post-text-subtle: color-mix(
            in oklab,
            var(--color-base-content) 72%,
            transparent
        );
        --post-text-readable: color-mix(
            in oklab,
            var(--color-base-content) 90%,
            transparent
        );
        --post-primary: var(--color-primary);
        --post-primary-content: var(--color-primary-content);
        --post-primary-soft: color-mix(
            in oklab,
            var(--color-primary) 12%,
            transparent
        );
        --post-primary-avatar: color-mix(
            in oklab,
            var(--color-primary) 16%,
            transparent
        );
        --post-secondary-avatar: color-mix(
            in oklab,
            var(--color-secondary) 16%,
            transparent
        );
        --post-error: var(--color-error);
        background: var(--post-bg);
        color: var(--post-text);
        padding: var(--post-padding-y) var(--post-padding-x)
            var(--post-padding-bottom);
    }

    .timeline-post.clickable {
        cursor: pointer;
    }

    .timeline-post.detail {
        --post-padding-x: 1rem;
        --post-padding-y: 1rem;
        --post-padding-bottom: 0.75rem;
        --post-gap: 0.5rem;
        --post-avatar-gap: 0.75rem;
    }

    .timeline-post.quote {
        --post-padding-x: 0;
        --post-padding-y: 0;
        --post-padding-bottom: 0;
        --post-embedded-radius: var(--radius-box);
        --avatar-size: 2rem;
        border: 0;
        background: transparent;
        box-shadow: none;
    }

    .timeline-post.parent {
        --post-padding-x: 0;
        --post-padding-y: 0;
        --post-padding-bottom: 0;
    }

    .parent-stack {
        display: grid;
    }

    .parent-container {
        position: relative;
        min-width: 0;
        padding-bottom: var(--post-parent-gap);
    }

    .parent-thread-line {
        --divider-m: 0;
        position: absolute;
        top: var(--avatar-size);
        bottom: 0;
        left: calc(var(--avatar-size) / 2);
        z-index: 1;
        width: 1px;
        height: auto;
        margin: 0;
        pointer-events: none;
    }

    .parent-thread-line::before {
        width: 1px;
    }

    .parent-thread-line::after {
        display: none;
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
        background: var(--post-bg-muted);
        color: var(--post-text-readable);
        padding: 0.55rem 0.65rem;
    }

    .text-button {
        width: fit-content;
        color: var(--post-primary);
        font-size: 0.84rem;
        font-weight: 700;
    }

    .text-button:hover {
        text-decoration: underline;
    }

    .post-text {
        display: grid;
        gap: 0.25rem;
        color: var(--post-text-readable);
        font-size: 0.96rem;
        line-height: 1.5;
        overflow-wrap: anywhere;
    }

    .timeline-post.detail .post-text {
        font-size: 1rem;
        line-height: 1.55;
    }

    .detail-time {
        display: block;
        width: fit-content;
        color: var(--post-text-muted);
        font-size: 0.82rem;
        line-height: 1.35;
    }

    .post-text-block.clamped {
        display: -webkit-box;
        overflow: hidden;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: var(--line-limit);
        line-clamp: var(--line-limit);
    }

    .show-media-button {
        width: fit-content;
        color: var(--post-text-subtle);
        font-size: 0.82rem;
        font-weight: 700;
    }

    .media-shell {
        position: relative;
        overflow: hidden;
        border-radius: var(--post-embedded-radius);
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

    .sensitive-overlay-action {
        font-size: 0.74rem;
        letter-spacing: 0;
    }

    .hide-sensitive-button {
        position: absolute;
        top: 0.55rem;
        left: 0.55rem;
    }

    .quote-box {
        overflow: hidden;
    }

    .quote-item {
        --post-embedded-radius: var(--radius-box);
        padding: var(--post-quote-padding);
    }

    .quote-divider {
        --divider-m: 0;
        height: 1px;
        margin: 0;
    }

    .quote-divider::before,
    .quote-divider::after {
        height: 1px;
    }

    @media (max-width: 520px) {
        .timeline-post {
            --post-padding-x: 0.85rem;
        }
    }
</style>
