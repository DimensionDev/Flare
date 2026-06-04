<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import DiscoverSection from '$lib/components/discover/DiscoverSection.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { useReactiveRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import {
		createSearchPresenterController,
		type AccountType,
		type MicroBlogKey,
		type PagingState,
		type UiProfile as SearchUiProfile,
	} from '@flare/web-presenters/search.svelte';
	import type {
		PagingState as TimelinePagingState,
		UiProfile as TimelineUiProfile,
		UiTimelineV2,
	} from '@flare/web-presenters/timeline.svelte';

	const accountSegment = $derived(initialAccountFromUrl());
	const searchQuerySegment = $derived(initialQueryFromUrl());
	const accountType = $derived(parseAccountType(accountSegment));
	const search = useReactiveRetainedPresenter(
		() => `search:page:${accountSegment}:${searchQuerySegment}`,
		() => createSearchPresenterController(accountType, searchQuerySegment),
		{ ttlMs: 0 }
	);

	let query = $state(initialQueryFromUrl());
	let showAccountMenu = $state(false);
	const accounts = $derived(search.accounts.type === 'Success' ? search.accounts.data : []);
	const hasMultipleAccounts = $derived(accounts.length > 1);
	const hasQuery = $derived(query.trim().length > 0);
	const refreshing = $derived(
		(search.users.type === 'Success' && search.users.isRefreshing) ||
			(search.status.type === 'Success' && search.status.isRefreshing)
	);

	$effect(() => {
		const next = searchQuerySegment;
		if (next === query) return;
		query = next;
		search.search(next);
	});

	$effect(() => {
		if (accountType.type !== 'Specific' || search.accounts.type !== 'Success') return;
		const selected = search.selectedAccount;
		if (
			selected?.key.id === accountType.accountKey.id &&
			selected?.key.host === accountType.accountKey.host
		) {
			return;
		}
		search.setAccountKey(accountType.accountKey);
	});

	function initialQueryFromUrl(): string {
		return page.url.searchParams.get('q') ?? '';
	}

	function initialAccountFromUrl(): string {
		return page.url.searchParams.get('account') ?? 'guest';
	}

	function submitSearch(): void {
		const next = query.trim();
		search.search(next);
		void goto(searchUrl(next, accountSegment), {
			keepFocus: true,
			noScroll: true,
			replaceState: true,
		});
	}

	function selectAccount(profile: SearchUiProfile): void {
		search.setAccountKey(profile.key);
		showAccountMenu = false;
		void goto(searchUrl(query.trim(), accountSegmentFromKey(profile.key)), {
			keepFocus: true,
			noScroll: true,
			replaceState: true,
		});
		if (query.trim()) {
			search.search(query.trim());
		}
	}

	function searchUrl(nextQuery: string, nextAccountSegment: string): string {
		const params = new URLSearchParams();
		if (nextAccountSegment !== 'guest') {
			params.set('account', nextAccountSegment);
		}
		if (nextQuery) {
			params.set('q', nextQuery);
		}
		const queryString = params.toString();
		return queryString ? `/search?${queryString}` : '/search';
	}

	function parseAccountType(value: string): AccountType {
		if (!value || value === 'guest') return { type: 'Guest' };
		if (value.startsWith('guest@')) {
			return { type: 'GuestHost', host: value.slice('guest@'.length) };
		}
		return { type: 'Specific', accountKey: parseMicroBlogKey(value) };
	}

	function parseMicroBlogKey(value: string): MicroBlogKey {
		let escaping = false;
		let idFinished = false;
		let id = '';
		let host = '';

		for (const char of value) {
			if (escaping) {
				if (idFinished) host += char;
				else id += char;
				escaping = false;
				continue;
			}

			if (char === '\\') {
				escaping = true;
				continue;
			}

			if (char === '@' && !idFinished) {
				idFinished = true;
				continue;
			}

			if (char === ',') break;

			if (idFinished) host += char;
			else id += char;
		}

		return { id, host };
	}

	function accountSegmentFromKey(key: MicroBlogKey): string {
		return key.host ? `${escapeMicroBlogKeyPart(key.id)}@${escapeMicroBlogKeyPart(key.host)}` : key.id;
	}

	function escapeMicroBlogKeyPart(value: string): string {
		return value.replace(/[\\@,]/g, (char) => `\\${char}`);
	}

	function indexes<T>(state: PagingState<T>, limit: number): number[] {
		if (state.type !== 'Success') return [];
		return Array.from({ length: Math.min(state.itemCount, limit) }, (_, index) => index);
	}

	function requestItem<T>(state: PagingState<T>, index: number): void {
		if (state.type !== 'Success') return;
		state.get(index);
	}

	function loadWhenVisible<T>(node: HTMLElement, params: { state: PagingState<T>; index: number }) {
		let current = params;
		let observer: IntersectionObserver | null = null;

		const request = () => requestItem(current.state, current.index);

		if (typeof IntersectionObserver === 'undefined') {
			queueMicrotask(request);
			return {
				update(next: { state: PagingState<T>; index: number }) {
					current = next;
					queueMicrotask(request);
				},
			};
		}

		observer = new IntersectionObserver((entries) => {
			if (entries.some((entry) => entry.isIntersecting)) request();
		});
		observer.observe(node);

		return {
			update(next: { state: PagingState<T>; index: number }) {
				current = next;
			},
			destroy() {
				observer?.disconnect();
			},
		};
	}
