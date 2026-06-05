<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { useReactiveRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import {
		createEmojiHistoryPresenterController,
		type AccountType,
	} from '@flare/web-presenters/emojiHistory.svelte';
	import type { UiEmoji } from '@flare/web-presenters/compose.svelte';

	type EmojiGroup = {
		category: string;
		emojis: UiEmoji[];
	};

	let {
		accountType = null,
		emojis,
		disabled = false,
		onSelect,
	}: {
		accountType?: AccountType | null;
		emojis: UiEmoji[];
		disabled?: boolean;
		onSelect: (emoji: UiEmoji) => void | Promise<void>;
	} = $props();

	let searchText = $state('');
	let expandedCategories = $state<string[]>([]);

	const groups = $derived(groupEmojis(emojis));
	const filteredGroups = $derived(
		groups
			.map((group) => ({
				...group,
				emojis: group.emojis.filter((emoji) => emojiMatchesSearch(emoji, searchText)),
			}))
			.filter((group) => group.emojis.length > 0)
		);
	const hasSearch = $derived(searchText.trim().length > 0);
	const emojiShortcodes = $derived(emojis.map((emoji) => emoji.shortcode));
	const emojiShortcodesText = $derived(emojiShortcodes.join('\u001f'));
	const emojiByShortcode = $derived(new Map(emojis.map((emoji) => [emoji.shortcode, emoji])));
	const emojiHistory = useReactiveRetainedPresenter(
		() => `emoji-history:${accountTypeKey(accountType)}:${emojiShortcodesText}`,
		() => createEmojiHistoryPresenterController(accountType, emojiShortcodesText),
		{ ttlMs: 0 }
	);
	const recentEmojis = $derived(
		emojiHistory.historyShortcodes.type === 'Success'
			? emojiHistory.historyShortcodes.data
					.map((shortcode) => emojiByShortcode.get(shortcode))
					.filter((emoji): emoji is UiEmoji => Boolean(emoji))
			: []
	);

	function groupEmojis(items: UiEmoji[]): EmojiGroup[] {
		const grouped = new Map<string, UiEmoji[]>();
		for (const emoji of items) {
			const category = emoji.category || 'Emoji';
			grouped.set(category, [...(grouped.get(category) ?? []), emoji]);
		}
		return Array.from(grouped, ([category, groupItems]) => ({
			category,
			emojis: groupItems,
		}));
	}

	function emojiMatchesSearch(emoji: UiEmoji, query: string): boolean {
		const normalizedQuery = query.trim().toLowerCase();
		if (!normalizedQuery) return true;
		return (
			emoji.shortcode.toLowerCase().includes(normalizedQuery) ||
			emoji.searchKeywords.some((keyword) => keyword.toLowerCase().includes(normalizedQuery))
		);
	}

	function categoryExpanded(category: string): boolean {
		return expandedCategories.includes(category);
	}

	function toggleCategory(category: string): void {
		expandedCategories = categoryExpanded(category)
			? expandedCategories.filter((item) => item !== category)
			: [...expandedCategories, category];
	}

	function selectEmoji(emoji: UiEmoji): void | Promise<void> {
		emojiHistory.addHistory(emoji.shortcode);
		return onSelect(emoji);
	}

	function accountTypeKey(value: AccountType | null): string {
		if (!value) return 'none';
		switch (value.type) {
			case 'Specific':
				return `specific:${value.accountKey.host}:${value.accountKey.id}`;
			case 'GuestHost':
				return `guest-host:${value.host}`;
			case 'Guest':
				return 'guest';
		}
	}
</script>

<input
	class="input input-sm emoji-search"
	bind:value={searchText}
	disabled={disabled}
	placeholder="Search"
/>

<div class="emoji-groups">
	{#if !hasSearch && recentEmojis.length > 0}
		<section class="emoji-group">
			<div class="emoji-category emoji-category-static">
				<span>Recent</span>
			</div>
			<div class="emoji-grid">
				{#each recentEmojis as emoji (emoji.shortcode)}
					<button
						class="emoji-button"
						type="button"
						disabled={disabled}
						title={`:${emoji.shortcode}:`}
						aria-label={emoji.shortcode}
						onclick={() => selectEmoji(emoji)}
					>
						<img src={emoji.url} alt="" />
					</button>
				{/each}
			</div>
		</section>
		<div class="emoji-divider"></div>
	{/if}

	{#each filteredGroups as group (group.category)}
		<section class="emoji-group">
			<button
				class="emoji-category"
				type="button"
				disabled={disabled}
				onclick={() => toggleCategory(group.category)}
			>
				<span>{group.category}</span>
				<FaIcon name={categoryExpanded(group.category) ? 'ChevronUp' : 'ChevronDown'} size={12} />
			</button>
			{#if filteredGroups.length === 1 || categoryExpanded(group.category) || hasSearch}
				<div class="emoji-grid">
					{#each group.emojis as emoji (emoji.shortcode)}
						<button
							class="emoji-button"
							type="button"
							disabled={disabled}
							title={`:${emoji.shortcode}:`}
							aria-label={emoji.shortcode}
							onclick={() => selectEmoji(emoji)}
						>
							<img src={emoji.url} alt="" />
						</button>
					{/each}
				</div>
			{/if}
		</section>
	{/each}
</div>

<style>
	.emoji-search {
		width: 100%;
	}

	.emoji-groups {
		display: grid;
		gap: 0.75rem;
		min-height: 0;
		overflow-y: auto;
		padding-right: 0.15rem;
	}

	.emoji-group {
		display: grid;
		gap: 0.25rem;
	}

	.emoji-category {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		width: 100%;
		align-items: center;
		border: 0;
		border-radius: var(--radius-field);
		background: transparent;
		padding: 0.4rem 0.45rem;
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.74rem;
		font-weight: 700;
		text-align: left;
	}

	.emoji-category:hover,
	.emoji-category:focus-visible {
		background: var(--color-base-200);
		color: var(--color-base-content);
	}

	.emoji-category-static {
		pointer-events: none;
	}

	.emoji-category span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.emoji-divider {
		height: 1px;
		background: var(--color-base-300);
	}

	.emoji-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(2.25rem, 1fr));
		gap: 0.15rem;
	}

	.emoji-button {
		display: grid;
		width: 2.25rem;
		height: 2.25rem;
		place-items: center;
		border: 0;
		border-radius: var(--radius-field);
		background: transparent;
		padding: 0.25rem;
	}

	.emoji-button:hover,
	.emoji-button:focus-visible {
		background: var(--color-base-200);
	}

	.emoji-button img {
		width: 1.6rem;
		height: 1.6rem;
		object-fit: contain;
	}
</style>
