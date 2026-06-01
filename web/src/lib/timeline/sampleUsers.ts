import type {
	AccountType,
	ActionMenuItem,
	ActionMenuItemTextLocalizedType,
	ClickEvent,
	MicroBlogKey,
	PlatformType,
	TranslationDisplayState,
	UiDateTime,
	UiHandle,
	UiIcon,
	UiNumber,
	UiProfile,
	UiProfileMatrices,
	UiRichText,
	UiTimelineV2,
	UiTimelineV2Message,
	UiTimelineV2MessageType,
	UiTimelineV2MessageTypeLocalizedMessageId,
	UiTimelineV2Post,
	WebPresenterRef,
} from '@flare/web-presenters/timeline.svelte';
import { timelinePostSamples } from './samplePosts';

type UiTimelineUser = Extract<UiTimelineV2, { type: 'User' }>;
type UiTimelineUserList = Extract<UiTimelineV2, { type: 'UserList' }>;

export type UiTimelineUserSample = {
	id: string;
	label: string;
	variant: string;
	user: UiTimelineUser;
};

export type UiTimelineUserListSample = {
	id: string;
	label: string;
	variant: string;
	userList: UiTimelineUserList;
};

type MatrixOptions = {
	fans?: number;
	follows?: number;
	statuses?: number;
	platformFans?: string | null;
};

type ProfileOptions = {
	id: string;
	name: string;
	handle: string;
	avatar?: string;
	avatarSeed?: string;
	description?: string | null;
	platformType?: PlatformType;
	mark?: UiProfile['mark'];
	matrices?: MatrixOptions;
};

const guestAccount: AccountType = { type: 'Guest' };
const noopClickEvent: ClickEvent = { type: 'Noop' };

let nextPresenterRef = 40_000;

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
	relative = '9m',
	full = '2026-06-01T09:51:00+09:00',
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

function formatNumber(value: number): string {
	return new Intl.NumberFormat(undefined, {
		notation: value >= 10_000 ? 'compact' : 'standard',
		maximumFractionDigits: 1,
	}).format(value);
}

function matrices(options: MatrixOptions = {}): UiProfileMatrices {
	const fansCount = options.fans ?? 2400;
	const followsCount = options.follows ?? 360;
	const statusesCount = options.statuses ?? 920;

	return withRef({
		fansCount,
		fansCountHumanized: formatNumber(fansCount),
		followsCount,
		followsCountHumanized: formatNumber(followsCount),
		platformFansCount: options.platformFans ?? null,
		statusesCount,
		statusesCountHumanized: formatNumber(statusesCount),
	});
}

function profile(options: ProfileOptions): UiProfile {
	const userHandle = handle(options.handle);
	const handleWithoutAt = options.handle.replace(/^@/, '');
	const handleWithoutAtAndHost = handleWithoutAt.split('@')[0] ?? handleWithoutAt;
	const avatar =
		options.avatar ??
		(options.avatarSeed
			? `https://api.dicebear.com/9.x/thumbs/svg?seed=${encodeURIComponent(options.avatarSeed)}`
			: '');

	return withRef({
		avatar,
		banner: null,
		bottomContent: null,
		clickEvent: noopClickEvent,
		description: options.description ? richText(options.description) : null,
		handle: userHandle,
		handleWithoutAt,
		handleWithoutAtAndHost,
		host: userHandle.host || null,
		key: key(options.id, userHandle.host || 'example.social'),
		mark: options.mark ?? [],
		matrices: matrices(options.matrices),
		name: richText(options.name),
		platformType: options.platformType ?? 'Mastodon',
		sourceLanguages: [],
		translationDisplayState: 'Hidden' satisfies TranslationDisplayState,
	});
}

function numberValue(value: number): UiNumber {
	return withRef({
		humanized: formatNumber(value),
		value,
	});
}

function localizedAction(
	icon: UiIcon | null,
	type: ActionMenuItemTextLocalizedType,
	color: ActionMenuItem['color'] = null,
	updateKey = type.toLowerCase(),
	count: number | null = null
): ActionMenuItem {
	return withRef({
		clickEvent: noopClickEvent,
		color,
		count: count === null ? null : numberValue(count),
		icon,
		text: { type: 'Localized', parameters: [], type_: type },
		updateKey,
	});
}

function rawAction(
	icon: UiIcon | null,
	text: string,
	color: ActionMenuItem['color'] = null,
	updateKey = text.toLowerCase(),
	count: number | null = null
): ActionMenuItem {
	return withRef({
		clickEvent: noopClickEvent,
		color,
		count: count === null ? null : numberValue(count),
		icon,
		text: { type: 'Raw', text },
		updateKey,
	});
}

