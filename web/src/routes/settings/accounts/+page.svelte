<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import {
		createAccountsPresenter,
		type AccountsStateAccountItem,
		type MicroBlogKey,
		type PlatformType,
		type UiAccount,
		type UiProfile,
	} from '@flare/web-presenters/accounts.svelte';

	const accountsPresenter = createAccountsPresenter();
	const accountsState = $derived(accountsPresenter.accounts);
	const activeAccountState = $derived(accountsPresenter.activeAccount);
	const activeAccount = $derived(
		activeAccountState.type === 'Success' ? activeAccountState.data : null
	);

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

	function accountKeyEquals(first: MicroBlogKey | null | undefined, second: MicroBlogKey): boolean {
		return first?.id === second.id && first?.host === second.host;
	}

	function isActiveAccount(account: UiAccount): boolean {
		return accountKeyEquals(activeAccount?.accountKey, account.accountKey);
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

	function setActive(item: AccountsStateAccountItem): void {
		if (isActiveAccount(item.account)) return;
		accountsPresenter.setActiveAccount(item.account.accountKey);
	}

	function removeAccount(item: AccountsStateAccountItem): void {
		const title = accountTitle(item);
		if (!confirm(`Remove ${title} from Flare?`)) return;
		accountsPresenter.removeAccount(item.account.accountKey);
	}
</script>

<svelte:head>
	<title>Accounts | Flare</title>
</svelte:head>

<div class="accounts-page bg-base-200">
	<AppTopBar title="Accounts">
		{#snippet start()}
			<a class="btn btn-ghost btn-square btn-sm rounded-box" href="/settings" aria-label="Back to Settings">
				<FaIcon name="Back" size={16} />
			</a>
		{/snippet}
	</AppTopBar>

	<div class="accounts-content">
		<section class="accounts-section" aria-labelledby="signed-in-heading">
			<h2 id="signed-in-heading" class="section-title">Signed in</h2>

			{#if accountsState.type === 'Success'}
				{#if accountsState.data.length > 0}
					<div class="list rounded-box border border-base-300 bg-base-100">
						{#each accountsState.data as item (accountKeyText(item.account.accountKey))}
							<div class="list-row account-row">
								<div class="account-avatar bg-base-200 text-base-content">
									{#if item.profile.type === 'Success' && item.profile.data.avatar}
										<img src={item.profile.data.avatar} alt="" loading="lazy" />
									{:else}
										<FaIcon name={platformIcon(item.account.platformType)} size={18} />
									{/if}
								</div>

								<div class="account-copy">
									<div class="account-title-line">
										<span class="account-title">{accountTitle(item)}</span>
										{#if isActiveAccount(item.account)}
											<span class="badge badge-primary badge-sm">Current</span>
										{/if}
									</div>
									<span class="account-description">{accountDescription(item)}</span>
								</div>

								<div class="account-actions">
									<button
										class="btn btn-ghost btn-sm"
										type="button"
										disabled={isActiveAccount(item.account)}
										onclick={() => setActive(item)}
									>
										<FaIcon name="Check" size={13} />
										<span>Use</span>
									</button>
									<button
										class="btn btn-ghost btn-square btn-sm text-error"
										type="button"
										aria-label={`Remove ${accountTitle(item)}`}
										onclick={() => removeAccount(item)}
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
							<h3>No accounts</h3>
							<p>Add an account to start using authenticated timelines.</p>
						</div>
					</div>
				{/if}
			{:else if accountsState.type === 'Error'}
				<div class="alert alert-error">
					<FaIcon name="CircleExclamation" size={18} />
					<span>{accountsState.message ?? 'Unable to load accounts.'}</span>
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
							<div class="skeleton h-8 w-24 rounded-box"></div>
						</div>
					{/each}
				</div>
			{/if}
		</section>

		<section class="accounts-section" aria-labelledby="add-account-heading">
			<h2 id="add-account-heading" class="section-title">Add account</h2>
			<div class="list rounded-box border border-base-300 bg-base-100">
				<div class="list-row account-row">
					<span class="setting-icon bg-primary/10 text-primary">
						<FaIcon name="React" size={18} />
					</span>
					<span class="account-copy">
						<span class="account-title">Add account</span>
						<span class="account-description">Sign in with another platform or identity</span>
					</span>
					<a class="btn btn-primary btn-sm" href="/login">
						<FaIcon name="React" size={13} />
						<span>Add</span>
					</a>
				</div>
			</div>
		</section>
	</div>
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

	.account-avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.account-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.account-title-line {
		display: flex;
		align-items: center;
		gap: 0.5rem;
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