</script>

	<div class="search-page">
		<AppTopBar>
			{#snippet start()}
				<AppBackButton iconSize={14} />
					<form class="search-shell" role="search" onsubmit={(event) => { event.preventDefault(); submitSearch(); }}>
						<label class="input input-bordered input-sm search-input rounded-box">
							<FaIcon name="Search" size={14} />
							<input
								bind:value={query}
								type="search"
								placeholder={m.discoverSearchPlaceholder()}
								aria-label={m.discoverSearchPlaceholder()}
							/>
						</label>
					</form>
			{/snippet}

		{#snippet end()}
			{#if search.selectedAccount}
				<div class="dropdown dropdown-end account-dropdown">
					<button
						class="btn btn-ghost btn-square btn-sm rounded-box"
						type="button"
						aria-label={search.selectedAccount.handle.canonical}
						onclick={() => {
							showAccountMenu = !showAccountMenu;
						}}
					>
						<img class="account-avatar" src={search.selectedAccount.avatar} alt="" />
					</button>
					{#if showAccountMenu && hasMultipleAccounts}
						<ul class="menu dropdown-content z-20 mt-2 w-64 rounded-box border border-base-300 bg-base-100 p-2 shadow-lg">
							{#each accounts as account (account.key.host + ':' + account.key.id)}
								<li>
									<button type="button" onclick={() => selectAccount(account)}>
										<img class="menu-avatar" src={account.avatar} alt="" />
										<span>{account.handle.canonical}</span>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
			{/if}
		{/snippet}

		{#snippet bottom()}
			{#if refreshing}
				<progress class="progress progress-primary block h-0.5 w-full"></progress>
			{/if}
		{/snippet}
	</AppTopBar>

	<section class="search-content">
		{#if hasMultipleAccounts}
			<div class="account-chips">
				{#each accounts as account (account.key.host + ':' + account.key.id)}
					<button
						class:btn-primary={search.selectedAccount?.key.id === account.key.id && search.selectedAccount?.key.host === account.key.host}
						class:btn-outline={search.selectedAccount?.key.id !== account.key.id || search.selectedAccount?.key.host !== account.key.host}
						class="btn btn-sm rounded-box"
						type="button"
						onclick={() => selectAccount(account)}
					>
						<img class="chip-avatar" src={account.avatar} alt="" />
						<span>{account.handle.canonical}</span>
					</button>
				{/each}
			</div>
		{/if}

		{#if hasQuery}
			<DiscoverSection title={m.searchUsers()} state={search.users}>
				<div class="user-row">
					{#if search.users.type === 'Loading'}
						{#each Array.from({ length: 4 }) as _, index (index)}
							<div class="user-card card rounded-box border border-base-300 bg-base-100">
								{@render UserSkeleton()}
							</div>
						{/each}
					{:else}
						{#each indexes(search.users, 12) as index (index)}
							{@const user = search.users.type === 'Success' ? search.users.peek(index) : null}
							<div class="user-card card rounded-box border border-base-300 bg-base-100" use:loadWhenVisible={{ state: search.users, index }}>
								{#if user}
									<UserDisplay user={user as unknown as TimelineUiProfile} variant="compact" />
								{:else}
									{@render UserSkeleton()}
								{/if}
							</div>
						{/each}
					{/if}
				</div>
			</DiscoverSection>

			<DiscoverSection title={m.searchStatus()} state={search.status}>
				<TimelineList listState={search.status as unknown as TimelinePagingState<UiTimelineV2>} />
			</DiscoverSection>
		{/if}
	</section>
</div>

{#snippet UserSkeleton()}
	<div class="user-skeleton">
		<div class="skeleton h-11 w-11 rounded-full"></div>
		<div class="grid min-w-0 flex-1 gap-2">
			<div class="skeleton h-4 w-32"></div>
			<div class="skeleton h-3 w-24"></div>
		</div>
	</div>
{/snippet}

<style>
	.search-page {
		display: grid;
		min-height: 100vh;
		min-width: 0;
		grid-template-rows: auto minmax(0, 1fr);
		background: var(--color-base-200);
	}

	.search-shell {
		display: flex;
		width: 100%;
		min-width: 0;
		align-items: center;
		gap: 0.4rem;
	}

	.search-input {
		flex: 1 1 auto;
		min-width: 0;
	}

	.search-input input {
		min-width: 0;
	}

	.account-avatar,
	.menu-avatar,
	.chip-avatar {
		flex: 0 0 auto;
		border-radius: 999px;
		object-fit: cover;
		background: var(--color-base-300);
	}

	.account-avatar {
		width: 1.8rem;
		height: 1.8rem;
	}

	.menu-avatar,
	.chip-avatar {
		width: 1.25rem;
		height: 1.25rem;
	}

	.search-content {
		display: grid;
		align-content: start;
		min-width: 0;
		gap: 0.75rem;
		padding: 0.75rem;
	}

	.account-chips {
		display: flex;
		min-width: 0;
		gap: 0.5rem;
		overflow-x: auto;
		padding-bottom: 0.1rem;
		scrollbar-width: none;
	}

	.account-chips::-webkit-scrollbar,
	.user-row::-webkit-scrollbar {
		display: none;
	}

	.account-chips .btn {
		flex: 0 0 auto;
		max-width: 13rem;
	}

	.account-chips .btn span {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.user-row {
		display: grid;
		grid-auto-columns: minmax(15rem, 16rem);
		grid-auto-flow: column;
		gap: 0.5rem;
		overflow-x: auto;
		padding-bottom: 0.15rem;
		scrollbar-width: none;
	}

	.user-card {
		min-width: 0;
		overflow: hidden;
	}

	.user-skeleton {
		display: flex;
		align-items: center;
		gap: 0.6rem;
		min-height: 3.75rem;
		padding: 0.6rem 0.75rem;
	}

	:global(.search-page .timeline-list.card-mode),
	:global(.search-page .card-mode .timeline-list-body),
	:global(.search-page .timeline-loading-placeholder-list.card-mode),
	:global(.search-page .card-mode .timeline-placeholder-body) {
		background: transparent;
	}

	:global(.search-page .card-mode .timeline-list-body),
	:global(.search-page .card-mode .timeline-placeholder-body) {
		padding: 0;
	}

	@media (max-width: 820px) {
		.account-chips {
			display: none;
		}
	}
</style>
