<script lang="ts">
	import type { UiTimelineV2Message } from '@flare/web-presenters/timeline.svelte';
	import UiTimelineMessage from '../Message.svelte';

	let {
		message,
		sideAvatarVisible,
	}: {
		message: UiTimelineV2Message;
		sideAvatarVisible: boolean;
	} = $props();
</script>

<UiTimelineMessage {message} variant="postTop" {sideAvatarVisible} />
