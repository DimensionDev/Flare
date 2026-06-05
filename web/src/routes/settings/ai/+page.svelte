<script lang="ts">
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createAiConfigPresenter,
		type AiReasoningEffortOption,
		type AiTypeOption,
	} from '@flare/web-presenters/aiConfig.svelte';

	const ai = createAiConfigPresenter();

	function checked(event: Event): boolean {
		return (event.currentTarget as HTMLInputElement).checked;
	}

	function selectValue<T extends string>(event: Event): T {
		return (event.currentTarget as HTMLSelectElement).value as T;
	}

	function textValue(event: Event): string {
		return (event.currentTarget as HTMLInputElement | HTMLTextAreaElement).value;
	}

	function aiTypeLabel(value: AiTypeOption): string {
		return value === 'OnDevice' ? m.settingsAiTypeOnDevice() : m.settingsAiTypeOpenAI();
	}

	function reasoningLabel(value: AiReasoningEffortOption): string {
		switch (value) {
			case 'Low':
				return m.settingsAiReasoningLow();
			case 'Medium':
				return m.settingsAiReasoningMedium();
			case 'High':
				return m.settingsAiReasoningHigh();
			case 'Default':
				return m.settingsAiReasoningDefault();
		}
	}
</script>

<svelte:head>
	<title>{m.settingsAiConfigTitle()} | Flare</title>
</svelte:head>

<div class="settings-subpage bg-base-200">
	<AppTopBar title={m.settingsAiConfigTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}
	</AppTopBar>

	<div class="settings-content">
		<section class="settings-section" aria-labelledby="ai-provider-heading">
			<h2 id="ai-provider-heading" class="section-title">{m.settingsAiProvider()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row setting-row">
					{@render Icon('Bot')}
					{@render Copy(m.settingsAiType(), m.settingsAiTypeDescription())}
					<select
						class="select select-sm w-48 max-w-full"
						value={ai.aiType}
						onchange={(event) => ai.selectType(selectValue<AiTypeOption>(event))}
					>
						{#each ai.supportedTypes as type (type)}
							<option value={type}>{aiTypeLabel(type)}</option>
						{/each}
					</select>
				</div>
			</div>
		</section>

		{#if ai.aiType === 'OpenAI'}
			<section class="settings-section" aria-labelledby="openai-heading">
				<h2 id="openai-heading" class="section-title">{m.settingsAiOpenAI()}</h2>
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					<label class="form-control">
						<span class="label-text">{m.settingsAiServerUrl()}</span>
						<input
							class="input input-bordered w-full"
							type="url"
							list="openai-server-suggestions"
							value={ai.openAIServerUrl}
							onchange={(event) => ai.setOpenAIServerUrl(textValue(event))}
						/>
						<datalist id="openai-server-suggestions">
							{#each ai.serverSuggestions as suggestion}
								<option value={suggestion}></option>
							{/each}
						</datalist>
					</label>

					<label class="form-control">
						<span class="label-text">{m.settingsAiApiKey()}</span>
						<input
							class="input input-bordered w-full"
							type="password"
							autocomplete="off"
							value={ai.openAIApiKey}
							onchange={(event) => ai.setOpenAIApiKey(textValue(event))}
						/>
					</label>

					<label class="form-control">
						<span class="label-text">{m.settingsAiModel()}</span>
						<input
							class="input input-bordered w-full"
							type="text"
							list="openai-model-suggestions"
							value={ai.openAIModel}
							onchange={(event) => ai.setOpenAIModel(textValue(event))}
						/>
						<datalist id="openai-model-suggestions">
							{#if ai.openAIModels.type === 'Success'}
								{#each ai.openAIModels.data as model}
									<option value={model}></option>
								{/each}
							{/if}
						</datalist>
					</label>

					<div class="list-row setting-row title-only-row compact-row">
						<span class="setting-title">{m.settingsAiReasoningEffort()}</span>
						<select
							class="select select-sm w-48 max-w-full"
							value={ai.openAIReasoningEffort}
							onchange={(event) =>
								ai.setOpenAIReasoningEffort(selectValue<AiReasoningEffortOption>(event))}
						>
							{#each ai.supportedOpenAIReasoningEfforts as effort}
								<option value={effort}>{reasoningLabel(effort)}</option>
							{/each}
						</select>
					</div>

					<label class="form-control">
						<span class="label-text">{m.settingsAiExtraBody()}</span>
						<textarea
							class="textarea textarea-bordered min-h-28 w-full font-mono text-xs"
							value={ai.openAIExtraBody}
							onchange={(event) => ai.setOpenAIExtraBody(textValue(event))}
						></textarea>
					</label>
				</div>
			</section>
		{/if}

		<section class="settings-section" aria-labelledby="ai-features-heading">
			<h2 id="ai-features-heading" class="section-title">{m.settingsAiFeatures()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row setting-row">
					{@render Icon('News')}
					{@render Copy(m.settingsAiTldr(), m.settingsAiTldrDescription())}
					<input
						class="toggle toggle-primary"
						type="checkbox"
						checked={ai.aiTldr}
						onchange={(event) => ai.setAITldr(checked(event))}
					/>
				</div>
			</div>
			{#if ai.aiTldr}
				<div class="form-panel rounded-box border border-base-300 bg-base-100">
					<label class="form-control">
						<span class="label-text">{m.settingsAiTldrPrompt()}</span>
						<textarea
							class="textarea textarea-bordered min-h-32 w-full"
							value={ai.tldrPrompt}
							onchange={(event) => ai.setTldrPrompt(textValue(event))}
						></textarea>
					</label>
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

	.setting-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.35rem;
		padding: 0.8rem 1rem;
	}

	.compact-row {
		margin-inline: -1rem;
	}

	.title-only-row {
		grid-template-columns: minmax(0, 1fr) auto;
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

	@media (max-width: 560px) {
		.setting-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.setting-row > :global(select),
		.setting-row > :global(input.toggle) {
			grid-column: 2;
			justify-self: start;
		}

		.title-only-row {
			grid-template-columns: minmax(0, 1fr);
		}

		.title-only-row > :global(select) {
			grid-column: 1;
		}
	}
</style>
