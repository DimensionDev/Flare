<script lang="ts">
	import TimelineAppearancePanel from '$lib/components/TimelineAppearancePanel.svelte';
	import UiTimelinePost from '$lib/components/UiTimeline/Post.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { timelinePostSamples } from '$lib/timeline/samplePosts';

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const hasTimelineOverride = $derived(environmentSettings.timelineAppearanceOverride() !== null);
	const timelineAppearanceLabel = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data.timelineDisplayMode
			: timelineAppearanceState.type
	);
</script>

<svelte:head>
	<title>Flare Timeline</title>
</svelte:head>

<main class="min-h-screen bg-base-200 text-base-content">
	<div class="mx-auto grid min-h-screen w-full max-w-6xl gap-4 p-4 lg:grid-cols-[20rem_minmax(0,1fr)]">
		<div class="lg:pt-0">
			<TimelineAppearancePanel />
		</div>

		<section class="min-h-screen min-w-0 bg-base-100">
			<header
				class="sticky top-0 z-10 border-b border-base-300 bg-base-100/90 px-4 py-3 backdrop-blur"
			>
				<div class="flex items-center justify-between gap-3">
					<div class="min-w-0">
						<h1 class="truncate text-base font-semibold">Timeline</h1>
						<p class="mt-0.5 truncate text-xs text-base-content/55">UiTimeline.Post samples</p>
					</div>
					<div class="flex shrink-0 items-center gap-2">
						{#if hasTimelineOverride}
							<div class="badge badge-primary badge-sm">Preview</div>
						{/if}
						<div class="badge badge-ghost">{timelineAppearanceLabel}</div>
						<div class="badge badge-outline">{timelinePostSamples.length} samples</div>
					</div>
				</div>
			</header>

			<div class="grid gap-5 px-4 py-5">
				{#each timelinePostSamples as sample (sample.id)}
					<section aria-label={sample.label}>
						<div class="mb-3 flex items-center justify-between gap-3">
							<h2 class="min-w-0 truncate text-sm font-semibold">{sample.label}</h2>
							<span class="badge badge-ghost badge-sm shrink-0">{sample.variant}</span>
						</div>

						<div data-sample-id={sample.id}>
							<UiTimelinePost post={sample.post} />
						</div>
					</section>
				{/each}
			</div>
		</section>
	</div>
</main>
