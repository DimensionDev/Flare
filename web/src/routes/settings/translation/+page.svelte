<script lang="ts">
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import RichText from '$lib/components/RichText.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createAiConfigPresenter,
		type TranslateProviderOption,
	} from '@flare/web-presenters/aiConfig.svelte';
	import { createAiTranslationTestPresenter } from '@flare/web-presenters/aiTranslationTest.svelte';

	const ai = createAiConfigPresenter();
	const aiTranslationTest = createAiTranslationTestPresenter();

	function checked(event: Event): boolean {
		return (event.currentTarget as HTMLInputElement).checked;
	}

	function selectValue<T extends string>(event: Event): T {
		return (event.currentTarget as HTMLSelectElement).value as T;
	}

	function textValue(event: Event): string {
		return (event.currentTarget as HTMLInputElement | HTMLTextAreaElement).value;
	}

	function providerLabel(value: TranslateProviderOption): string {
		switch (value) {
			case 'AI':
				return m.settingsTranslationProviderAI();
			case 'GoogleWeb':
				return m.settingsTranslationProviderGoogleWeb();
			case 'DeepL':
				return m.settingsTranslationProviderDeepL();
			case 'GoogleCloud':
				return m.settingsTranslationProviderGoogleCloud();
			case 'LibreTranslate':
				return m.settingsTranslationProviderLibreTranslate();
		}
	}

	function excludedLanguagesValue(): string {
		return ai.autoTranslateExcludedLanguages.join(', ');
	}

	function setExcludedLanguages(value: string): void {
		ai.setAutoTranslateExcludedLanguagesText(value);
	}
</script>

<svelte:head>
	<title>{m.settingsTranslationTitle()} | Flare</title>
</svelte:head>