function localizedMessage(
	data: UiTimelineV2MessageTypeLocalizedMessageId,
	args: string[] = []
): UiTimelineV2MessageType {
	return {
		type: 'Localized',
		args,
		data,
	};
}

function rawMessage(content: string): UiTimelineV2MessageType {
	return {
		type: 'Raw',
		content,
	};
}

function message(options: {
	id: string;
	user?: UiProfile | null;
	icon: UiIcon;
	type: UiTimelineV2MessageType;
	createdAt?: UiDateTime;
}): UiTimelineV2Message {
	const user = options.user ?? null;
	const host = user?.host ?? 'example.social';

	return withRef({
		accountType: guestAccount,
		clickEvent: noopClickEvent,
		createdAt: options.createdAt ?? dateTime(),
		icon: options.icon,
		itemKey: `user-message-${options.id}`,
		renderHash: hash(`user-message-${options.id}`),
		searchText: null,
		statusKey: key(options.id, host),
		type: options.type,
		user,
	});
}

function hash(value: string): number {
	let result = 0;
	for (let index = 0; index < value.length; index += 1) {
		result = (result * 31 + value.charCodeAt(index)) | 0;
	}
	return Math.abs(result);
}

function userItem(options: {
	id: string;
	value: UiProfile;
	button?: ActionMenuItem[];
	message?: UiTimelineV2Message | null;
	createdAt?: UiDateTime;
}): UiTimelineUser {
	return withRef({
		type: 'User',
		accountType: guestAccount,
		button: options.button ?? [],
		createdAt: options.createdAt ?? dateTime(),
		itemKey: `user-${options.id}`,
		message: options.message ?? null,
		statusKey: key(options.id, options.value.host ?? 'example.social'),
		value: options.value,
	});
}

function userListItem(options: {
	id: string;
	users: UiProfile[];
	message?: UiTimelineV2Message | null;
	post?: UiTimelineV2Post | null;
	createdAt?: UiDateTime;
}): UiTimelineUserList {
	return withRef({
		type: 'UserList',
		accountType: guestAccount,
		createdAt: options.createdAt ?? dateTime(),
		itemKey: `user-list-${options.id}`,
		message: options.message ?? null,
		post: options.post ?? null,
		statusKey: key(options.id),
		users: options.users,
	});
}

function samplePost(id: string): UiTimelineV2Post | null {
	return (
		timelinePostSamples.find((sample) => sample.id === id)?.post ??
		timelinePostSamples[0]?.post ??
		null
	);
}

const users = {
	akari: profile({
		id: 'user-akari',
		name: 'Akari Tanaka',
		handle: '@akari@example.social',
		avatar: 'https://i.pravatar.cc/160?img=5',
		description: 'Building small timeline details, testing dense rows, and keeping the web version pleasant to scan.',
		mark: ['Verified'],
		platformType: 'Mastodon',
		matrices: { fans: 12800, follows: 420, statuses: 1840 },
	}),
	kei: profile({
		id: 'user-kei-summary',
		name: 'Kei',
		handle: '@kei.dev@bsky.social',
		avatar: 'https://i.pravatar.cc/160?img=32',
		description: 'Frontend notes, design systems, and quiet experiments.',
		platformType: 'Bluesky',
		matrices: { fans: 9200, follows: 310, statuses: 620 },
	}),
	mira: profile({
		id: 'user-mira-summary',
		name: 'Mira',
		handle: '@mira@misskey.io',
		avatar: 'https://i.pravatar.cc/160?img=48',
		description: 'Posting release notes, plugin sketches, and the occasional impossible bug report.',
		mark: ['Verified', 'Bot'],
		platformType: 'Misskey',
		matrices: { fans: 54000, follows: 86, statuses: 12800 },
	}),
	locked: profile({
		id: 'user-locked',
		name: 'Private Notes',
		handle: '@private@example.social',
		avatarSeed: 'locked-user',
		description: 'A locked profile should keep the same row rhythm while showing only the data it actually has.',
		mark: ['Locked'],
		platformType: 'Mastodon',
		matrices: { fans: 0, follows: 72, statuses: 38 },
	}),
	noAvatar: profile({
		id: 'user-no-avatar-summary',
		name: 'No Avatar Account',
		handle: '@missing-avatar@example.social',
		description: null,
		platformType: 'xQt',
		matrices: { fans: 12, follows: 48, statuses: 3 },
	}),
	long: profile({
		id: 'user-long-summary',
		name: 'A Very Long Display Name That Refuses To Fit In One Comfortable Row',
		handle: '@a.really.long.handle.name.for.layout.testing@very-long-instance-name.example.social',
		avatar: 'https://i.pravatar.cc/160?img=56',
		description:
			'Long identity text is here to test truncation, wrapping, and whether the verification/check/lock marks remain visually aligned while the card is squeezed into the horizontal list.',
		mark: ['Verified', 'Cat'],
		platformType: 'Mastodon',
		matrices: { fans: 231000, follows: 1800, statuses: 48000 },
	}),
	complex: profile({
		id: 'user-complex-summary',
		name: 'Complex Sample User',
		handle: '@complex.sample@dev.example',
		avatarSeed: 'complex-user',
		description:
			'This profile combines avatar fallback, multiple marks, long description text, stats, user actions, a top message, and neighboring user-list/post previews.',
		mark: ['Verified', 'Locked', 'Bot', 'Cat'],
		platformType: 'Misskey',
		matrices: { fans: 87000, follows: 2048, statuses: 36000 },
	}),
} satisfies Record<string, UiProfile>;

