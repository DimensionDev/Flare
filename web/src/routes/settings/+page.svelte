<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { createAccountsPresenter } from '@flare/web-presenters/accounts.svelte';

	type SettingsItem = {
		title: string;
		description: string;
		icon: string;
		status?: string;
		href?: string;
	};

	const environmentSettings = useEnvironmentSettings();
	const accountsPresenter = createAccountsPresenter();
	const accountsState = $derived(accountsPresenter.accounts);
	const appSettingsState = $derived(environmentSettings.appSettings());
	const appVersion = $derived(
		appSettingsState.type === 'Success' ? appSettingsState.data.version : undefined
	);
	const accountsItems = $derived<SettingsItem[]>([
		{
			title: m.settingsAccountsTitle(),
			description: m.settingsAccountsSubtitle(),
			icon: 'Profile',
			href: '/settings/accounts',
			status:
				accountsState.type === 'Success'
					? accountsState.data.length === 1
						? m.settingsAccountCountOne()
						: m.settingsAccountCountOther({ count: accountsState.data.length })
					: undefined,
		},
	]);

	const appearanceItems = $derived<SettingsItem[]>([
		{
			title: m.settingsAppearanceThemeGroupTitle(),
			description: m.settingsAppearanceThemeGroupSubtitle(),
			icon: 'Palette',
			href: '/settings/appearance/theme',
		},
		{
			title: m.settingsAppearanceLayoutGroupTitle(),
			description: m.settingsAppearanceLayoutGroupSubtitle(),
			icon: 'TableList',
			href: '/settings/appearance/layout',
		},
		{
			title: m.settingsAppearanceDisplayGroupTitle(),
			description: m.settingsAppearanceDisplayGroupSubtitle(),
			icon: 'News',
			href: '/settings/appearance/display',
		},
		{
			title: m.settingsAppearanceMediaGroupTitle(),
			description: m.settingsAppearanceMediaGroupSubtitle(),
			icon: 'PhotoFilm',
			href: '/settings/appearance/media',
		},
	]);

	const dataItems = $derived<SettingsItem[]>([
		{
			title: m.settingsLocalFilterTitle(),
			description: m.settingsLocalFilterDescription(),
			icon: 'Filter',
		},
		{
			title: m.settingsStorageTitle(),
			description: m.settingsStorageSubtitle(),
			icon: 'Database',
		},
	]);

	const intelligenceItems = $derived<SettingsItem[]>([
		{
			title: m.settingsAiConfigTitle(),
			description: m.settingsAiConfigDescription(),
			icon: 'Bot',
		},
		{
			title: m.settingsTranslationTitle(),
			description: m.settingsTranslationDescription(),
			icon: 'Translate',
		},
	]);

	const aboutItems = $derived<SettingsItem[]>([
		{
			title: m.settingsAboutTitle(),
			description: m.settingsAboutSubtitle(),
			icon: 'Info',
			status: appVersion,
		},
	]);
</script>

<div class="settings-page bg-base-200">
	<AppTopBar title={m.settingsTitle()} />

	<div class="settings-content">
		{@render SettingsList(accountsItems, m.settingsAccountsTitle())}
		{@render SettingsList(appearanceItems, m.settingsAppearanceTitle())}
		{@render SettingsList(dataItems, m.settingsSectionLocalData())}
		{@render SettingsList(intelligenceItems, m.settingsSectionIntelligence())}
		{@render SettingsList(aboutItems, m.settingsAboutTitle())}
	</div>
</div>

{#snippet SettingsList(items: SettingsItem[], label: string)}
	<section class="settings-section" aria-labelledby={`${label}-heading`}>
		<h2 id={`${label}-heading`} class="section-title">{label}</h2>
		<div class="list rounded-box border border-base-300 bg-base-100">
			{#each items as item (item.title)}
				{#if item.href}
					<a class="list-row setting-row" href={item.href}>
						{@render SettingsItemContent(item)}
					</a>
				{:else}
					<div class="list-row setting-row">
						{@render SettingsItemContent(item)}
					</div>
				{/if}
			{/each}
		</div>
	</section>
{/snippet}

{#snippet SettingsItemContent(item: SettingsItem)}
	<span class="setting-icon bg-base-200 text-base-content">
		<FaIcon name={item.icon} size={18} />
	</span>
	<span class="setting-copy">
		<span class="setting-title">{item.title}</span>
		<span class="setting-description">{item.description}</span>
	</span>
	<span class="setting-trailing">
		{#if item.status}
			<span class="badge badge-ghost badge-sm">{item.status}</span>
		{/if}
		<FaIcon name="ChevronRight" size={13} />
	</span>
{/snippet}

<style>
	.settings-page {
		min-height: 100vh;
	}

	.settings-content {
		display: grid;
		gap: 1.2rem;
		padding: 1rem;
	}

	.settings-section {
		display: grid;
		gap: 0.45rem;
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

	.setting-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.setting-title {
		overflow-wrap: anywhere;
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.setting-description {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}

	.setting-trailing {
		display: inline-flex;
		align-items: center;
		gap: 0.45rem;
		color: color-mix(in oklab, var(--color-base-content) 42%, transparent);
	}

	@media (max-width: 560px) {
		.setting-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.setting-trailing {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
