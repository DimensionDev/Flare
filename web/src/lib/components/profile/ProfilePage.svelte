<script lang="ts">
    import { page } from "$app/state";
    import FaIcon from "$lib/components/FaIcon.svelte";
    import RichText from "$lib/components/RichText.svelte";
    import ProfileTimelineTabPanel from "$lib/components/profile/ProfileTimelineTabPanel.svelte";
    import TimelineLoadingPlaceholderList from "$lib/components/UiTimeline/TimelineLoadingPlaceholderList.svelte";
    import { useDeepLink } from "$lib/deeplink/deepLink.svelte";
    import { m } from "$lib/paraglide/messages.js";
    import { useReactiveRetainedPresenter } from "$lib/presenter/presenterStore.svelte";
    import {
        createProfilePresenterController,
        type ActionMenu as ProfileActionMenu,
        type ActionMenuItemTextLocalizedType,
        type ClickEvent as ProfileClickEvent,
        type FollowButtonState,
        type ProfileStateTab,
        type TimelinePresenter,
        type UiProfile,
        type UiProfileMark,
    } from "@flare/web-presenters/profile.svelte";
    import type { ClickEvent as TimelineClickEvent } from "@flare/web-presenters/timeline.svelte";

    let selectedTabId = $state<string | null>(null);

    const routeAccountKey = $derived(page.params.accountKey ?? null);
    const routeUserKey = $derived(page.params.userKey ?? null);
    const routeUserName = $derived(page.params.userName ?? null);
    const routeHost = $derived(page.params.host ?? null);
    const hasProfileRoute = $derived(
        Boolean(
            routeAccountKey && (routeUserKey || (routeUserName && routeHost)),
        ),
    );
    const profilePresenterKey = $derived(
        routeAccountKey
            ? `${routeAccountKey}:${routeUserKey ?? `handle:${routeHost}:${routeUserName}`}`
            : "empty",
    );
    const profileState = useReactiveRetainedPresenter(
        () => `profile:${profilePresenterKey}`,
        () =>
            createProfilePresenterController(
                routeAccountKey,
                routeUserKey,
                routeUserName,
                routeHost,
            ),
        { ttlMs: Infinity },
    );
    const deepLink = useDeepLink();
    const displayProfile = $derived(
        hasProfileRoute && profileState.userState.type === "Success"
            ? profileState.userState.data
            : null,
    );
    const profileActions = $derived(
        hasProfileRoute ? profileState.actions : [],
    );
    const profileTabs = $derived<ProfileStateTab[]>(
        hasProfileRoute && profileState.tabs.type === "Success"
            ? profileState.tabs.data
            : [],
    );
    const selectedTab = $derived(
        profileTabs.find(
            (tab, index) => profileTabId(tab, index) === selectedTabId,
        ) ??
            profileTabs[0] ??
            null,
    );
    const showFollowControls = $derived(
        hasProfileRoute &&
            profileState.isMe.type === "Success" &&
            !profileState.isMe.data,
    );
    const profileError = $derived(
        hasProfileRoute && profileState.userState.type === "Error"
            ? (profileState.userState.message ?? "Unable to load profile")
            : null,
    );
    const followButtonState = $derived(
        hasProfileRoute && profileState.followButtonState.type === "Success"
            ? profileState.followButtonState.data
            : null,
    );
    const followButtonLabel = $derived(
        followButtonState
            ? followLabel(followButtonState)
            : m.profileFollowButtonFollow(),
    );
    const followButtonOutlined = $derived(
        followButtonState?.type === "Following" ||
            followButtonState?.type === "Requested",
    );
    const followButtonError = $derived(followButtonState?.type === "Blocked");
    const followButtonPrimary = $derived(
        followButtonState?.type === "Follow" ||
            followButtonState?.type === "RequestFollow",
    );
    const followsYou = $derived(
        hasProfileRoute && profileState.relationState.type === "Success"
            ? profileState.relationState.data.isFans
            : false,
    );
    const profileClickable = $derived(
        Boolean(
            displayProfile &&
            deepLink.canPerformClickEvent(displayProfile.clickEvent),
        ),
    );

    $effect(() => {
        if (profileTabs.length === 0) {
            selectedTabId = null;
            return;
        }

        if (
            !selectedTabId ||
            !profileTabs.some(
                (tab, index) => profileTabId(tab, index) === selectedTabId,
            )
        ) {
            selectedTabId = profileTabId(profileTabs[0], 0);
        }
    });

    function profileNameText(user: UiProfile): string {
        return user.name.innerText || user.handle.canonical;
    }

    function profileStats(user: UiProfile) {
        return [
            { label: "Posts", value: user.matrices.statusesCountHumanized },
            {
                label: "Following",
                value: user.matrices.followsCountHumanized,
            },
            { label: "Followers", value: user.matrices.fansCountHumanized },
        ];
    }

    function profileTabId(tab: ProfileStateTab, index: number): string {
        if (tab.type === "Media") return `media_${index}`;

        switch (tab.type_) {
            case "Status":
                return `posts_${index}`;
            case "StatusWithReplies":
                return `replies_${index}`;
            case "Likes":
                return `likes_${index}`;
        }
    }

    function profileTabTitle(tab: ProfileStateTab): string {
        if (tab.type === "Media") return "Media";

        switch (tab.type_) {
            case "Status":
                return "Posts";
            case "StatusWithReplies":
                return "Replies";
            case "Likes":
                return "Likes";
        }
    }

    function timelinePresenterForTab(tab: ProfileStateTab): TimelinePresenter {
        return tab.type === "Media"
            ? tab.presenter.getMediaTimelinePresenter()
            : tab.presenter;
    }

    function followLabel(state: FollowButtonState): string {
        switch (state.type) {
            case "Follow":
                return m.profileFollowButtonFollow();
            case "RequestFollow":
                return m.profileFollowButtonRequestFollow();
            case "Requested":
                return m.profileFollowButtonRequested();
            case "Following":
                return m.profileFollowButtonFollowing();
            case "Blocked":
                return m.profileFollowButtonBlocked();
        }
    }

    function performProfileClick(event: MouseEvent): void {
        const clickEvent = displayProfile?.clickEvent;
        if (!clickEvent || !deepLink.canPerformClickEvent(clickEvent)) return;

        event.stopPropagation();
        performClickEvent(clickEvent);
    }

    function performClickEvent(clickEvent: ProfileClickEvent): void {
        deepLink.performClickEvent(clickEvent as unknown as TimelineClickEvent);
    }

    function handleFollowClick(): void {
        const user = displayProfile;
        if (!followButtonState || !user) return;

        switch (followButtonState.type) {
            case "Follow":
            case "RequestFollow":
                profileState.follow(user.key);
                break;
            case "Following":
            case "Requested":
                profileState.unfollow(user.key);
                break;
            case "Blocked":
                profileState.unblock(user.key);
                break;
        }
    }

    function performMenuAction(
        event: MouseEvent,
        action: Extract<ProfileActionMenu, { type: "Item" }>,
    ): void {
        performClickEvent(action.clickEvent);
        const menu = (event.currentTarget as HTMLElement).closest(
            "[popover]",
        ) as HTMLElement | null;
        menu?.hidePopover?.();
    }

    function profileActionLabel(
        action: Extract<ProfileActionMenu, { type: "Item" }>,
    ): string {
        if (action.text?.type === "Raw") return action.text.text;
        if (action.text?.type === "Localized")
            return localizedActionText(action.text.type_);
        return action.icon ?? "Action";
    }

    function localizedActionText(type: ActionMenuItemTextLocalizedType): string {
        switch (type) {
            case "EditUserList":
                return "Edit list";
            case "SendMessage":
                return "Message";
            case "Mute":
                return "Mute";
            case "UnMute":
                return "Unmute";
            case "Block":
                return "Block";
            case "UnBlock":
                return "Unblock";
            case "Report":
                return "Report";
            default:
                return type;
        }
    }

    function profileMarkIcon(mark: UiProfileMark): string {
        switch (mark) {
            case "Cat":
                return "Cat";
            case "Verified":
                return "Verified";
            case "Locked":
                return "Lock";
            case "Bot":
                return "Bot";
        }
    }

    function profileMarkLabel(mark: UiProfileMark): string {
        switch (mark) {
            case "Cat":
                return "Cat";
            case "Verified":
                return "Verified";
            case "Locked":
                return "Locked";
            case "Bot":
                return "Bot";
        }
    }
