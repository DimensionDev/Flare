<script lang="ts">
	import type { UiTimelineV2, UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import UiTimelineFeed from './Feed.svelte';
	import UiTimelineMessage from './Message.svelte';
	import UiTimelinePost from './Post.svelte';
	import UiTimelineUser from './User.svelte';
	import UiTimelineUserList from './UserList.svelte';

	let {
		item,
	}: {
		item: UiTimelineV2;
	} = $props();

	function asPost(value: UiTimelineV2): UiTimelineV2Post {
		return value as unknown as UiTimelineV2Post;
	}
</script>

{#if item.type === 'Post'}
	<UiTimelinePost post={asPost(item)} />
{:else if item.type === 'Feed'}
	<UiTimelineFeed feed={item} />
{:else if item.type === 'Message'}
	<UiTimelineMessage message={item} />
{:else if item.type === 'User'}
	<UiTimelineUser user={item} />
{:else if item.type === 'UserList'}
	<UiTimelineUserList userList={item} />
{/if}
