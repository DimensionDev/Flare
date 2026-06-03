<script lang="ts">
	import type { TimelineTabItemV2 } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import { bindTimelinePresenterController } from '@flare/web-presenters/timeline.svelte';
	import type { WebPresenterRef as TimelineWebPresenterRef } from '@flare/web-presenters/timeline.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';

	let {
		tab,
		onRefreshingChange = () => {},
	}: {
		tab: TimelineTabItemV2;
		onRefreshingChange?: (isRefreshing: boolean) => void;
	} = $props();

	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timelinePresenterKey = `home:timeline:${tab.key}`;
	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timeline = useRetainedPresenter(
		timelinePresenterKey,
		() => bindTimelinePresenterController(tab.createPresenter() as unknown as TimelineWebPresenterRef),
		{ ttlMs: Infinity }
	);
	const isRefreshing = $derived(
		timeline.listState.type === 'Success' ? timeline.listState.isRefreshing : false
	);

	$effect(() => {
		onRefreshingChange(isRefreshing);

		return () => {
			onRefreshingChange(false);
		};
	});
</script>

<div class="home-timeline-tab-panel">
	<TimelineList listState={timeline.listState} />
</div>

<style>
	.home-timeline-tab-panel {
		min-height: 0;
		height: 100%;
		min-width: 0;
	}
</style>
