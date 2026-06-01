import type {
	AccountType,
	ClickEvent,
	RssDisplayMode,
	TranslationDisplayState,
	UiDateTime,
	UiMediaImage,
	UiTimelineV2,
	UiTimelineV2FeedSource,
	WebPresenterRef,
} from '@flare/web-presenters/timeline.svelte';

type UiTimelineFeed = Extract<UiTimelineV2, { type: 'Feed' }>;

export type UiTimelineFeedSample = {
	id: string;
	label: string;
	variant: string;
	feed: UiTimelineFeed;
};

type FeedOptions = {
	id: string;
	title?: string | null;
	description?: string | null;
	descriptionHtml?: string | null;
	url?: string;
	sourceName?: string;
	sourceIcon?: string | null;
	sourceLanguages?: string[];
	translationDisplayState?: TranslationDisplayState;
	createdAt?: UiDateTime;
	displayMode?: RssDisplayMode;
	media?: UiMediaImage | null;
};

const guestAccount: AccountType = { type: 'Guest' };
const noopClickEvent: ClickEvent = { type: 'Noop' };

let nextPresenterRef = 20_000;

function withRef<T extends object>(value: T): T & WebPresenterRef {
	return {
		__webPresenterRef: nextPresenterRef++,
		__webPresenterRefs: [],
		...value,
	} as T & WebPresenterRef;
}

function dateTime(
	relative = '14m',
	full = '2026-06-01T09:46:00+09:00',
	absolute = 'Jun 1, 2026'
): UiDateTime {
	return withRef({
		absolute,
		full,
		relative,
		shouldShowFull: false,
	});
}

function emptyDateTime(): UiDateTime {
	return dateTime('', '', '');
}

function source(name: string, icon: string | null): UiTimelineV2FeedSource {
	return withRef({
		icon,
		name,
	});
}

function feedImage(
	seed: string,
	options: { description?: string; width?: number; height?: number } = {}
): UiMediaImage {
	const width = options.width ?? 1200;
	const height = options.height ?? 800;

	return withRef({
		aspectRatio: width / height,
		description: options.description ?? 'Feed thumbnail',
		height,
		previewUrl: `https://picsum.photos/seed/${seed}-feed-preview/${width}/${height}`,
		sensitive: false,
		url: `https://picsum.photos/seed/${seed}-feed/${width}/${height}`,
		width,
	});
}

function createFeed(options: FeedOptions): UiTimelineFeed {
	const url = options.url ?? `https://example.com/feed/${options.id}`;

	return withRef({
		type: 'Feed',
		accountType: guestAccount,
		clickEvent: noopClickEvent,
		createdAt: options.createdAt ?? dateTime(),
		description: options.description ?? 'A short article summary from an RSS feed item.',
		descriptionHtml: options.descriptionHtml ?? null,
		displayMode: options.displayMode ?? 'FULL_CONTENT',
		itemKey: options.id,
		media: options.media ?? null,
		source: source(
			options.sourceName ?? 'Flare RSS',
			options.sourceIcon === undefined
				? `https://picsum.photos/seed/${options.id}-source-icon/80/80`
				: options.sourceIcon
		),
		sourceLanguages: options.sourceLanguages ?? [],
		title: options.title ?? 'A compact feed item for the web timeline',
		translationDisplayState: options.translationDisplayState ?? 'Hidden',
		url,
	});
}

