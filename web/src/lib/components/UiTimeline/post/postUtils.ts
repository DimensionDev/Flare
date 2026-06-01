import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
import type {
	ActionMenu,
	ActionMenuItem,
	PlatformType,
	TranslationDisplayState,
	UiMedia,
	UiTimelineV2Message,
	UiTimelineV2Post,
	UiTimelineV2PostVisibility,
} from '@flare/web-presenters/timeline.svelte';

export const defaultTimelineAppearance = {
	absoluteTimestamp: false,
	aiConfig: { tldr: false, translation: true },
	avatarShape: 'CIRCLE',
	compatLinkPreview: false,
	expandMediaSize: true,
	fullWidthPost: false,
	lineLimit: 5,
	postActionStyle: 'LeftAligned',
	showLinkPreview: true,
	showMedia: true,
	showNumbers: true,
	showPlatformLogo: true,
	showSensitiveContent: false,
	showTranslateButton: true,
	timelineDisplayMode: 'Plain',
	videoAutoplay: 'NEVER',
} as TimelineAppearance;

type ActionDisplayItem = Extract<ActionMenu, { type: 'Item' }> | ActionMenuItem;

export function postKey(post: UiTimelineV2Post): string {
	return post.itemKey ?? `${post.statusKey.host}:${post.statusKey.id}`;
}

export function initials(name: string | null | undefined): string {
	const value = name?.trim();
	if (!value) return '';
	const words = value.split(/\s+/).slice(0, 2);
	return words.map((word) => word[0]?.toUpperCase()).join('');
}

export function formatNumber(value: number): string {
	return new Intl.NumberFormat(undefined, {
		notation: value >= 10_000 ? 'compact' : 'standard',
		maximumFractionDigits: 1,
	}).format(value);
}

export function mediaPreview(media: UiMedia): string {
	switch (media.type) {
		case 'Video':
			return media.thumbnailUrl;
		case 'Audio':
			return media.previewUrl ?? '';
		default:
			return media.previewUrl;
	}
}

export function mediaAlt(media: UiMedia): string | null {
	return media.description;
}

export function mediaAspect(media: UiMedia): string {
	if (media.type === 'Audio') return '1 / 1';
	const width = Math.max(media.width, 1);
	const height = Math.max(media.height, 1);
	return `${width} / ${height}`;
}

export function pollPercentage(option: { percentage: number }): number {
	const value = option.percentage <= 1 ? option.percentage * 100 : option.percentage;
	return Math.max(0, Math.min(value, 100));
}

export function visibilityIcon(visibility: UiTimelineV2PostVisibility): string {
	switch (visibility) {
		case 'Public':
			return 'World';
		case 'Home':
			return 'LockOpen';
		case 'Followers':
			return 'Lock';
		case 'Specified':
			return 'Mention';
		case 'Channel':
			return 'Channel';
	}
}

export function visibilityLabel(visibility: UiTimelineV2PostVisibility): string {
	switch (visibility) {
		case 'Public':
			return 'Public';
		case 'Home':
			return 'Home timeline';
		case 'Followers':
			return 'Followers only';
		case 'Specified':
			return 'Specified users';
		case 'Channel':
			return 'Channel';
	}
}

export function platformIcon(platform: PlatformType): string {
	switch (platform) {
		case 'xQt':
			return 'X';
		case 'VVo':
			return 'Weibo';
		default:
			return platform;
	}
}

export function translationLabel(state: TranslationDisplayState): string {
	switch (state) {
		case 'Hidden':
			return 'Translation hidden';
		case 'Translating':
			return 'Translating';
		case 'Translated':
			return 'Translated';
		case 'Failed':
			return 'Translation failed';
	}
}

export function messageText(message: UiTimelineV2Message): string {
	switch (message.type.type) {
		case 'Raw':
			return message.type.content;
		case 'Localized':
			return localizedMessageText(message.type.data);
		case 'Unknown':
			return message.type.rawType;
	}
}

export function cardHost(url: string): string {
	try {
		return new URL(url).host;
	} catch {
		return url;
	}
}

export function hasActionControls(actions: ActionMenu[]): boolean {
	return actions.some((action) => action.type === 'Item' || action.type === 'Group');
}

export function actionLabel(action: ActionDisplayItem): string {
	if (action.text?.type === 'Raw') return action.text.text;
	if (action.text?.type === 'Localized') return action.text.parameters.join(' ') || action.icon || 'Action';
	return action.icon ?? 'Action';
}

function localizedMessageText(id: string): string {
	switch (id) {
		case 'Repost':
			return 'reposted';
		case 'Quote':
			return 'quoted';
		case 'Reply':
			return 'replied';
		case 'Mention':
			return 'mentioned you';
		case 'Like':
		case 'Favourite':
			return 'liked';
		default:
			return id;
	}
}
