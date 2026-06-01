<script lang="ts">
	import DeepLinkProvider from '$lib/components/deeplink/DeepLinkProvider.svelte';
	import EnvironmentSettingsProvider from '$lib/components/environment/EnvironmentSettingsProvider.svelte';
	import ThemeController from '$lib/components/environment/ThemeController.svelte';
	import favicon from '$lib/assets/favicon.svg';
	import '../app.css';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<EnvironmentSettingsProvider>
	<DeepLinkProvider>
		<ThemeController />
		{@render children()}
	</DeepLinkProvider>
</EnvironmentSettingsProvider>
