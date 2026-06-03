<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { formatBytes, getStorageOverview, type StorageOverview } from '$lib/storage/webStorage';
	import { m } from '$lib/paraglide/messages.js';
	import { onMount } from 'svelte';
	import { createDataTransferPresenter } from '@flare/web-presenters/dataTransfer.svelte';
	import { createStoragePresenter } from '@flare/web-presenters/storage.svelte';

	const storage = createStoragePresenter();
	const dataTransfer = createDataTransferPresenter(
		(data) => {
			downloadText('flare-data.json', data);
			statusMessage = m.settingsStorageExportDone();
			exportingData = false;
		},
		() => {
			statusMessage = m.settingsStorageImportDone();
			importingData = false;
			void refreshOverview();
		},
		(message) => {
			errorMessage = message;
			exportingData = false;
			importingData = false;
		}
	);
	let overview = $state<StorageOverview | null>(null);
	let loadingOverview = $state(true);
	let clearingCache = $state(false);
	let exportingData = $state(false);
	let importingData = $state(false);
	let statusMessage = $state<string | null>(null);
	let errorMessage = $state<string | null>(null);
	let importInput = $state<HTMLInputElement | null>(null);

	onMount(() => {
		refreshOverview();
	});

	async function refreshOverview(): Promise<void> {
		loadingOverview = true;
		try {
			overview = await getStorageOverview();
		} finally {
			loadingOverview = false;
		}
	}

	async function clearCache(): Promise<void> {
		clearingCache = true;
		statusMessage = null;
		errorMessage = null;
		try {
			storage.clearCache();
			await new Promise((resolve) => setTimeout(resolve, 650));
			await refreshOverview();
			statusMessage = m.settingsStorageCacheCleared();
		} catch (error) {
			errorMessage = error instanceof Error ? error.message : m.settingsStorageCacheClearFailed();
		} finally {
			clearingCache = false;
		}
	}

	function exportData(): void {
		exportingData = true;
		statusMessage = null;
		errorMessage = null;
		dataTransfer.exportData();
	}

	async function importData(event: Event): Promise<void> {
		const file = (event.currentTarget as HTMLInputElement).files?.[0];
		if (!file) return;

		importingData = true;
		statusMessage = null;
		errorMessage = null;
		try {
			const jsonContent = await file.text();
			dataTransfer.importData(jsonContent);
		} catch (error) {
			errorMessage = error instanceof Error ? error.message : m.settingsStorageImportFailed();
			importingData = false;
		} finally {
			if (importInput) importInput.value = '';
		}
	}

	function downloadText(fileName: string, text: string): void {
		const blob = new Blob([text], { type: 'application/json' });
		const url = URL.createObjectURL(blob);
		const anchor = document.createElement('a');
		anchor.href = url;
		anchor.download = fileName;
		anchor.click();
		URL.revokeObjectURL(url);
	}
</script>

<svelte:head>
	<title>{m.settingsStorageTitle()} | Flare</title>
</svelte:head>

