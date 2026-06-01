import type {
	ActionMenu,
	ActionMenuItem,
	AccountType,
	MicroBlogKey,
	PlatformType,
	ReferenceType,
	TranslationDisplayState,
	UiCard,
	UiDateTime,
	UiHandle,
	UiIcon,
	UiMedia,
	UiNumber,
	UiPoll,
	UiPollOption,
	UiProfile,
	UiProfileMatrices,
	UiRichText,
	UiTimelineV2Message,
	UiTimelineV2MessageType,
	UiTimelineV2Post,
	UiTimelineV2PostEmojiReaction,
	UiTimelineV2PostReference,
	UiTimelineV2PostSourceChannel,
	UiTimelineV2PostVisibility,
	WebPresenterRef,
} from '@flare/web-presenters/timeline.svelte';

export type UiTimelinePostSample = {
	id: string;
	label: string;
	variant: string;
	post: UiTimelineV2Post;
};

type PostOptions = {
	id: string;
	user?: UiProfile | null;
	createdAt?: UiDateTime;
	content?: string;
	contentWarning?: string | null;
	images?: UiMedia[];
	card?: UiCard | null;
	poll?: UiPoll | null;
	quote?: UiTimelineV2Post[];
	message?: UiTimelineV2Message | null;
	parents?: UiTimelineV2Post[];
	internalRepost?: UiTimelineV2Post | null;
	platformType?: PlatformType;
	replyToHandle?: string | null;
	references?: UiTimelineV2PostReference[];
	emojiReactions?: UiTimelineV2PostEmojiReaction[];
	sourceChannel?: UiTimelineV2PostSourceChannel | null;
	sourceLanguages?: string[];
	translationDisplayState?: TranslationDisplayState;
	visibility?: UiTimelineV2PostVisibility | null;
	sensitive?: boolean;
	shouldExpandTextByDefault?: boolean;
	actions?: ActionMenu[];
	actionCounts?: {
		replies?: number;
		reposts?: number;
		likes?: number;
	};
};

const guestAccount: AccountType = { type: 'Guest' };

let nextPresenterRef = 1;

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
	relative = 'now',
	full = '2026-06-01T10:00:00+09:00',
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
		fansCount: 12800,
		fansCountHumanized: '12.8K',
		followsCount: 420,
		followsCountHumanized: '420',
		platformFansCount: null,
		statusesCount: 1840,
		statusesCountHumanized: '1.8K',
	});
}

function profile(options: {
	id: string;
	name: string;
	handle: string;
	avatar: string;
	platformType: PlatformType;
	verified?: boolean;
	description?: string;
}): UiProfile {
	const userHandle = handle(options.handle);
	const handleWithoutAt = options.handle.replace(/^@/, '');
	const handleWithoutAtAndHost = handleWithoutAt.split('@')[0] ?? handleWithoutAt;

	return withRef({
		avatar: options.avatar,
		banner: null,
		bottomContent: null,
		description: options.description ? richText(options.description) : null,
		handle: userHandle,
		handleWithoutAt,
		handleWithoutAtAndHost,
		host: userHandle.host || null,
		key: key(options.id, userHandle.host || 'example.social'),
		mark: options.verified ? ['Verified'] : [],
		matrices: matrices(),
		name: richText(options.name),
		platformType: options.platformType,
		sourceLanguages: [],
		translationDisplayState: 'Hidden',
	});
}

function numberValue(value: number): UiNumber {
	return withRef({
		humanized: new Intl.NumberFormat(undefined, {
			notation: value >= 10_000 ? 'compact' : 'standard',
			maximumFractionDigits: 1,
		}).format(value),
		value,
	});
}

function image(seed: string, index: number, sensitive = false): UiMedia {
	return {
		type: 'Image',
		url: `https://picsum.photos/seed/${seed}-${index}/1440/1080`,
		previewUrl: `https://picsum.photos/seed/${seed}-${index}/720/540`,
		description: `Sample media ${index}`,
		width: 1440,
		height: 1080,
		sensitive,
	};
}

