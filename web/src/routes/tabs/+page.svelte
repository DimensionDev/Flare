<script lang="ts">
	import AppBackButton from '$lib/components/AppBackButton.svelte';
	import AppTopBar from '$lib/components/AppTopBar.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useEnvironmentSettings } from '$lib/environment/environmentSettings.svelte';
	import { localizedUiString } from '$lib/i18n/uiStrings';
	import { m } from '$lib/paraglide/messages.js';
	import {
		createHomeTabSettingsPresenter,
		type AvatarShape,
		type IconType,
		type PostActionStyle,
		type TimelineAppearance,
		type TimelineDisplayMode,
		type TimelineMergePolicy,
		type TimelinePostContent,
		type TimelinePostKind,
		type TimelineTabItemV2,
		type UiText,
		type VideoAutoplay,
	} from '@flare/web-presenters/homeTabSettings.svelte';
	import {
		createAllTabsPresenter,
		type TimelineTabItemV2 as CandidateTimelineTabItemV2,
	} from '@flare/web-presenters/allTabs.svelte';

	const SYSTEM_HOME_MIXED_TIMELINE_ID = 'mixed_timeline_system_home';
	const tabSettings = createHomeTabSettingsPresenter();
	const allTabs = createAllTabsPresenter();
	const environmentSettings = useEnvironmentSettings();
	const mergePolicyOptions: Array<{ value: TimelineMergePolicy; label: string }> = [
		{ value: 'TimePerPage', label: m.tabSettingsMergePolicyTimePerPage() },
		{ value: 'Time', label: m.tabSettingsMergePolicyTime() },
		{ value: 'Staggered', label: m.tabSettingsMergePolicyStaggered() },
	];
	const postKindOptions: Array<{ value: TimelinePostKind; label: string }> = [
		{ value: 'Original', label: m.tabSettingsFilterOriginal() },
		{ value: 'Reply', label: m.tabSettingsFilterReply() },
		{ value: 'Repost', label: m.tabSettingsFilterRepost() },
		{ value: 'Quote', label: m.tabSettingsFilterQuote() },
	];
	const postContentOptions: Array<{ value: TimelinePostContent; label: string }> = [
		{ value: 'Text', label: m.tabSettingsFilterText() },
		{ value: 'Image', label: m.tabSettingsFilterImage() },
		{ value: 'Video', label: m.tabSettingsFilterVideo() },
		{ value: 'Other', label: m.tabSettingsFilterOther() },
	];
	const timelineDisplayOptions: Array<{ value: TimelineDisplayMode; label: string }> = [
		{ value: 'Card', label: m.settingsAppearanceTimelineDisplayModeCard() },
		{ value: 'Plain', label: m.settingsAppearanceTimelineDisplayModePlain() },
		{ value: 'Gallery', label: m.settingsAppearanceTimelineDisplayModeGallery() },
	];
	const postActionOptions: Array<{ value: PostActionStyle; label: string }> = [
		{ value: 'Hidden', label: m.settingsAppearancePostActionStyleHidden() },
		{ value: 'LeftAligned', label: m.settingsAppearancePostActionStyleLeftAligned() },
		{ value: 'RightAligned', label: m.settingsAppearancePostActionStyleRightAligned() },
		{ value: 'Stretch', label: m.settingsAppearancePostActionStyleStretch() },
	];
	const videoAutoplayOptions: Array<{ value: VideoAutoplay; label: string }> = [
		{ value: 'NEVER', label: m.settingsAppearanceVideoAutoplayNever() },
		{ value: 'WIFI', label: m.settingsAppearanceVideoAutoplayWifi() },
		{ value: 'ALWAYS', label: m.settingsAppearanceVideoAutoplayAlways() },
	];
	const avatarShapeOptions: Array<{ value: AvatarShape; label: string }> = [
		{ value: 'CIRCLE', label: m.settingsAppearanceAvatarShapeRound() },
		{ value: 'SQUARE', label: m.settingsAppearanceAvatarShapeSquare() },
	];
	const defaultTimelineAppearance = {
		avatarShape: 'CIRCLE',
		showMedia: true,
		showSensitiveContent: false,
		expandContentWarning: false,
		expandMediaSize: true,
		videoAutoplay: 'WIFI',
		showLinkPreview: true,
		compatLinkPreview: false,
		showNumbers: true,
		postActionStyle: 'LeftAligned',
		fullWidthPost: false,
		absoluteTimestamp: false,
		showPlatformLogo: true,
		timelineDisplayMode: 'Card',
		aiConfig: {
			translation: false,
			tldr: false,
		},
		lineLimit: 5,
		showTranslateButton: true,
	} as unknown as TimelineAppearance;

	type EditForm = {
		tab: TimelineTabItemV2;
		title: string;
		icon: IconType;
		enabled: boolean;
		excludedKinds: TimelinePostKind[];
		excludedContents: TimelinePostContent[];
		layoutOverride: boolean;
		timelineDisplayMode: TimelineDisplayMode;
		fullWidthPost: boolean;
		postActionStyle: PostActionStyle;
		showNumbers: boolean;
		displayOverride: boolean;
		absoluteTimestamp: boolean;
		showPlatformLogo: boolean;
		showLinkPreview: boolean;
		compatLinkPreview: boolean;
		mediaOverride: boolean;
		showMedia: boolean;
		showSensitiveContent: boolean;
		expandContentWarning: boolean;
		expandMediaSize: boolean;
		videoAutoplay: VideoAutoplay;
		themeOverride: boolean;
		avatarShape: AvatarShape;
	};

	type GroupForm = {
		initialItem: TimelineTabItemV2 | null;
		title: string;
		icon: IconType;
		enabled: boolean;
		mergePolicy: TimelineMergePolicy;
		excludedKinds: TimelinePostKind[];
		excludedContents: TimelinePostContent[];
		layoutOverride: boolean;
		timelineDisplayMode: TimelineDisplayMode;
		fullWidthPost: boolean;
		postActionStyle: PostActionStyle;
		showNumbers: boolean;
		displayOverride: boolean;
		absoluteTimestamp: boolean;
		showPlatformLogo: boolean;
		showLinkPreview: boolean;
		compatLinkPreview: boolean;
		mediaOverride: boolean;
		showMedia: boolean;
		showSensitiveContent: boolean;
		expandContentWarning: boolean;
		expandMediaSize: boolean;
		videoAutoplay: VideoAutoplay;
		themeOverride: boolean;
		avatarShape: AvatarShape;
		children: TimelineTabItemV2[];
	};

	let tabItems = $state<TimelineTabItemV2[]>([]);
	let loadedTabs = $state(false);
	let enableMixedTimeline = $state(false);
	let mergePolicy = $state<TimelineMergePolicy>('TimePerPage');
	let saved = $state(false);
	let dirty = $state(false);
	let showAddDialog = $state(false);
	let editForm = $state<EditForm | null>(null);
	let groupForm = $state<GroupForm | null>(null);
	let expandedPickerSections = $state<Set<string>>(new Set(['rss']));

	const visibleTabCount = $derived(tabItems.filter((tab) => !isSystemHomeMixedTimeline(tab)).length);
	const canShowMixedTimelineSetting = $derived(visibleTabCount > 1);
	const availableMaterialIcons = $derived(
		tabSettings.availableIcons.filter((icon) => icon.type === 'Material')
	);
	const accountGroups = $derived(
		allTabs.flattenedAccountTabs.type === 'Success' ? allTabs.flattenedAccountTabs.data : []
	);
	const timelineAppearanceState = $derived(environmentSettings.timelineAppearance());

	$effect(() => {
		if (loadedTabs || tabSettings.homeTimelineTabs.type !== 'Success') return;

		tabItems = [...tabSettings.homeTimelineTabs.data];
		enableMixedTimeline = tabItems.some(isSystemHomeMixedTimeline);
		loadedTabs = true;
		dirty = false;
	});

	$effect(() => {
		if (visibleTabCount < 2 && enableMixedTimeline) {
			enableMixedTimeline = false;
		}
	});

	function tabTitle(title: UiText): string {
		if (title.type === 'Raw') return title.string;
		return localizedUiString(title.string);
	}

	function tabIconName(tab: TimelineTabItemV2): string {
		switch (tab.icon.type) {
			case 'Material':
				return tab.icon.icon;
			case 'Mixed':
				return tab.icon.icon;
			default:
				return 'List';
		}
	}

	function iconName(icon: IconType): string {
		if (icon.type === 'Material' || icon.type === 'Mixed') return icon.icon;
		return 'List';
	}

	function iconByName(name: string, fallback: IconType): IconType {
		return availableMaterialIcons.find((icon) => icon.type === 'Material' && icon.icon === name) ?? fallback;
	}

	function tabImageSource(icon: IconType): string | null {
		switch (icon.type) {
			case 'Url':
				return icon.url;
			case 'FavIcon':
				return `https://icons.duckduckgo.com/ip3/${icon.host}.ico`;
			default:
				return null;
		}
	}

	function iconKey(icon: IconType): string {
		switch (icon.type) {
			case 'Material':
				return `Material:${icon.icon}`;
			case 'Mixed':
				return `Mixed:${icon.icon}:${JSON.stringify(icon.accountKey)}`;
			case 'FavIcon':
				return `FavIcon:${icon.host}`;
			case 'Url':
				return `Url:${icon.url}`;
			case 'Avatar':
				return `Avatar:${JSON.stringify(icon.accountKey)}`;
		}
	}

	function iconPickerOptions(currentIcon: IconType): IconType[] {
		const options = [currentIcon, ...availableMaterialIcons];
		const seen = new Set<string>();
		return options.filter((icon) => {
			const key = iconKey(icon);
			if (seen.has(key)) return false;
			seen.add(key);
			return true;
		});
	}

	function isSystemHomeMixedTimeline(tab: TimelineTabItemV2): boolean {
		return tab.id === SYSTEM_HOME_MIXED_TIMELINE_ID;
	}

	function isAdded(tab: CandidateTimelineTabItemV2 | TimelineTabItemV2, list = tabItems): boolean {
		return list.some((item) => item.id === tab.id);
	}

	function markDirty(): void {
		dirty = true;
		saved = false;
	}

	function syncSystemHomeMixedTimeline(tabs: TimelineTabItemV2[]): TimelineTabItemV2[] {
		if (!tabs.some(isSystemHomeMixedTimeline)) return tabs;
		return tabSettings.homeTimelineTabsWithSystemHomeMixedTimeline(tabs, true, mergePolicy);
	}

	function moveTab(fromIndex: number, direction: -1 | 1, target: 'home' | 'group' = 'home'): void {
		const list = target === 'group' ? groupForm?.children : tabItems;
		if (!list) return;
		const toIndex = fromIndex + direction;
		if (toIndex < 0 || toIndex >= list.length) return;

		const next = [...list];
		const [item] = next.splice(fromIndex, 1);
		next.splice(toIndex, 0, item);

		if (target === 'group' && groupForm) {
			groupForm = { ...groupForm, children: next };
		} else {
			tabItems = syncSystemHomeMixedTimeline(next);
			markDirty();
		}
	}

	function deleteTab(id: string): void {
		if (id === SYSTEM_HOME_MIXED_TIMELINE_ID) {
			return;
		}
		tabItems = syncSystemHomeMixedTimeline(tabItems.filter((tab) => tab.id !== id));
		markDirty();
	}

	function setEnableMixedTimeline(value: boolean): void {
		enableMixedTimeline = value;
		tabItems = tabSettings.homeTimelineTabsWithSystemHomeMixedTimeline(tabItems, value, mergePolicy);
		markDirty();
	}

	function setMergePolicy(value: TimelineMergePolicy): void {
		mergePolicy = value;
		if (enableMixedTimeline) {
			tabItems = tabSettings.homeTimelineTabsWithSystemHomeMixedTimeline(tabItems, true, value);
		}
		markDirty();
	}

	function saveTabs(): void {
		tabSettings.replaceHomeTimelineTabsWithSystemHomeMixedTimeline(
			tabItems,
			enableMixedTimeline && canShowMixedTimelineSetting,
			mergePolicy
		);
		dirty = false;
		saved = true;
	}

	function addCandidate(tab: CandidateTimelineTabItemV2 | TimelineTabItemV2): void {
		const item = tab as unknown as TimelineTabItemV2;
		if (groupForm) {
			if (!isAdded(item, groupForm.children)) {
				groupForm = { ...groupForm, children: [...groupForm.children, item] };
			}
			return;
		}
		if (!isAdded(item)) {
			tabItems = syncSystemHomeMixedTimeline([...tabItems, item]);
			markDirty();
		}
	}

	function removeGroupChild(id: string): void {
		if (!groupForm) return;
		groupForm = { ...groupForm, children: groupForm.children.filter((tab) => tab.id !== id) };
	}

	function pickerSectionExpanded(key: string): boolean {
		return expandedPickerSections.has(key);
	}

	function setPickerSectionExpanded(key: string, expanded: boolean): void {
		const next = new Set(expandedPickerSections);
		if (expanded) {
			next.add(key);
		} else {
			next.delete(key);
		}
		expandedPickerSections = next;
	}

	function toggleCandidate(tab: CandidateTimelineTabItemV2 | TimelineTabItemV2): void {
		const item = tab as unknown as TimelineTabItemV2;
		const targetList = groupForm ? groupForm.children : tabItems;
		if (isAdded(item, targetList)) {
			if (groupForm) {
				removeGroupChild(item.id);
			} else {
				deleteTab(item.id);
			}
		} else {
			addCandidate(tab);
		}
	}

	function baseTimelineAppearance(): TimelineAppearance {
		return timelineAppearanceState.type === 'Success'
			? (timelineAppearanceState.data as TimelineAppearance)
			: defaultTimelineAppearance;
	}

	function openEditTab(tab: TimelineTabItemV2): void {
		const appearance = tabSettings.resolveAppearance(tab, baseTimelineAppearance());
		editForm = {
			tab,
			title: tabTitle(tab.title),
			icon: tab.icon,
			enabled: tab.enabled,
			excludedKinds: [...tab.filterConfig.excludedKinds],
			excludedContents: [...tab.filterConfig.excludedContents],
			layoutOverride: tabSettings.hasLayoutAppearanceOverride(tab),
			timelineDisplayMode: appearance.timelineDisplayMode,
			fullWidthPost: appearance.fullWidthPost,
			postActionStyle: appearance.postActionStyle,
			showNumbers: appearance.showNumbers,
			displayOverride: tabSettings.hasDisplayAppearanceOverride(tab),
			absoluteTimestamp: appearance.absoluteTimestamp,
			showPlatformLogo: appearance.showPlatformLogo,
			showLinkPreview: appearance.showLinkPreview,
			compatLinkPreview: appearance.compatLinkPreview,
			mediaOverride: tabSettings.hasMediaAppearanceOverride(tab),
			showMedia: appearance.showMedia,
			showSensitiveContent: appearance.showSensitiveContent,
			expandContentWarning: appearance.expandContentWarning,
			expandMediaSize: appearance.expandMediaSize,
			videoAutoplay: appearance.videoAutoplay,
			themeOverride: tabSettings.hasThemeAppearanceOverride(tab),
			avatarShape: appearance.avatarShape,
		};
	}

	function openGroupDialog(tab: TimelineTabItemV2 | null = null): void {
		const appearance = tab
			? tabSettings.resolveAppearance(tab, baseTimelineAppearance())
			: baseTimelineAppearance();
		groupForm = {
			initialItem: tab,
			title: tab ? tabTitle(tab.title) : '',
			icon: tab?.icon ?? { type: 'Material', icon: 'Rss' },
			enabled: tab?.enabled ?? true,
			mergePolicy: tab ? tabSettings.groupMergePolicy(tab) : 'TimePerPage',
			excludedKinds: tab ? [...tab.filterConfig.excludedKinds] : [],
			excludedContents: tab ? [...tab.filterConfig.excludedContents] : [],
			layoutOverride: tab ? tabSettings.hasLayoutAppearanceOverride(tab) : false,
			timelineDisplayMode: appearance.timelineDisplayMode,
			fullWidthPost: appearance.fullWidthPost,
			postActionStyle: appearance.postActionStyle,
			showNumbers: appearance.showNumbers,
			displayOverride: tab ? tabSettings.hasDisplayAppearanceOverride(tab) : false,
			absoluteTimestamp: appearance.absoluteTimestamp,
			showPlatformLogo: appearance.showPlatformLogo,
			showLinkPreview: appearance.showLinkPreview,
			compatLinkPreview: appearance.compatLinkPreview,
			mediaOverride: tab ? tabSettings.hasMediaAppearanceOverride(tab) : false,
			showMedia: appearance.showMedia,
			showSensitiveContent: appearance.showSensitiveContent,
			expandContentWarning: appearance.expandContentWarning,
			expandMediaSize: appearance.expandMediaSize,
			videoAutoplay: appearance.videoAutoplay,
			themeOverride: tab ? tabSettings.hasThemeAppearanceOverride(tab) : false,
			avatarShape: appearance.avatarShape,
			children: tab ? tabSettings.groupChildren(tab) : [],
		};
	}

	function editTab(tab: TimelineTabItemV2): void {
		if (!isSystemHomeMixedTimeline(tab) && tabSettings.isGroup(tab)) {
			openGroupDialog(tab);
		} else {
			openEditTab(tab);
		}
	}

	function saveEditForm(): void {
		if (!editForm || editForm.title.trim().length === 0) return;
		const updated = tabSettings.updateTabPresentation(
			editForm.tab,
			editForm.title.trim(),
			editForm.icon,
			editForm.enabled,
			editForm.excludedKinds.join(','),
			editForm.excludedContents.join(','),
			editForm.layoutOverride,
			editForm.timelineDisplayMode,
			editForm.fullWidthPost,
			editForm.postActionStyle,
			editForm.showNumbers,
			editForm.displayOverride,
			editForm.absoluteTimestamp,
			editForm.showPlatformLogo,
			editForm.showLinkPreview,
			editForm.compatLinkPreview,
			editForm.mediaOverride,
			editForm.showMedia,
			editForm.showSensitiveContent,
			editForm.expandContentWarning,
			editForm.expandMediaSize,
			editForm.videoAutoplay,
			editForm.themeOverride,
			editForm.avatarShape
		);

		tabItems = syncSystemHomeMixedTimeline(
			tabItems.map((tab) => (tab.id === editForm?.tab.id ? updated : tab))
		);
		if (groupForm) {
			groupForm = {
				...groupForm,
				children: groupForm.children.map((tab) => (tab.id === editForm?.tab.id ? updated : tab)),
			};
		}
		editForm = null;
		markDirty();
	}

	function saveGroupForm(): void {
		if (!groupForm) return;
		const group = tabSettings.buildGroupItem(
			groupForm.initialItem,
			groupForm.title.trim(),
			groupForm.icon,
			groupForm.enabled,
			groupForm.children,
			groupForm.mergePolicy,
			groupForm.excludedKinds.join(','),
			groupForm.excludedContents.join(','),
			groupForm.layoutOverride,
			groupForm.timelineDisplayMode,
			groupForm.fullWidthPost,
			groupForm.postActionStyle,
			groupForm.showNumbers,
			groupForm.displayOverride,
			groupForm.absoluteTimestamp,
			groupForm.showPlatformLogo,
			groupForm.showLinkPreview,
			groupForm.compatLinkPreview,
			groupForm.mediaOverride,
			groupForm.showMedia,
			groupForm.showSensitiveContent,
			groupForm.expandContentWarning,
			groupForm.expandMediaSize,
			groupForm.videoAutoplay,
			groupForm.themeOverride,
			groupForm.avatarShape,
			m.tabSettingsGroupDefaultName()
		);
		if (!group) return;

		const next = tabItems.filter((tab) => tab.id !== groupForm?.initialItem?.id && tab.id !== group.id);
		const initialIndex = groupForm.initialItem
			? tabItems.findIndex((tab) => tab.id === groupForm?.initialItem?.id)
			: -1;
		next.splice(initialIndex >= 0 ? initialIndex : next.length, 0, group);
		tabItems = syncSystemHomeMixedTimeline(next);
		groupForm = null;
		markDirty();
	}

	function toggleArrayValue<T>(items: T[], value: T): T[] {
		return items.includes(value) ? items.filter((item) => item !== value) : [...items, value];
	}
