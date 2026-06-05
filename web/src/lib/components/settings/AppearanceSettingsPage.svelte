<script lang="ts">
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import UiTimelinePost from '$lib/components/UiTimeline/Post.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import {
		useEnvironmentSettings,
		type AvatarShape,
		type PostActionStyle,
		type Theme,
		type TimelineDisplayMode,
		type VideoAutoplay,
	} from '$lib/environment/environmentSettings.svelte';
	import { createAppearancePresenter } from '@flare/web-presenters/appearance.svelte';
	import { createSettingsPresenter } from '@flare/web-presenters/settings.svelte';
	import type { UiTimelineV2Post as TimelinePost } from '@flare/web-presenters/timeline.svelte';

	type PageKind = 'theme' | 'layout' | 'display' | 'media';
	type SelectOption<T extends string> = {
		label: string;
		value: T;
	};

	let { kind }: { kind: PageKind } = $props();

	const environmentSettings = useEnvironmentSettings();
	const appearancePreview = createAppearancePresenter();
	const appearanceSettings = createSettingsPresenter();
	const globalAppearanceState = $derived(environmentSettings.globalAppearance());
	const globalAppearance = $derived(
		globalAppearanceState.type === 'Success' ? globalAppearanceState.data : null
	);
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());
	const timelineAppearance = $derived(
		timelineAppearanceState.type === 'Success' ? timelineAppearanceState.data : null
	);
	const pageTitle = $derived(titleForKind(kind));
	const sampleStatus = $derived(appearancePreview.sampleStatus);

	const themeOptions = $derived<SelectOption<Theme>[]>([
		{ label: m.settingsAppearanceThemeAuto(), value: 'SYSTEM' },
		{ label: m.settingsAppearanceThemeLight(), value: 'LIGHT' },
		{ label: m.settingsAppearanceThemeDark(), value: 'DARK' },
	]);
	const avatarShapeOptions = $derived<SelectOption<AvatarShape>[]>([
		{ label: m.settingsAppearanceAvatarShapeRound(), value: 'CIRCLE' },
		{ label: m.settingsAppearanceAvatarShapeSquare(), value: 'SQUARE' },
	]);
	const timelineDisplayOptions = $derived<SelectOption<TimelineDisplayMode>[]>([
		{ label: m.settingsAppearanceTimelineDisplayModeCard(), value: 'Card' },
		{ label: m.settingsAppearanceTimelineDisplayModePlain(), value: 'Plain' },
		{ label: m.settingsAppearanceTimelineDisplayModeGallery(), value: 'Gallery' },
	]);
	const postActionOptions = $derived<SelectOption<PostActionStyle>[]>([
		{ label: m.settingsAppearancePostActionStyleHidden(), value: 'Hidden' },
		{ label: m.settingsAppearancePostActionStyleLeftAligned(), value: 'LeftAligned' },
		{ label: m.settingsAppearancePostActionStyleRightAligned(), value: 'RightAligned' },
		{ label: m.settingsAppearancePostActionStyleStretch(), value: 'Stretch' },
	]);
	const videoAutoplayOptions = $derived<SelectOption<VideoAutoplay>[]>([
		{ label: m.settingsAppearanceVideoAutoplayNever(), value: 'NEVER' },
		{ label: m.settingsAppearanceVideoAutoplayWifi(), value: 'WIFI' },
		{ label: m.settingsAppearanceVideoAutoplayAlways(), value: 'ALWAYS' },
	]);

	function titleForKind(value: PageKind): string {
		switch (value) {
			case 'theme':
				return m.settingsAppearanceThemeGroupTitle();
			case 'layout':
				return m.settingsAppearanceLayoutGroupTitle();
			case 'display':
				return m.settingsAppearanceDisplayGroupTitle();
			case 'media':
				return m.settingsAppearanceMediaGroupTitle();
		}
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

	function asTimelinePost(value: unknown): TimelinePost {
		return value as TimelinePost;
	}
</script>

<div class="appearance-page bg-base-200">
	<AppTopBar title={pageTitle}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}
	</AppTopBar>

	<div class="appearance-content">
		{#if globalAppearance && timelineAppearance}
			{#if kind === 'theme'}
				<div class="list rounded-box border border-base-300 bg-base-100">
					{@render SelectRow(
						'Palette',
						m.settingsAppearanceTheme(),
						m.settingsAppearanceThemeDescription(),
						globalAppearance.theme,
						themeOptions,
						(event) => appearanceSettings.updateTheme(selectValue<Theme>(event))
					)}
					{@render SelectRow(
						'Profile',
						m.settingsAppearanceAvatarShape(),
						m.settingsAppearanceAvatarShapeDescription(),
						timelineAppearance.avatarShape,
						avatarShapeOptions,
						(event) => appearanceSettings.updateAvatarShape(selectValue<AvatarShape>(event))
					)}
					{@render RangeRow(
						'Edit',
						m.settingsAppearanceFontSizeDiff(),
						`${globalAppearance.fontSizeDiff > 0 ? '+' : ''}${globalAppearance.fontSizeDiff}`,
						-2,
						4,
						1,
						globalAppearance.fontSizeDiff,
						(event) => appearanceSettings.updateFontScale(numberValue(event))
					)}
				</div>
			{:else if kind === 'layout'}
				<div class="list rounded-box border border-base-300 bg-base-100">
					{@render SelectRow(
						'TableList',
						m.settingsAppearanceTimelineDisplayMode(),
						m.settingsAppearanceTimelineDisplayModeDescription(),
						timelineAppearance.timelineDisplayMode,
						timelineDisplayOptions,
						(event) =>
							appearanceSettings.updateTimelineDisplayMode(selectValue<TimelineDisplayMode>(event))
					)}
					{@render ToggleRow(
						'List',
						m.settingsAppearanceShowBottomBarLabels(),
						m.settingsAppearanceShowBottomBarLabelsDescription(),
						globalAppearance.showBottomBarLabels,
						(event) => appearanceSettings.updateShowBottomBarLabels(checked(event))
					)}
					{@render ToggleRow(
						'TableList',
						m.settingsAppearanceDeckMode(),
						m.settingsAppearanceDeckModeDescription(),
						globalAppearance.deckMode,
						(event) => appearanceSettings.updateDeckMode(checked(event))
					)}
				</div>

				{@render PreviewBlock()}

				<div class="list rounded-box border border-base-300 bg-base-100">
					{@render ToggleRow(
						'News',
						m.settingsAppearanceFullWidthPost(),
						m.settingsAppearanceFullWidthPostDescription(),
						timelineAppearance.fullWidthPost,
						(event) => appearanceSettings.updateFullWidthPost(checked(event))
					)}
					{@render SelectRow(
						'Reply',
						m.settingsAppearancePostActionStyle(),
						m.settingsAppearancePostActionStyleDescription(),
						timelineAppearance.postActionStyle,
						postActionOptions,
						(event) =>
							appearanceSettings.updatePostActionStyle(selectValue<PostActionStyle>(event))
					)}
					{#if timelineAppearance.postActionStyle !== 'Hidden'}
						{@render ToggleRow(
							'List',
							m.settingsAppearanceShowNumbers(),
							m.settingsAppearanceShowNumbersDescription(),
							timelineAppearance.showNumbers,
							(event) => appearanceSettings.updateShowNumbers(checked(event))
						)}
					{/if}
				</div>
			{:else if kind === 'display'}
				{@render PreviewBlock()}

				<div class="list rounded-box border border-base-300 bg-base-100">
					{@render ToggleRow(
						'News',
						m.settingsAppearanceAbsoluteTimestamp(),
						m.settingsAppearanceAbsoluteTimestampDescription(),
						timelineAppearance.absoluteTimestamp,
						(event) => appearanceSettings.updateAbsoluteTimestamp(checked(event))
					)}
					{@render ToggleRow(
						'World',
						m.settingsAppearanceShowPlatformLogo(),
						m.settingsAppearanceShowPlatformLogoDescription(),
						timelineAppearance.showPlatformLogo,
						(event) => appearanceSettings.updateShowPlatformLogo(checked(event))
					)}
					{@render ToggleRow(
						'World',
						m.settingsAppearanceShowLinkPreviews(),
						m.settingsAppearanceShowLinkPreviewsDescription(),
						timelineAppearance.showLinkPreview,
						(event) => appearanceSettings.updateShowLinkPreview(checked(event))
					)}
					{#if timelineAppearance.showLinkPreview}
						{@render ToggleRow(
							'List',
							m.settingsAppearanceCompatLinkPreviews(),
							m.settingsAppearanceCompatLinkPreviewsDescription(),
							timelineAppearance.compatLinkPreview,
							(event) => appearanceSettings.updateCompatLinkPreview(checked(event))
						)}
					{/if}
					{@render ToggleRow(
						'World',
						m.settingsAppearanceInAppBrowser(),
						m.settingsAppearanceInAppBrowserDescription(),
						globalAppearance.inAppBrowser,
						(event) => appearanceSettings.updateInAppBrowser(checked(event))
					)}
				</div>
			{:else if kind === 'media'}
				{@render PreviewBlock()}

				<div class="list rounded-box border border-base-300 bg-base-100">
					{@render ToggleRow(
						'Image',
						m.settingsAppearanceShowMedia(),
						m.settingsAppearanceShowMediaDescription(),
						timelineAppearance.showMedia,
						(event) => appearanceSettings.updateShowMedia(checked(event))
					)}
					{#if timelineAppearance.showMedia}
						{@render ToggleRow(
							'PhotoFilm',
							m.settingsAppearanceExpandMedia(),
							m.settingsAppearanceExpandMediaDescription(),
							timelineAppearance.expandMediaSize,
							(event) => appearanceSettings.updateExpandMediaSize(checked(event))
						)}
						{@render ToggleRow(
							'CircleExclamation',
							m.settingsAppearanceShowCwImg(),
							m.settingsAppearanceShowCwImgDescription(),
							timelineAppearance.showSensitiveContent,
							(event) => appearanceSettings.updateShowSensitiveContent(checked(event))
						)}
						{@render SelectRow(
							'Video',
							m.settingsAppearanceVideoAutoplay(),
							m.settingsAppearanceVideoAutoplayDescription(),
							timelineAppearance.videoAutoplay,
							videoAutoplayOptions,
							(event) =>
								appearanceSettings.updateVideoAutoplay(selectValue<VideoAutoplay>(event))
						)}
					{/if}
				</div>
			{/if}
		{:else if globalAppearanceState.type === 'Error' || timelineAppearanceState.type === 'Error'}
			<div class="alert alert-error">
				<FaIcon name="CircleExclamation" size={18} />
				<span>{m.settingsAppearanceLoadError()}</span>
			</div>
		{:else}
			<div class="settings-loading rounded-box border border-base-300 bg-base-100">
				<div class="skeleton h-14 w-full"></div>
				<div class="skeleton h-14 w-full"></div>
				<div class="skeleton h-14 w-full"></div>
			</div>
		{/if}
	</div>
</div>

{#snippet SelectRow<T extends string>(
	icon: string,
	title: string,
	description: string,
	value: T,
	options: SelectOption<T>[],
	onchange: (event: Event) => void
)}
	<label class="list-row setting-row">
		{@render Icon(icon)}
		{@render Copy(title, description)}
		<select class="select select-sm w-48 max-w-full" {value} {onchange}>
			{#each options as option (option.value)}
				<option value={option.value}>{option.label}</option>
			{/each}
		</select>
	</label>
{/snippet}

{#snippet ToggleRow(
	icon: string,
	title: string,
	description: string,
	value: boolean,
	onchange: (event: Event) => void
)}
	<label class="list-row setting-row">
		{@render Icon(icon)}
		{@render Copy(title, description)}
		<input class="toggle toggle-sm" type="checkbox" checked={value} {onchange} />
	</label>
{/snippet}

{#snippet RangeRow(
	icon: string,
	title: string,
	description: string,
	min: number,
	max: number,
	step: number,
	value: number,
	oninput: (event: Event) => void
)}
	<label class="list-row setting-row range-row">
		{@render Icon(icon)}
		{@render Copy(title, description)}
		<input class="range range-xs" type="range" {min} {max} {step} {value} {oninput} />
	</label>
{/snippet}

{#snippet Icon(icon: string)}
	<span class="setting-icon bg-base-200 text-base-content">
		<FaIcon name={icon} size={18} />
	</span>
{/snippet}

{#snippet Copy(title: string, description: string)}
	<span class="setting-copy">
		<span class="setting-title">{title}</span>
		<span class="setting-description">{description}</span>
	</span>
{/snippet}

{#snippet PreviewBlock()}
	{#if sampleStatus.type !== 'Error'}
		<section class="preview-section" aria-label={m.previewAriaLabel()}>
			{#if sampleStatus.type === 'Success'}
				<div class="preview-post">
					<UiTimelinePost
						post={asTimelinePost(sampleStatus.data)}
						forceHideActions={false}
						showParents={false}
					/>
				</div>
			{:else}
				<div class="preview-loading rounded-box border border-base-300 bg-base-100">
					<div class="skeleton h-10 w-10 rounded-full"></div>
					<div class="grid gap-2">
						<div class="skeleton h-4 w-36"></div>
						<div class="skeleton h-4 w-full"></div>
						<div class="skeleton h-20 w-full"></div>
					</div>
				</div>
			{/if}
		</section>
	{/if}
{/snippet}

<style>
	.appearance-page {
		min-height: 100vh;
	}

	.appearance-content {
		display: grid;
		gap: 1rem;
		padding: 1rem;
	}

	.setting-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.35rem;
		padding: 0.8rem 1rem;
	}

	.range-row {
		grid-template-columns: auto minmax(0, 1fr) minmax(10rem, 16rem);
	}

	.setting-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		border-radius: var(--radius-box);
	}

	.setting-copy {
		display: grid;
		gap: 0.18rem;
		min-width: 0;
	}

	.setting-title {
		overflow-wrap: anywhere;
		font-size: 0.96rem;
		font-weight: 650;
		line-height: 1.25;
	}

	.setting-description {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.79rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}

	.preview-post {
		overflow: hidden;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		background: var(--color-base-100);
	}

	.preview-loading {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		gap: 0.75rem;
		padding: 1rem;
	}

	.settings-loading {
		display: grid;
		gap: 0.75rem;
		padding: 1rem;
	}

	@media (max-width: 560px) {
		.setting-row,
		.range-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.setting-row > :global(.select),
		.setting-row > :global(.toggle),
		.setting-row > :global(.range) {
			grid-column: 2;
			justify-self: start;
		}

		.setting-row > :global(.range) {
			width: 100%;
		}
	}
</style>