export const timelineUserSamples = [
	{
		id: 'standard',
		label: '用户',
		variant: 'basic',
		user: userItem({
			id: 'sample-user-standard',
			value: users.akari,
		}),
	},
	{
		id: 'no-avatar',
		label: '用户无头像/无简介',
		variant: 'missing-content',
		user: userItem({
			id: 'sample-user-no-avatar',
			value: users.noAvatar,
		}),
	},
	{
		id: 'locked',
		label: '锁定用户',
		variant: 'marks',
		user: userItem({
			id: 'sample-user-locked',
			value: users.locked,
		}),
	},
	{
		id: 'follow-request',
		label: '带消息和操作按钮',
		variant: 'message-actions',
		user: userItem({
			id: 'sample-user-follow-request',
			value: users.kei,
			message: message({
				id: 'follow-request',
				user: users.kei,
				icon: 'Follow',
				type: localizedMessage('FollowRequest'),
			}),
			button: [
				localizedAction('Check', 'AcceptFollowRequest', 'PrimaryColor', 'accept-follow-request'),
				localizedAction('Delete', 'RejectFollowRequest', 'Red', 'reject-follow-request'),
			],
		}),
	},
	{
		id: 'long-identity',
		label: '长用户名/长 handle',
		variant: 'overflow',
		user: userItem({
			id: 'sample-user-long',
			value: users.long,
			button: [rawAction('Messages', 'Message')],
		}),
	},
	{
		id: 'complex-all',
		label: '复杂用户',
		variant: 'all',
		user: userItem({
			id: 'sample-user-complex-all',
			value: users.complex,
			message: message({
				id: 'complex-user',
				user: users.mira,
				icon: 'Info',
				type: rawMessage('invited this account to a collaborative list'),
			}),
			button: [
				localizedAction('Check', 'AcceptFollowRequest', 'PrimaryColor', 'complex-accept'),
				localizedAction('Delete', 'RejectFollowRequest', 'Red', 'complex-reject'),
				localizedAction('Messages', 'SendMessage', null, 'complex-message'),
			],
		}),
	},
] satisfies UiTimelineUserSample[];

export const timelineUserListSamples = [
	{
		id: 'two-users',
		label: '用户列表',
		variant: 'two',
		userList: userListItem({
			id: 'sample-user-list-two',
			users: [users.akari, users.kei],
		}),
	},
	{
		id: 'many-users',
		label: '横向用户列表',
		variant: 'scroll',
		userList: userListItem({
			id: 'sample-user-list-many',
			users: [users.akari, users.kei, users.mira, users.locked, users.long],
		}),
	},
	{
		id: 'with-message',
		label: '带顶部消息的用户列表',
		variant: 'message',
		userList: userListItem({
			id: 'sample-user-list-message',
			users: [users.mira, users.noAvatar, users.locked],
			message: message({
				id: 'user-list-message',
				user: users.akari,
				icon: 'List',
				type: localizedMessage('StarterpackJoined'),
			}),
		}),
	},
	{
		id: 'with-post',
		label: '带关联 post 的用户列表',
		variant: 'post',
		userList: userListItem({
			id: 'sample-user-list-post',
			users: [users.kei, users.mira, users.akari],
			post: samplePost('card'),
		}),
	},
	{
		id: 'complex-all',
		label: '复杂用户列表',
		variant: 'all',
		userList: userListItem({
			id: 'sample-user-list-complex-all',
			users: [users.complex, users.long, users.mira, users.noAvatar, users.locked],
			message: message({
				id: 'complex-user-list',
				user: users.complex,
				icon: 'Local',
				type: rawMessage('combined user list preview with message, cards, marks, and a related post'),
			}),
			post: samplePost('complex-all'),
		}),
	},
] satisfies UiTimelineUserListSample[];