</script>

<svelte:head>
    <title
        >{displayProfile ? profileNameText(displayProfile) : "Profile"} |
        Flare</title
    >
</svelte:head>

<div class="profile-page">
    <header
        class="profile-app-bar border-b border-base-300 bg-base-100/95 backdrop-blur"
    >
        <button
            class="btn btn-ghost btn-square btn-sm rounded-box"
            type="button"
            aria-label="Back"
            onclick={() => history.back()}
        >
            <FaIcon name="Back" size={16} />
        </button>
        <div class="profile-app-title">
            {#if displayProfile}
                <strong>{profileNameText(displayProfile)}</strong>
                <span
                    >{displayProfile.matrices.statusesCountHumanized}
                    posts</span
                >
            {:else}
                <div class="skeleton h-4 w-32"></div>
                <div class="skeleton mt-1 h-3 w-20"></div>
            {/if}
        </div>
        {#if profileActions.length > 0}
            <button
                class="btn btn-ghost btn-square btn-sm rounded-box"
                type="button"
                aria-label="More"
                popovertarget="profile-action-menu"
                style="anchor-name: --profile-action-menu-anchor;"
            >
                <FaIcon name="More" size={16} />
            </button>
            <ul
                class="dropdown dropdown-end menu bg-base-100 rounded-box w-52 shadow-sm profile-action-menu"
                popover="auto"
                id="profile-action-menu"
                style="position-anchor: --profile-action-menu-anchor;"
            >
                {#each profileActions as action}
                    {@render ProfileActionMenuEntry(action)}
                {/each}
            </ul>
        {:else}
            <button
                class="btn btn-ghost btn-square btn-sm rounded-box"
                type="button"
                aria-label="More"
                disabled
            >
                <FaIcon name="More" size={16} />
            </button>
        {/if}
    </header>

    {#if profileError}
        <section class="profile-state">
            <div class="alert alert-error">
                <span>{profileError}</span>
            </div>
        </section>
    {:else if displayProfile}
        <section class="profile-header">
            <button
                class:clickable={profileClickable}
                class="banner-button"
                type="button"
                aria-label="Open banner"
                onclick={performProfileClick}
            >
                {#if displayProfile.banner}
                    <img src={displayProfile.banner} alt="" loading="lazy" />
                {/if}
            </button>

            <div class="profile-avatar-row">
                <button
                    class:clickable={profileClickable}
                    class="profile-avatar rounded-full bg-base-100"
                    type="button"
                    aria-label="Open avatar"
                    onclick={performProfileClick}
                >
                    {#if displayProfile.avatar}
                        <img
                            src={displayProfile.avatar}
                            alt=""
                            loading="lazy"
                        />
                    {:else}
                        <span
                            >{profileNameText(displayProfile)
                                .slice(0, 1)
                                .toUpperCase()}</span
                        >
                    {/if}
                </button>
                <div class="profile-actions">
                    {#if showFollowControls}
                        {#if profileState.followButtonState.type === "Loading"}
                            <div class="skeleton h-8 w-24 rounded-box"></div>
                        {:else if profileState.followButtonState.type === "Success"}
                            <div class="profile-follow-control">
                                <button
                                    class:btn-primary={followButtonPrimary}
                                    class:btn-outline={followButtonOutlined}
                                    class:btn-error={followButtonError}
                                    class="btn btn-sm rounded-box"
                                    type="button"
                                    onclick={handleFollowClick}
                                >
                                    {followButtonLabel}
                                </button>
                                {#if followsYou}
                                    <span>{m.profileFollowButtonIsFans()}</span>
                                {/if}
                            </div>
                        {/if}
                    {/if}
                </div>
            </div>

            <div class="profile-card rounded-box bg-base-100">
                <div class="profile-title-row">
                    <div class="profile-title">
                        <div
                            class="profile-heading"
                            role="heading"
                            aria-level="1"
                        >
                            <RichText
                                text={displayProfile.name}
                                className="profile-name-rich"
                                maxLines={1}
                            />
                        </div>
                        <div class="profile-handle-row">
                            <span>{displayProfile.handle.canonical}</span>
                            {#each displayProfile.mark as mark}
                                <span
                                    class="profile-mark"
                                    title={profileMarkLabel(mark)}
                                >
                                    <FaIcon
                                        name={profileMarkIcon(mark)}
                                        size={13}
                                    />
                                </span>
                            {/each}
                        </div>
                    </div>
                </div>

                {#if displayProfile.description && displayProfile.description.innerText}
                    <div class="profile-description">
                        <RichText
                            text={displayProfile.description}
                            className="profile-description-rich"
                        />
                    </div>
                {/if}

                <div class="profile-stats">
                    {#each profileStats(displayProfile) as stat (stat.label)}
                        <button class="stat-button" type="button">
                            <strong>{stat.value}</strong>
                            <span>{stat.label}</span>
                        </button>
                    {/each}
                </div>
            </div>
        </section>
    {:else}
        <section class="profile-header">
            <div class="skeleton banner-button"></div>
            <div class="profile-avatar-row">
                <div class="skeleton profile-avatar rounded-full"></div>
                <div class="profile-actions">
                    <div class="skeleton h-8 w-20 rounded-box"></div>
                </div>
            </div>
            <div class="profile-card rounded-box bg-base-100">
                <div class="skeleton h-5 w-40"></div>
                <div class="skeleton h-4 w-28"></div>
                <div class="skeleton h-16 w-full"></div>
            </div>
        </section>
    {/if}

    <nav
        class="profile-tabs-shell border-b border-base-300 bg-base-100"
        aria-label="Profile timelines"
    >
        <div class="tabs tabs-border profile-tabs" role="tablist">
            {#if hasProfileRoute && profileState.tabs.type === "Loading"}
                <div class="tab profile-tab">
                    <div class="skeleton h-5 w-20"></div>
                </div>
            {:else}
                {#each profileTabs as tab, index (profileTabId(tab, index))}
                    {@const tabId = profileTabId(tab, index)}
                    <button
                        class:tab-active={selectedTabId === tabId}
                        class="tab profile-tab"
                        role="tab"
                        type="button"
                        aria-selected={selectedTabId === tabId}
                        onclick={() => (selectedTabId = tabId)}
                    >
                        {profileTabTitle(tab)}
                    </button>
                {/each}
            {/if}
        </div>
    </nav>

    <section class="profile-tab-content">
        {#if hasProfileRoute && profileState.tabs.type === "Error"}
            <div class="profile-state">
                <div class="alert alert-error">
                    <span
                        >{profileState.tabs.message ??
                            "Unable to load profile tabs"}</span
                    >
                </div>
            </div>
        {:else if selectedTab}
            {@const selectedTabIndex = profileTabs.indexOf(selectedTab)}
            {@const selectedTabKey = profileTabId(selectedTab, selectedTabIndex)}
            {#key `${profilePresenterKey}:${selectedTabKey}`}
                {@const tabPresenter = timelinePresenterForTab(selectedTab)}
                <ProfileTimelineTabPanel
                    presenter={tabPresenter}
                    profileKey={profilePresenterKey}
                    tabKey={selectedTabKey}
                />
            {/key}
        {:else}
            <TimelineLoadingPlaceholderList />
        {/if}
    </section>
</div>

{#snippet ProfileActionMenuEntry(action: ProfileActionMenu)}
    {#if action.type === "Divider"}
        <li class="profile-action-menu-divider" aria-hidden="true"></li>
    {:else if action.type === "Item"}
        <li>
            <button
                class:destructive={action.color === "Red"}
                class="rounded-box"
                type="button"
                title={profileActionLabel(action)}
                onclick={(event) => performMenuAction(event, action)}
            >
                <FaIcon name={action.icon} size={14} />
                <span>{profileActionLabel(action)}</span>
            </button>
        </li>
    {:else if action.type === "Group"}
        {#each action.actions as child}
            {@render ProfileActionMenuEntry(child)}
        {/each}
    {/if}
{/snippet}

<style>
    .profile-page {
        --post-primary: var(--color-primary);
        min-height: 100vh;
        min-width: 0;
        background: var(--color-base-200);
    }

    .profile-app-bar {
        position: sticky;
        top: 0;
        z-index: 20;
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.5rem;
        min-height: 3.5rem;
        padding: 0.25rem 0.75rem;
    }

    .profile-app-title {
        display: grid;
        min-width: 0;
        line-height: 1.15;
    }

    .profile-app-title strong,
    .profile-app-title span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-app-title strong {
        font-size: 0.98rem;
        font-weight: 500;
    }

    .profile-app-title span {
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.75rem;
    }

    .profile-action-menu button span {
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-action-menu button.destructive {
        color: var(--color-error);
    }

    .profile-action-menu-divider {
        margin: 0.3rem 0.2rem;
    }

    .profile-state {
        padding: 1rem;
    }

    .profile-header {
        min-width: 0;
        padding-bottom: 0.5rem;
    }

    .banner-button {
        display: block;
        width: 100%;
        height: 150px;
        overflow: hidden;
        border: 0;
        background: var(--color-base-300);
        cursor: default;
        padding: 0;
    }

    .banner-button.clickable {
        cursor: pointer;
    }

    .banner-button img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }

    .profile-avatar-row {
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
        min-width: 0;
        margin-top: -48px;
        padding: 0 1rem;
    }

    .profile-avatar {
        display: grid;
        width: 96px;
        height: 96px;
        flex: 0 0 auto;
        overflow: hidden;
        border: 4px solid var(--color-base-100);
        cursor: default;
        padding: 0;
        place-items: center;
        color: color-mix(in oklab, var(--color-base-content) 64%, transparent);
        font-size: 1.75rem;
    }

    .profile-avatar.clickable {
        cursor: pointer;
    }

    .profile-avatar img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }

    .profile-actions {
        display: flex;
        flex: 1 1 auto;
        justify-content: flex-end;
        gap: 0.5rem;
        min-width: 0;
        padding-top: 3.5rem;
    }

    .profile-follow-control {
        display: grid;
        justify-items: center;
        gap: 0.2rem;
    }

    .profile-follow-control > span {
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.75rem;
        line-height: 1.2;
        text-align: center;
    }

    .profile-card {
        display: grid;
        gap: 0.75rem;
        margin: 0.5rem 1rem 0;
        padding: 1rem;
    }

    .profile-title-row,
    .profile-title {
        min-width: 0;
    }

    .profile-heading {
        margin: 0;
        overflow: hidden;
        font-size: 1.25rem;
        font-weight: 500;
        line-height: 1.15;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-heading :global(.profile-name-rich) {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-heading :global(.profile-name-rich > *) {
        display: inline;
        margin: 0;
    }

    .profile-handle-row {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        min-width: 0;
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.86rem;
    }

    .profile-handle-row > span:first-child {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-mark {
        display: inline-grid;
        flex: 0 0 auto;
        place-items: center;
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        line-height: 1;
    }

    .profile-mark[title="Verified"] {
        color: var(--color-primary);
    }

    .profile-description {
        min-width: 0;
        margin: 0;
        color: color-mix(in oklab, var(--color-base-content) 86%, transparent);
        font-size: 0.95rem;
        line-height: 1.5;
        overflow-wrap: anywhere;
        word-break: break-word;
    }

    .profile-description :global(.profile-description-rich) {
        min-width: 0;
        max-width: 100%;
    }

    .profile-stats {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 0.5rem;
    }

    .stat-button {
        display: grid;
        min-width: 0;
        border: 0;
        background: transparent;
        color: inherit;
        cursor: pointer;
        padding: 0.25rem 0;
        text-align: left;
    }

    .stat-button strong {
        font-size: 0.95rem;
        font-weight: 500;
        line-height: 1.2;
    }

    .stat-button span {
        overflow: hidden;
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.78rem;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .profile-tabs-shell {
        position: sticky;
        top: 3.5rem;
        z-index: 10;
        min-width: 0;
    }

    .profile-tabs {
        min-width: 0;
        overflow-x: auto;
        overflow-y: hidden;
        scrollbar-width: none;
    }

    .profile-tabs::-webkit-scrollbar {
        display: none;
    }

    .profile-tab {
        min-width: max-content;
        height: 2.75rem;
        padding-inline: 1rem;
        font-weight: 400;
        letter-spacing: 0;
    }

    .profile-tab-content {
        min-width: 0;
        background: var(--color-base-100);
    }

    @media (max-width: 520px) {
        .profile-avatar-row {
            padding-inline: 0.85rem;
        }

        .profile-card {
            margin-inline: 0.85rem;
        }

        .profile-actions {
            gap: 0.35rem;
        }
    }
</style>