function images(count: number, options: { sensitive?: boolean; seed?: string } = {}): UiMedia[] {
	return Array.from({ length: count }, (_, index) =>
		image(options.seed ?? `flare-post-${count}`, index + 1, options.sensitive ?? false)
	);
}

function gif(id: string): UiMedia {
	return {
		type: 'Gif',
		url: `https://picsum.photos/seed/${id}/960/720`,
		previewUrl: `https://picsum.photos/seed/${id}-preview/640/480`,
		description: 'Looping sample animation',
		width: 960,
		height: 720,
	};
}

function video(id: string): UiMedia {
	return {
		type: 'Video',
		url: `https://example.com/media/${id}.mp4`,
		thumbnailUrl: `https://picsum.photos/seed/${id}-thumb/960/540`,
		description: 'Sample video thumbnail',
		width: 1920,
		height: 1080,
	};
}

function audio(id: string): UiMedia {
	return {
		type: 'Audio',
		url: `https://example.com/media/${id}.mp3`,
		previewUrl: `https://picsum.photos/seed/${id}-cover/720/720`,
		description: 'Field Notes From The Timeline',
	};
}

function card(overrides: Partial<Omit<UiCard, keyof WebPresenterRef>> = {}): UiCard {
	return withRef({
		title: 'Designing timeline density for social clients',
		description: 'Notes on balancing text, media, metadata, and actions in a multi-platform timeline.',
		url: 'https://example.com/flare/timeline-density',
		media: image('flare-card', 1),
		...overrides,
	});
}

function pollOption(title: string, percentage: number, votesCount: number): UiPollOption {
	return withRef({
		humanizedPercentage: `${Math.round(percentage * 100)}%`,
		percentage,
		title,
		votesCount,
	});
}

function poll(options: {
	id?: string;
	multiple?: boolean;
	canVote?: boolean;
	expired?: boolean;
	ownVotes?: number[];
	options?: UiPollOption[];
} = {}): UiPoll {
	const ownVotes = options.ownVotes ?? [1];

	return withRef({
		canVote: options.canVote ?? false,
		expired: options.expired ?? false,
		expiredAt: dateTime('23h', '2026-06-02T10:00:00+09:00', 'Jun 2, 2026'),
		id: options.id ?? 'poll-ui-priority',
		multiple: options.multiple ?? false,
		options:
			options.options ?? [
				pollOption('Text rhythm', 0.34, 118),
				pollOption('Media grid', 0.45, 157),
				pollOption('Action row', 0.21, 73),
			],
		ownVotes,
		voted: ownVotes.length > 0,
	});
}

function reaction(
	name: string,
	count: number,
	options: { me?: boolean; url?: string; isUnicode?: boolean } = {}
): UiTimelineV2PostEmojiReaction {
	const isUnicode = options.isUnicode ?? !options.url;

	return withRef({
		count: numberValue(count),
		isImageReaction: Boolean(options.url),
		isUnicode,
		me: options.me ?? false,
		name,
		url: options.url ?? '',
	});
}

function reactions(): UiTimelineV2PostEmojiReaction[] {
	return [
		reaction('sparkles', 32, { me: true, isUnicode: true }),
		reaction('thinking', 14, { url: 'https://picsum.photos/seed/reaction-thinking/64/64' }),
		reaction('heart', 87, { isUnicode: true }),
	];
}

function sourceChannel(id: string, name: string): UiTimelineV2PostSourceChannel {
	return withRef({ id, name });
}

function reference(id: string, type: ReferenceType, host = 'example.social'): UiTimelineV2PostReference {
	return withRef({
		statusKey: key(id, host),
		type,
	});
}

function actionMenuItem(
	icon: UiIcon | null,
	text: string,
	count: number | null = null,
	updateKey = text.toLowerCase(),
	color: ActionMenuItem['color'] = null
): ActionMenuItem {
	return withRef({
		color,
		count: count === null ? null : numberValue(count),
		icon,
		text: { type: 'Raw', text },
		updateKey,
	});
}

