<script lang="ts">
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { createLocalCacheSearchPresenter } from '@flare/web-presenters/localCacheSearch.svelte';

	type SearchType = 'status' | 'user';

	const history = createLocalCacheSearchPresenter();
	let query = $state('');
	let selectedType = $state<SearchType>('status');
	const activeStatusState = $derived(query.trim() ? history.data : history.history);
	const activeUserState = $derived(query.trim() ? history.searchUser : history.userHistory);
	const userIndexes = $derived(
		activeUserState.type === 'Success'
			? Array.from({ length: activeUserState.itemCount }, (_, index) => index)
			: []
	);

	function submitSearch(event: Event): void {
		event.preventDefault();
		history.setQuery(query.trim());
	}

	let requestedUserIndexes = new Set<number>();

	function requestUser(index: number) {
		if (activeUserState.type !== 'Success' || requestedUserIndexes.has(index)) return;
		requestedUserIndexes.add(index);
		activeUserState.get(index);
	}

	function loadUserWhenVisible(node: HTMLElement, index: number) {
		let currentIndex = index;
		let observer: IntersectionObserver | null = null;

		if (typeof IntersectionObserver === 'undefined') {
			queueMicrotask(() => requestUser(currentIndex));
			return {
				update(nextIndex: number) {
					currentIndex = nextIndex;
					queueMicrotask(() => requestUser(currentIndex));
				},
			};
		}

		observer = new IntersectionObserver((entries) => {
			if (entries.some((entry) => entry.isIntersecting)) requestUser(currentIndex);
		});
		observer.observe(node);

		return {
			update(nextIndex: number) {
				currentIndex = nextIndex;
			},
			destroy() {
				observer?.disconnect();
			},
		};
	}
</script>

<svelte:head>
	<title>{m.settingsLocalHistoryTitle()} | Flare</title>
</svelte:head>

<div class="history-page bg-base-200">
	<AppTopBar title={m.settingsLocalHistoryTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}
	</AppTopBar>

	<div class="history-content">
		<form class="search-row" onsubmit={submitSearch}>
			<label class="input input-bordered flex flex-1 items-center gap-2">
				<FaIcon name="Search" size={15} />
				<input
					class="grow"
					type="search"
					bind:value={query}
					placeholder={m.localHistorySearchPlaceholder()}
				/>
			</label>
			<button class="btn btn-primary" type="submit">{m.searchTitle()}</button>
		</form>

		<div class="tabs tabs-box w-fit">
			<button
				class="tab"
				class:tab-active={selectedType === 'status'}
				type="button"
				onclick={() => (selectedType = 'status')}
			>
				{m.localHistorySearchStatusTitle()}
			</button>
			<button
				class="tab"
				class:tab-active={selectedType === 'user'}
				type="button"
				onclick={() => (selectedType = 'user')}
			>
				{m.localHistorySearchUserTitle()}
			</button>
		</div>
	</div>

	{#if selectedType === 'status'}
		<TimelineList listState={activeStatusState} />
	{:else}
		<div class="history-content user-list-wrap">
			{#if activeUserState.type === 'Loading'}
				<div class="skeleton h-16 w-full"></div>
				<div class="skeleton h-16 w-full"></div>
			{:else if activeUserState.type === 'Error'}
				<div class="alert alert-error">
					<span>{activeUserState.message ?? m.profileUnableToLoad()}</span>
				</div>
			{:else if activeUserState.type === 'Empty'}
				<div class="empty-user-state">{m.timelineEmpty()}</div>
			{:else}
				<div class="list rounded-box border border-base-300 bg-base-100">
					{#each userIndexes as index (index)}
						{@const user = activeUserState.peek(index)}
						<div class="list-row user-row">
							{#if user}
								<UserDisplay {user} variant="account" />
								{:else}
									<div class="user-skeleton" use:loadUserWhenVisible={index}>
										<span class="skeleton h-10 w-10 rounded-full"></span>
										<span class="skeleton h-5 w-44"></span>
									</div>
								{/if}
						</div>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.history-page {
		min-height: 100vh;
	}

	.history-content {
		display: grid;
		gap: 0.85rem;
		padding: 1rem;
	}

	.search-row {
		display: flex;
		gap: 0.65rem;
		min-width: 0;
	}

	.user-list-wrap {
		padding-top: 0;
	}

	.user-row {
		min-height: 4.2rem;
		padding: 0 1rem;
	}

	.user-row :global(.user-display) {
		width: 100%;
	}

	.user-skeleton {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: 0.75rem;
		width: 100%;
		padding: 0.85rem 0;
		border: 0;
		background: transparent;
	}

	.empty-user-state {
		display: grid;
		min-height: 8rem;
		place-items: center;
		color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
	}

	@media (max-width: 560px) {
		.search-row {
			display: grid;
		}
	}
</style>
