<script lang="ts">
	import EnvironmentSettingsProvider from '$lib/components/environment/EnvironmentSettingsProvider.svelte';
	import favicon from '$lib/assets/favicon.svg';
	import '../app.css';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<EnvironmentSettingsProvider>
	{@render children()}
</EnvironmentSettingsProvider>
