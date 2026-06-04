<script lang="ts">
    import AppBackButton from "$lib/components/AppBackButton.svelte";
    import AppTopBar from "$lib/components/AppTopBar.svelte";
    import FaIcon from "$lib/components/FaIcon.svelte";
    import LoginFlowPanel from "$lib/components/login/LoginFlowPanel.svelte";
    import { m } from "$lib/paraglide/messages.js";
    import {
        createLoginServiceSelectPresenter,
        type NodeData,
        type PlatformType,
        type UiInstance,
    } from "@flare/web-presenters/loginServiceSelect.svelte";

    type LoginMethodType =
        | "OAuth"
        | "Password"
        | "CredentialImport"
        | "QrConnect";

    const serviceSelect = createLoginServiceSelectPresenter();
    const supportedMethods: LoginMethodType[] = [
        "OAuth",
        "Password",
        "CredentialImport",
        "QrConnect",
    ];

    let instanceInput = $state("");
    let selectedMethod = $state<LoginMethodType | null>(null);

    const detectedPlatform = $derived(serviceSelect.detectedPlatformType);
    const detectedNode = $derived(
        detectedPlatform.type === "Success" ? detectedPlatform.data : null,
    );
    const instances = $derived(serviceSelect.instances);
    const filteredMethods = $derived<LoginMethodType[]>(
        detectedNode ? loginMethods(detectedNode.platformType) : [],
    );
    const activeMethod = $derived(
        filteredMethods.includes(selectedMethod as LoginMethodType)
            ? selectedMethod
            : (filteredMethods[0] ?? null),
    );

    function setInput(value: string): void {
        instanceInput = value;
        selectedMethod = null;
        serviceSelect.setFilter(value);
    }

    function selectInstance(instance: UiInstance): void {
        setInput(instance.domain);
    }

    function clearInput(): void {
        setInput("");
    }

    function loginMethods(platformType: PlatformType): LoginMethodType[] {
        const methods: LoginMethodType[] = (() => {
            switch (platformType) {
                case "Mastodon":
                case "Misskey":
                    return ["OAuth"];
                case "Bluesky":
                    return ["Password", "OAuth"];
                case "xQt":
                case "VVo":
                    return [];
                case "Nostr":
                    return ["QrConnect"];
            }
        })();
        return methods.filter((method) => supportedMethods.includes(method));
    }

    function methodLabel(method: LoginMethodType): string {
        switch (method) {
            case "OAuth":
                return m.loginOauth();
            case "Password":
                return m.loginPassword();
            case "CredentialImport":
                return m.loginImportKey();
            case "QrConnect":
                return m.loginQrConnect();
        }
    }

    function normalizeHost(value: string): string {
        return value
            .trim()
            .replace(/^https?:\/\//, "")
            .replace(/\/$/, "")
            .toLowerCase();
    }

    function platformIcon(
        platformType: PlatformType | null | undefined,
    ): string {
        switch (platformType) {
            case "Mastodon":
                return "Mastodon";
            case "Bluesky":
                return "Bluesky";
            case "Misskey":
                return "Misskey";
            case "xQt":
                return "X";
            case "Nostr":
                return "Nostr";
            case "VVo":
                return "Weibo";
            default:
                return "World";
        }
    }

    function platformTitle(platformType: PlatformType): string {
        switch (platformType) {
            case "Mastodon":
                return "Mastodon";
            case "Bluesky":
                return "Bluesky";
            case "Misskey":
                return "Misskey";
            case "xQt":
                return "X";
            case "Nostr":
                return "Nostr";
            case "VVo":
                return "Weibo";
        }
    }

    function usersCountText(value: number): string {
        if (value >= 1_000_000)
            return m.serviceSelectUserCount({ count: `${(value / 1_000_000).toFixed(1)}M` });
        if (value >= 1_000) return m.serviceSelectUserCount({ count: `${Math.round(value / 1_000)}K` });
        if (value > 0) return m.serviceSelectUserCount({ count: value });
        return "";
    }

    function visibleInstances(): UiInstance[] {
        if (instances.type !== "Success") return [];
        return Array.from({ length: instances.itemCount })
            .map((_, index) => instances.peek(index))
            .filter(
                (item): item is UiInstance =>
                    item !== null && item !== undefined,
            )
            .slice(0, 20);
    }
</script>

<svelte:head>
    <title>{m.loginButton()} | Flare</title>
</svelte:head>

<div class="login-page bg-base-200">
    <AppTopBar title={m.loginButton()}>
        {#snippet start()}
            <AppBackButton />
        {/snippet}
    </AppTopBar>

    <main class="login-content">
        <section class="welcome-panel">
            <h1>{m.serviceSelectWelcomeTitle()}</h1>
            <p>
                {m.serviceSelectWelcomeMessage()}
            </p>
        </section>

        <section class="service-input-section" aria-label={m.serviceSelectLoginMethod()}>
            <label class="input input-bordered service-input">
                <span class="service-leading" aria-hidden="true">
                    {#if detectedNode}
                        <FaIcon
                            name={platformIcon(detectedNode.platformType)}
                            size={18}
                        />
                    {:else}
                        <FaIcon name="Search" size={16} />
                    {/if}
                </span>
                <input
                    type="text"
                    placeholder={m.serviceSelectInstanceInputPlaceholder()}
                    autocomplete="off"
                    autocapitalize="none"
                    spellcheck="false"
                    value={instanceInput}
                    oninput={(event) => setInput(event.currentTarget.value)}
                />
                <button
                    class="btn btn-ghost btn-square btn-xs rounded-box"
                    type="button"
                    aria-label={m.serviceSelectClearInstanceUrl()}
                    disabled={!instanceInput}
                    onclick={clearInput}
                >
                    <FaIcon name="Close" size={13} />
                </button>
            </label>
            {#if instanceInput && detectedPlatform.type === "Loading"}
                <span
                    class="loading loading-bars loading-sm text-primary service-detect-progress"
                    aria-label={m.serviceSelectDetectingPlatform()}
                ></span>
            {/if}
            <p class="service-hint">{m.serviceSelectChooseServers()}</p>
        </section>

        {#if detectedNode && instanceInput}
            {@render LoginMethodPanel(detectedNode)}
        {:else}
            {@render RecommendedInstances()}
        {/if}
    </main>
</div>

{#snippet LoginMethodPanel(node: NodeData)}
    <section
        class="login-method-panel rounded-box border border-base-300 bg-base-100"
    >
        <header class="selected-service">
            <span class="service-icon bg-base-200 text-base-content">
                <FaIcon name={platformIcon(node.platformType)} size={18} />
            </span>
            <span class="selected-copy">
                <span class="selected-title"
                    >{platformTitle(node.platformType)}</span
                >
                <span class="selected-host">{node.host}</span>
            </span>
        </header>

        {#if node.compatibleMode}
            <div class="alert alert-warning">
                <FaIcon name="CircleExclamation" size={18} />
                <span>
                    {m.serviceSelectCompatibleMode({ software: node.software })}
                </span>
            </div>
        {/if}

        {#if filteredMethods.length > 1}
            <div
                class="tabs tabs-box method-tabs"
                role="tablist"
                aria-label={m.serviceSelectLoginMethod()}
            >
                {#each filteredMethods as method (method)}
                    <button
                        class="tab"
                        class:tab-active={activeMethod === method}
                        role="tab"
                        aria-selected={activeMethod === method}
                        type="button"
                        onclick={() => (selectedMethod = method)}
                    >
                        {methodLabel(method)}
                    </button>
                {/each}
            </div>
        {/if}

        {#if activeMethod}
            {#key `${node.platformType}:${node.host}:${activeMethod}`}
                <LoginFlowPanel
                    platformType={node.platformType}
                    host={node.host}
                    methodType={activeMethod}
                />
            {/key}
        {/if}
    </section>
{/snippet}

{#snippet RecommendedInstances()}
    <section class="instances-section" aria-label={m.serviceSelectRecommendedServers()}>
        {#if instances.type === "Loading"}
            {@render InstancePlaceholders(6)}
        {:else if visibleInstances().length > 0}
            <div class="instances-grid">
                {#each visibleInstances() as instance, index (instance.type + ":" + instance.domain)}
                    <button
                        class="instance-card rounded-box border border-base-300 bg-base-100"
                        style={`animation-delay: ${Math.min(index, 8) * 22}ms;`}
                        type="button"
                        onclick={() => selectInstance(instance)}
                    >
                        {#if instance.bannerUrl}
                            <img
                                class="instance-banner"
                                src={instance.bannerUrl}
                                alt=""
                                loading="lazy"
                            />
                        {/if}
                        <div class="instance-body">
                            <div class="instance-title-row">
                                <span class="instance-icon text-base-content">
                                    {#if instance.iconUrl}
                                        <img
                                            src={instance.iconUrl}
                                            alt=""
                                            loading="lazy"
                                        />
                                    {:else}
                                        <FaIcon
                                            name={platformIcon(instance.type)}
                                            size={28}
                                        />
                                    {/if}
                                </span>
                                <div>
                                    <h2>{instance.name}</h2>
                                    <span>{instance.domain}</span>
                                </div>
                            </div>
                            {#if instance.description}
                                <p>{instance.description}</p>
                            {/if}
                            {#if usersCountText(instance.usersCount)}
                                <span class="badge badge-ghost badge-sm"
                                    >{usersCountText(instance.usersCount)}</span
                                >
                            {/if}
                        </div>
                    </button>
                {/each}
                {#if instances.type === "Success" && instances.appendState.type === "Loading"}
                    {@render InstancePlaceholderCards(3)}
                {/if}
            </div>
        {:else}
            <div
                class="empty-state rounded-box border border-base-300 bg-base-100"
            >
                <FaIcon name="CircleExclamation" size={18} />
                <span>{m.serviceSelectEmptyMessage()}</span>
            </div>
        {/if}
    </section>
{/snippet}

{#snippet InstancePlaceholders(count: number)}
    <div class="instances-grid" aria-label={m.serviceSelectLoadingServers()} aria-busy="true">
        {@render InstancePlaceholderCards(count)}
    </div>
{/snippet}

{#snippet InstancePlaceholderCards(count: number)}
    {#each Array.from({ length: count }) as _, index (index)}
        <div
            class="instance-card instance-placeholder rounded-box border border-base-300 bg-base-100"
            style={`animation-delay: ${Math.min(index, 8) * 22}ms;`}
        >
            <div class="skeleton instance-banner"></div>
            <div class="instance-body">
                <div class="instance-title-row">
                    <div class="skeleton instance-icon"></div>
                    <div class="placeholder-copy">
                        <div class="skeleton placeholder-title"></div>
                        <div class="skeleton placeholder-domain"></div>
                    </div>
                </div>
                <div class="placeholder-description">
                    <div class="skeleton"></div>
                    <div class="skeleton"></div>
                    <div class="skeleton"></div>
                </div>
                <div class="skeleton placeholder-badge"></div>
            </div>
        </div>
    {/each}
{/snippet}

<style>
    .login-page {
        min-height: 100vh;
    }

    .login-content {
        display: grid;
        gap: 1rem;
        padding: 1rem;
    }

    .welcome-panel {
        display: grid;
        justify-items: center;
        gap: 0.45rem;
        padding: 1.2rem 0.5rem 0.4rem;
        text-align: center;
    }

    .welcome-panel h1 {
        font-size: 1.55rem;
        font-weight: 720;
        line-height: 1.15;
    }

    .welcome-panel p {
        max-width: 34rem;
        color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
        font-size: 0.92rem;
        line-height: 1.45;
    }

    .service-input-section {
        display: grid;
        justify-items: center;
        gap: 0.5rem;
    }

    .service-input {
        width: min(100%, 28rem);
        gap: 0.35rem;
        transition:
            border-color 0.16s ease,
            box-shadow 0.16s ease,
            background-color 0.16s ease;
    }

    .service-detect-progress {
        width: 2.4rem;
        height: 0.8rem;
        animation: progress-in 0.16s ease-out both;
    }

    .service-input input {
        min-width: 0;
    }

    .service-leading {
        display: grid;
        width: 1.5rem;
        place-items: center;
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        transition:
            color 0.16s ease,
            transform 0.16s ease;
    }

    .service-hint {
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.82rem;
    }

    .login-method-panel {
        display: grid;
        gap: 0.9rem;
        width: min(100%, 34rem);
        justify-self: center;
        padding: 1rem;
        animation: panel-in 0.2s ease-out both;
    }

    .selected-service {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: center;
        gap: 0.75rem;
    }

    .service-icon,
    .instance-icon {
        display: grid;
        width: 2.15rem;
        height: 2.15rem;
        place-items: center;
        border-radius: var(--radius-box);
        overflow: hidden;
    }

    .selected-copy {
        display: grid;
        gap: 0.12rem;
        min-width: 0;
    }

    .selected-title {
        font-size: 0.98rem;
        font-weight: 650;
        line-height: 1.25;
    }

    .selected-host {
        overflow: hidden;
        color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
        font-size: 0.8rem;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .method-tabs {
        width: 100%;
    }

    .method-tabs > .tab {
        flex: 1 1 0;
    }

    .instances-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 18rem), 1fr));
        gap: 0.85rem;
    }

    .instances-section {
        animation: panel-in 0.2s ease-out both;
    }

    .instance-card {
        display: grid;
        overflow: hidden;
        text-align: left;
        animation: card-in 0.24s ease-out both;
        transition:
            border-color 0.15s ease,
            background-color 0.15s ease,
            box-shadow 0.15s ease,
            transform 0.15s ease;
    }

    .instance-card:hover {
        border-color: color-mix(
            in oklab,
            var(--color-primary) 45%,
            var(--color-base-300)
        );
        background: color-mix(
            in oklab,
            var(--color-base-100) 92%,
            var(--color-primary)
        );
        box-shadow: 0 0.45rem 1.25rem
            color-mix(in oklab, var(--color-base-content) 8%, transparent);
        transform: translateY(-1px);
    }

    .instance-placeholder {
        pointer-events: none;
    }

    .instance-banner {
        aspect-ratio: 16 / 5;
        width: 100%;
        object-fit: cover;
    }

    .instance-body {
        display: grid;
        gap: 0.65rem;
        padding: 0.85rem;
    }

    .instance-title-row {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr);
        align-items: center;
        gap: 0.65rem;
    }

    .instance-icon img {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }

    .instance-icon :global(.fa-icon),
    .instance-icon :global(svg) {
        max-width: 100%;
        max-height: 100%;
    }

    .instance-title-row h2 {
        overflow: hidden;
        font-size: 0.96rem;
        font-weight: 650;
        line-height: 1.25;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .instance-title-row span {
        display: block;
        overflow: hidden;
        color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
        font-size: 0.78rem;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .instance-body p {
        display: -webkit-box;
        overflow: hidden;
        color: color-mix(in oklab, var(--color-base-content) 64%, transparent);
        font-size: 0.82rem;
        line-height: 1.35;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 3;
        line-clamp: 3;
    }

    .placeholder-copy,
    .placeholder-description {
        display: grid;
        gap: 0.35rem;
        min-width: 0;
    }

    .placeholder-title {
        width: min(10rem, 78%);
        height: 1rem;
    }

    .placeholder-domain {
        width: min(7.5rem, 54%);
        height: 0.75rem;
    }

    .placeholder-description .skeleton {
        height: 0.78rem;
    }

    .placeholder-description .skeleton:nth-child(1) {
        width: 100%;
    }

    .placeholder-description .skeleton:nth-child(2) {
        width: 92%;
    }

    .placeholder-description .skeleton:nth-child(3) {
        width: 62%;
    }

    .placeholder-badge {
        width: 4.7rem;
        height: 1.25rem;
        border-radius: var(--radius-selector);
    }

    .empty-state {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        justify-self: center;
        padding: 1rem;
        color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
        animation: panel-in 0.18s ease-out both;
    }

    @keyframes panel-in {
        from {
            opacity: 0;
            transform: translateY(0.35rem);
        }

        to {
            opacity: 1;
            transform: translateY(0);
        }
    }

    @keyframes card-in {
        from {
            opacity: 0;
            transform: translateY(0.55rem) scale(0.985);
        }

        to {
            opacity: 1;
            transform: translateY(0) scale(1);
        }
    }

    @keyframes progress-in {
        from {
            opacity: 0;
            transform: scaleX(0.72);
        }

        to {
            opacity: 1;
            transform: scaleX(1);
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .service-input,
        .service-leading,
        .service-detect-progress,
        .login-method-panel,
        .instances-section,
        .instance-card,
        .empty-state {
            animation: none;
            transition: none;
        }

        .instance-card:hover {
            box-shadow: none;
            transform: none;
        }
    }
</style>
