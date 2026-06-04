<script lang="ts">
	import type { TimelinePresenter } from '@flare/web-presenters/profile.svelte';
	import { bindTimelinePresenterController } from '@flare/web-presenters/timeline.svelte';
	import type { WebPresenterRef as TimelineWebPresenterRef } from '@flare/web-presenters/timeline.svelte';
	import TimelineList from '$lib/components/UiTimeline/TimelineList.svelte';
	import { useRetainedPresenter } from '$lib/presenter/presenterStore.svelte';

	let {
		presenter,
		profileKey,
		tabKey,
	}: {
		presenter: TimelinePresenter;
		profileKey: string;
		tabKey: string;
	} = $props();

	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timelinePresenterKey = `profile:timeline:v2:${profileKey}:${tabKey}`;
	// svelte-ignore state_referenced_locally -- parent keys this component by tab id.
	const timeline = useRetainedPresenter(
		timelinePresenterKey,
		() => bindTimelinePresenterController(presenter as unknown as TimelineWebPresenterRef),
		{ ttlMs: Infinity }
	);
</script>

<div class="profile-timeline-tab-panel">
	<TimelineList listState={timeline.listState} />
</div>

<style>
	.profile-timeline-tab-panel {
		min-height: 0;
		min-width: 0;
	}
</style>