export const timelineFeedSamples = [
	{
		id: 'standard',
		label: '标准 feed',
		variant: 'rss',
		feed: createFeed({
			id: 'sample-feed-standard',
			title: 'Timeline clients are getting calmer without getting emptier',
			description:
				'A short field note on how timeline density, metadata, and media thumbnails can coexist without turning every item into a card.',
			media: feedImage('standard'),
			sourceName: 'Flare Journal',
			url: 'https://example.com/journal/timeline-calm',
		}),
	},
	{
		id: 'no-media',
		label: '无图 feed',
		variant: 'rss',
		feed: createFeed({
			id: 'sample-feed-no-media',
			title: 'Release notes should scan like a timeline item',
			description:
				'No thumbnail here, just source, title, time, and a five-line description region with secondary text.',
			sourceName: 'Release Desk',
			url: 'https://example.com/releases/web-feed',
		}),
	},
	{
		id: 'title-only',
		label: '只有标题',
		variant: 'minimal',
		feed: createFeed({
			id: 'sample-feed-title-only',
			title: 'A title-only RSS item should not leave awkward empty space',
			description: null,
			sourceName: 'Minimal Feed',
			sourceIcon: null,
			url: 'https://example.com/minimal/title-only',
		}),
	},
	{
		id: 'description-only',
		label: '无标题 / 有描述',
		variant: 'partial',
		feed: createFeed({
			id: 'sample-feed-description-only',
			title: null,
			description:
				'Some feeds omit titles or collapse them into body text. The component should still keep the source row and body readable.',
			media: feedImage('description-only', { width: 900, height: 900 }),
			sourceName: 'Untitled Notes',
			url: 'https://example.com/notes/untitled',
		}),
	},
	{
		id: 'long-content',
		label: '长标题 / 长描述',
		variant: 'stress',
		feed: createFeed({
			id: 'sample-feed-long-content',
			title:
				'A very long RSS headline that wraps across multiple lines while still leaving metadata readable at the top',
			description:
				'Long descriptions need a firm five-line clamp. This sample keeps going with enough words to test wrapping, line-height, and whether the thumbnail remains anchored to the right edge like the iOS implementation. It should feel compact, not cramped.',
			media: feedImage('long-content', { width: 1600, height: 900 }),
			sourceName:
				'A Very Long Source Name For Layout Testing And Header Wrapping In The Feed Component',
			url: 'https://example.com/long/feed-item',
		}),
	},
	{
		id: 'translation-translating',
		label: '翻译中 feed',
		variant: 'translation',
		feed: createFeed({
			id: 'sample-feed-translation-translating',
			title: '海外記事を翻訳中の状態',
			description: '翻訳状態は source 行の右侧に小さく表示されます。',
			sourceName: 'Design JP',
			sourceLanguages: ['ja'],
			translationDisplayState: 'Translating',
			url: 'https://example.jp/design/feed',
		}),
	},
	{
		id: 'translation-failed',
		label: '翻译失败 feed',
		variant: 'translation',
		feed: createFeed({
			id: 'sample-feed-translation-failed',
			title: 'Uebersetzung fehlgeschlagen',
			description:
				'Failed translation uses the same compact icon treatment as posts, without adding a separate status row.',
			sourceName: 'Design DE',
			sourceLanguages: ['de'],
			translationDisplayState: 'Failed',
			url: 'https://example.de/design/feed',
		}),
	},
	{
		id: 'no-date',
		label: '无时间 feed',
		variant: 'partial',
		feed: createFeed({
			id: 'sample-feed-no-date',
			title: 'Imported items can arrive without a meaningful publish time',
			description: 'When date text is absent, the source row should simply omit the trailing time.',
			createdAt: emptyDateTime(),
			media: feedImage('no-date'),
			sourceName: 'Archive Import',
			url: 'https://example.com/archive/imported',
		}),
	},
	{
		id: 'open-browser',
		label: '浏览器打开模式',
		variant: 'display-mode',
		feed: createFeed({
			id: 'sample-feed-open-browser',
			title: 'This feed item is configured to open in the browser',
			description:
				'The visual treatment stays the same; navigation behavior is owned by the Kotlin-side display mode.',
			displayMode: 'OPEN_IN_BROWSER',
			media: feedImage('open-browser', { width: 1200, height: 1200 }),
			sourceName: 'External News',
			url: 'https://example.com/external/browser-mode',
		}),
	},
	{
		id: 'complex-all',
		label: '复杂综合 feed',
		variant: 'all-in-one',
		feed: createFeed({
			id: 'sample-feed-complex-all',
			title:
				'Everything in one feed item: long source, translated state, thumbnail, description, and browser display mode',
			description:
				'This sample intentionally combines a long source name, a long title, a thumbnail, translated state, source languages, and a description that should clamp after five lines. It is useful for checking desktop and mobile layouts in both light and dark modes.',
			descriptionHtml:
				'<p>This sample intentionally combines a long source name, translated state, media, and long body copy.</p>',
			displayMode: 'DESCRIPTION_ONLY',
			media: feedImage('complex-feed', { width: 1400, height: 1000 }),
			sourceName:
				'International Product Design Weekly With A Long Source Name',
			sourceLanguages: ['zh', 'ja'],
			translationDisplayState: 'Translated',
			url: 'https://example.com/weekly/complex-feed-item',
		}),
	},
] satisfies UiTimelineFeedSample[];
