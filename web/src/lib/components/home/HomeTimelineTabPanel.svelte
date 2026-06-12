<script lang="ts">
	import type { UiTimelineTabItem } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
	import { createTimelineItemPresenterController } from '@flare/web-presenters/timelineItem.svelte';
	import TimelineAppearanceProvider from '$lib/components/environment/TimelineAppearanceProvider.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';

	let {
		tab,
		appearance = null,
		refreshRequestId = 0,
		onRefreshingChange = () => {},
	}: {
		tab: UiTimelineTabItem;
		appearance?: TimelineAppearance | null;
		refreshRequestId?: number;
		onRefreshingChange?: (isRefreshing: boolean) => void;
	} = $props();

	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timelinePresenterKey = `home:timeline:${tab.key}`;
	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timeline = useRetainedPresenter(
		timelinePresenterKey,
		() => createTimelineItemPresenterController(tab.loaderKey),
		{ ttlMs: Infinity }
	);
	const isRefreshing = $derived(
		timeline.listState.type === 'Success' ? timeline.listState.isRefreshing : false
	);
	let handledRefreshRequestId = $state<number | null>(null);

	$effect(() => {
		onRefreshingChange(isRefreshing);

		return () => {
			onRefreshingChange(false);
		};
	});

	$effect(() => {
		if (handledRefreshRequestId === null) {
			handledRefreshRequestId = refreshRequestId;
			return;
		}
		if (refreshRequestId === handledRefreshRequestId) return;
		handledRefreshRequestId = refreshRequestId;
		timeline.refreshSync();
	});
</script>

<div class="home-timeline-tab-panel">
	<TimelineAppearanceProvider {appearance}>
		<TimelineList listState={timeline.listState} />
	</TimelineAppearanceProvider>
</div>

<style>
	.home-timeline-tab-panel {
		min-height: 0;
		height: 100%;
		min-width: 0;
	}
</style>
