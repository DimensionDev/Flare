<script lang="ts">
	import {
		bindTimelinePresenter,
		type ActionMenu,
		type Image,
		type Item,
		type Post,
		type UiCard,
		type UiRichText,
		type UiMedia,
		type UiTimelineV2,
	} from '@flare/web-presenters/timeline.svelte';
	import type { TimelineTabItemV2 } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
	import { createWindowVirtualizer } from '@tanstack/svelte-virtual';
	import { get } from 'svelte/store';
	import FaIcon from './FaIcon.svelte';

	let { tab }: { tab: TimelineTabItemV2 } = $props();

	// svelte-ignore state_referenced_locally -- +page.svelte keys this component by tab id.
	const timeline = bindTimelinePresenter(tab.createPresenter());
	const virtualItemCount = $derived(
		timeline.listState.type === 'Success' ? timeline.listState.itemCount : 0
	);
	const rowVirtualizer = createWindowVirtualizer<HTMLElement>({
		count: 0,
		estimateSize: () => 220,
		getItemKey: (index) => itemKey(index),
		overscan: 6,
	});

	type TimelinePost = Extract<UiTimelineV2, { type: 'Post' }>;
	type ActionDisplayItem = Extract<ActionMenu, { type: 'Item' }> | Item;

	$effect(() => {
		const count = virtualItemCount;
		get(rowVirtualizer).setOptions({
			count,
		});
	});

	function visibleItem(index: number): UiTimelineV2 | null {
		if (timeline.listState.type !== 'Success') return null;
		return timeline.listState.peek(index);
	}

	function itemKey(index: number): string {
		const item = visibleItem(index);
		return `${item?.type ?? 'placeholder'}-${item?.itemKey ?? index}`;
	}

	function measureVirtualItem(node: HTMLElement) {
		get(rowVirtualizer).measureElement(node);

		return {
			destroy() {
				get(rowVirtualizer).measureElement(null);
			},
		};
	}

	function loadWhenVisible(node: HTMLElement, index: number) {
		let currentIndex = index;
		let loaded = false;

		const load = () => {
			if (loaded || timeline.listState.type !== 'Success') return;
			loaded = true;
			timeline.listState.get(currentIndex);
		};

		if (typeof IntersectionObserver === 'undefined') {
			load();
			return {
				update(nextIndex: number) {
					currentIndex = nextIndex;
				},
			};
		}

		const observer = new IntersectionObserver(
			(entries) => {
				if (entries.some((entry) => entry.isIntersecting)) {
					load();
				}
			},
			{ rootMargin: '640px 0px' }
		);
		observer.observe(node);

		return {
			update(nextIndex: number) {
				currentIndex = nextIndex;
				loaded = false;
			},
			destroy() {
				observer.disconnect();
			},
		};
	}

	function postAuthor(post: TimelinePost | Post): string {
		return post.user?.name.innerText || post.user?.handleWithoutAt || 'Unknown';
	}

	function postHandle(post: TimelinePost | Post): string {
		return post.user?.handle.raw || post.user?.handle.canonical || '';
	}

	function mediaPreview(media: UiMedia | Image): string | null {
		if (!('type' in media)) {
			return media.previewUrl || media.url;
		}
		switch (media.type) {
			case 'Image':
			case 'Gif':
				return media.previewUrl || media.url;
			case 'Video':
				return media.thumbnailUrl;
			case 'Audio':
				return media.previewUrl;
		}
	}

	function actionItems(actions: ActionMenu[]): ActionDisplayItem[] {
		const items: ActionDisplayItem[] = [];
		for (const action of actions) {
			if (action.type === 'Item') {
				items.push(action);
			} else if (action.type === 'Group') {
				items.push(action.displayItem);
			}
		}
		return items.slice(0, 5);
	}

	function actionLabel(action: ActionDisplayItem): string {
		if (action.text?.type === 'Raw') return action.text.text;
		if (action.text?.type === 'Localized') return action.text.type;
		return action.icon ?? 'Action';
	}

	function actionCount(action: ActionDisplayItem): string {
		return action.count?.humanized || '';
	}

	function cardHost(url: string): string {
		try {
			return new URL(url).host;
		} catch {
			return url;
		}
	}
</script>

