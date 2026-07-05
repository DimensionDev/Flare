<script lang="ts">
	import type { MicroBlogKey, UiTimelineV2, UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import UiTimelineFeed from './Feed.svelte';
	import UiTimelineMessage from './Message.svelte';
	import UiTimelinePost from './Post.svelte';
	import UiTimelineUser from './User.svelte';
	import UiTimelineUserList from './UserList.svelte';

	let {
		item,
		detailStatusKey = null,
	}: {
		item: UiTimelineV2;
		detailStatusKey?: MicroBlogKey | null;
	} = $props();

	type UiTimelineV2TimelinePostItem = Extract<UiTimelineV2, { type: 'TimelinePostItem' }>;

	function asPost(value: UiTimelineV2): UiTimelineV2Post {
		return value as unknown as UiTimelineV2Post;
	}

	function asTimelinePostItem(value: UiTimelineV2): UiTimelineV2TimelinePostItem {
		return value as UiTimelineV2TimelinePostItem;
	}

	function displayPost(item: UiTimelineV2TimelinePostItem): UiTimelineV2Post {
		return item.presentation.repost ?? item.post;
	}

	function isDetailPost(post: UiTimelineV2Post): boolean {
		return (
			detailStatusKey !== null &&
			post.statusKey.id === detailStatusKey.id &&
			post.statusKey.host === detailStatusKey.host
		);
	}
</script>

{#if item.type === 'Post'}
	<UiTimelinePost post={asPost(item)} isDetail={isDetailPost(asPost(item))} />
{:else if item.type === 'TimelinePostItem'}
	{@const timelinePostItem = asTimelinePostItem(item)}
	{@const post = displayPost(timelinePostItem)}
	<UiTimelinePost
		{post}
		isDetail={isDetailPost(post)}
		message={timelinePostItem.presentation.message}
		inlineParents={timelinePostItem.presentation.inlineParents}
		quotes={timelinePostItem.presentation.quotes}
	/>
{:else if item.type === 'Feed'}
	<UiTimelineFeed feed={item} />
{:else if item.type === 'Message'}
	<UiTimelineMessage message={item} />
{:else if item.type === 'User'}
	<UiTimelineUser user={item} />
{:else if item.type === 'UserList'}
	<UiTimelineUserList userList={item} />
{/if}
