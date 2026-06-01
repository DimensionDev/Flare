<script lang="ts">
	import TimelineAppearancePanel from '$lib/components/TimelineAppearancePanel.svelte';
	import UiTimelineFeed from '$lib/components/UiTimeline/Feed.svelte';
	import UiTimelineMessage from '$lib/components/UiTimeline/Message.svelte';
	import UiTimelinePost from '$lib/components/UiTimeline/Post.svelte';
	import UiTimelineUser from '$lib/components/UiTimeline/User.svelte';
	import UiTimelineUserList from '$lib/components/UiTimeline/UserList.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { timelineFeedSamples } from '$lib/timeline/sampleFeeds';
	import { timelineMessageSamples } from '$lib/timeline/sampleMessages';
	import { timelinePostSamples } from '$lib/timeline/samplePosts';
	import { timelineUserListSamples, timelineUserSamples } from '$lib/timeline/sampleUsers';

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const hasTimelineOverride = $derived(environmentSettings.timelineAppearanceOverride() !== null);
	const timelineAppearanceLabel = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data.timelineDisplayMode
			: timelineAppearanceState.type
	);
	const cardStyleItems = $derived(
		timelineAppearanceState.type === 'Success' &&
			timelineAppearanceState.data.timelineDisplayMode !== 'Plain'
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
				class="timeline-header sticky top-0 z-10 border-b bg-base-100/90 px-4 py-3 backdrop-blur"
			>
				<div class="flex flex-wrap items-center justify-between gap-3">
					<div class="min-w-0">
						<h1 class="truncate text-base font-semibold">Timeline</h1>
						<p class="mt-0.5 truncate text-xs text-base-content/55">UiTimeline samples</p>
					</div>
					<div class="flex min-w-0 flex-wrap items-center justify-end gap-2">
						{#if hasTimelineOverride}
							<div class="badge badge-primary badge-sm">Preview</div>
						{/if}
						<div class="badge badge-ghost">{timelineAppearanceLabel}</div>
						<div class="badge badge-outline">{timelinePostSamples.length} posts</div>
						<div class="badge badge-outline">{timelineFeedSamples.length} feeds</div>
						<div class="badge badge-outline">{timelineMessageSamples.length} messages</div>
						<div class="badge badge-outline">{timelineUserSamples.length} users</div>
						<div class="badge badge-outline">{timelineUserListSamples.length} lists</div>
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

						<div
							class:card-style={cardStyleItems}
							class="sample-timeline-item"
							data-sample-id={sample.id}
						>
							<UiTimelinePost post={sample.post} />
						</div>
					</section>
				{/each}

				<div class="sample-group-heading">
					<h2>UiTimelineV2.Feed samples</h2>
					<span class="badge badge-outline badge-sm">{timelineFeedSamples.length} samples</span>
				</div>

				{#each timelineFeedSamples as sample (sample.id)}
					<section aria-label={sample.label}>
						<div class="mb-3 flex items-center justify-between gap-3">
							<h2 class="min-w-0 truncate text-sm font-semibold">{sample.label}</h2>
							<span class="badge badge-ghost badge-sm shrink-0">{sample.variant}</span>
						</div>

						<div
							class:card-style={cardStyleItems}
							class="sample-timeline-item"
							data-sample-id={`feed-${sample.id}`}
						>
							<UiTimelineFeed feed={sample.feed} />
						</div>
					</section>
				{/each}

				<div class="sample-group-heading">
					<h2>UiTimelineV2.Message samples</h2>
					<span class="badge badge-outline badge-sm">{timelineMessageSamples.length} samples</span>
				</div>

				{#each timelineMessageSamples as sample (sample.id)}
					<section aria-label={sample.label}>
						<div class="mb-3 flex items-center justify-between gap-3">
							<h2 class="min-w-0 truncate text-sm font-semibold">{sample.label}</h2>
							<span class="badge badge-ghost badge-sm shrink-0">{sample.variant}</span>
						</div>

						<div
							class:card-style={cardStyleItems}
							class="sample-timeline-item"
							data-sample-id={`message-${sample.id}`}
						>
							<UiTimelineMessage message={sample.message} />
						</div>
					</section>
				{/each}

				<div class="sample-group-heading">
					<h2>UiTimelineV2.User samples</h2>
					<span class="badge badge-outline badge-sm">{timelineUserSamples.length} samples</span>
				</div>

				{#each timelineUserSamples as sample (sample.id)}
					<section aria-label={sample.label}>
						<div class="mb-3 flex items-center justify-between gap-3">
							<h2 class="min-w-0 truncate text-sm font-semibold">{sample.label}</h2>
							<span class="badge badge-ghost badge-sm shrink-0">{sample.variant}</span>
						</div>

						<div
							class:card-style={cardStyleItems}
							class="sample-timeline-item"
							data-sample-id={`user-${sample.id}`}
						>
							<UiTimelineUser user={sample.user} />
						</div>
					</section>
				{/each}

				<div class="sample-group-heading">
					<h2>UiTimelineV2.UserList samples</h2>
					<span class="badge badge-outline badge-sm">{timelineUserListSamples.length} samples</span>
				</div>

				{#each timelineUserListSamples as sample (sample.id)}
					<section aria-label={sample.label}>
						<div class="mb-3 flex items-center justify-between gap-3">
							<h2 class="min-w-0 truncate text-sm font-semibold">{sample.label}</h2>
							<span class="badge badge-ghost badge-sm shrink-0">{sample.variant}</span>
						</div>

						<div
							class:card-style={cardStyleItems}
							class="sample-timeline-item"
							data-sample-id={`user-list-${sample.id}`}
						>
							<UiTimelineUserList userList={sample.userList} />
						</div>
					</section>
				{/each}
			</div>
		</section>
	</div>
</main>

<style>
	section[aria-label],
	.sample-timeline-item {
		min-width: 0;
	}

	.sample-timeline-item.card-style {
		border: 1px solid var(--flare-separator-color);
		border-radius: var(--radius-box);
		background: var(--color-base-100);
		box-shadow: 0 1px 0 color-mix(in oklab, var(--flare-separator-color) 70%, transparent);
	}

	.timeline-header {
		border-bottom-color: var(--flare-separator-color);
	}

	.sample-group-heading {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
		border-top: 1px solid var(--flare-separator-color);
		padding-top: 1.25rem;
	}

	.sample-group-heading h2 {
		margin: 0;
		min-width: 0;
		overflow: hidden;
		color: color-mix(in oklab, var(--color-base-content) 72%, transparent);
		font-size: 0.78rem;
		font-weight: 650;
		letter-spacing: 0;
		text-overflow: ellipsis;
		text-transform: uppercase;
		white-space: nowrap;
	}
</style>
