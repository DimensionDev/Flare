<script lang="ts">
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { defaultTimelineAppearance } from './postUtils';

	let {
		lineCount = 2,
		showMedia = false,
	}: {
		lineCount?: number;
		showMedia?: boolean;
	} = $props();

	const environmentSettings = useEnvironmentSettings();
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const appearance = $derived(
		timelineAppearanceState.type === 'Success'
			? timelineAppearanceState.data
			: defaultTimelineAppearance
	);
	const showSideAvatar = $derived(!appearance.fullWidthPost);
	const textLines = $derived(
		Array.from({ length: Math.max(1, Math.min(lineCount, 4)) }, (_, index) => index)
	);
	const showActions = $derived(appearance.postActionStyle !== 'Hidden');
</script>

<article class:full-width={!showSideAvatar} class="post-loading-placeholder" aria-hidden="true">
	<div class:with-avatar={showSideAvatar} class="placeholder-grid">
		{#if showSideAvatar}
			<div
				class:rounded-box={appearance.avatarShape === 'SQUARE'}
				class:rounded-full={appearance.avatarShape === 'CIRCLE'}
				class="skeleton avatar-skeleton"
			></div>
		{/if}

		<div class="placeholder-content">
			<header class:inline-avatar={!showSideAvatar} class:full-width={!showSideAvatar} class="placeholder-header">
				{#if !showSideAvatar}
					<div
						class:rounded-box={appearance.avatarShape === 'SQUARE'}
						class:rounded-full={appearance.avatarShape === 'CIRCLE'}
						class="skeleton inline-avatar-skeleton"
					></div>
				{/if}

				<div class="identity-skeleton">
					<div class="skeleton name-skeleton"></div>
					<div class="skeleton handle-skeleton"></div>
				</div>

				<div class="meta-skeleton">
					<div class="skeleton meta-icon-skeleton"></div>
					<div class="skeleton time-skeleton"></div>
				</div>
			</header>

			<div class="placeholder-stack">
				<div class="text-skeleton-stack">
					{#each textLines as line}
						<div
							class:last-line={line === textLines.length - 1}
							class="skeleton text-line-skeleton"
						></div>
					{/each}
				</div>

				{#if showMedia}
					<div class="skeleton rounded-box media-skeleton"></div>
				{/if}

				{#if showActions}
					<div class={`actions-skeleton actions-${appearance.postActionStyle.toLowerCase()}`}>
						<div class="skeleton action-skeleton"></div>
						<div class="skeleton action-skeleton"></div>
						<div class="skeleton action-skeleton"></div>
						<div class="skeleton action-skeleton"></div>
					</div>
				{/if}
			</div>
		</div>
	</div>
</article>

<style>
	.post-loading-placeholder {
		--post-padding-x: 1rem;
		--post-padding-y: 0.75rem;
		--post-padding-bottom: 0.25rem;
		--post-gap: 0.25rem;
		--post-avatar-gap: 0.5rem;
		--avatar-size: 2.75rem;
		min-width: 0;
		background: var(--color-base-100);
		padding: var(--post-padding-y) var(--post-padding-x) var(--post-padding-bottom);
	}

	.placeholder-grid {
		display: grid;
		min-width: 0;
		gap: var(--post-gap) var(--post-avatar-gap);
	}

	.placeholder-grid.with-avatar {
		grid-template-columns: var(--avatar-size) minmax(0, 1fr);
	}

	.avatar-skeleton,
	.inline-avatar-skeleton {
		width: var(--avatar-size);
		height: var(--avatar-size);
		flex: 0 0 auto;
	}

	.placeholder-content {
		min-width: 0;
	}

	.placeholder-header {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: var(--post-gap);
	}

	.placeholder-header.inline-avatar {
		gap: var(--post-avatar-gap);
	}

	.placeholder-header.full-width .identity-skeleton {
		display: grid;
		gap: 0.25rem;
	}

	.identity-skeleton {
		display: flex;
		width: 0;
		min-width: 0;
		flex: 1 1 auto;
		align-items: center;
		gap: 0.25rem;
	}

	.name-skeleton {
		width: min(8rem, 48%);
		height: 0.9rem;
	}

	.handle-skeleton {
		width: min(7rem, 42%);
		height: 0.75rem;
	}

	.placeholder-header.full-width .name-skeleton {
		width: min(8rem, 70%);
	}

	.placeholder-header.full-width .handle-skeleton {
		width: min(10rem, 90%);
	}

	.meta-skeleton {
		display: flex;
		flex: 0 0 auto;
		align-items: center;
		justify-content: flex-end;
		gap: var(--post-gap);
	}

	.meta-icon-skeleton {
		width: 1rem;
		height: 1rem;
		border-radius: 999px;
	}

	.time-skeleton {
		width: 2.25rem;
		height: 0.75rem;
	}

	.placeholder-stack {
		display: grid;
		gap: var(--post-gap);
		margin-top: var(--post-gap);
	}

	.text-skeleton-stack {
		display: grid;
		gap: 0.35rem;
		padding-block: 0.15rem;
	}

	.text-line-skeleton {
		width: 100%;
		height: 0.9rem;
	}

	.text-line-skeleton.last-line {
		width: 68%;
	}

	.media-skeleton {
		width: 100%;
		aspect-ratio: 16 / 9;
		min-height: 9rem;
	}

	.actions-skeleton {
		display: flex;
		min-height: 1.9rem;
		align-items: center;
		gap: 0.5rem;
		padding-top: 0;
	}

	.actions-skeleton.actions-rightaligned {
		justify-content: flex-end;
	}

	.actions-skeleton.actions-stretch {
		justify-content: space-between;
	}

	.actions-skeleton.actions-leftaligned .action-skeleton:last-child {
		margin-left: auto;
	}

	.action-skeleton {
		width: 2.25rem;
		height: 1.9rem;
		border-radius: var(--radius-box);
	}

	@media (max-width: 520px) {
		.post-loading-placeholder {
			--post-padding-x: 0.85rem;
		}

		.meta-skeleton {
			max-width: 38%;
		}
	}
</style>