<div class="settings-subpage bg-base-200">
	<AppTopBar title={m.settingsTranslationTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}
	</AppTopBar>

	<div class="settings-content">
		<section class="settings-section" aria-labelledby="translation-provider-heading">
			<h2 id="translation-provider-heading" class="section-title">{m.settingsTranslationProvider()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row setting-row">
					{@render Icon('Translate')}
					{@render Copy(m.settingsTranslationProvider(), m.settingsTranslationProviderDescription())}
					<select
						class="select select-sm w-52 max-w-full"
						value={ai.translateProvider}
						onchange={(event) => ai.selectTranslateProvider(selectValue<TranslateProviderOption>(event))}
					>
						{#each ai.supportedTranslateProviders as provider (provider)}
							<option value={provider}>{providerLabel(provider)}</option>
						{/each}
					</select>
				</div>
			</div>
		</section>

		{#if ai.translateProvider === 'DeepL'}
			<section class="settings-section" aria-labelledby="deepl-heading">
				<h2 id="deepl-heading" class="section-title">{m.settingsTranslationProviderDeepL()}</h2>
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					<label class="form-control">
						<span class="label-text">{m.settingsTranslationDeepLApiKey()}</span>
						<input
							class="input input-bordered w-full"
							type="password"
							autocomplete="off"
							value={ai.deepLApiKey}
							onchange={(event) => ai.setDeepLApiKey(textValue(event))}
						/>
					</label>
					<label class="setting-row inline-row">
						{@render Icon('LockOpen')}
						{@render Copy(m.settingsTranslationDeepLPro(), m.settingsTranslationDeepLProDescription())}
						<input
							class="toggle toggle-primary"
							type="checkbox"
							checked={ai.deepLUsePro}
							onchange={(event) => ai.setDeepLUsePro(checked(event))}
						/>
					</label>
				</div>
			</section>
		{:else if ai.translateProvider === 'GoogleCloud'}
			<section class="settings-section" aria-labelledby="google-cloud-heading">
				<h2 id="google-cloud-heading" class="section-title">{m.settingsTranslationProviderGoogleCloud()}</h2>
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					<label class="form-control">
						<span class="label-text">{m.settingsTranslationGoogleCloudApiKey()}</span>
						<input
							class="input input-bordered w-full"
							type="password"
							autocomplete="off"
							value={ai.googleCloudApiKey}
							onchange={(event) => ai.setGoogleCloudApiKey(textValue(event))}
						/>
					</label>
				</div>
			</section>
		{:else if ai.translateProvider === 'LibreTranslate'}
			<section class="settings-section" aria-labelledby="libre-heading">
				<h2 id="libre-heading" class="section-title">{m.settingsTranslationProviderLibreTranslate()}</h2>
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					<label class="form-control">
						<span class="label-text">{m.settingsTranslationLibreTranslateBaseUrl()}</span>
						<input
							class="input input-bordered w-full"
							type="url"
							value={ai.libreTranslateBaseUrl}
							onchange={(event) => ai.setLibreTranslateBaseUrl(textValue(event))}
						/>
					</label>
					<label class="form-control">
						<span class="label-text">{m.settingsTranslationLibreTranslateApiKey()}</span>
						<input
							class="input input-bordered w-full"
							type="password"
							autocomplete="off"
							value={ai.libreTranslateApiKey}
							onchange={(event) => ai.setLibreTranslateApiKey(textValue(event))}
						/>
					</label>
				</div>
			</section>
		{/if}

		<section class="settings-section" aria-labelledby="translation-behavior-heading">
			<h2 id="translation-behavior-heading" class="section-title">{m.settingsTranslationBehavior()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row setting-row">
					{@render Icon('ClockRotateLeft')}
					{@render Copy(m.settingsTranslationPreTranslate(), m.settingsTranslationPreTranslateDescription())}
					<input
						class="toggle toggle-primary"
						type="checkbox"
						checked={ai.preTranslate}
						onchange={(event) => ai.setPreTranslate(checked(event))}
					/>
				</div>
				<div class="list-row setting-row">
					{@render Icon('Language')}
					{@render Copy(m.settingsTranslationShowOriginal(), m.settingsTranslationShowOriginalDescription())}
					<input
						class="toggle toggle-primary"
						type="checkbox"
						checked={ai.showOriginalWithTranslation}
						onchange={(event) => ai.setShowOriginalWithTranslation(checked(event))}
					/>
				</div>
				{#if ai.preTranslate}
					<div class="list-row setting-row">
						{@render Icon('Globe')}
						{@render Copy(m.settingsTranslationPreferPlatform(), m.settingsTranslationPreferPlatformDescription())}
						<input
							class="toggle toggle-primary"
							type="checkbox"
							checked={ai.preferPlatformTranslation}
							onchange={(event) => ai.setPreferPlatformTranslation(checked(event))}
						/>
					</div>
				{/if}
			</div>

			{#if ai.translateProvider === 'AI' || ai.preTranslate}
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					{#if ai.translateProvider === 'AI'}
						<label class="form-control">
							<span class="label-text">{m.settingsTranslationPrompt()}</span>
							<textarea
								class="textarea textarea-bordered min-h-32 w-full"
								value={ai.translatePrompt}
								onchange={(event) => ai.setTranslatePrompt(textValue(event))}
							></textarea>
						</label>

						<div class="translation-test">
							<div class="test-header">
								<span class="setting-title">{m.settingsTranslationAiTestTitle()}</span>
								<span class="setting-description">{m.settingsTranslationAiTestDescription()}</span>
							</div>

							<div class="test-block">
								<span class="test-label">{m.settingsTranslationAiTestOriginal()}</span>
								<div class="rich-test-text">
									<RichText text={aiTranslationTest.sampleText} />
								</div>
							</div>

							<button
								class="btn btn-primary btn-sm test-button"
								type="button"
								disabled={aiTranslationTest.isLoading}
								onclick={() => aiTranslationTest.runTest()}
							>
								{#if aiTranslationTest.isLoading}
									<span class="loading loading-spinner loading-xs"></span>
								{/if}
								<span>{m.settingsTranslationAiTestAction()}</span>
							</button>

							{#if aiTranslationTest.errorMessage}
								<div class="alert alert-error">
									<FaIcon name="CircleExclamation" size={18} />
									<span>{aiTranslationTest.errorMessage}</span>
								</div>
							{/if}

							{#if aiTranslationTest.translatedText}
								<div class="test-block">
									<span class="test-label">{m.settingsTranslationAiTestResult()}</span>
									<div class="rich-test-text">
										<RichText text={aiTranslationTest.translatedText} />
									</div>
								</div>
							{/if}
						</div>
					{/if}

					{#if ai.preTranslate}
						<label class="form-control">
							<span class="label-text">{m.settingsTranslationExcludedLanguages()}</span>
							<textarea
								class="textarea textarea-bordered min-h-20 w-full"
								value={excludedLanguagesValue()}
								placeholder={m.settingsTranslationExcludedLanguagesPlaceholder()}
								onchange={(event) => setExcludedLanguages(textValue(event))}
							></textarea>
						</label>
					{/if}
				</div>
			{/if}
		</section>
	</div>
</div>

{#snippet Icon(icon: string)}
	<span class="setting-icon bg-base-200"><FaIcon name={icon} size={17} /></span>
{/snippet}

{#snippet Copy(title: string, description: string)}
	<span class="setting-copy">
		<span class="setting-title">{title}</span>
		<span class="setting-description">{description}</span>
	</span>
{/snippet}

<style>
	.settings-subpage {
		min-height: 100vh;
	}

	.settings-content {
		display: grid;
		gap: 1.2rem;
		padding: 1rem;
	}

	.settings-section,
	.form-panel,
	.setting-copy {
		display: grid;
		gap: 0.55rem;
		min-width: 0;
	}

	.section-title {
		padding-inline: 0.25rem;
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.74rem;
		font-weight: 700;
		letter-spacing: 0;
		text-transform: uppercase;
	}

	.form-panel {
		padding: 1rem;
	}

	.inline-row {
		margin-inline: -1rem;
	}

	.setting-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.35rem;
		padding: 0.8rem 1rem;
	}

	.setting-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
	}

	.setting-title {
		font-weight: 650;
		overflow-wrap: anywhere;
	}

	.setting-description {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}

	.translation-test,
	.test-header,
	.test-block {
		display: grid;
		gap: 0.45rem;
		min-width: 0;
	}

	.translation-test {
		gap: 0.75rem;
		padding-top: 0.35rem;
	}

	.test-label {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.76rem;
		font-weight: 700;
		letter-spacing: 0;
	}

	.rich-test-text {
		padding: 0.75rem;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		background: var(--color-base-200);
		line-height: 1.45;
		overflow-wrap: anywhere;
	}

	.test-button {
		justify-self: start;
	}

	@media (max-width: 560px) {
		.setting-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.setting-row > :global(select),
		.setting-row > :global(input.toggle) {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
