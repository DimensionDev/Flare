<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createAccountsPresenter,
		type AccountsStateAccountItem,
		type MicroBlogKey,
		type PlatformType,
		type UiProfile,
	} from '@flare/web-presenters/accounts.svelte';

	const accountsPresenter = createAccountsPresenter();
	const accountsState = $derived(accountsPresenter.accounts);
	let removeDialog = $state<HTMLDialogElement | null>(null);
	let pendingRemoval = $state<AccountsStateAccountItem | null>(null);

	function profileNameText(user: UiProfile): string {
		return user.name.innerText || user.handle.canonical || user.handle.raw;
	}

	function accountTitle(item: AccountsStateAccountItem): string {
		return item.profile.type === 'Success' ? profileNameText(item.profile.data) : item.account.accountKey.id;
	}

	function accountDescription(item: AccountsStateAccountItem): string {
		return item.profile.type === 'Success'
			? item.profile.data.handle.canonical
			: accountKeyText(item.account.accountKey);
	}

	function accountKeyText(key: MicroBlogKey): string {
		return key.host ? `${key.id}@${key.host}` : key.id;
	}

	function platformIcon(platformType: PlatformType): string {
		switch (platformType) {
			case 'Mastodon':
				return 'Mastodon';
			case 'Bluesky':
				return 'Bluesky';
			case 'Misskey':
				return 'Misskey';
			case 'xQt':
				return 'X';
			case 'Nostr':
				return 'Nostr';
			case 'VVo':
				return 'Weibo';
			default:
				return 'World';
		}
	}

	function requestRemoveAccount(item: AccountsStateAccountItem): void {
		pendingRemoval = item;
		removeDialog?.showModal();
	}

	function confirmRemoveAccount(): void {
		if (!pendingRemoval) return;
		accountsPresenter.removeAccount(pendingRemoval.account.accountKey);
		pendingRemoval = null;
	}
</script>

<svelte:head>
	<title>{m.settingsAccountsTitle()} | Flare</title>
</svelte:head>

