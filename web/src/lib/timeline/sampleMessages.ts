import type {
	AccountType,
	ClickEvent,
	MicroBlogKey,
	PlatformType,
	TranslationDisplayState,
	UiDateTime,
	UiHandle,
	UiIcon,
	UiProfile,
	UiProfileMatrices,
	UiRichText,
	UiTimelineV2,
	UiTimelineV2MessageType,
	UiTimelineV2MessageTypeLocalizedMessageId,
	WebPresenterRef,
} from '@flare/web-presenters/timeline.svelte';

type UiTimelineMessage = Extract<UiTimelineV2, { type: 'Message' }>;

export type UiTimelineMessageSample = {
	id: string;
	label: string;
	variant: string;
	message: UiTimelineMessage;
};

type ProfileOptions = {
	id: string;
	name: string;
	handle: string;
	avatarSeed: string;
	platformType?: PlatformType;
	mark?: UiProfile['mark'];
};

type MessageOptions = {
	id: string;
	user?: UiProfile | null;
	icon: UiIcon;
	messageType: UiTimelineV2MessageType;
	createdAt?: UiDateTime;
	host?: string;
};

const guestAccount: AccountType = { type: 'Guest' };
const noopClickEvent: ClickEvent = { type: 'Noop' };

let nextPresenterRef = 30_000;

function withRef<T extends object>(value: T): T & WebPresenterRef {
	return {
		__webPresenterRef: nextPresenterRef++,
		__webPresenterRefs: [],
		...value,
	} as T & WebPresenterRef;
}

function htmlEscape(value: string): string {
	return value
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
		.replaceAll("'", '&#39;');
}

function platformText(value: string): string {
	if (!value.trim()) return '';

	return value
		.split(/\n{2,}/)
		.map((paragraph) => `<p>${htmlEscape(paragraph).replaceAll('\n', '<br>')}</p>`)
		.join('');
}

function richText(value: string): UiRichText {
	const trimmed = value.trim();

	return withRef({
		imageUrls: [],
		innerText: value,
		isEmpty: trimmed.length === 0,
		isLongText: value.length > 280,
		isRtl: false,
		platformText: platformText(value),
		raw: value,
		renderRuns: [],
		truncatedText: value.length > 280 ? `${value.slice(0, 240)}...` : null,
	});
}

function key(id: string, host = 'example.social'): MicroBlogKey {
	return withRef({
		host,
		id,
	});
}

function dateTime(
	relative = '8m',
	full = '2026-06-01T09:52:00+09:00',
	absolute = 'Jun 1, 2026'
): UiDateTime {
	return withRef({
		absolute,
		full,
		relative,
		shouldShowFull: false,
	});
}

function handle(raw: string): UiHandle {
	const handleParts = raw.split('@').filter(Boolean);
	const host = handleParts.at(-1) ?? '';
	const normalizedRaw = raw.toLowerCase();

	return withRef({
		canonical: normalizedRaw,
		host,
		normalizedHost: host.toLowerCase(),
		normalizedRaw,
		raw,
	});
}

function matrices(): UiProfileMatrices {
	return withRef({
		fansCount: 1200,
		fansCountHumanized: '1.2K',
		followsCount: 240,
		followsCountHumanized: '240',
		platformFansCount: null,
		statusesCount: 3800,
		statusesCountHumanized: '3.8K',
	});
}

function profile(options: ProfileOptions): UiProfile {
	const userHandle = handle(options.handle);
	const host = userHandle.host || 'example.social';

	return withRef({
		avatar: `https://api.dicebear.com/9.x/thumbs/svg?seed=${encodeURIComponent(options.avatarSeed)}`,
		banner: null,
		bottomContent: null,
		clickEvent: noopClickEvent,
		description: null,
		handle: userHandle,
		handleWithoutAt: options.handle.replace(/^@/, ''),
		handleWithoutAtAndHost: options.handle.split('@').filter(Boolean)[0] ?? options.handle,
		host,
		key: key(options.id, host),
		mark: options.mark ?? [],
		matrices: matrices(),
		name: richText(options.name),
		platformType: options.platformType ?? 'Mastodon',
		sourceLanguages: [],
		translationDisplayState: 'Hidden' satisfies TranslationDisplayState,
	});
}

function localized(
	data: UiTimelineV2MessageTypeLocalizedMessageId,
	args: string[] = []
): UiTimelineV2MessageType {
	return {
		type: 'Localized',
		args,
		data,
	};
}

function raw(content: string): UiTimelineV2MessageType {
	return {
		type: 'Raw',
		content,
	};
}

function unknown(rawType: string): UiTimelineV2MessageType {
	return {
		type: 'Unknown',
		rawType,
	};
}

function message(options: MessageOptions): UiTimelineMessage {
	const host = options.host ?? options.user?.host ?? 'example.social';

	return withRef({
		type: 'Message',
		accountType: guestAccount,
		clickEvent: noopClickEvent,
		createdAt: options.createdAt ?? dateTime(),
		icon: options.icon,
		itemKey: `message-${options.id}`,
		statusKey: key(options.id, host),
		type_: options.messageType,
		user: options.user ?? null,
	});
}

