<script lang="ts">
    import { browser } from "$app/environment";
    import { goto } from "$app/navigation";
    import { onMount } from "svelte";
    import AppTopBar from "$lib/components/AppTopBar.svelte";
    import FaIcon from "$lib/components/FaIcon.svelte";
    import { m } from "$lib/paraglide/messages.js";
    import {
        clearLoginOAuthContext,
        loadLoginOAuthContext,
        loginRedirectUri,
    } from "$lib/login/oauthContext";
    import { createLoginFlowPresenter } from "@flare/web-presenters/loginFlow.svelte";

    const context = browser ? loadLoginOAuthContext() : null;
    const callbackUrl = browser ? globalThis.location.href : "";
    const loginFlow = createLoginFlowPresenter(
        context?.platformType ?? "Mastodon",
        context?.host ?? "",
        context?.methodType ?? "OAuth",
        browser && context ? loginRedirectUri(context.platformType) : null,
        () => {},
        () => {},
        () => {},
        () => {
            clearLoginOAuthContext();
            void goto("/");
        },
    );

    onMount(() => {
        if (context) {
            loginFlow.resume(callbackUrl);
        }
    });
</script>

<svelte:head>
    <title>{m.loginCallbackTitle()} | Flare</title>
</svelte:head>

<div class="callback-page bg-base-200">
    <AppTopBar title={m.loginButton()}>
        {#snippet start()}
            <a
                class="btn btn-ghost btn-square btn-sm rounded-box"
                href="/login"
                aria-label={m.navigateBack()}
            >
                <FaIcon name="Back" size={16} />
            </a>
        {/snippet}
    </AppTopBar>

    <main class="callback-content">
        <section class="callback-panel rounded-box border border-base-300 bg-base-100">
            <span class="callback-icon bg-base-200 text-base-content">
                {#if loginFlow.error || !context}
                    <FaIcon name="CircleExclamation" size={22} />
                {:else}
                    <span class="loading loading-spinner loading-sm"></span>
                {/if}
            </span>
            <div class="callback-copy">
                <h1>{loginFlow.error || !context ? m.loginFailed() : m.loginSigningIn()}</h1>
                <p>
                    {#if !context}
                        {m.loginOauthContextMissing()}
                    {:else if loginFlow.error}
                        {loginFlow.error}
                    {:else}
                        {m.loginVerifyingAccount()}
                    {/if}
                </p>
            </div>
            {#if loginFlow.error || !context}
                <a class="btn btn-primary btn-sm" href="/login">{m.loginBackToLogin()}</a>
            {/if}
        </section>
    </main>
</div>

<style>
    .callback-page {
        min-height: 100vh;
    }

    .callback-content {
        display: grid;
        padding: 1rem;
    }

    .callback-panel {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.9rem;
        width: min(100%, 34rem);
        justify-self: center;
        padding: 1rem;
    }

    .callback-icon {
        display: grid;
        width: 2.4rem;
        height: 2.4rem;
        place-items: center;
        border-radius: var(--radius-box);
    }

    .callback-copy {
        display: grid;
        gap: 0.2rem;
        min-width: 0;
    }

    .callback-copy h1 {
        font-size: 1rem;
        font-weight: 680;
        line-height: 1.25;
    }

    .callback-copy p {
        color: color-mix(in oklab, var(--color-base-content) 64%, transparent);
        font-size: 0.86rem;
        line-height: 1.4;
    }

    @media (max-width: 520px) {
        .callback-panel {
            grid-template-columns: auto minmax(0, 1fr);
        }

        .callback-panel > .btn {
            grid-column: 1 / -1;
            width: 100%;
        }
    }
</style>