</script>

<svelte:head>
	<title>{m.tabSettingsTitle()} | Flare</title>
</svelte:head>

<div class="tab-settings-page bg-base-200">
	<AppTopBar title={m.tabSettingsTitle()}>
		{#snippet start()}
			<AppBackButton />
		{/snippet}

		{#snippet end()}
			<div class="toolbar-actions">
				<div class="dropdown dropdown-end">
					<button
						class="btn btn-ghost btn-sm rounded-box"
						type="button"
						tabindex="0"
						disabled={!loadedTabs}
					>
						<FaIcon name="Plus" size={14} />
						<span>{m.tabSettingsAdd()}</span>
					</button>
					<ul class="menu dropdown-content z-10 mt-2 w-40 rounded-box border border-base-300 bg-base-100 p-2 shadow">
						<li>
							<button type="button" onclick={() => openGroupDialog()}>
								<FaIcon name="TableList" size={14} />
								<span>{m.tabSettingsGroupMenu()}</span>
							</button>
						</li>
						<li>
							<button
								type="button"
								onclick={() => {
									showAddDialog = true;
								}}
							>
								<FaIcon name="Plus" size={14} />
								<span>{m.tabSettingsTab()}</span>
							</button>
						</li>
					</ul>
				</div>
				<button
					class="btn btn-primary btn-sm rounded-box"
					type="button"
					disabled={!loadedTabs || !dirty}
					onclick={saveTabs}
				>
					<FaIcon name="FloppyDisk" size={14} />
					<span>{m.tabSettingsSave()}</span>
				</button>
			</div>
		{/snippet}
	</AppTopBar>

	<div class="tab-settings-content">
		<section class="settings-intro" aria-label={m.tabSettingsTitle()}>
			<p>{m.tabSettingsDescription()}</p>
		</section>

		{#if tabSettings.homeTimelineTabs.type === 'Loading'}
			<section class="settings-section" aria-label={m.tabSettingsHomeTabs()}>
				<div class="list rounded-box border border-base-300 bg-base-100">
					<div class="list-row setting-row">
						<div class="skeleton h-9 w-9 rounded-box"></div>
						<div class="grid gap-2">
							<div class="skeleton h-4 w-32"></div>
							<div class="skeleton h-3 w-52"></div>
						</div>
					</div>
				</div>
			</section>
		{:else if tabSettings.homeTimelineTabs.type === 'Error'}
			<div class="alert alert-error">
				<span>{tabSettings.homeTimelineTabs.message ?? m.timelineUnableToLoadTabs()}</span>
			</div>
		{:else}
			{#if canShowMixedTimelineSetting}
				<section class="settings-section" aria-labelledby="mixed-timeline-heading">
					<h2 id="mixed-timeline-heading" class="section-title">{m.tabSettingsMixedTimeline()}</h2>
					<div class="list rounded-box border border-base-300 bg-base-100">
						<label class="list-row setting-row">
							<span class="setting-icon bg-base-200 text-base-content">
								<FaIcon name="Feeds" size={18} />
							</span>
							<span class="setting-copy">
								<span class="setting-title">{m.tabSettingsMixedTimeline()}</span>
								<span class="setting-description">{m.tabSettingsMixedTimelineDescription()}</span>
							</span>
							<input
								class="toggle toggle-primary"
								type="checkbox"
								checked={enableMixedTimeline}
								onchange={(event) => setEnableMixedTimeline(event.currentTarget.checked)}
							/>
						</label>

						{#if enableMixedTimeline}
							<label class="list-row setting-row merge-policy-row">
								<span class="setting-icon bg-base-200 text-base-content">
									<FaIcon name="Sliders" size={18} />
								</span>
								<span class="setting-copy">
									<span class="setting-title">{m.tabSettingsMergePolicy()}</span>
									<span class="setting-description">{m.tabSettingsMergePolicyDescription()}</span>
								</span>
								<select
									class="select select-bordered select-sm w-full max-w-48"
									value={mergePolicy}
									onchange={(event) => setMergePolicy(event.currentTarget.value as TimelineMergePolicy)}
								>
									{#each mergePolicyOptions as option (option.value)}
										<option value={option.value}>{option.label}</option>
									{/each}
								</select>
							</label>
						{/if}
					</div>
				</section>
			{/if}

			<section class="settings-section" aria-labelledby="home-tabs-heading">
				<h2 id="home-tabs-heading" class="section-title">{m.tabSettingsHomeTabs()}</h2>
				<div class="list rounded-box border border-base-300 bg-base-100">
					{#if tabItems.length === 0}
						<div class="list-row setting-row">
							<span class="setting-copy">
								<span class="setting-title">{m.tabSettingsCurrentTabsEmpty()}</span>
							</span>
						</div>
					{:else}
						{#each tabItems as tab, index (tab.id)}
							{@const imageSource = tabImageSource(tab.icon)}
							<div class="list-row tab-row">
								<span class="setting-icon bg-base-200 text-base-content">
									{#if imageSource}
										<img class="tab-icon-image" src={imageSource} alt="" loading="lazy" />
									{:else}
										<FaIcon name={tabIconName(tab)} size={18} />
									{/if}
								</span>
								<span class="setting-copy">
									<span class="setting-title">{tabTitle(tab.title)}</span>
									{#if isSystemHomeMixedTimeline(tab)}
										<span class="setting-description">{m.tabSettingsMixedTimeline()}</span>
									{:else if tabSettings.isGroup(tab)}
										<span class="setting-description">{m.tabSettingsGroup()}</span>
									{/if}
								</span>
								<span class="tab-actions">
									<button
										class="btn btn-ghost btn-square btn-sm rounded-box"
										type="button"
										aria-label={m.tabSettingsEdit()}
										title={m.tabSettingsEdit()}
										onclick={() => editTab(tab)}
									>
										<FaIcon name="Edit" size={13} />
									</button>
									<button
										class="btn btn-ghost btn-square btn-sm rounded-box"
										type="button"
										disabled={index === 0}
										aria-label={m.tabSettingsMoveUp()}
										title={m.tabSettingsMoveUp()}
										onclick={() => moveTab(index, -1)}
									>
										<FaIcon name="ChevronUp" size={13} />
									</button>
									<button
										class="btn btn-ghost btn-square btn-sm rounded-box"
										type="button"
										disabled={index === tabItems.length - 1}
										aria-label={m.tabSettingsMoveDown()}
										title={m.tabSettingsMoveDown()}
										onclick={() => moveTab(index, 1)}
									>
										<FaIcon name="ChevronDown" size={13} />
									</button>
									{#if !isSystemHomeMixedTimeline(tab)}
										<button
											class="btn btn-ghost btn-square btn-sm rounded-box text-error"
											type="button"
											aria-label={m.tabSettingsDelete()}
											title={m.tabSettingsDelete()}
											onclick={() => deleteTab(tab.id)}
										>
											<FaIcon name="Delete" size={14} />
										</button>
									{/if}
								</span>
							</div>
						{/each}
					{/if}
				</div>
			</section>
		{/if}
	</div>
</div>

{#if showAddDialog}
	<div class="modal modal-open" class:modal-stack-top={!!groupForm} role="dialog" aria-modal="true">
		<div class="modal-box tab-picker-modal ios-sheet">
			<header class="ios-sheet-header">
				<button
					class="btn btn-ghost btn-square btn-sm rounded-box"
					type="button"
					aria-label={m.close()}
					onclick={() => {
						showAddDialog = false;
					}}
				>
					<FaIcon name="Close" size={14} />
				</button>
				<h3>{groupForm ? m.tabSettingsAddGroupTabs() : m.tabSettingsAddTab()}</h3>
				<span class="ios-sheet-spacer"></span>
			</header>

			<div class="ios-picker-list">
				<details
					class="ios-disclosure"
					open={pickerSectionExpanded('rss')}
					ontoggle={(event) => {
						if (event.currentTarget !== event.target) return;
						setPickerSectionExpanded('rss', event.currentTarget.open);
					}}
				>
					<summary class="ios-disclosure-summary">
						<span class="setting-icon bg-base-200 text-base-content">
							<FaIcon name="Feeds" size={18} />
						</span>
						<span class="setting-title">{m.allRssFeedsTitle()}</span>
					</summary>
					<div class="ios-disclosure-content">
						{#each allTabs.rssTabs as tab (tab.id)}
							{@render CandidateRow(tab)}
						{/each}
						<a class="ios-add-source-row" href="/subscriptions">
							<span class="ios-action-icon text-primary">
								<FaIcon name="Plus" size={13} />
							</span>
							<span>{m.addRssSource()}</span>
						</a>
					</div>
				</details>

				{#if allTabs.flattenedAccountTabs.type === 'Loading'}
					<div class="ios-loading-list">
						<div class="skeleton h-12 w-full rounded-box"></div>
						<div class="skeleton h-12 w-full rounded-box"></div>
					</div>
				{:else if allTabs.flattenedAccountTabs.type === 'Error'}
					<div class="alert alert-error">
						<span>{allTabs.flattenedAccountTabs.message ?? m.timelineUnableToLoadTabs()}</span>
					</div>
				{:else}
					{#each accountGroups as group (group.profile.key.id + group.profile.key.host)}
						{@const accountKey = `account-${group.profile.key.id}-${group.profile.key.host}`}
						<details
							class="ios-disclosure"
							open={pickerSectionExpanded(accountKey)}
							ontoggle={(event) => {
								if (event.currentTarget !== event.target) return;
								setPickerSectionExpanded(accountKey, event.currentTarget.open);
							}}
						>
							<summary class="ios-disclosure-summary">
								<img class="picker-avatar" src={group.profile.avatar?.url} alt="" loading="lazy" />
								<span class="setting-copy">
									<span class="setting-title">{group.profile.handleWithoutAt}</span>
									<span class="setting-description">{group.profile.host ?? group.profile.platformType}</span>
								</span>
							</summary>
							<div class="ios-disclosure-content nested">
								{#each group.sections as section, sectionIndex (tabTitle(section.title))}
									{@const sectionKey = `${accountKey}-section-${sectionIndex}`}
									<details
										class="ios-disclosure ios-sub-disclosure"
										open={pickerSectionExpanded(sectionKey)}
										ontoggle={(event) => {
											if (event.currentTarget !== event.target) return;
											setPickerSectionExpanded(sectionKey, event.currentTarget.open);
										}}
									>
										<summary class="ios-disclosure-summary ios-sub-summary">
											<span class="setting-title">{tabTitle(section.title)}</span>
										</summary>
										<div class="ios-disclosure-content">
											{#each section.data as tab (tab.id)}
												{@render CandidateRow(tab)}
											{/each}
										</div>
									</details>
								{/each}
							</div>
						</details>
					{/each}
				{/if}

			</div>
		</div>
		<button
			class="modal-backdrop"
			type="button"
			aria-label={m.close()}
			onclick={() => {
				showAddDialog = false;
			}}
		></button>
	</div>
{/if}

{#if editForm}
	<div class="modal modal-open" role="dialog" aria-modal="true">
		<div class="modal-box tab-edit-modal">
			<div class="desktop-dialog-title">
				<div>
					<h3 class="modal-title">{m.tabSettingsEdit()}</h3>
					<p>{tabTitle(editForm.tab.title)}</p>
				</div>
				<button
					class="btn btn-ghost btn-square btn-sm rounded-box"
					type="button"
					aria-label={m.close()}
					onclick={() => (editForm = null)}
				>
					<FaIcon name="Close" size={14} />
				</button>
			</div>
			{@render TabPresentationEditor(editForm, (next) => (editForm = next))}
			<div class="modal-action desktop-dialog-actions">
				<button class="btn rounded-box" type="button" onclick={() => (editForm = null)}>{m.close()}</button>
				<button
					class="btn btn-primary rounded-box"
					type="button"
					disabled={editForm.title.trim().length === 0}
					onclick={saveEditForm}
				>
					{m.tabSettingsApply()}
				</button>
			</div>
		</div>
		<button class="modal-backdrop" type="button" aria-label={m.close()} onclick={() => (editForm = null)}></button>
	</div>
{/if}

{#if groupForm}
	<div class="modal modal-open" role="dialog" aria-modal="true">
		<div class="modal-box group-edit-modal">
			<div class="desktop-dialog-title">
				<div>
					<h3 class="modal-title">
						{groupForm.initialItem ? m.tabSettingsEditGroup() : m.tabSettingsAddGroup()}
					</h3>
					<p>{groupForm.title || m.tabSettingsGroupDefaultName()}</p>
				</div>
				<button
					class="btn btn-ghost btn-square btn-sm rounded-box"
					type="button"
					aria-label={m.close()}
					onclick={() => (groupForm = null)}
				>
					<FaIcon name="Close" size={14} />
				</button>
			</div>
			{@render GroupPresentationEditor(groupForm, (next) => (groupForm = next))}
			<div class="modal-action desktop-dialog-actions">
				<button class="btn rounded-box" type="button" onclick={() => (groupForm = null)}>{m.close()}</button>
				<button
					class="btn btn-primary rounded-box"
					type="button"
					disabled={groupForm.children.length === 0}
					onclick={saveGroupForm}
				>
					{m.tabSettingsApply()}
				</button>
			</div>
		</div>
		<button class="modal-backdrop" type="button" aria-label={m.close()} onclick={() => (groupForm = null)}></button>
	</div>
{/if}

{#snippet CandidateRow(tab: CandidateTimelineTabItemV2 | TimelineTabItemV2)}
	{@const item = tab as unknown as TimelineTabItemV2}
	{@const targetList = groupForm ? groupForm.children : tabItems}
	{@const added = isAdded(item, targetList)}
	<label
		class="candidate-row"
	>
		<span class="setting-icon bg-base-200 text-base-content">
			{@render TabIconView(item.icon, 18)}
		</span>
		<span class="setting-copy">
			<span class="setting-title">{tabTitle(item.title)}</span>
		</span>
		<input
			class="checkbox checkbox-primary checkbox-sm"
			type="checkbox"
			checked={added}
			aria-label={tabTitle(item.title)}
			onchange={() => toggleCandidate(tab)}
		/>
	</label>
{/snippet}

{#snippet TabPresentationEditor(form: EditForm, update: (next: EditForm) => void)}
	<div class="tab-edit-layout">
		<div class="tab-edit-columns">
			<section class="tab-edit-panel">
				<h4 class="section-title">{m.editTabName()}</h4>
				<label class="form-control">
					<input
						class="input input-bordered"
						value={form.title}
						placeholder={m.editTabNamePlaceholder()}
						oninput={(event) => update({ ...form, title: event.currentTarget.value })}
					/>
				</label>

				<h4 class="section-title">{m.editTabIcon()}</h4>
				<div class="icon-picker-grid">
					{#each iconPickerOptions(form.icon) as icon (iconKey(icon))}
						<button
							class="btn btn-ghost btn-square rounded-box icon-choice"
							class:icon-choice-selected={iconKey(form.icon) === iconKey(icon)}
							type="button"
							title={icon.type}
							aria-label={icon.type}
							onclick={() => update({ ...form, icon })}
						>
							{@render TabIconView(icon, 16)}
						</button>
					{/each}
				</div>
			</section>

			<section class="tab-edit-panel">
				{#if !isSystemHomeMixedTimeline(form.tab)}
					<h4 class="section-title">{m.editTabEnabled()}</h4>
					<label class="list-row compact-row rounded-box border border-base-300 bg-base-100">
						<span class="setting-copy">
							<span class="setting-title">{m.editTabEnabled()}</span>
							<span class="setting-description">{m.editTabEnabledDescription()}</span>
						</span>
						<input
							class="toggle toggle-primary"
							type="checkbox"
							checked={form.enabled}
							onchange={(event) => update({ ...form, enabled: event.currentTarget.checked })}
						/>
					</label>
				{/if}

				<div class="filter-copy">
					<h4 class="section-title">{m.tabSettingsFilterTitle()}</h4>
					<p>{m.tabSettingsFilterDescription()}</p>
				</div>
				<div class="tab-edit-filter">
					<div>
						<h4 class="section-title">{m.tabSettingsFilterKinds()}</h4>
						<div class="filter-options">
							{#each postKindOptions as option (option.value)}
								<label class="filter-option desktop-filter-option">
									<input
										class="checkbox checkbox-sm"
										type="checkbox"
										checked={form.excludedKinds.includes(option.value)}
										onchange={() =>
											update({
												...form,
												excludedKinds: toggleArrayValue(form.excludedKinds, option.value),
											})}
									/>
									<span>{option.label}</span>
								</label>
							{/each}
						</div>
					</div>
					<div>
						<h4 class="section-title">{m.tabSettingsFilterContents()}</h4>
						<div class="filter-options">
							{#each postContentOptions as option (option.value)}
								<label class="filter-option desktop-filter-option">
									<input
										class="checkbox checkbox-sm"
										type="checkbox"
										checked={form.excludedContents.includes(option.value)}
										onchange={() =>
											update({
												...form,
												excludedContents: toggleArrayValue(form.excludedContents, option.value),
											})}
									/>
									<span>{option.label}</span>
								</label>
							{/each}
						</div>
					</div>
				</div>

				<section class="appearance-overrides">
					{@render AppearanceOverrideGroup(
						m.settingsAppearanceLayoutGroupTitle(),
						m.settingsAppearanceLayoutGroupSubtitle(),
						form.layoutOverride,
						(value) => update({ ...form, layoutOverride: value })
					)}
					{#if form.layoutOverride}
						<div class="appearance-group-content">
							{@render AppearanceSelectRow(
								m.settingsAppearanceTimelineDisplayMode(),
								m.settingsAppearanceTimelineDisplayModeDescription(),
								form.timelineDisplayMode,
								timelineDisplayOptions,
								(value) => update({ ...form, timelineDisplayMode: value as TimelineDisplayMode })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceFullWidthPost(),
								m.settingsAppearanceFullWidthPostDescription(),
								form.fullWidthPost,
								(value) => update({ ...form, fullWidthPost: value })
							)}
							{@render AppearanceSelectRow(
								m.settingsAppearancePostActionStyle(),
								m.settingsAppearancePostActionStyleDescription(),
								form.postActionStyle,
								postActionOptions,
								(value) => update({ ...form, postActionStyle: value as PostActionStyle })
							)}
							{#if form.postActionStyle !== 'Hidden'}
								{@render AppearanceToggleRow(
									m.settingsAppearanceShowNumbers(),
									m.settingsAppearanceShowNumbersDescription(),
									form.showNumbers,
									(value) => update({ ...form, showNumbers: value })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceDisplayGroupTitle(),
						m.settingsAppearanceDisplayGroupSubtitle(),
						form.displayOverride,
						(value) => update({ ...form, displayOverride: value })
					)}
					{#if form.displayOverride}
						<div class="appearance-group-content">
							{@render AppearanceToggleRow(
								m.settingsAppearanceAbsoluteTimestamp(),
								m.settingsAppearanceAbsoluteTimestampDescription(),
								form.absoluteTimestamp,
								(value) => update({ ...form, absoluteTimestamp: value })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowPlatformLogo(),
								m.settingsAppearanceShowPlatformLogoDescription(),
								form.showPlatformLogo,
								(value) => update({ ...form, showPlatformLogo: value })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowLinkPreviews(),
								m.settingsAppearanceShowLinkPreviewsDescription(),
								form.showLinkPreview,
								(value) => update({ ...form, showLinkPreview: value })
							)}
							{#if form.showLinkPreview}
								{@render AppearanceToggleRow(
									m.settingsAppearanceCompatLinkPreviews(),
									m.settingsAppearanceCompatLinkPreviewsDescription(),
									form.compatLinkPreview,
									(value) => update({ ...form, compatLinkPreview: value })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceMediaGroupTitle(),
						m.settingsAppearanceMediaGroupSubtitle(),
						form.mediaOverride,
						(value) => update({ ...form, mediaOverride: value })
					)}
					{#if form.mediaOverride}
						<div class="appearance-group-content">
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowMedia(),
								m.settingsAppearanceShowMediaDescription(),
								form.showMedia,
								(value) => update({ ...form, showMedia: value })
							)}
							{#if form.showMedia}
								{@render AppearanceToggleRow(
									m.settingsAppearanceShowCwImg(),
									m.settingsAppearanceShowCwImgDescription(),
									form.showSensitiveContent,
									(value) => update({ ...form, showSensitiveContent: value })
								)}
								{@render AppearanceToggleRow(
									m.settingsAppearanceExpandContentWarning(),
									m.settingsAppearanceExpandContentWarningDescription(),
									form.expandContentWarning,
									(value) => update({ ...form, expandContentWarning: value })
								)}
								{@render AppearanceToggleRow(
									m.settingsAppearanceExpandMedia(),
									m.settingsAppearanceExpandMediaDescription(),
									form.expandMediaSize,
									(value) => update({ ...form, expandMediaSize: value })
								)}
								{@render AppearanceSelectRow(
									m.settingsAppearanceVideoAutoplay(),
									m.settingsAppearanceVideoAutoplayDescription(),
									form.videoAutoplay,
									videoAutoplayOptions,
									(value) => update({ ...form, videoAutoplay: value as VideoAutoplay })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceThemeGroupTitle(),
						m.settingsAppearanceThemeGroupSubtitle(),
						form.themeOverride,
						(value) => update({ ...form, themeOverride: value })
					)}
					{#if form.themeOverride}
						<div class="appearance-group-content">
							{@render AppearanceSelectRow(
								m.settingsAppearanceAvatarShape(),
								m.settingsAppearanceAvatarShapeDescription(),
								form.avatarShape,
								avatarShapeOptions,
								(value) => update({ ...form, avatarShape: value as AvatarShape })
							)}
						</div>
					{/if}
				</section>
			</section>
		</div>
	</div>
{/snippet}

{#snippet AppearanceOverrideGroup(
	title: string,
	description: string,
	enabled: boolean,
	onchange: (value: boolean) => void
)}
	<label class="appearance-override-row rounded-box border border-base-300 bg-base-100">
		<span class="setting-copy">
			<span class="setting-title">{title}</span>
			<span class="setting-description">{description}</span>
		</span>
		<input
			class="toggle toggle-primary toggle-sm"
			type="checkbox"
			checked={enabled}
			onchange={(event) => onchange(event.currentTarget.checked)}
		/>
	</label>
{/snippet}

{#snippet AppearanceToggleRow(
	title: string,
	description: string,
	value: boolean,
	onchange: (value: boolean) => void
)}
	<label class="appearance-setting-row">
		<span class="setting-copy">
			<span class="setting-title">{title}</span>
			<span class="setting-description">{description}</span>
		</span>
		<input
			class="toggle toggle-sm"
			type="checkbox"
			checked={value}
			onchange={(event) => onchange(event.currentTarget.checked)}
		/>
	</label>
{/snippet}

{#snippet AppearanceSelectRow<T extends string>(
	title: string,
	description: string,
	value: T,
	options: Array<{ value: T; label: string }>,
	onchange: (value: T) => void
)}
	<label class="appearance-setting-row">
		<span class="setting-copy">
			<span class="setting-title">{title}</span>
			<span class="setting-description">{description}</span>
		</span>
		<select
			class="select select-bordered select-sm w-44 max-w-full"
			value={value}
			onchange={(event) => onchange(event.currentTarget.value as T)}
		>
			{#each options as option (option.value)}
				<option value={option.value}>{option.label}</option>
			{/each}
		</select>
	</label>
{/snippet}

{#snippet TabIconView(icon: IconType, size = 18)}
	{@const imageSource = tabImageSource(icon)}
	{#if imageSource}
		<img class="tab-icon-image" src={imageSource} alt="" loading="lazy" />
	{:else if icon.type === 'Material' || icon.type === 'Mixed'}
		<FaIcon name={icon.icon} {size} />
	{:else if icon.type === 'Avatar'}
		<FaIcon name="Profile" {size} />
	{:else}
		<FaIcon name="List" {size} />
	{/if}
{/snippet}

{#snippet GroupPresentationEditor(form: GroupForm, update: (next: GroupForm) => void)}
	<div class="tab-edit-layout">
		<div class="tab-edit-columns">
			<section class="tab-edit-panel">
				<h4 class="section-title">{m.editTabName()}</h4>
				<label class="form-control">
					<input
						class="input input-bordered"
						value={form.title}
						placeholder={m.tabSettingsGroupNamePlaceholder()}
						oninput={(event) => update({ ...form, title: event.currentTarget.value })}
					/>
				</label>

				<h4 class="section-title">{m.editTabIcon()}</h4>
				<div class="icon-picker-grid">
					{#each iconPickerOptions(form.icon) as icon (iconKey(icon))}
						<button
							class="btn btn-ghost btn-square rounded-box icon-choice"
							class:icon-choice-selected={iconKey(form.icon) === iconKey(icon)}
							type="button"
							title={icon.type}
							aria-label={icon.type}
							onclick={() => update({ ...form, icon })}
						>
							{@render TabIconView(icon, 16)}
						</button>
					{/each}
				</div>

				<h4 class="section-title">{m.editTabEnabled()}</h4>
				<label class="list-row compact-row rounded-box border border-base-300 bg-base-100">
					<span class="setting-copy">
						<span class="setting-title">{m.editTabEnabled()}</span>
						<span class="setting-description">{m.editTabEnabledDescription()}</span>
					</span>
					<input
						class="toggle toggle-primary"
						type="checkbox"
						checked={form.enabled}
						onchange={(event) => update({ ...form, enabled: event.currentTarget.checked })}
					/>
				</label>

				<h4 class="section-title">{m.tabSettingsBehaviorGroupTitle()}</h4>
				<h4 class="section-title">{m.tabSettingsMergePolicy()}</h4>
				<label class="appearance-setting-row rounded-box border border-base-300 bg-base-100">
					<span class="setting-copy">
						<span class="setting-title">{m.tabSettingsMergePolicy()}</span>
						<span class="setting-description">{m.tabSettingsMergePolicyDescription()}</span>
					</span>
					<select
						class="select select-bordered select-sm w-44 max-w-full"
						value={form.mergePolicy}
						onchange={(event) =>
							update({ ...form, mergePolicy: event.currentTarget.value as TimelineMergePolicy })}
					>
						{#each mergePolicyOptions as option (option.value)}
							<option value={option.value}>{option.label}</option>
						{/each}
					</select>
				</label>

			</section>

			<section class="tab-edit-panel">
				<div class="filter-copy">
					<h4 class="section-title">{m.tabSettingsFilterTitle()}</h4>
					<p>{m.tabSettingsFilterDescription()}</p>
				</div>
				<div class="tab-edit-filter">
					<div>
						<h4 class="section-title">{m.tabSettingsFilterKinds()}</h4>
						<div class="filter-options">
							{#each postKindOptions as option (option.value)}
								<label class="filter-option desktop-filter-option">
									<input
										class="checkbox checkbox-sm"
										type="checkbox"
										checked={form.excludedKinds.includes(option.value)}
										onchange={() =>
											update({
												...form,
												excludedKinds: toggleArrayValue(form.excludedKinds, option.value),
											})}
									/>
									<span>{option.label}</span>
								</label>
							{/each}
						</div>
					</div>
					<div>
						<h4 class="section-title">{m.tabSettingsFilterContents()}</h4>
						<div class="filter-options">
							{#each postContentOptions as option (option.value)}
								<label class="filter-option desktop-filter-option">
									<input
										class="checkbox checkbox-sm"
										type="checkbox"
										checked={form.excludedContents.includes(option.value)}
										onchange={() =>
											update({
												...form,
												excludedContents: toggleArrayValue(form.excludedContents, option.value),
											})}
									/>
									<span>{option.label}</span>
								</label>
							{/each}
						</div>
					</div>
				</div>

				<section class="appearance-overrides">
					{@render AppearanceOverrideGroup(
						m.settingsAppearanceLayoutGroupTitle(),
						m.settingsAppearanceLayoutGroupSubtitle(),
						form.layoutOverride,
						(value) => update({ ...form, layoutOverride: value })
					)}
					{#if form.layoutOverride}
						<div class="appearance-group-content">
							{@render AppearanceSelectRow(
								m.settingsAppearanceTimelineDisplayMode(),
								m.settingsAppearanceTimelineDisplayModeDescription(),
								form.timelineDisplayMode,
								timelineDisplayOptions,
								(value) => update({ ...form, timelineDisplayMode: value as TimelineDisplayMode })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceFullWidthPost(),
								m.settingsAppearanceFullWidthPostDescription(),
								form.fullWidthPost,
								(value) => update({ ...form, fullWidthPost: value })
							)}
							{@render AppearanceSelectRow(
								m.settingsAppearancePostActionStyle(),
								m.settingsAppearancePostActionStyleDescription(),
								form.postActionStyle,
								postActionOptions,
								(value) => update({ ...form, postActionStyle: value as PostActionStyle })
							)}
							{#if form.postActionStyle !== 'Hidden'}
								{@render AppearanceToggleRow(
									m.settingsAppearanceShowNumbers(),
									m.settingsAppearanceShowNumbersDescription(),
									form.showNumbers,
									(value) => update({ ...form, showNumbers: value })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceDisplayGroupTitle(),
						m.settingsAppearanceDisplayGroupSubtitle(),
						form.displayOverride,
						(value) => update({ ...form, displayOverride: value })
					)}
					{#if form.displayOverride}
						<div class="appearance-group-content">
							{@render AppearanceToggleRow(
								m.settingsAppearanceAbsoluteTimestamp(),
								m.settingsAppearanceAbsoluteTimestampDescription(),
								form.absoluteTimestamp,
								(value) => update({ ...form, absoluteTimestamp: value })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowPlatformLogo(),
								m.settingsAppearanceShowPlatformLogoDescription(),
								form.showPlatformLogo,
								(value) => update({ ...form, showPlatformLogo: value })
							)}
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowLinkPreviews(),
								m.settingsAppearanceShowLinkPreviewsDescription(),
								form.showLinkPreview,
								(value) => update({ ...form, showLinkPreview: value })
							)}
							{#if form.showLinkPreview}
								{@render AppearanceToggleRow(
									m.settingsAppearanceCompatLinkPreviews(),
									m.settingsAppearanceCompatLinkPreviewsDescription(),
									form.compatLinkPreview,
									(value) => update({ ...form, compatLinkPreview: value })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceMediaGroupTitle(),
						m.settingsAppearanceMediaGroupSubtitle(),
						form.mediaOverride,
						(value) => update({ ...form, mediaOverride: value })
					)}
					{#if form.mediaOverride}
						<div class="appearance-group-content">
							{@render AppearanceToggleRow(
								m.settingsAppearanceShowMedia(),
								m.settingsAppearanceShowMediaDescription(),
								form.showMedia,
								(value) => update({ ...form, showMedia: value })
							)}
							{#if form.showMedia}
								{@render AppearanceToggleRow(
									m.settingsAppearanceShowCwImg(),
									m.settingsAppearanceShowCwImgDescription(),
									form.showSensitiveContent,
									(value) => update({ ...form, showSensitiveContent: value })
								)}
								{@render AppearanceToggleRow(
									m.settingsAppearanceExpandContentWarning(),
									m.settingsAppearanceExpandContentWarningDescription(),
									form.expandContentWarning,
									(value) => update({ ...form, expandContentWarning: value })
								)}
								{@render AppearanceToggleRow(
									m.settingsAppearanceExpandMedia(),
									m.settingsAppearanceExpandMediaDescription(),
									form.expandMediaSize,
									(value) => update({ ...form, expandMediaSize: value })
								)}
								{@render AppearanceSelectRow(
									m.settingsAppearanceVideoAutoplay(),
									m.settingsAppearanceVideoAutoplayDescription(),
									form.videoAutoplay,
									videoAutoplayOptions,
									(value) => update({ ...form, videoAutoplay: value as VideoAutoplay })
								)}
							{/if}
						</div>
					{/if}

					{@render AppearanceOverrideGroup(
						m.settingsAppearanceThemeGroupTitle(),
						m.settingsAppearanceThemeGroupSubtitle(),
						form.themeOverride,
						(value) => update({ ...form, themeOverride: value })
					)}
					{#if form.themeOverride}
						<div class="appearance-group-content">
							{@render AppearanceSelectRow(
								m.settingsAppearanceAvatarShape(),
								m.settingsAppearanceAvatarShapeDescription(),
								form.avatarShape,
								avatarShapeOptions,
								(value) => update({ ...form, avatarShape: value as AvatarShape })
							)}
						</div>
					{/if}
				</section>

				<section class="group-children group-tabs-panel">
					<div class="group-children-header">
						<h4 class="section-title">{m.tabSettingsGroupTabs()}</h4>
						<button
							class="btn btn-ghost btn-sm rounded-box"
							type="button"
							onclick={() => {
								showAddDialog = true;
							}}
						>
							<FaIcon name="Plus" size={13} />
							<span>{m.tabSettingsAddTab()}</span>
						</button>
					</div>
					<div class="list group-tab-list rounded-box border border-base-300 bg-base-100">
						{#if form.children.length === 0}
							<div class="list-row setting-row">
								<span class="setting-title">{m.tabSettingsGroupEmpty()}</span>
							</div>
						{:else}
							{#each form.children as tab, index (tab.id)}
								<div class="list-row tab-row">
									<span class="setting-icon bg-base-200 text-base-content">
										{@render TabIconView(tab.icon, 18)}
									</span>
									<span class="setting-title">{tabTitle(tab.title)}</span>
									<span class="tab-actions">
										<button
											class="btn btn-ghost btn-square btn-sm rounded-box"
											type="button"
											disabled={index === 0}
											aria-label={m.tabSettingsMoveUp()}
											title={m.tabSettingsMoveUp()}
											onclick={() => moveTab(index, -1, 'group')}
										>
											<FaIcon name="ChevronUp" size={13} />
										</button>
										<button
											class="btn btn-ghost btn-square btn-sm rounded-box"
											type="button"
											disabled={index === form.children.length - 1}
											aria-label={m.tabSettingsMoveDown()}
											title={m.tabSettingsMoveDown()}
											onclick={() => moveTab(index, 1, 'group')}
										>
											<FaIcon name="ChevronDown" size={13} />
										</button>
										<button
											class="btn btn-ghost btn-square btn-sm rounded-box text-error"
											type="button"
											aria-label={m.tabSettingsDelete()}
											title={m.tabSettingsDelete()}
											onclick={() => removeGroupChild(tab.id)}
										>
											<FaIcon name="Delete" size={14} />
										</button>
									</span>
								</div>
							{/each}
						{/if}
					</div>
				</section>
			</section>
		</div>
	</div>
{/snippet}

<style>
	.tab-settings-page {
		min-height: 100vh;
	}

	.toolbar-actions {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
	}

	.tab-settings-content {
		display: grid;
		gap: 1.2rem;
		padding: 1rem;
	}

	.settings-intro {
		color: color-mix(in oklab, var(--color-base-content) 68%, transparent);
		font-size: 0.88rem;
		line-height: 1.45;
	}

	.settings-section,
	.group-children {
		display: grid;
		gap: 0.45rem;
		min-width: 0;
	}

	.section-title {
		padding-inline: 0.25rem;
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.74rem;
		font-weight: 700;
		letter-spacing: 0;
		text-transform: uppercase;
	}

	.modal-title {
		margin: 0;
		font-size: 1.05rem;
		font-weight: 700;
	}

	.setting-row,
	.tab-row,
	.candidate-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 4.1rem;
		padding: 0.8rem 1rem;
		text-align: start;
	}

	.compact-row {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		padding: 0.8rem 1rem;
	}

	.candidate-row {
		width: 100%;
	}

	.setting-icon {
		display: grid;
		width: 2.15rem;
		height: 2.15rem;
		place-items: center;
		overflow: hidden;
		border-radius: var(--radius-box);
	}

	.tab-icon-image {
		width: 1.2rem;
		height: 1.2rem;
		object-fit: contain;
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

	.tab-actions {
		display: inline-flex;
		align-items: center;
		gap: 0.1rem;
	}

	.tab-picker-modal {
		max-width: min(42rem, calc(100vw - 2rem));
		border-radius: var(--radius-box);
	}

	.modal-stack-top {
		z-index: 1100;
	}

	.ios-sheet {
		display: flex;
		flex-direction: column;
		width: min(42rem, calc(100vw - 1rem));
		height: min(82dvh, 44rem);
		max-height: min(82dvh, 44rem);
		min-height: 0;
		padding: 0;
		overflow: hidden;
		background: var(--color-base-200);
	}

	.ios-sheet-header {
		display: grid;
		grid-template-columns: 2.25rem minmax(0, 1fr) 2.25rem;
		align-items: center;
		gap: 0.5rem;
		padding: 0.7rem 0.8rem;
		border-bottom: 1px solid var(--color-base-300);
		background: color-mix(in oklab, var(--color-base-100) 88%, var(--color-base-200));
	}

	.ios-sheet-header h3 {
		overflow-wrap: anywhere;
		font-size: 1rem;
		font-weight: 750;
		line-height: 1.25;
		text-align: center;
	}

	.ios-sheet-spacer {
		width: 2.25rem;
	}

	.ios-picker-list {
		display: grid;
		align-content: start;
		gap: 0.8rem;
		flex: 1 1 auto;
		max-height: none;
		min-height: 0;
		overflow-x: hidden;
		overflow-y: auto;
		padding: 0.9rem;
		overscroll-behavior: contain;
		-webkit-overflow-scrolling: touch;
	}

	.ios-disclosure {
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		background: var(--color-base-100);
	}

	.ios-disclosure-summary {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: 0.75rem;
		min-height: 3.6rem;
		padding: 0.75rem 0.9rem;
		cursor: pointer;
		list-style: none;
	}

	.ios-disclosure-summary::-webkit-details-marker {
		display: none;
	}

	.ios-disclosure-summary::after {
		content: "";
		width: 0.45rem;
		height: 0.45rem;
		border-right: 1.5px solid currentColor;
		border-bottom: 1.5px solid currentColor;
		justify-self: end;
		grid-column: 3;
		transform: rotate(-45deg);
		opacity: 0.55;
		transition: transform 0.16s ease;
	}

	.ios-disclosure[open] > .ios-disclosure-summary::after {
		transform: rotate(45deg);
	}

	.ios-disclosure-summary {
		grid-template-columns: auto minmax(0, 1fr) auto;
	}

	.ios-disclosure-content {
		display: grid;
		border-top: 1px solid var(--color-base-300);
	}

	.ios-disclosure-content.nested {
		gap: 0.6rem;
		padding: 0.65rem;
		background: var(--color-base-200);
	}

	.ios-sub-disclosure {
		border-color: color-mix(in oklab, var(--color-base-300) 78%, transparent);
	}

	.ios-sub-summary {
		min-height: 3rem;
		padding-block: 0.65rem;
	}

	.picker-avatar {
		width: 2.15rem;
		height: 2.15rem;
		border-radius: 999px;
		object-fit: cover;
		background: var(--color-base-300);
	}

	.candidate-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		width: 100%;
		min-height: 3.6rem;
		padding: 0.72rem 0.9rem;
		border-top: 1px solid color-mix(in oklab, var(--color-base-300) 70%, transparent);
		cursor: pointer;
		text-align: start;
	}

	.ios-disclosure-content > .candidate-row:first-child {
		border-top: 0;
	}

	.ios-action-icon {
		display: grid;
		width: 1.75rem;
		height: 1.75rem;
		place-items: center;
		border-radius: 999px;
		background: color-mix(in oklab, currentColor 10%, transparent);
	}

	.ios-add-source-row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
		align-items: center;
		gap: 0.75rem;
		min-height: 3.4rem;
		padding: 0.7rem 0.9rem;
		border-top: 1px solid color-mix(in oklab, var(--color-base-300) 70%, transparent);
		color: var(--color-primary);
		font-weight: 650;
	}

	.ios-loading-list {
		display: grid;
		gap: 0.6rem;
	}

	.tab-edit-modal,
	.group-edit-modal {
		max-width: min(52rem, calc(100vw - 2rem));
		padding: 0;
		overflow: hidden;
		border-radius: var(--radius-box);
	}

	.group-edit-modal {
		display: grid;
		grid-template-rows: auto minmax(0, 1fr) auto;
		max-height: min(86dvh, 48rem);
	}

	.group-edit-modal .tab-edit-layout {
		min-height: 0;
		overflow-y: auto;
	}

	.group-edit-modal .tab-edit-columns {
		grid-template-columns: minmax(14rem, 0.74fr) minmax(22rem, 1.26fr);
	}

	.desktop-dialog-title {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 1rem;
		padding: 1rem 1rem 0.85rem;
		border-bottom: 1px solid var(--color-base-300);
	}

	.desktop-dialog-title p {
		margin-top: 0.2rem;
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.82rem;
		line-height: 1.3;
		overflow-wrap: anywhere;
	}

	.tab-edit-layout {
		display: grid;
		gap: 1rem;
		padding: 1rem;
		background: var(--color-base-200);
	}

	.tab-edit-columns {
		display: grid;
		grid-template-columns: minmax(14rem, 0.82fr) minmax(18rem, 1fr);
		gap: 1rem;
		align-items: start;
	}

	.tab-edit-panel {
		display: grid;
		gap: 0.65rem;
		min-width: 0;
		padding: 0.9rem;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		background: var(--color-base-100);
	}

	.icon-picker-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(2.25rem, 1fr));
		gap: 0.35rem;
		max-height: 14rem;
		overflow: auto;
		padding-right: 0.15rem;
	}

	.icon-choice {
		width: 2.25rem;
		height: 2.25rem;
		min-height: 2.25rem;
		border: 1px solid transparent;
	}

	.icon-choice-selected {
		border-color: var(--color-primary);
		background: color-mix(in oklab, var(--color-primary) 14%, transparent);
		color: var(--color-primary);
	}

	.tab-edit-filter {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 0.8rem;
	}

	.filter-copy {
		display: grid;
		gap: 0.2rem;
	}

	.filter-copy p {
		padding-inline: 0.25rem;
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.8rem;
		line-height: 1.35;
	}

	.appearance-overrides {
		display: grid;
		gap: 0.55rem;
	}

	.appearance-override-row,
	.appearance-setting-row {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.75rem;
		min-height: 3.6rem;
		padding: 0.7rem 0.8rem;
	}

	.appearance-group-content {
		display: grid;
		gap: 0.2rem;
		margin-top: -0.25rem;
		margin-bottom: 0.25rem;
		padding: 0.35rem 0.55rem 0.45rem;
		border-inline-start: 2px solid var(--color-base-300);
	}

	.appearance-setting-row {
		min-height: 3.35rem;
		padding: 0.55rem 0.35rem 0.55rem 0.65rem;
	}

	.desktop-filter-option {
		min-height: 2rem;
		padding: 0.25rem 0;
	}

	.desktop-dialog-actions {
		margin: 0;
		padding: 0.85rem 1rem 1rem;
		border-top: 1px solid var(--color-base-300);
		background: var(--color-base-100);
	}

	.group-children-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
	}

	.group-tabs-panel {
		padding-top: 0.25rem;
	}

	.group-tab-list {
		max-height: 18rem;
		overflow: auto;
	}

	.filter-options {
		display: grid;
		gap: 0.45rem;
		padding-top: 0.35rem;
	}

	.filter-option {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		font-size: 0.9rem;
	}

	@media (max-width: 560px) {
		.merge-policy-row,
		.tab-row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.merge-policy-row > :global(select),
		.tab-actions {
			grid-column: 2;
			justify-self: start;
		}

		.tab-edit-columns,
		.group-edit-modal .tab-edit-columns,
		.tab-edit-filter,
		.appearance-setting-row {
			grid-template-columns: 1fr;
		}

		.appearance-setting-row > :global(select),
		.appearance-setting-row > :global(input) {
			justify-self: start;
		}
	}
</style>
