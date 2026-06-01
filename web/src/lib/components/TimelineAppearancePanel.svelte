<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import {
		useEnvironmentSettings,
		type AvatarShape,
		type PostActionStyle,
		type TimelineAppearance,
		type TimelineDisplayMode,
		type VideoAutoplay,
	} from '$lib/environment/environmentSettings.svelte';

	const environmentSettings = useEnvironmentSettings();
	const appearanceState = $derived(environmentSettings.timelineAppearance());
	const appearance = $derived(appearanceState.type === 'Success' ? appearanceState.data : null);
	const hasOverride = $derived(environmentSettings.timelineAppearanceOverride() !== null);

	const displayModes: TimelineDisplayMode[] = ['Plain', 'Card', 'Gallery'];
	const avatarShapes: AvatarShape[] = ['CIRCLE', 'SQUARE'];
	const actionStyles: PostActionStyle[] = ['LeftAligned', 'RightAligned', 'Stretch', 'Hidden'];
	const videoAutoplayOptions: VideoAutoplay[] = ['NEVER', 'WIFI', 'ALWAYS'];

	function updateAppearance(patch: Partial<TimelineAppearance>): void {
		if (!appearance) return;
		environmentSettings.setTimelineAppearanceOverride({
			...appearance,
			aiConfig: { ...appearance.aiConfig },
			...patch,
		} as TimelineAppearance);
	}

	function updateAiConfig(patch: Partial<TimelineAppearance['aiConfig']>): void {
		if (!appearance) return;
		updateAppearance({
			aiConfig: {
				...appearance.aiConfig,
				...patch,
			},
		} as Partial<TimelineAppearance>);
	}

	function checked(event: Event): boolean {
		return (event.currentTarget as HTMLInputElement).checked;
	}

	function numberValue(event: Event): number {
		return Number((event.currentTarget as HTMLInputElement).value);
	}

	function selectValue<T extends string>(event: Event): T {
		return (event.currentTarget as HTMLSelectElement).value as T;
	}
</script>

