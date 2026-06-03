<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createLocalFilterPresenter,
		type WebKeywordFilter,
	} from '@flare/web-presenters/localFilter.svelte';

	const localFilter = createLocalFilterPresenter();
	let keyword = $state('');
	let forTimeline = $state(true);
	let forNotification = $state(false);
	let forSearch = $state(false);
	let isRegex = $state(false);
	let editingKeyword = $state<string | null>(null);

	const items = $derived(localFilter.items.type === 'Success' ? localFilter.items.data : []);
	const canSave = $derived(keyword.trim().length > 0);

	function edit(item: WebKeywordFilter): void {
		keyword = item.keyword;
		forTimeline = item.forTimeline;
		forNotification = item.forNotification;
		forSearch = item.forSearch;
		isRegex = item.isRegex;
		editingKeyword = item.keyword;
	}

	function resetForm(): void {
		keyword = '';
		forTimeline = true;
		forNotification = false;
		forSearch = false;
		isRegex = false;
		editingKeyword = null;
	}

	function save(): void {
		const value = keyword.trim();
		if (!value) return;

		if (editingKeyword) {
			if (editingKeyword !== value) {
				localFilter.delete(editingKeyword);
				localFilter.add(value, forTimeline, forNotification, forSearch, isRegex);
			} else {
				localFilter.update(value, forTimeline, forNotification, forSearch, isRegex);
			}
		} else {
			localFilter.add(value, forTimeline, forNotification, forSearch, isRegex);
		}
		resetForm();
	}

	function checked(event: Event): boolean {
		return (event.currentTarget as HTMLInputElement).checked;
	}
</script>

<svelte:head>
	<title>{m.settingsLocalFilterTitle()} | Flare</title>
</svelte:head>

<div class="settings-subpage bg-base-200">
	<AppTopBar title={m.settingsLocalFilterTitle()}>
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings" aria-label={m.navigateBack()}>
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
	</AppTopBar>

	<div class="settings-content">
		<section class="settings-section" aria-labelledby="local-filter-editor-heading">
			<h2 id="local-filter-editor-heading" class="section-title">
				{editingKeyword ? m.settingsLocalFilterEditTitle() : m.settingsLocalFilterAddTitle()}
			</h2>
			<div class="editor-panel rounded-box border border-base-300 bg-base-100">
				<label class="form-control">
					<span class="label-text">{m.settingsLocalFilterKeyword()}</span>
					<input
						class="input input-bordered w-full"
						type="text"
						bind:value={keyword}
						placeholder={m.settingsLocalFilterKeywordPlaceholder()}
					/>
				</label>

				<div class="target-grid">
					<label class="target-option">
						<input
							class="checkbox checkbox-sm"
							type="checkbox"
							checked={forTimeline}
							onchange={(event) => (forTimeline = checked(event))}
						/>
						<span>{m.settingsLocalFilterTimeline()}</span>
					</label>
					<label class="target-option">
						<input
							class="checkbox checkbox-sm"
							type="checkbox"
							checked={forNotification}
							onchange={(event) => (forNotification = checked(event))}
						/>
						<span>{m.settingsLocalFilterNotifications()}</span>
					</label>
					<label class="target-option">
						<input
							class="checkbox checkbox-sm"
							type="checkbox"
							checked={forSearch}
							onchange={(event) => (forSearch = checked(event))}
						/>
						<span>{m.settingsLocalFilterSearch()}</span>
					</label>
					<label class="target-option">
						<input
							class="checkbox checkbox-sm"
							type="checkbox"
							checked={isRegex}
							onchange={(event) => (isRegex = checked(event))}
						/>
						<span>{m.settingsLocalFilterRegex()}</span>
					</label>
				</div>

				<div class="editor-actions">
					{#if editingKeyword}
						<button class="btn btn-ghost btn-sm" type="button" onclick={resetForm}>
							{m.settingsLocalFilterCancelEdit()}
						</button>
					{/if}
					<button class="btn btn-primary btn-sm" type="button" disabled={!canSave} onclick={save}>
						<span>{editingKeyword ? m.settingsLocalFilterSave() : m.settingsLocalFilterAdd()}</span>
					</button>
				</div>
			</div>
		</section>

		<section class="settings-section" aria-labelledby="local-filter-list-heading">
			<h2 id="local-filter-list-heading" class="section-title">{m.settingsLocalFilterRules()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				{#if localFilter.items.type === 'Loading'}
					<div class="list-row empty-row">
						<span class="loading loading-spinner loading-sm"></span>
						<span>{m.settingsLocalFilterLoading()}</span>
					</div>
				{:else if localFilter.items.type === 'Error'}
					<div class="list-row empty-row text-error">
						<FaIcon name="CircleExclamation" size={18} />
						<span>{m.settingsLocalFilterLoadError()}</span>
					</div>
				{:else if items.length === 0}
					<div class="list-row empty-row">
						<FaIcon name="Filter" size={18} />
						<span>{m.settingsLocalFilterEmpty()}</span>
					</div>
				{:else}
					{#each items as item (item.keyword)}
						<div class="list-row filter-row">
							<span class="setting-icon bg-base-200">
								<FaIcon name="Filter" size={17} />
							</span>
							<span class="filter-copy">
								<span class="filter-keyword">{item.keyword}</span>
								<span class="filter-tags">
									{#if item.forTimeline}<span class="badge badge-ghost badge-sm">{m.settingsLocalFilterTimeline()}</span>{/if}
									{#if item.forNotification}<span class="badge badge-ghost badge-sm">{m.settingsLocalFilterNotifications()}</span>{/if}
									{#if item.forSearch}<span class="badge badge-ghost badge-sm">{m.settingsLocalFilterSearch()}</span>{/if}
									{#if item.isRegex}<span class="badge badge-warning badge-sm">{m.settingsLocalFilterRegex()}</span>{/if}
								</span>
							</span>
							<span class="row-actions">
								<button
									class="btn btn-ghost btn-square btn-sm"
									type="button"
									aria-label={m.settingsLocalFilterEditAria({ keyword: item.keyword })}
									onclick={() => edit(item)}
								>
									<FaIcon name="PenToSquare" size={14} />
								</button>
								<button
									class="btn btn-ghost btn-square btn-sm text-error"
									type="button"
									aria-label={m.settingsLocalFilterDeleteAria({ keyword: item.keyword })}
									onclick={() => localFilter.delete(item.keyword)}
								>
									<FaIcon name="Delete" size={14} />
								</button>
							</span>
						</div>
					{/each}
				{/if}
			</div>
		</section>
	</div>
</div>

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
	.editor-panel,
	.filter-copy {
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

	.editor-panel {
		padding: 1rem;
	}

	.target-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 0.55rem;
	}

	.target-option,
	.editor-actions,
	.row-actions,
	.filter-tags,
	.empty-row {
		display: flex;
		align-items: center;
		gap: 0.55rem;
	}

	.target-option {
		min-height: 2.35rem;
	}

	.editor-actions {
		justify-content: flex-end;
	}

	.empty-row {
		min-height: 4rem;
		color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
	}

	.filter-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.25rem;
		padding: 0.8rem 1rem;
	}

	.setting-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
	}

	.filter-keyword {
		font-weight: 650;
		overflow-wrap: anywhere;
	}

	.filter-tags {
		flex-wrap: wrap;
	}

	@media (max-width: 560px) {
		.target-grid,
		.filter-row {
			grid-template-columns: 1fr;
		}

		.filter-row {
			justify-items: start;
		}
	}
</style>
