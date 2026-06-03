<script lang="ts">
    import { browser } from "$app/environment";
    import { goto } from "$app/navigation";
    import { untrack } from "svelte";
    import {
        loginRedirectUri,
        saveLoginOAuthContext,
        type LoginMethodType,
    } from "$lib/login/oauthContext";
    import { m } from "$lib/paraglide/messages.js";
    import {
        createLoginFlowPresenter,
        type LoginFieldType,
        type PlatformType,
    } from "@flare/web-presenters/loginFlow.svelte";

    let {
        platformType,
        host,
        methodType,
    }: {
        platformType: PlatformType;
        host: string;
        methodType: LoginMethodType;
    } = $props();

    const initialPlatformType = untrack(() => platformType);
    const initialHost = untrack(() => host);
    const initialMethodType = untrack(() => methodType);
    const redirectUri = browser ? loginRedirectUri(initialPlatformType) : null;
    let qrContent = $state<string | null>(null);
    const loginFlow = createLoginFlowPresenter(
        initialPlatformType,
        initialHost,
        initialMethodType,
        redirectUri,
        (url) => {
            saveLoginOAuthContext({
                platformType: initialPlatformType,
                host: initialHost,
                methodType: initialMethodType,
            });
            globalThis.location.href = url;
        },
        () => {},
        (content) => {
            qrContent = content;
        },
        () => {
            void goto("/");
        },
    );

    function inputType(type: LoginFieldType): string {
        switch (type) {
            case "PasswordInput":
                return "password";
            case "OtpInput":
                return "text";
            case "TextInput":
            case "DisplayText":
                return "text";
        }
    }

    function perform(actionId: string, label: string): void {
        loginFlow.perform(actionId);
        if (label === "Cancel") {
            qrContent = null;
        }
    }

    function submitFirstEnabledAction(): void {
        const action = loginFlow.actions.find((item) => item.enabled);
        if (action && !loginFlow.loading) {
            perform(action.id, action.label);
        }
    }
</script>

<div class="grid w-full justify-items-center gap-3">
    {#each loginFlow.fields as field (field.id)}
        {#if field.type === "DisplayText"}
            <p class="w-full max-w-xs text-center text-sm leading-snug text-base-content/60">
                {field.value || field.label}
            </p>
        {:else}
            <label class="form-control w-full max-w-xs">
                <span class="label-text">{field.label}</span>
                <input
                    class="input input-bordered w-full"
                    type={inputType(field.type)}
                    value={field.value}
                    placeholder={field.placeholder ?? ""}
                    readonly={field.readOnly}
                    disabled={loginFlow.loading || field.readOnly}
                    autocomplete={field.id === "username" ? "username" : "off"}
                    autocapitalize="none"
                    spellcheck="false"
                    oninput={(event) =>
                        loginFlow.updateField(field.id, event.currentTarget.value)}
                    onkeydown={(event) => {
                        if (event.key === "Enter") {
                            event.preventDefault();
                            submitFirstEnabledAction();
                        }
                    }}
                />
                {#if field.error}
                    <span class="label-text-alt text-error">{field.error}</span>
                {/if}
            </label>
        {/if}
    {/each}

    {#if qrContent}
        <section
            class="grid w-full max-w-xs justify-items-center gap-3 rounded-box border border-base-300 bg-base-100 p-3"
        >
            <p class="w-full text-center text-sm leading-snug text-base-content/60">
                {m.loginQrInstruction()}
            </p>
            <div
                class="grid aspect-square w-full max-w-56 place-items-center rounded-box bg-base-200 font-bold text-base-content"
            >
                <span>QR</span>
            </div>
            <p
                class="inline-flex items-center justify-center gap-2 text-center text-sm leading-snug text-base-content/60"
            >
                <span class="loading loading-spinner loading-xs"></span>
                {m.loginWaitingConfirmation()}
            </p>
            <p class="w-full text-center text-sm leading-snug text-base-content/60">
                {m.loginConnectionLink()}
            </p>
            <p
                class="max-h-24 w-full overflow-auto break-all rounded-box bg-base-200 p-2 text-center font-mono text-xs leading-snug"
            >
                {qrContent}
            </p>
        </section>
    {/if}

    <div class="grid w-full max-w-xs gap-2">
        {#each loginFlow.actions as action (action.id)}
            <button
                class="btn btn-primary w-full"
                type="button"
                disabled={!action.enabled || loginFlow.loading}
                onclick={() => perform(action.id, action.label)}
            >
                {#if loginFlow.loading}
                    <span class="loading loading-spinner loading-xs"></span>
                {/if}
                {action.label}
            </button>
        {/each}
    </div>

    {#if loginFlow.error}
        <div class="alert alert-error w-full max-w-xs">
            <span>{loginFlow.error}</span>
        </div>
    {/if}

    {#if loginFlow.loading}
        <div class="grid w-full max-w-xs gap-2">
            <p class="text-center text-sm leading-snug text-base-content/60">
                {m.loginVerifyingCredentials()}
            </p>
            <progress
                class="progress progress-primary w-full"
                aria-label={m.loginVerifying()}
            ></progress>
        </div>
    {/if}
</div>