<aside class="settings-panel">
	<div class="panel-header">
		<div>
			<h2>Timeline Appearance</h2>
			<span>{hasOverride ? 'Preview override' : 'Presenter defaults'}</span>
		</div>
		<button
			class="btn btn-ghost btn-square btn-sm"
			type="button"
			title="Reset"
			aria-label="Reset"
			disabled={!hasOverride}
			onclick={() => environmentSettings.resetTimelineAppearanceOverride()}
		>
			<FaIcon name="Reset" size={15} />
		</button>
	</div>

	{#if appearance}
		<div class="control-group">
			<label>
				<span>Display</span>
				<select
					class="select select-sm w-full"
					value={appearance.timelineDisplayMode}
					onchange={(event) =>
						updateAppearance({ timelineDisplayMode: selectValue<TimelineDisplayMode>(event) })}
				>
					{#each displayModes as mode}
						<option value={mode}>{mode}</option>
					{/each}
				</select>
			</label>

			<label>
				<span>Actions</span>
				<select
					class="select select-sm w-full"
					value={appearance.postActionStyle}
					onchange={(event) => updateAppearance({ postActionStyle: selectValue<PostActionStyle>(event) })}
				>
					{#each actionStyles as style}
						<option value={style}>{style}</option>
					{/each}
				</select>
			</label>

			<label>
				<span>Avatar</span>
				<select
					class="select select-sm w-full"
					value={appearance.avatarShape}
					onchange={(event) => updateAppearance({ avatarShape: selectValue<AvatarShape>(event) })}
				>
					{#each avatarShapes as shape}
						<option value={shape}>{shape}</option>
					{/each}
				</select>
			</label>

			<label>
				<span>Autoplay</span>
				<select
					class="select select-sm w-full"
					value={appearance.videoAutoplay}
					onchange={(event) => updateAppearance({ videoAutoplay: selectValue<VideoAutoplay>(event) })}
				>
					{#each videoAutoplayOptions as option}
						<option value={option}>{option}</option>
					{/each}
				</select>
			</label>
		</div>

		<div class="range-control">
			<div>
				<span>Line limit</span>
				<strong>{appearance.lineLimit}</strong>
			</div>
			<input
				class="range range-xs"
				type="range"
				min="1"
				max="12"
				value={appearance.lineLimit}
				oninput={(event) => updateAppearance({ lineLimit: numberValue(event) })}
			/>
		</div>

		<div class="toggle-grid">
			<label>
				<span>Full width</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.fullWidthPost}
					onchange={(event) => updateAppearance({ fullWidthPost: checked(event) })}
				/>
			</label>
			<label>
				<span>Absolute time</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.absoluteTimestamp}
					onchange={(event) => updateAppearance({ absoluteTimestamp: checked(event) })}
				/>
			</label>
			<label>
				<span>Numbers</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showNumbers}
					onchange={(event) => updateAppearance({ showNumbers: checked(event) })}
				/>
			</label>
			<label>
				<span>Media</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showMedia}
					onchange={(event) => updateAppearance({ showMedia: checked(event) })}
				/>
			</label>
			<label>
				<span>Link preview</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showLinkPreview}
					onchange={(event) => updateAppearance({ showLinkPreview: checked(event) })}
				/>
			</label>
			<label>
				<span>Compat card</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.compatLinkPreview}
					onchange={(event) => updateAppearance({ compatLinkPreview: checked(event) })}
				/>
			</label>
			<label>
				<span>Expand media</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.expandMediaSize}
					onchange={(event) => updateAppearance({ expandMediaSize: checked(event) })}
				/>
			</label>
			<label>
				<span>Sensitive media</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showSensitiveContent}
					onchange={(event) => updateAppearance({ showSensitiveContent: checked(event) })}
				/>
			</label>
			<label>
				<span>Platform icon</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showPlatformLogo}
					onchange={(event) => updateAppearance({ showPlatformLogo: checked(event) })}
				/>
			</label>
			<label>
				<span>Translate icon</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.showTranslateButton}
					onchange={(event) => updateAppearance({ showTranslateButton: checked(event) })}
				/>
			</label>
			<label>
				<span>AI translation</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.aiConfig.translation}
					onchange={(event) => updateAiConfig({ translation: checked(event) })}
				/>
			</label>
			<label>
				<span>AI TLDR</span>
				<input
					class="toggle toggle-sm"
					type="checkbox"
					checked={appearance.aiConfig.tldr}
					onchange={(event) => updateAiConfig({ tldr: checked(event) })}
				/>
			</label>
		</div>
	{:else}
		<div class="skeleton h-56 w-full"></div>
	{/if}
</aside>

<style>
	.settings-panel {
		display: grid;
		gap: 1rem;
		border: 1px solid var(--color-base-300);
		background: var(--color-base-100);
		padding: 1rem;
	}

	.panel-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
	}

	.panel-header h2 {
		font-size: 0.9rem;
		font-weight: 700;
		line-height: 1.2;
	}

	.panel-header span,
	.control-group span,
	.range-control span,
	.toggle-grid span {
		color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
		font-size: 0.74rem;
		font-weight: 650;
	}

	.control-group {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 0.75rem;
	}

	.control-group label,
	.range-control {
		display: grid;
		gap: 0.35rem;
	}

	.range-control > div {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
	}

	.range-control strong {
		font-size: 0.82rem;
	}

	.toggle-grid {
		display: grid;
		gap: 0.1rem;
	}

	.toggle-grid label {
		display: flex;
		min-height: 2rem;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
	}

	@media (min-width: 1024px) {
		.settings-panel {
			position: sticky;
			top: 1rem;
			max-height: calc(100vh - 2rem);
			overflow: auto;
		}
	}

	@media (max-width: 640px) {
		.control-group {
			grid-template-columns: 1fr;
		}
	}
</style>