<section class="timeline" aria-label="Timeline">
	{#if timeline.listState.type === 'Loading'}
		<div class="state-row">Loading</div>
	{:else if timeline.listState.type === 'Error'}
		<div class="state-row">{timeline.listState.message ?? 'Failed to load timeline'}</div>
	{:else if timeline.listState.type === 'Empty'}
		<div class="state-row">No posts</div>
	{:else}
		<div class="virtual-canvas" style:height={`${$rowVirtualizer.getTotalSize()}px`}>
			{#each $rowVirtualizer.getVirtualItems() as virtualRow (virtualRow.key)}
				{@const index = virtualRow.index}
				{@const item = visibleItem(index)}
				<div
					class="virtual-row"
					data-index={virtualRow.index}
					style:transform={`translateY(${virtualRow.start}px)`}
					use:measureVirtualItem
					use:loadWhenVisible={virtualRow.index}
				>
					{#if item === null}
						<article class="status placeholder">
							<div class="avatar skeleton"></div>
							<div class="status-body">
								<div class="skeleton line short"></div>
								<div class="skeleton line"></div>
								<div class="skeleton line medium"></div>
							</div>
						</article>
					{:else if item.type === 'Post'}
						<article class="status">
							<img
								class="avatar"
								src={item.user?.avatar || '/favicon.svg'}
								alt=""
								loading="lazy"
							/>
							<div class="status-body">
								<header class="status-header">
									<div class="identity">
										<strong>{postAuthor(item)}</strong>
										<span>{postHandle(item)}</span>
									</div>
									<time title={item.createdAt.full}>{item.createdAt.relative}</time>
								</header>

								{#if item.contentWarning && !item.contentWarning.isEmpty}
									{@render RichTextView({ text: item.contentWarning, className: 'content-warning' })}
								{/if}

								{#if !item.content.isEmpty}
									{@render RichTextView({ text: item.content, className: 'post-text' })}
								{/if}

								{#if item.images.length > 0}
									<div class:single={item.images.length === 1} class="media-grid">
										{#each item.images.slice(0, 4) as media}
											{@const preview = mediaPreview(media)}
											{#if preview}
												<figure class="media-item">
													<img src={preview} alt={media.description ?? ''} loading="lazy" />
													{#if media.type === 'Video'}
														<span class="media-badge">
															<FaIcon name="Video" size={13} />
														</span>
													{/if}
												</figure>
											{/if}
										{/each}
									</div>
								{/if}

								{#if item.card}
									{@render CardView({ card: item.card })}
								{/if}

								{#if item.quote.length > 0}
									<div class="quote-stack">
										{#each item.quote.slice(0, 1) as quote}
											<div class="quote">
												<strong>{postAuthor(quote)}</strong>
												{@render RichTextView({ text: quote.content, className: 'quote-text' })}
											</div>
										{/each}
									</div>
								{/if}

								<div class="actions" aria-label="Post actions">
									{#each actionItems(item.actions) as action}
										<button type="button" title={actionLabel(action)} aria-label={actionLabel(action)}>
											<FaIcon name={action.icon} size={15} />
											{#if actionCount(action)}
												<span>{actionCount(action)}</span>
											{/if}
										</button>
									{/each}
								</div>
							</div>
						</article>
					{:else if item.type === 'Feed'}
						<article class="status compact">
							<div class="feed-icon">
								<FaIcon name="Rss" size={18} />
							</div>
							<div class="status-body">
								<header class="status-header">
									<div class="identity">
										<strong>{item.title ?? item.source.name}</strong>
										<span>{item.source.name}</span>
									</div>
									<time title={item.createdAt.full}>{item.createdAt.relative}</time>
								</header>
								{#if item.description}
									<p class="post-text">{item.description}</p>
								{/if}
								{#if item.media}
									{@const preview = mediaPreview(item.media)}
									<div class="media-grid single">
										{#if preview}
											<figure class="media-item">
												<img src={preview} alt={item.media.description ?? ''} loading="lazy" />
											</figure>
										{/if}
									</div>
								{/if}
							</div>
						</article>
					{:else if item.type === 'User'}
						<article class="status compact">
							<img class="avatar" src={item.value.avatar || '/favicon.svg'} alt="" loading="lazy" />
							<div class="status-body">
								<header class="status-header">
									<div class="identity">
										<strong>{item.value.name.innerText}</strong>
										<span>{item.value.handle.raw}</span>
									</div>
									<time title={item.createdAt.full}>{item.createdAt.relative}</time>
								</header>
								{#if item.value.description}
									{@render RichTextView({ text: item.value.description, className: 'post-text' })}
								{/if}
							</div>
						</article>
					{:else}
						<article class="status compact">
							<div class="feed-icon">
								<FaIcon name={item.type === 'Message' ? item.icon : 'List'} size={18} />
							</div>
							<div class="status-body">
								<header class="status-header">
									<div class="identity">
										<strong>{item.type}</strong>
										<span>{item.itemKey ?? item.statusKey.id}</span>
									</div>
									<time title={item.createdAt.full}>{item.createdAt.relative}</time>
								</header>
							</div>
						</article>
					{/if}
				</div>
			{/each}
		</div>
	{/if}
</section>

{#snippet CardView({ card }: { card: UiCard })}
	<a class="link-card" href={card.url} target="_blank" rel="noreferrer">
		{#if card.media}
			{@const preview = mediaPreview(card.media)}
			{#if preview}
				<img src={preview} alt="" loading="lazy" />
			{/if}
		{/if}
		<div>
			<strong>{card.title}</strong>
			{#if card.description}
				<span>{card.description}</span>
			{/if}
			<small>{cardHost(card.url)}</small>
		</div>
	</a>
{/snippet}

{#snippet RichTextView({ text, className }: { text: UiRichText; className: string })}
	<div class={`${className} rich-text`} dir={text.isRtl ? 'rtl' : 'auto'}>{@html text.platformText}</div>
{/snippet}

<style>
	.timeline {
		display: grid;
		min-width: 0;
	}

	.virtual-canvas {
		position: relative;
		width: 100%;
	}

	.virtual-row {
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
	}

	.virtual-row > .status {
		box-sizing: border-box;
	}

	.state-row {
		display: grid;
		min-height: 180px;
		place-items: center;
		border-bottom: 1px solid #e0e4ea;
		color: #697586;
		font-size: 0.95rem;
	}

	.status {
		display: grid;
		grid-template-columns: 46px minmax(0, 1fr);
		gap: 12px;
		padding: 16px 18px;
		border-bottom: 1px solid #e0e4ea;
		background: #ffffff;
	}

	.status.compact {
		align-items: start;
	}

	.status.placeholder {
		min-height: 112px;
	}

	.skeleton {
		border-radius: 8px;
		background: linear-gradient(90deg, #eef2f7 0%, #f6f8fb 48%, #eef2f7 100%);
		background-size: 180% 100%;
		animation: skeleton-pulse 1.2s ease-in-out infinite;
	}

	.skeleton.line {
		height: 12px;
		width: 100%;
	}

	.skeleton.line.short {
		width: 34%;
	}

	.skeleton.line.medium {
		width: 68%;
	}

	@keyframes skeleton-pulse {
		from {
			background-position: 100% 0;
		}
		to {
			background-position: -100% 0;
		}
	}

	.avatar,
	.feed-icon {
		width: 46px;
		height: 46px;
		border-radius: 50%;
		background: #eef2f7;
		color: #536172;
		object-fit: cover;
	}

	.feed-icon {
		display: grid;
		place-items: center;
	}

	.status-body {
		min-width: 0;
		display: grid;
		gap: 8px;
	}

	.status-header {
		display: flex;
		min-width: 0;
		align-items: baseline;
		justify-content: space-between;
		gap: 12px;
	}

	.identity {
		min-width: 0;
		display: flex;
		align-items: baseline;
		gap: 8px;
	}

	.identity strong,
	.identity span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.identity strong {
		color: #1f2937;
		font-size: 0.95rem;
		font-weight: 700;
	}

	.identity span,
	time {
		color: #697586;
		font-size: 0.86rem;
	}

	.content-warning {
		width: fit-content;
		max-width: 100%;
		border: 1px solid #d7dde6;
		border-radius: 8px;
		background: #f5f7fa;
		padding: 7px 10px;
		color: #344054;
		font-size: 0.88rem;
		font-weight: 650;
	}

	.post-text {
		margin: 0;
		color: #273142;
		font-size: 0.98rem;
		line-height: 1.48;
		overflow-wrap: anywhere;
	}

	.rich-text {
		white-space: pre-wrap;
	}

	.rich-text :global(.rt-block) {
		margin: 0 0 0.72em;
	}

	.rich-text :global(.rt-block:last-child) {
		margin-bottom: 0;
	}

	.rich-text :global(a) {
		color: #2563eb;
		text-decoration: none;
	}

	.rich-text :global(a:hover) {
		text-decoration: underline;
	}

	.rich-text :global(code) {
		border-radius: 4px;
		background: #eef2f7;
		padding: 0 0.24em;
		font-family:
			ui-monospace,
			SFMono-Regular,
			Menlo,
			Monaco,
			Consolas,
			'Liberation Mono',
			monospace;
		font-size: 0.94em;
	}

	.rich-text :global(.rt-inline-image) {
		display: inline-block;
		width: 1.2em;
		height: 1.2em;
		object-fit: contain;
		vertical-align: -0.22em;
	}

	.rich-text :global(.rt-block-image) {
		overflow: hidden;
		margin: 8px 0 0;
		border: 1px solid #d8dee8;
		border-radius: 8px;
		background: #f2f4f7;
	}

	.rich-text :global(.rt-block-image img) {
		display: block;
		width: 100%;
		max-height: 420px;
		object-fit: cover;
	}

	.rich-text :global(.rt-blockquote) {
		border-left: 3px solid #d8dee8;
		padding-left: 12px;
		color: #475467;
	}

	.rich-text :global(.rt-align-center) {
		text-align: center;
	}

	.rich-text :global(.rt-figcaption),
	.rich-text :global(small) {
		color: #697586;
		font-size: 0.88em;
	}

	.media-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 6px;
		overflow: hidden;
		border-radius: 8px;
		border: 1px solid #d8dee8;
		background: #f2f4f7;
	}

	.media-grid.single {
		grid-template-columns: minmax(0, 1fr);
	}

	.media-item {
		position: relative;
		min-width: 0;
		aspect-ratio: 16 / 10;
		margin: 0;
		background: #eceff4;
	}

	.media-item img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}

	.media-badge {
		position: absolute;
		right: 8px;
		bottom: 8px;
		display: grid;
		width: 26px;
		height: 26px;
		place-items: center;
		border-radius: 50%;
		background: rgb(17 24 39 / 80%);
		color: #fff;
	}

	.link-card {
		display: grid;
		grid-template-columns: 96px minmax(0, 1fr);
		gap: 12px;
		min-width: 0;
		overflow: hidden;
		border: 1px solid #d8dee8;
		border-radius: 8px;
		color: inherit;
		text-decoration: none;
	}

	.link-card img {
		width: 96px;
		height: 100%;
		min-height: 92px;
		object-fit: cover;
		background: #edf0f5;
	}

	.link-card div {
		min-width: 0;
		display: grid;
		align-content: center;
		gap: 3px;
		padding: 10px 12px 10px 0;
	}

	.link-card strong,
	.link-card span,
	.link-card small {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.link-card strong {
		font-size: 0.9rem;
	}

	.link-card span,
	.link-card small {
		color: #697586;
		font-size: 0.82rem;
	}

	.quote {
		display: grid;
		gap: 4px;
		border: 1px solid #d8dee8;
		border-radius: 8px;
		padding: 10px 12px;
		color: #344054;
	}

	.quote-text {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: #596579;
		font-size: 0.9rem;
	}

	.quote-text :global(.rt-block) {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.actions {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		max-width: 360px;
		padding-top: 2px;
	}

	.actions button {
		display: inline-flex;
		min-width: 34px;
		height: 32px;
		align-items: center;
		justify-content: center;
		gap: 6px;
		border: 0;
		border-radius: 8px;
		background: transparent;
		color: #667085;
		cursor: pointer;
	}

	.actions button:hover {
		background: #f1f4f8;
		color: #344054;
	}

	.actions span {
		font-size: 0.82rem;
	}

	@media (max-width: 640px) {
		.status {
			grid-template-columns: 38px minmax(0, 1fr);
			padding: 14px;
		}

		.avatar,
		.feed-icon {
			width: 38px;
			height: 38px;
		}

		.link-card {
			grid-template-columns: minmax(0, 1fr);
		}

		.link-card img {
			width: 100%;
			height: 120px;
		}

		.link-card div {
			padding: 0 12px 12px;
		}
	}
</style>
