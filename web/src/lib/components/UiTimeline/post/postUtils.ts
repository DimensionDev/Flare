import type { TimelineAppearance } from '$lib/environment/environmentSettings.svelte';
import { m } from '$lib/paraglide/messages.js';
import type {
	ActionMenu,
	ActionMenuItem,
	ActionMenuItemTextLocalizedType,
	PlatformType,
	TranslationDisplayState,
	UiMedia,
	UiTimelineV2MessageType,
	UiTimelineV2Post,
	UiTimelineV2PostVisibility,
} from '@flare/web-presenters/timeline.svelte';

export const defaultTimelineAppearance = {
	absoluteTimestamp: false,
	aiConfig: { tldr: false, translation: true },
	avatarShape: 'CIRCLE',
	compatLinkPreview: false,
	expandContentWarning: false,
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
	timelineDisplayMode: 'Card',
	videoAutoplay: 'NEVER',
} as TimelineAppearance;

type ActionDisplayItem = Extract<ActionMenu, { type: 'Item' }> | ActionMenuItem;
type MessageTextSource = { type: UiTimelineV2MessageType } | { type_: UiTimelineV2MessageType };

const postClickIgnoreSelector = [
	'a',
	'button',
	'input',
	'select',
	'textarea',
	'summary',
	'[role="button"]',
	'[role="link"]',
	'[data-post-click-ignore]',
].join(',');

export function postKey(post: UiTimelineV2Post): string {
	return post.itemKey ?? `${post.statusKey.host}:${post.statusKey.id}`;
}

export function shouldIgnorePostContainerClick(event: MouseEvent): boolean {
	if (event.defaultPrevented) return true;

	const target = event.target;
	const currentTarget = event.currentTarget;
	if (!(target instanceof Element) || !(currentTarget instanceof Element)) {
		return false;
	}

	const ignoredElement = target.closest(postClickIgnoreSelector);
	return ignoredElement !== null && ignoredElement !== currentTarget;
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
			return m.visibilityPublic();
		case 'Home':
			return m.visibilityHome();
		case 'Followers':
			return m.visibilityFollowers();
		case 'Specified':
			return m.visibilitySpecified();
		case 'Channel':
			return m.visibilityChannel();
	}
}

export function platformIcon(platform: PlatformType): string {
	switch (platform) {
		case 'xQt':
			return 'World';
		case 'VVo':
			return 'World';
		default:
			return platform;
	}
}

export function translationLabel(state: TranslationDisplayState): string {
	switch (state) {
		case 'Hidden':
			return m.translationHidden();
		case 'Translating':
			return m.translationTranslating();
		case 'Translated':
			return m.translationTranslated();
		case 'Failed':
			return m.translationFailed();
	}
}

export function messageText(message: MessageTextSource): string {
	const type = 'type_' in message ? message.type_ : message.type;

	switch (type.type) {
		case 'Raw':
			return type.content;
		case 'Localized':
			return localizedMessageText(type.data, type.args);
		case 'Unknown':
			return type.rawType.trim();
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
	if (action.text?.type === 'Localized') {
		const text = localizedActionText(action.text.type_);
		const parameters = action.text.parameters.filter(Boolean).join(' ');
		return parameters ? `${text} ${parameters}` : text;
	}
	return action.icon ?? m.actionFallback();
}

function localizedActionText(type: ActionMenuItemTextLocalizedType): string {
	switch (type) {
		case 'Like':
			return m.actionLike();
		case 'Unlike':
			return m.actionUnlike();
		case 'Retweet':
			return m.actionRepost();
		case 'Unretweet':
			return m.actionUndoRepost();
		case 'Reply':
			return m.actionReply();
		case 'Comment':
			return m.actionComment();
		case 'Quote':
			return m.actionQuote();
		case 'Bookmark':
			return m.actionBookmark();
		case 'Unbookmark':
			return m.actionRemoveBookmark();
		case 'More':
			return m.actionMore();
		case 'Delete':
			return m.actionDelete();
		case 'Report':
			return m.actionReport();
		case 'React':
			return m.actionReact();
		case 'UnReact':
			return m.actionRemoveReaction();
		case 'Share':
			return m.actionShare();
		case 'FxShare':
			return m.actionFxShare();
		case 'EditUserList':
			return m.actionEditList();
		case 'SendMessage':
			return m.actionMessage();
		case 'Mute':
			return m.actionMute();
		case 'UnMute':
			return m.actionUnmute();
		case 'Block':
			return m.actionBlock();
		case 'UnBlock':
			return m.actionUnblock();
		case 'BlockWithHandleParameter':
			return m.actionBlock();
		case 'MuteWithHandleParameter':
			return m.actionMute();
		case 'AcceptFollowRequest':
			return m.actionAccept();
		case 'RejectFollowRequest':
			return m.actionReject();
		case 'RetryTranslation':
			return m.actionRetryTranslation();
		case 'Translate':
			return m.actionTranslate();
		case 'ShowOriginal':
			return m.actionShowOriginal();
		case 'Favorite':
			return m.actionFavorite();
		case 'UnFavorite':
			return m.actionUnfavorite();
	}
}

function localizedMessageText(id: string, args: string[] = []): string {
	switch (id) {
		case 'Mention':
			return m.messageMention();
		case 'NewPost':
			return m.messageNewPost();
		case 'Repost':
			return m.messageRepost();
		case 'Follow':
			return m.messageFollow();
		case 'FollowRequest':
			return m.messageFollowRequest();
		case 'Favourite':
			return m.messageFavourite();
		case 'PollEnded':
			return m.messagePollEnded();
		case 'PostUpdated':
			return m.messagePostUpdated();
		case 'Reply':
			return m.messageReply();
		case 'Quote':
			return m.messageQuote();
		case 'Reaction':
			return args[0] ? m.messageReaction({ emoji: args[0] }) : m.messageReactionFallback();
		case 'FollowRequestAccepted':
			return m.messageFollowRequestAccepted();
		case 'ScheduledNotePosted':
			return m.messageScheduledNotePosted();
		case 'ScheduledNotePostFailed':
			return m.messageScheduledNotePostFailed();
		case 'RoleAssigned':
			return m.messageRoleAssigned();
		case 'ChatRoomInvitationReceived':
			return m.messageChatRoomInvitationReceived();
		case 'AchievementEarned':
			return args[0]
				? m.messageAchievementEarned({ name: args.join(' ') })
				: m.messageAchievementEarnedFallback();
		case 'ExportCompleted':
			return m.messageExportCompleted();
		case 'Test':
			return m.messageTest();
		case 'Login':
			return m.messageLogin();
		case 'CreateToken':
			return m.messageCreateToken();
		case 'App':
			return m.messageApp();
		case 'StarterpackJoined':
			return m.messageStarterpackJoined();
		case 'Pinned':
			return m.messagePinned();
		case 'Like':
			return m.messageLike();
		default:
			return id;
	}
}
