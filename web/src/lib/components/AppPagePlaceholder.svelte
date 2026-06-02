<script lang="ts">
	import FaIcon from '$lib/components/FaIcon.svelte';

	let { icon, title }: { icon: string; title: string } = $props();
</script>

<div class="content-panel">
	<div class="content-placeholder">
		<FaIcon name={icon} size={28} />
		<span>{title}</span>
	</div>
</div>

<style>
	.content-panel {
		display: grid;
		min-height: calc(100vh - 5rem);
		min-width: 0;
		align-content: start;
		gap: 1rem;
	}

	.content-placeholder {
		display: grid;
		min-height: 12rem;
		place-items: center;
		align-content: center;
		gap: 0.65rem;
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
	}

	.content-placeholder span {
		font-size: 0.9rem;
		font-weight: 400;
	}
</style>