const users = {
	akari: profile({
		id: 'akari',
		name: 'Akari Tanaka',
		handle: '@akari@example.social',
		avatarSeed: 'akari-message',
		mark: ['Verified'],
	}),
	dev: profile({
		id: 'dev',
		name: 'Flare Web',
		handle: '@flare@dev.example',
		avatarSeed: 'flare-message',
		platformType: 'Bluesky',
	}),
	longName: profile({
		id: 'long-name',
		name: 'A Very Long Display Name That Should Stay On One Line',
		handle: '@long.name@example.social',
		avatarSeed: 'long-message',
		platformType: 'Misskey',
	}),
};

export const timelineMessageSamples = [
	{
		id: 'mention',
		label: '提及消息',
		variant: 'localized',
		message: message({
			id: 'sample-message-mention',
			user: users.akari,
			icon: 'Mention',
			messageType: localized('Mention'),
		}),
	},
	{
		id: 'new-post',
		label: '新状态消息',
		variant: 'localized',
		message: message({
			id: 'sample-message-new-post',
			user: users.dev,
			icon: 'Notification',
			messageType: localized('NewPost'),
			createdAt: dateTime('15m', '2026-06-01T09:45:00+09:00'),
		}),
	},
	{
		id: 'repost',
		label: '转发消息',
		variant: 'localized',
		message: message({
			id: 'sample-message-repost',
			user: users.akari,
			icon: 'Retweet',
			messageType: localized('Repost'),
			createdAt: dateTime('21m', '2026-06-01T09:39:00+09:00'),
		}),
	},
	{
		id: 'follow',
		label: '关注消息',
		variant: 'localized',
		message: message({
			id: 'sample-message-follow',
			user: users.dev,
			icon: 'Follow',
			messageType: localized('Follow'),
			createdAt: dateTime('32m', '2026-06-01T09:28:00+09:00'),
		}),
	},
	{
		id: 'favourite',
		label: '收藏消息',
		variant: 'localized',
		message: message({
			id: 'sample-message-favourite',
			user: users.akari,
			icon: 'Favourite',
			messageType: localized('Favourite'),
			createdAt: dateTime('46m', '2026-06-01T09:14:00+09:00'),
		}),
	},
	{
		id: 'reaction',
		label: 'Reaction 消息',
		variant: 'localized+args',
		message: message({
			id: 'sample-message-reaction',
			user: users.dev,
			icon: 'React',
			messageType: localized('Reaction', [':sparkles:']),
			createdAt: dateTime('1h', '2026-06-01T09:00:00+09:00'),
		}),
	},
	{
		id: 'system-no-user',
		label: '无用户系统消息',
		variant: 'no-user',
		message: message({
			id: 'sample-message-system-no-user',
			icon: 'Info',
			messageType: localized('ExportCompleted'),
			createdAt: dateTime('2h', '2026-06-01T08:00:00+09:00'),
		}),
	},
	{
		id: 'raw',
		label: 'Raw 消息',
		variant: 'raw',
		message: message({
			id: 'sample-message-raw',
			icon: 'ChatMessage',
			messageType: raw('A raw notification payload can render without a localized key.'),
			createdAt: dateTime('3h', '2026-06-01T07:00:00+09:00'),
		}),
	},
	{
		id: 'unknown',
		label: 'Unknown 消息',
		variant: 'unknown',
		message: message({
			id: 'sample-message-unknown',
			icon: 'Notification',
			messageType: unknown('custom.instance.notification'),
			createdAt: dateTime('4h', '2026-06-01T06:00:00+09:00'),
		}),
	},
	{
		id: 'achievement',
		label: '带参数消息',
		variant: 'localized+args',
		message: message({
			id: 'sample-message-achievement',
			user: users.akari,
			icon: 'Featured',
			messageType: localized('AchievementEarned', ['notes100', 'Wrote 100 notes']),
			createdAt: dateTime('5h', '2026-06-01T05:00:00+09:00'),
		}),
	},
	{
		id: 'long-name',
		label: '长用户名消息',
		variant: 'stress',
		message: message({
			id: 'sample-message-long-name',
			user: users.longName,
			icon: 'Reply',
			messageType: localized('Reply'),
			createdAt: dateTime('6h', '2026-06-01T04:00:00+09:00'),
		}),
	},
	{
		id: 'complex-all',
		label: '复杂综合消息',
		variant: 'all-in-one',
		message: message({
			id: 'sample-message-complex-all',
			user: users.longName,
			icon: 'Info',
			messageType: raw(
				'This combines a long display name, a raw message body, wrapping text, and the standard 8px icon/content spacing used by message-only timeline rows.'
			),
			createdAt: dateTime('yesterday', '2026-05-31T22:20:00+09:00', 'May 31, 2026'),
		}),
	},
] satisfies UiTimelineMessageSample[];