<div class="storage-page bg-base-200">
	<AppTopBar title={m.settingsStorageTitle()}>
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings" aria-label={m.navigateBack()}>
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
	</AppTopBar>

	<div class="storage-content">
		{#if statusMessage}
			<div class="alert alert-success">
				<FaIcon name="Check" size={18} />
				<span>{statusMessage}</span>
			</div>
		{/if}

		{#if errorMessage}
			<div class="alert alert-error">
				<FaIcon name="CircleExclamation" size={18} />
				<span>{errorMessage}</span>
			</div>
		{/if}

		<section class="storage-section" aria-labelledby="storage-overview-heading">
			<h2 id="storage-overview-heading" class="section-title">{m.settingsStorageOverview()}</h2>
			<div class="stats stats-vertical rounded-box border border-base-300 bg-base-100">
				<div class="stat">
					<div class="stat-title">{m.settingsStorageUsed()}</div>
					<div class="stat-value text-lg">
						{loadingOverview ? '-' : formatBytes(overview?.usageBytes ?? null)}
					</div>
					<div class="stat-desc">
						{m.settingsStorageQuota({ value: formatBytes(overview?.quotaBytes ?? null) })}
					</div>
				</div>
				<div class="stat">
					<div class="stat-title">{m.settingsStorageCachedObjects()}</div>
					<div class="stat-value text-lg">{storage.statusCount + storage.userCount}</div>
					<div class="stat-desc">
						{m.settingsStorageCacheCounts({ posts: storage.statusCount, users: storage.userCount })}
					</div>
				</div>
			</div>
		</section>

		<section class="storage-section" aria-labelledby="storage-actions-heading">
			<h2 id="storage-actions-heading" class="section-title">{m.settingsStorageTitle()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row storage-row">
					{@render Icon('Database')}
					{@render Copy(m.settingsStorageClearDatabaseCache(), m.settingsStorageClearDatabaseCacheDescription())}
					<button class="btn btn-warning btn-sm" type="button" disabled={clearingCache} onclick={clearCache}>
						{#if clearingCache}
							<span class="loading loading-spinner loading-xs"></span>
						{:else}
							<FaIcon name="Delete" size={13} />
						{/if}
						<span>{m.settingsStorageClear()}</span>
					</button>
				</div>

				<a class="list-row storage-row" href="/settings/logs">
					{@render Icon('FileLines')}
					{@render Copy(m.settingsAppLogsTitle(), m.settingsAppLogsSubtitle())}
					<span class="storage-trailing">
						<FaIcon name="ChevronRight" size={13} />
					</span>
				</a>
			</div>
		</section>

		<section class="storage-section" aria-labelledby="storage-transfer-heading">
			<h2 id="storage-transfer-heading" class="section-title">{m.settingsStorageDataTransfer()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row storage-row">
					{@render Icon('FileExport')}
					{@render Copy(m.settingsStorageExportData(), m.settingsStorageExportDataDescription())}
					<button class="btn btn-sm" type="button" disabled={exportingData} onclick={exportData}>
						{#if exportingData}
							<span class="loading loading-spinner loading-xs"></span>
						{:else}
							<FaIcon name="FileExport" size={13} />
						{/if}
						<span>{m.settingsStorageExport()}</span>
					</button>
				</div>

				<label class="list-row storage-row">
					{@render Icon('FileImport')}
					{@render Copy(m.settingsStorageImportData(), m.settingsStorageImportDataDescription())}
					<span class="btn btn-sm" aria-disabled={importingData}>
						{#if importingData}
							<span class="loading loading-spinner loading-xs"></span>
						{:else}
							<FaIcon name="FileImport" size={13} />
						{/if}
						<span>{m.settingsStorageImport()}</span>
					</span>
					<input
						bind:this={importInput}
						class="hidden"
						type="file"
						accept="application/json,.json"
						disabled={importingData}
						onchange={importData}
					/>
				</label>
			</div>
		</section>
	</div>
</div>

{#snippet Icon(icon: string)}
	<span class="storage-icon bg-base-200 text-base-content">
		<FaIcon name={icon} size={18} />
	</span>
{/snippet}

{#snippet Copy(title: string, description: string)}
	<span class="storage-copy">
		<span class="storage-title">{title}</span>
		<span class="storage-description">{description}</span>
	</span>
{/snippet}

<style>
	.storage-page {
		min-height: 100vh;
	}

	.storage-content {
		display: grid;
		gap: 1rem;
		padding: 1rem;
	}

	.storage-section {
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

	.storage-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.35rem;
		padding: 0.8rem 1rem;
	}

	.storage-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
	}

	.storage-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.storage-title {
		overflow-wrap: anywhere;
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.storage-description {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}

	.storage-trailing {
		display: inline-flex;
		color: color-mix(in oklab, var(--color-base-content) 42%, transparent);
	}

	@media (max-width: 560px) {
		.storage-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.storage-row > :global(.btn),
		.storage-trailing {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
