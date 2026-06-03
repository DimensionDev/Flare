<script lang="ts">
	import logoUrl from '$lib/assets/logo.svg';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { m } from '$lib/paraglide/messages.js';

	type AboutLink = {
		title: string;
		subtitle: string;
		icon: string;
		href: string;
	};

	const environmentSettings = useEnvironmentSettings();
	const appSettingsState = $derived(environmentSettings.appSettings());
	const appVersion = $derived(
		appSettingsState.type === 'Success' ? appSettingsState.data.version : undefined
	);
	const links = $derived<AboutLink[]>([
		{
			title: m.settingsAboutSourceCode(),
			subtitle: 'https://github.com/DimensionDev/Flare',
			icon: 'Github',
			href: 'https://github.com/DimensionDev/Flare',
		},
		{
			title: m.settingsAboutTelegram(),
			subtitle: m.settingsAboutTelegramDescription(),
			icon: 'Telegram',
			href: 'https://t.me/+VZ63fqNQXIA0MzVl',
		},
		{
			title: m.settingsAboutDiscord(),
			subtitle: m.settingsAboutDiscordDescription(),
			icon: 'Discord',
			href: 'https://discord.gg/De9NhXBryT',
		},
		{
			title: m.settingsAboutLocalization(),
			subtitle: m.settingsAboutLocalizationDescription(),
			icon: 'Translate',
			href: 'https://crowdin.com/project/flareapp',
		},
		{
			title: m.settingsPrivacyPolicy(),
			subtitle: 'https://legal.mask.io/maskbook',
			icon: 'Lock',
			href: 'https://legal.mask.io/maskbook/',
		},
	]);
</script>

<svelte:head>
	<title>{m.settingsAboutTitle()} | Flare</title>
</svelte:head>

<div class="about-page bg-base-200">
	<AppTopBar title={m.settingsAboutTitle()}>
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings" aria-label={m.navigateBack()}>
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
	</AppTopBar>

	<div class="about-content">
		<section class="about-header" aria-label={m.settingsAboutTitle()}>
			<img class="app-logo" src={logoUrl} alt="Flare" />
			<div class="about-copy">
				<h1>Flare</h1>
				<p>{m.settingsAboutDescription()}</p>
				{#if appVersion}
					<span class="version-badge">{appVersion}</span>
				{/if}
			</div>
		</section>

		<section class="settings-section" aria-labelledby="about-links-heading">
			<h2 id="about-links-heading" class="section-title">{m.settingsAboutTitle()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				{#each links as link (link.href)}
					<a class="list-row about-row" href={link.href} target="_blank" rel="noreferrer">
						<span class="setting-icon bg-base-200">
							<FaIcon name={link.icon} size={18} />
						</span>
						<span class="link-copy">
							<span class="link-title">{link.title}</span>
							<span class="link-subtitle">{link.subtitle}</span>
						</span>
						<FaIcon name="ChevronRight" size={13} />
					</a>
				{/each}
			</div>
		</section>
	</div>
</div>

<style>
	.about-page {
		min-height: 100vh;
	}

	.about-content {
		display: grid;
		gap: 1.2rem;
		padding: 1rem;
	}

	.about-header {
		display: grid;
		justify-items: center;
		gap: 0.9rem;
		padding: 1.75rem 1rem;
		text-align: center;
	}

	.app-logo {
		width: 6.5rem;
		height: 6.5rem;
		border-radius: 1.5rem;
		box-shadow: 0 1rem 2.5rem color-mix(in oklab, var(--color-base-content) 18%, transparent);
	}

	.about-copy {
		display: grid;
		justify-items: center;
		gap: 0.35rem;
		max-width: 32rem;
	}

	.about-copy h1 {
		margin: 0;
		font-size: 2rem;
		font-weight: 750;
		line-height: 1.1;
	}

	.about-copy p {
		margin: 0;
		color: color-mix(in oklab, var(--color-base-content) 64%, transparent);
		line-height: 1.45;
	}

	.version-badge {
		color: color-mix(in oklab, var(--color-base-content) 52%, transparent);
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
		font-size: 0.8rem;
	}

	.settings-section,
	.link-copy {
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

	.about-row {
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

	.link-title {
		font-weight: 650;
		overflow-wrap: anywhere;
	}

	.link-subtitle {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}
</style>
