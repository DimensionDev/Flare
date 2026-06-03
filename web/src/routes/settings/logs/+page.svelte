<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { createDevModePresenter } from '@flare/web-presenters/devMode.svelte';

	const devMode = createDevModePresenter();
	const logs = $derived(devMode.messages.toReversed());

	function logBadgeClass(message: string): string {
		const normalized = message.toLowerCase();
		if (normalized.includes('error') || normalized.includes('exception') || normalized.includes('failed')) {
				return 'badge-error';
		}
		if (normalized.includes('warn')) return 'badge-warning';
		if (normalized.includes('debug')) return 'badge-info';
		return 'badge-ghost';
	}

	function exportLogs(): void {
		const text = devMode.printMessageToString();
		const blob = new Blob([text], { type: 'text/plain' });
		const url = URL.createObjectURL(blob);
		const anchor = document.createElement('a');
		anchor.href = url;
		anchor.download = `flare-log-${Date.now()}.txt`;
		anchor.click();
		URL.revokeObjectURL(url);
	}
</script>

<svelte:head>
	<title>{m.settingsAppLogsTitle()} | Flare</title>
</svelte:head>

<div class="logs-page bg-base-200">
	<AppTopBar title={m.settingsAppLogsTitle()} subtitle={m.settingsAppLogsCount({ count: logs.length })}>
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings/storage" aria-label={m.navigateBack()}>
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
		{#snippet end()}
			<button class="btn btn-ghost btn-square btn-sm rounded-box" type="button" aria-label={m.settingsStorageExport()} onclick={exportLogs}>
				<FaIcon name="FileExport" size={15} />
			</button>
			<button class="btn btn-ghost btn-square btn-sm rounded-box text-error" type="button" aria-label={m.actionDelete()} onclick={devMode.clear}>
				<FaIcon name="Delete" size={15} />
			</button>
		{/snippet}
	</AppTopBar>

	<div class="logs-content">
		<label class="list-row log-setting rounded-box border border-base-300 bg-base-100">
			<span class="empty-icon bg-base-200 text-base-content">
				<FaIcon name="FileLines" size={18} />
			</span>
			<span class="log-setting-copy">
				<span>{m.settingsAppLogsNetworkLogging()}</span>
				<small>{m.settingsAppLogsNetworkLoggingDescription()}</small>
			</span>
			<input
				class="toggle toggle-sm"
				type="checkbox"
				checked={devMode.enabled}
				onchange={(event) => devMode.setEnabled(event.currentTarget.checked)}
			/>
		</label>

		{#if logs.length > 0}
			<div class="list rounded-box border border-base-300 bg-base-100">
				{#each logs as log, index (`${index}-${log}`)}
					<article class="list-row log-row">
						<span class={`badge badge-sm ${logBadgeClass(log)}`}>log</span>
						<div class="log-copy">
							<p>{log}</p>
						</div>
					</article>
				{/each}
			</div>
		{:else}
			<div class="empty-state rounded-box border border-base-300 bg-base-100">
				<span class="empty-icon bg-base-200 text-base-content">
					<FaIcon name="FileLines" size={18} />
				</span>
				<div>
					<h2>{m.settingsAppLogsEmpty()}</h2>
					<p>{m.settingsAppLogsEmptyDescription()}</p>
				</div>
			</div>
		{/if}
	</div>
</div>

<style>
	.logs-page {
		min-height: 100vh;
	}

	.logs-content {
		display: grid;
		gap: 1rem;
		padding: 1rem;
	}

	.log-setting {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		padding: 0.8rem 1rem;
	}

	.log-setting-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.log-setting-copy span {
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.log-setting-copy small {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
	}

	.log-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: start;
		gap: 0.75rem;
		padding: 0.8rem 1rem;
	}

	.log-copy {
		display: grid;
		gap: 0.25rem;
		min-width: 0;
	}

	.log-copy p {
		display: -webkit-box;
		overflow: hidden;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
		-webkit-box-orient: vertical;
		-webkit-line-clamp: 5;
		line-clamp: 5;
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
		font-size: 0.78rem;
		line-height: 1.45;
	}

	.empty-state {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: 0.75rem;
		padding: 1rem;
	}

	.empty-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
	}

	.empty-state h2 {
		font-size: 0.96rem;
		font-weight: 650;
	}

	.empty-state p {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.35;
	}

	@media (max-width: 560px) {
		.log-setting {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.log-setting > :global(.toggle) {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