<div class="accounts-page bg-base-200">
	<AppTopBar title={m.settingsAccountsTitle()}>
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings" aria-label={m.navigateBack()}>
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
	</AppTopBar>

	<div class="accounts-content">
		<section class="accounts-section" aria-labelledby="signed-in-heading">
			<h2 id="signed-in-heading" class="section-title">{m.settingsAccountsSignedIn()}</h2>

			{#if accountsState.type === 'Success'}
				{#if accountsState.data.length > 0}
					<div class="list rounded-box border border-base-300 bg-base-100">
						{#each accountsState.data as item (accountKeyText(item.account.accountKey))}
							<div class="list-row account-row">
								{#if item.profile.type === 'Success'}
									<div class="account-user">
										<UserDisplay user={item.profile.data} variant="account" clickable={false} />
									</div>
								{:else}
									<div class="account-avatar bg-base-200 text-base-content">
										<FaIcon name={platformIcon(item.account.platformType)} size={18} />
									</div>
									<div class="account-copy">
										<span class="account-title">{accountTitle(item)}</span>
										<span class="account-description">{accountDescription(item)}</span>
									</div>
								{/if}

								<div class="account-actions">
									<button
										class="btn btn-ghost btn-square btn-sm text-error"
										type="button"
										aria-label={m.settingsAccountsRemoveAriaLabel({ name: accountTitle(item) })}
										onclick={() => requestRemoveAccount(item)}
									>
										<FaIcon name="Delete" size={14} />
									</button>
								</div>
							</div>
						{/each}
					</div>
				{:else}
					<div class="empty-state rounded-box border border-base-300 bg-base-100">
						<span class="empty-icon bg-base-200 text-base-content">
							<FaIcon name="Profile" size={18} />
						</span>
						<div>
							<h3>{m.settingsAccountsNoAccounts()}</h3>
							<p>{m.settingsAccountsNoAccountsDescription()}</p>
						</div>
					</div>
				{/if}
			{:else if accountsState.type === 'Error'}
				<div class="alert alert-error">
					<FaIcon name="CircleExclamation" size={18} />
					<span>{accountsState.message ?? m.settingsAccountsLoadError()}</span>
				</div>
			{:else}
				<div class="list rounded-box border border-base-300 bg-base-100">
					{#each Array.from({ length: 2 }) as _}
						<div class="list-row account-row">
							<div class="skeleton account-avatar"></div>
							<div class="account-copy">
								<div class="skeleton h-4 w-36"></div>
								<div class="skeleton h-3 w-48"></div>
							</div>
							<div class="skeleton h-8 w-8 rounded-box"></div>
						</div>
					{/each}
				</div>
			{/if}
		</section>

		<section class="accounts-section" aria-labelledby="add-account-heading">
			<h2 id="add-account-heading" class="section-title">{m.settingsAccountsAddTitle()}</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row account-row">
					<span class="setting-icon bg-primary/10 text-primary">
						<FaIcon name="React" size={18} />
					</span>
					<span class="account-copy">
						<span class="account-title">{m.settingsAccountsAddTitle()}</span>
						<span class="account-description">{m.settingsAccountsAddDescription()}</span>
					</span>
					<a class="btn btn-primary btn-sm" href="/login">
						<FaIcon name="React" size={13} />
						<span>{m.actionAdd()}</span>
					</a>
				</div>
			</div>
		</section>
	</div>

	<dialog
		bind:this={removeDialog}
		class="modal modal-middle"
		onclose={() => {
			pendingRemoval = null;
		}}
	>
		<div class="modal-box">
			{#if pendingRemoval}
				<h3 class="text-lg font-bold">{m.settingsAccountsRemoveTitle()}</h3>
				<p class="mt-2 text-sm text-base-content/70">
					{m.settingsAccountsRemoveConfirm()}
				</p>
				<div class="mt-4 rounded-box border border-base-300 bg-base-100 p-3">
					{#if pendingRemoval.profile.type === 'Success'}
						<UserDisplay user={pendingRemoval.profile.data} variant="account" clickable={false} />
					{:else}
						<div class="flex items-center gap-3">
							<span class="account-avatar bg-base-200 text-base-content">
								<FaIcon name={platformIcon(pendingRemoval.account.platformType)} size={18} />
							</span>
							<span class="account-copy">
								<span class="account-title">{accountTitle(pendingRemoval)}</span>
								<span class="account-description">{accountDescription(pendingRemoval)}</span>
							</span>
						</div>
					{/if}
				</div>
			{/if}
			<form method="dialog" class="modal-action">
				<button class="btn btn-ghost" type="submit">{m.loginCancel()}</button>
				<button class="btn btn-error" type="submit" onclick={confirmRemoveAccount}>
					<FaIcon name="Delete" size={14} />
					<span>{m.actionDelete()}</span>
				</button>
			</form>
		</div>
		<form method="dialog" class="modal-backdrop">
			<button>{m.loginCancel()}</button>
		</form>
	</dialog>
</div>

<style>
	.accounts-page {
		min-height: 100vh;
	}

	.accounts-content {
		display: grid;
		gap: 1.2rem;
		padding: 1rem;
	}

	.accounts-section {
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

	.account-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.35rem;
		padding: 0.8rem 1rem;
	}

	.account-avatar,
	.setting-icon,
	.empty-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
		overflow: hidden;
	}

	.account-user {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.5rem;
		grid-column: 1 / span 2;
		min-width: 0;
	}

	.account-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.account-title {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.account-description {
		overflow: hidden;
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.account-actions {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
	}

	.empty-state {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: 0.8rem;
		padding: 1rem;
	}

	.empty-state h3 {
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.empty-state p {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.82rem;
		line-height: 1.35;
	}

	@media (max-width: 560px) {
		.account-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.account-actions,
		.account-row > .btn {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