function actionItem(
	icon: UiIcon | null,
	text: string,
	count: number | null = null,
	updateKey = text.toLowerCase(),
	color: ActionMenuItem['color'] = null
): ActionMenu {
	return {
		type: 'Item',
		color,
		count: count === null ? null : numberValue(count),
		icon,
		text: { type: 'Raw', text },
		updateKey,
	};
}

function defaultActions(counts: { replies?: number; reposts?: number; likes?: number } = {}): ActionMenu[] {
	return [
		actionItem('Reply', 'Reply', counts.replies ?? 12, 'reply'),
		actionItem('Retweet', 'Repost', counts.reposts ?? 24, 'repost'),
		actionItem('Heart', 'Like', counts.likes ?? 128, 'like', 'PrimaryColor'),
		{
			type: 'Group',
			displayItem: actionMenuItem('More', 'More', null, 'more'),
			actions: [
				actionItem('Share', 'Share', null, 'share'),
				actionItem('Bookmark', 'Bookmark', null, 'bookmark'),
				actionItem('Report', 'Report', null, 'report', 'Red'),
			],
		},
	];
}

function message(options: {
	id: string;
	user: UiProfile | null;
	icon: UiIcon;
	type: UiTimelineV2MessageType;
	createdAt?: UiDateTime;
}): UiTimelineV2Message {
	return withRef({
		accountType: guestAccount,
		createdAt: options.createdAt ?? dateTime('12m', '2026-06-01T09:48:00+09:00'),
		icon: options.icon,
		itemKey: `message-${options.id}`,
		renderHash: hash(`message-${options.id}`),
		searchText: null,
		statusKey: key(options.id, options.user?.host ?? 'example.social'),
		type: options.type,
		user: options.user,
	});
}

function hash(value: string): number {
	let result = 0;
	for (let index = 0; index < value.length; index += 1) {
		result = (result * 31 + value.charCodeAt(index)) | 0;
	}
	return Math.abs(result);
}

function createPost(options: PostOptions): UiTimelineV2Post {
	const user = options.user === undefined ? authors.flare : options.user;
	const postPlatform = options.platformType ?? user?.platformType ?? 'Mastodon';
	const postContent = richText(options.content ?? 'Preparing a calmer timeline surface for the next Flare web iteration.');
	const postHost = user?.host ?? 'example.social';

	return withRef({
		accountType: guestAccount,
		actions: options.actions ?? defaultActions(options.actionCounts),
		card: options.card ?? null,
		content: postContent,
		contentWarning:
			options.contentWarning === undefined || options.contentWarning === null
				? null
				: richText(options.contentWarning),
		createdAt: options.createdAt ?? dateTime(),
		emojiReactions: options.emojiReactions ?? [],
		images: options.images ?? [],
		internalRepost: options.internalRepost ?? null,
		itemKey: options.id,
		message: options.message ?? null,
		parents: options.parents ?? [],
		platformType: postPlatform,
		poll: options.poll ?? null,
		quote: options.quote ?? [],
		references: options.references ?? [],
		renderHash: hash(options.id),
		replyToHandle: options.replyToHandle ?? null,
		searchText: postContent.innerText,
		sensitive: options.sensitive ?? false,
		shouldExpandTextByDefault: options.shouldExpandTextByDefault ?? false,
		sourceChannel: options.sourceChannel ?? null,
		sourceLanguages: options.sourceLanguages ?? [],
		statusKey: key(options.id, postHost),
		translationDisplayState: options.translationDisplayState ?? 'Hidden',
		user,
		visibility: options.visibility === undefined ? 'Public' : options.visibility,
	});
}

