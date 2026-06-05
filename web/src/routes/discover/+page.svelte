<script lang="ts">
	import { goto } from '$app/navigation';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import DiscoverSection from '$lib/components/discover/DiscoverSection.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { useReactiveRetainedPresenter, useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import {
		createDiscoverPresenterController,
		type PagingState as DiscoverPagingState,
		type UiHashtag,
		type UiProfile as DiscoverUiProfile,
	} from '@flare/web-presenters/discover.svelte';
	import {
		createSearchPresenterController,
		type AccountType,
		type MicroBlogKey,
		type UiProfile as SearchUiProfile,
	} from '@flare/web-presenters/search.svelte';
	import type {
		PagingState as TimelinePagingState,
		UiProfile as TimelineUiProfile,
		UiTimelineV2,
	} from '@flare/web-presenters/timeline.svelte';

	const discover = useRetainedPresenter(
		'discover',
		() => createDiscoverPresenterController(),
		{ ttlMs: Infinity }
	);

	let query = $state('');
	let submittedQuery = $state('');
	let showAccountMenu = $state(false);
	const isSearching = $derived(submittedQuery.trim().length > 0);
	const accountKey = $derived(accountTypeKey(discover.selectedAccountType));
	const search = useReactiveRetainedPresenter(
		() => `discover:search:${accountKey}`,
		() => createSearchPresenterController(discover.selectedAccountType, submittedQuery),
		{ ttlMs: 0 }
	);
	const accounts = $derived(discover.accounts.type === 'Success' ? discover.accounts.data : []);
	const hasMultipleAccounts = $derived(accounts.length > 1);

	$effect(() => {
		if (!isSearching) return;
		search.search(submittedQuery);
	});

	function submitSearch(): void {
		const next = query.trim();
		submittedQuery = next;
		if (next) {
			search.search(next);
			void goto(searchUrl(next));
		}
	}

	function clearSearch(): void {
		query = '';
		submittedQuery = '';
	}

	function selectAccount(profile: DiscoverUiProfile): void {
		discover.setAccountKey(profile.key);
		showAccountMenu = false;
	}

	function searchHashtag(hashtag: UiHashtag): void {
		query = hashtag.searchContent;
		submittedQuery = hashtag.searchContent;
		search.search(hashtag.searchContent);
		void goto(searchUrl(hashtag.searchContent));
	}

	function searchUrl(nextQuery: string): string {
		const params = new URLSearchParams();
		const account = accountSegment(discover.selectedAccountType);
		if (account !== 'guest') {
			params.set('account', account);
		}
		params.set('q', nextQuery);
		return `/search?${params.toString()}`;
	}

	function accountSegment(accountType: AccountType): string {
		switch (accountType.type) {
			case 'Specific':
				return accountSegmentFromKey(accountType.accountKey);
			case 'GuestHost':
				return `guest@${accountType.host}`;
			case 'Guest':
				return 'guest';
		}
	}

	function accountSegmentFromKey(key: MicroBlogKey): string {
		return key.host ? `${escapeMicroBlogKeyPart(key.id)}@${escapeMicroBlogKeyPart(key.host)}` : key.id;
	}

	function escapeMicroBlogKeyPart(value: string): string {
		return value.replace(/[\\@,]/g, (char) => `\\${char}`);
	}

	function accountTypeKey(accountType: AccountType): string {
		switch (accountType.type) {
			case 'Specific':
				return `specific:${accountType.accountKey.host}:${accountType.accountKey.id}`;
			case 'GuestHost':
				return `guest-host:${accountType.host}`;
			case 'Guest':
				return 'guest';
		}
	}

	function indexes<T>(state: DiscoverPagingState<T>, limit: number): number[] {
		if (state.type !== 'Success') return [];
		return Array.from({ length: Math.min(state.itemCount, limit) }, (_, index) => index);
	}

	function requestItem<T>(state: DiscoverPagingState<T>, index: number): void {
		if (state.type !== 'Success') return;
		state.get(index);
	}

	function loadWhenVisible<T>(node: HTMLElement, params: { state: DiscoverPagingState<T>; index: number }) {
		let current = params;
		let observer: IntersectionObserver | null = null;

		const request = () => requestItem(current.state, current.index);

		if (typeof IntersectionObserver === 'undefined') {
			queueMicrotask(request);
			return {
				update(next: { state: DiscoverPagingState<T>; index: number }) {
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
			update(next: { state: DiscoverPagingState<T>; index: number }) {
				current = next;
			},
			destroy() {
				observer?.disconnect();
			},
		};
	}
</script>

<div class="discover-page">
	<AppTopBar>
		{#snippet start()}
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
				{#if isSearching}
					<button class="btn btn-ghost btn-square btn-sm rounded-box" type="button" aria-label={m.discoverClearSearch()} title={m.discoverClearSearch()} onclick={clearSearch}>
						<FaIcon name="Close" size={14} />
					</button>
				{/if}
			</form>
		{/snippet}

		{#snippet end()}
			{#if discover.selectedAccount}
				<div class="dropdown dropdown-end account-dropdown">
					<button
						class="btn btn-ghost btn-square btn-sm rounded-box"
						type="button"
						aria-label={discover.selectedAccount.handle.canonical}
						onclick={() => {
							showAccountMenu = !showAccountMenu;
						}}
					>
						<img class="account-avatar" src={discover.selectedAccount.avatar?.url} alt="" />
					</button>
					{#if showAccountMenu && hasMultipleAccounts}
						<ul class="menu dropdown-content z-20 mt-2 w-64 rounded-box border border-base-300 bg-base-100 p-2 shadow-lg">
							{#each accounts as account (account.key.host + ':' + account.key.id)}
								<li>
									<button type="button" onclick={() => selectAccount(account)}>
										<img class="menu-avatar" src={account.avatar?.url} alt="" />
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
			{#if (isSearching && (search.users.type === 'Success' && search.users.isRefreshing || search.status.type === 'Success' && search.status.isRefreshing)) || (!isSearching && (discover.users.type === 'Success' && discover.users.isRefreshing || discover.hashtags.type === 'Success' && discover.hashtags.isRefreshing || discover.status.type === 'Success' && discover.status.isRefreshing))}
				<progress class="progress progress-primary block h-0.5 w-full"></progress>
			{/if}
		{/snippet}
	</AppTopBar>

	<section class="discover-content">
		{#if hasMultipleAccounts}
			<div class="account-chips">
				{#each accounts as account (account.key.host + ':' + account.key.id)}
					<button
						class:btn-primary={discover.selectedAccount?.key.id === account.key.id && discover.selectedAccount?.key.host === account.key.host}
						class:btn-outline={discover.selectedAccount?.key.id !== account.key.id || discover.selectedAccount?.key.host !== account.key.host}
						class="btn btn-sm rounded-box"
						type="button"
						onclick={() => selectAccount(account)}
					>
						<img class="chip-avatar" src={account.avatar?.url} alt="" />
						<span>{account.handle.canonical}</span>
					</button>
				{/each}
			</div>
		{/if}

		{#if isSearching}
			<DiscoverSection title={m.discoverUsers()} state={search.users}>
				<div class="user-row">
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
				</div>
			</DiscoverSection>

			<DiscoverSection title={m.discoverStatus()} state={search.status}>
				<TimelineList listState={search.status as unknown as TimelinePagingState<UiTimelineV2>} />
			</DiscoverSection>
		{:else}
			<DiscoverSection title={m.discoverUsers()} state={discover.users}>
				<div class="user-row">
					{#if discover.users.type === 'Loading'}
						{#each Array.from({ length: 4 }) as _, index (index)}
							<div class="user-card card rounded-box border border-base-300 bg-base-100">
								{@render UserSkeleton()}
							</div>
						{/each}
					{:else}
						{#each indexes(discover.users, 12) as index (index)}
							{@const user = discover.users.type === 'Success' ? discover.users.peek(index) : null}
							<div class="user-card card rounded-box border border-base-300 bg-base-100" use:loadWhenVisible={{ state: discover.users, index }}>
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

			<DiscoverSection title={m.discoverHashtags()} state={discover.hashtags}>
				<div class="hashtag-cloud">
					{#each indexes(discover.hashtags, 28) as index (index)}
						{@const hashtag = discover.hashtags.type === 'Success' ? discover.hashtags.peek(index) : null}
						<button
							class="btn btn-outline btn-sm rounded-box hashtag-chip"
							type="button"
							disabled={!hashtag}
							use:loadWhenVisible={{ state: discover.hashtags, index }}
							onclick={() => hashtag && searchHashtag(hashtag)}
						>
							{hashtag?.hashtag ?? '#'}
						</button>
					{/each}
					{#if discover.hashtags.type === 'Loading'}
						{#each Array.from({ length: 8 }) as _, index (index)}
							<div class="skeleton h-8 w-24 rounded-box"></div>
						{/each}
					{/if}
				</div>
			</DiscoverSection>

			<DiscoverSection title={m.discoverStatus()} state={discover.status}>
				<TimelineList listState={discover.status as unknown as TimelinePagingState<UiTimelineV2>} />
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
	.discover-page {
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

	.discover-content {
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

	.hashtag-cloud {
		display: flex;
		min-width: 0;
		flex-wrap: wrap;
		gap: 0.5rem;
	}

	.hashtag-chip {
		max-width: 100%;
		text-transform: none;
	}

	:global(.discover-page .timeline-list.card-mode),
	:global(.discover-page .card-mode .timeline-list-body),
	:global(.discover-page .timeline-loading-placeholder-list.card-mode),
	:global(.discover-page .card-mode .timeline-placeholder-body) {
		background: transparent;
	}

	:global(.discover-page .card-mode .timeline-list-body),
	:global(.discover-page .card-mode .timeline-placeholder-body) {
		padding: 0;
	}

	@media (max-width: 820px) {
		.account-chips {
			display: none;
		}
	}
</style>
