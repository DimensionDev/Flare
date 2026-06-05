<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { m } from '$lib/paraglide/messages.js';

	let { iconSize = 16 }: { iconSize?: number } = $props();

	function goBack(): void {
		globalThis.history.back();
	}
</script>

<button
	class="btn btn-ghost btn-square btn-sm rounded-box"
	type="button"
	aria-label={m.navigateBack()}
	title={m.navigateBack()}
	onclick={goBack}
>
	<FaIcon name="Back" size={iconSize} />
</button>