const authors = {
	flare: profile({
		id: 'user-flare',
		name: 'Flare Design',
		handle: '@flare.design@example.social',
		avatar: 'https://i.pravatar.cc/160?img=12',
		platformType: 'Mastodon',
		verified: true,
	}),
	kei: profile({
		id: 'user-kei',
		name: 'Kei',
		handle: '@kei.dev@bsky.social',
		avatar: 'https://i.pravatar.cc/160?img=32',
		platformType: 'Bluesky',
	}),
	mira: profile({
		id: 'user-mira',
		name: 'Mira',
		handle: '@mira@misskey.io',
		avatar: 'https://i.pravatar.cc/160?img=48',
		platformType: 'Misskey',
		verified: true,
	}),
	longName: profile({
		id: 'user-long-name',
		name: 'A Very Long Display Name That Refuses To Fit In One Comfortable Row',
		handle: '@a.really.long.handle.name.for.layout.testing@very-long-instance-name.example.social',
		avatar: 'https://i.pravatar.cc/160?img=56',
		platformType: 'Mastodon',
		verified: true,
	}),
	noAvatar: profile({
		id: 'user-no-avatar',
		name: 'No Avatar Account',
		handle: '@missing-avatar@example.social',
		avatar: '',
		platformType: 'xQt',
	}),
} satisfies Record<string, UiProfile>;

const quotedPost = createPost({
	id: 'sample-quote-target',
	user: authors.kei,
	createdAt: dateTime('36m', '2026-06-01T09:24:00+09:00'),
	content: 'A timeline item should make scanning feel effortless without flattening every service into the same voice.',
	actionCounts: {
		replies: 4,
		reposts: 9,
		likes: 42,
	},
});

