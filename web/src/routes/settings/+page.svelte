<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
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
			title: 'Accounts',
			description: 'Manage signed-in accounts',
			icon: 'Profile',
			href: '/settings/accounts',
			status:
				accountsState.type === 'Success'
					? `${accountsState.data.length} ${accountsState.data.length === 1 ? 'account' : 'accounts'}`
					: undefined,
		},
	]);

	const appearanceItems: SettingsItem[] = [
		{
			title: 'Theme',
			description: 'Colors, font size, and avatar shape',
			icon: 'Palette',
			href: '/settings/appearance/theme',
		},
		{
			title: 'Layout',
			description: 'Timeline layout, navigation, and post actions',
			icon: 'TableList',
			href: '/settings/appearance/layout',
		},
		{
			title: 'Display',
			description: 'Timestamps, source badges, links, and browser behavior',
			icon: 'News',
			href: '/settings/appearance/display',
		},
		{
			title: 'Media',
			description: 'Media visibility, playback, and sensitive content',
			icon: 'PhotoFilm',
			href: '/settings/appearance/media',
		},
	];

	const dataItems: SettingsItem[] = [
		{
			title: 'Local Filters',
			description: 'Hide matching content in timelines, notifications, and search',
			icon: 'Filter',
		},
		{
			title: 'Storage',
			description: 'Manage cached files, logs, and backups',
			icon: 'Database',
		},
	];

	const intelligenceItems: SettingsItem[] = [
		{
			title: 'AI',
			description: 'Set up AI providers and summarization',
			icon: 'Bot',
		},
		{
			title: 'Translation',
			description: 'Configure translation provider and settings',
			icon: 'Translate',
		},
	];

	const aboutItems = $derived<SettingsItem[]>([
		{
			title: 'About',
			description: 'Learn more about Flare',
			icon: 'Info',
			status: appVersion,
		},
	]);
</script>

<div class="settings-page bg-base-200">
	<AppTopBar title="Settings" />

	<div class="settings-content">
		{@render SettingsList(accountsItems, 'Accounts')}
		{@render SettingsList(appearanceItems, 'Appearance')}
		{@render SettingsList(dataItems, 'Local data')}
		{@render SettingsList(intelligenceItems, 'Intelligence')}
		{@render SettingsList(aboutItems, 'About')}
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
