<script lang="ts">
	import { page } from '$app/state';
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import TimelineLoadingPlaceholderList from '$lib/components/UiTimeline/TimelineLoadingPlaceholderList.svelte';
	import { useReactiveRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import {
		createStatusContextPresenterController,
		type AccountType,
		type MicroBlogKey,
	} from '@flare/web-presenters/statusContext.svelte';

	const routeAccountKey = $derived(page.params.accountKey ?? 'guest');
	const routeStatusKey = $derived(page.params.statusKey ?? '');
	const accountType = $derived(parseAccountType(routeAccountKey));
	const detailStatusKey = $derived(parseMicroBlogKey(routeStatusKey));
	const presenterKey = $derived(`${routeAccountKey}:${routeStatusKey}`);
	const statusState = useReactiveRetainedPresenter(
		() => `status-detail:${presenterKey}`,
		() => createStatusContextPresenterController(accountType, detailStatusKey),
		{ ttlMs: Infinity }
	);
	const currentError = $derived(
		statusState.current.type === 'Error' ? (statusState.current.message ?? m.statusUnableToLoadPost()) : null
	);
	const listError = $derived(
		statusState.listState.type === 'Error'
			? (statusState.listState.message ?? m.statusUnableToLoadConversation())
			: null
	);
	const isLoading = $derived(statusState.listState.type === 'Loading');
	const isRefreshing = $derived(
		statusState.listState.type === 'Success' ? statusState.listState.isRefreshing : false
	);

	function retry(): void {
		if (statusState.listState.type === 'Success') {
			statusState.listState.retry();
			return;
		}
		globalThis.location?.reload();
	}

	function parseAccountType(value: string): AccountType {
		if (value === 'guest') return { type: 'Guest' };
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
</script>

<section class="min-h-screen bg-base-100">
	<AppTopBar title={m.statusDetailTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}

		{#snippet end()}
			<button
				class="btn btn-ghost btn-square btn-sm rounded-box"
				type="button"
				aria-label={m.actionRetry()}
				title={m.actionRetry()}
				onclick={retry}
			>
				<FaIcon name="Reset" size={15} />
			</button>
		{/snippet}

		{#snippet bottom()}
			{#if isRefreshing}
				<progress class="progress progress-primary block h-0.5 w-full"></progress>
			{/if}
		{/snippet}
	</AppTopBar>

	<div class="min-h-[calc(100vh-3.5rem)] w-full bg-base-100">
		{#if currentError || listError}
			<div class="grid gap-3 p-4">
				<div class="alert alert-error">
					<span>{currentError ?? listError}</span>
				</div>
				<button class="btn btn-primary btn-sm rounded-box justify-self-start" type="button" onclick={retry}>
					{m.actionRetry()}
				</button>
			</div>
		{:else if isLoading}
			<TimelineLoadingPlaceholderList />
		{:else}
			<TimelineList listState={statusState.listState} {detailStatusKey} />
		{/if}
	</div>
</section>