export const timelinePostSamples = [
	{
		id: 'text-only',
		label: '无图',
		variant: 'text',
		post: createPost({
			id: 'sample-text-only',
			content: 'Text-only posts need the same care as media-heavy posts: hierarchy, rhythm, and readable density.',
		}),
	},
	{
		id: 'image-1',
		label: '带图 1 张',
		variant: 'media',
		post: createPost({
			id: 'sample-image-1',
			content: 'Single-image posts should keep the image generous while preserving action reachability.',
			images: images(1),
		}),
	},
	{
		id: 'image-2',
		label: '带图 2 张',
		variant: 'media',
		post: createPost({
			id: 'sample-image-2',
			content: 'Two images usually read best as a balanced pair.',
			images: images(2),
		}),
	},
	{
		id: 'image-3',
		label: '带图 3 张',
		variant: 'media',
		post: createPost({
			id: 'sample-image-3',
			content: 'Three-image layouts need a clear lead image and predictable cropping.',
			images: images(3),
		}),
	},
	{
		id: 'image-4',
		label: '带图 4 张',
		variant: 'media',
		post: createPost({
			id: 'sample-image-4',
			content: 'Four images are the grid baseline for dense visual posts.',
			images: images(4),
		}),
	},
	{
		id: 'image-9',
		label: '带图 9 张',
		variant: 'media',
		post: createPost({
			id: 'sample-image-9',
			content: 'Nine-image posts need a layout that can show abundance without turning into a wall.',
			images: images(9),
		}),
	},
	{
		id: 'card',
		label: '带 card',
		variant: 'card',
		post: createPost({
			id: 'sample-card',
			content: 'External links should feel attached to the post, not like an unrelated ad block.',
			card: card(),
		}),
	},
	{
		id: 'poll',
		label: '带 poll',
		variant: 'poll',
		post: createPost({
			id: 'sample-poll',
			content: 'Which post surface should we refine first?',
			poll: poll(),
		}),
	},
	{
		id: 'quote',
		label: '带 quote',
		variant: 'quote',
		post: createPost({
			id: 'sample-quote',
			content: 'This is exactly the constraint: enough platform flavor to be useful, enough consistency to stay calm.',
			quote: [quotedPost],
			references: [reference('sample-quote-target', 'Quote', authors.kei.host ?? 'bsky.social')],
			actionCounts: {
				replies: 18,
				reposts: 41,
				likes: 216,
			},
		}),
	},
	{
		id: 'repost',
		label: '带 repost',
		variant: 'repost',
		post: createPost({
			id: 'sample-repost',
			user: authors.kei,
			content: 'Reposts need to show context without making the original author compete with the boosting user.',
			images: images(1, { seed: 'repost' }),
			message: message({
				id: 'sample-repost-message',
				user: authors.mira,
				icon: 'Retweet',
				type: { type: 'Localized', args: [], data: 'Repost' },
			}),
			references: [reference('sample-repost-message', 'Retweet', authors.mira.host ?? 'misskey.io')],
			actionCounts: {
				replies: 7,
				reposts: 82,
				likes: 309,
			},
		}),
	},
	{
		id: 'long-text',
		label: '长文本 / 多段换行',
		variant: 'text-stress',
		post: createPost({
			id: 'sample-long-text',
			content: [
				'Long-form posts should keep paragraph rhythm readable without forcing every action below the fold.',
				'This sample includes a second paragraph so spacing, line-height, and collapsed/expanded states can be judged early.',
				'It also includes enough words to test comfortable scanning on both desktop and mobile widths.',
			].join('\n\n'),
			actionCounts: {
				replies: 27,
				reposts: 53,
				likes: 406,
			},
		}),
	},
	{
		id: 'short-text',
		label: '超短文本',
		variant: 'text-stress',
		post: createPost({
			id: 'sample-short-text',
			content: 'gm',
			actionCounts: {
				replies: 1,
				reposts: 3,
				likes: 19,
			},
		}),
	},
	{
		id: 'content-warning',
		label: 'content warning / 敏感媒体',
		variant: 'sensitive',
		post: createPost({
			id: 'sample-content-warning',
			content: 'The media is hidden until the reader chooses to reveal it.',
			contentWarning: 'Spoilers for an unreleased feature design',
			images: [...images(1, { sensitive: true, seed: 'sensitive-image' }), video('sensitive-video')],
			sensitive: true,
		}),
	},
	{
		id: 'reply',
		label: 'reply / reference',
		variant: 'reply',
		post: createPost({
			id: 'sample-reply',
			content: 'Reply context needs to be visible, but it should not steal the entire first line.',
			replyToHandle: authors.kei.handle.raw,
			references: [reference('sample-quote-target', 'Reply', authors.kei.host ?? 'bsky.social')],
			actionCounts: {
				replies: 3,
				reposts: 2,
				likes: 35,
			},
		}),
	},
	{
		id: 'video-gif-audio',
		label: 'video / gif / audio',
		variant: 'rich-media',
		post: createPost({
			id: 'sample-video-gif-audio',
			content: 'Mixed media posts test badges, playback affordances, and non-image attachment sizing.',
			images: [video('sample-video'), gif('sample-gif'), audio('sample-audio')],
		}),
	},
	{
		id: 'long-identity',
		label: '长用户名 / 无头像',
		variant: 'identity',
		post: createPost({
			id: 'sample-long-identity',
			user: authors.longName,
			content: 'Identity rows need to survive long display names, long handles, platform badges, and verification marks.',
			quote: [
				createPost({
					id: 'sample-no-avatar-quote',
					user: authors.noAvatar,
					content: 'The quoted account intentionally has no avatar.',
					actions: [],
				}),
			],
		}),
	},
	{
		id: 'media-only',
		label: '无正文但有图',
		variant: 'empty-text',
		post: createPost({
			id: 'sample-media-only',
			content: '',
			images: images(2, { seed: 'media-only' }),
		}),
	},
	{
		id: 'card-only',
		label: '无正文但有 card',
		variant: 'empty-text',
		post: createPost({
			id: 'sample-card-only',
			content: '',
			card: card({
				title: 'Link-only posts still need a readable anchor',
				description: 'A card can be the only meaningful content in the post body.',
				media: null,
			}),
		}),
	},
	{
		id: 'emoji-reactions',
		label: 'emoji reactions',
		variant: 'reactions',
		post: createPost({
			id: 'sample-emoji-reactions',
			user: authors.mira,
			content: 'Misskey-style reactions should stay expressive without crowding the core actions.',
			emojiReactions: reactions(),
			sourceChannel: sourceChannel('design-lab', 'Design Lab'),
		}),
	},
	{
		id: 'translation-translating',
		label: '翻译中',
		variant: 'translation',
		post: createPost({
			id: 'sample-translation-translating',
			content: '翻译状态应该保持在右上角，正文下方不再出现额外状态块。',
			translationDisplayState: 'Translating',
		}),
	},
	{
		id: 'translation-translated',
		label: '已翻译',
		variant: 'translation',
		post: createPost({
			id: 'sample-translation-translated',
			content: 'タイムラインの密度は、静けさと情報量のバランスで決まります。',
			translationDisplayState: 'Translated',
		}),
	},
	{
		id: 'translation-failed',
		label: '翻译失败',
		variant: 'translation',
		post: createPost({
			id: 'sample-translation-failed',
			content: 'Die Uebersetzung kann fehlschlagen und braucht nur ein kompaktes Signal.',
			translationDisplayState: 'Failed',
		}),
	},
	{
		id: 'visibility',
		label: '可见性 / followers / specified',
		variant: 'visibility',
		post: createPost({
			id: 'sample-visibility',
			content: 'Restricted visibility needs a small but reliable signal.',
			visibility: 'Specified',
		}),
	},
	{
		id: 'missing-author',
		label: '缺失作者 / 部分加载',
		variant: 'partial',
		post: createPost({
			id: 'sample-missing-author',
			user: null,
			content: 'This post intentionally has no author object. The component should simply omit missing identity content.',
			platformType: 'Mastodon',
		}),
	},
	{
		id: 'failed-post',
		label: '加载失败状态',
		variant: 'failed',
		post: createPost({
			id: 'sample-failed-post',
			user: null,
			content: '',
			visibility: null,
			platformType: 'Mastodon',
			actions: [],
		}),
	},
	{
		id: 'complex-all',
		label: '复杂综合样例',
		variant: 'all-in-one',
		post: createPost({
			id: 'sample-complex-all',
			user: authors.longName,
			createdAt: dateTime('1h', '2026-06-01T08:38:00+09:00'),
			content: [
				'This one intentionally combines many difficult states in a single post.',
				'It has a long identity, content warning, sensitive mixed media, a card, poll, quote, repost context, reply context, reactions, translation state, restricted visibility, and a source channel.',
			].join('\n\n'),
			contentWarning: 'Complex sample with sensitive mixed media',
			images: [
				...images(2, { sensitive: true, seed: 'complex-sensitive' }),
				video('complex-video'),
				gif('complex-gif'),
				audio('complex-audio'),
			],
			card: card({
				title: 'A difficult post component checklist',
				description: 'Everything that can crowd a social post card, deliberately placed together.',
			}),
			poll: poll({
				id: 'poll-complex',
				multiple: true,
				ownVotes: [0, 1],
				options: [
					pollOption('Layout resilience', 0.39, 359),
					pollOption('Media handling', 0.33, 304),
					pollOption('Information density', 0.18, 166),
					pollOption('Action clarity', 0.1, 92),
				],
			}),
			quote: [
				createPost({
					id: 'sample-complex-quote',
					user: authors.noAvatar,
					content: 'Quoted post with no avatar, its own media, and a compact card.',
					images: images(1, { seed: 'complex-quote' }),
					card: card({
						title: 'Nested card in quote',
						description: 'Tests whether quoted content stays visually subordinate.',
						media: null,
					}),
					actions: [],
				}),
			],
			message: message({
				id: 'sample-complex-repost-message',
				user: authors.mira,
				icon: 'Retweet',
				type: { type: 'Localized', args: [], data: 'Repost' },
			}),
			replyToHandle: authors.kei.handle.raw,
			references: [
				reference('complex-ref-reply', 'Reply', authors.kei.host ?? 'bsky.social'),
				reference('complex-ref-quote', 'Quote', authors.noAvatar.host ?? 'example.social'),
				reference('complex-ref-repost', 'Retweet', authors.mira.host ?? 'misskey.io'),
				reference('complex-ref-notification', 'Notification'),
			],
			emojiReactions: reactions(),
			sourceChannel: sourceChannel('complex-channel', 'Release Notes'),
			sourceLanguages: ['ja'],
			translationDisplayState: 'Translating',
			visibility: 'Followers',
			sensitive: true,
			actionCounts: {
				replies: 128,
				reposts: 640,
				likes: 3200,
			},
		}),
	},
] satisfies UiTimelinePostSample[];
