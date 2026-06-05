<script lang="ts">
	import { onMount } from 'svelte';
	import { useEnvironmentSettings, type Theme } from '$lib/environment/environmentSettings.svelte';

	const environmentSettings = useEnvironmentSettings();
	let mounted = $state(false);
	let systemDark = $state(false);

	const globalAppearanceState = $derived(environmentSettings.globalAppearance());
	const themeSetting = $derived<Theme>(
		globalAppearanceState.type === 'Success' ? globalAppearanceState.data.theme : 'SYSTEM'
	);
	const resolvedTheme = $derived(themeSetting === 'SYSTEM' ? (systemDark ? 'dark' : 'light') : themeSetting.toLowerCase());

	onMount(() => {
		mounted = true;
		const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
		const syncSystemTheme = () => {
			systemDark = mediaQuery.matches;
		};

		syncSystemTheme();
		mediaQuery.addEventListener('change', syncSystemTheme);

		return () => {
			mediaQuery.removeEventListener('change', syncSystemTheme);
		};
	});

	$effect(() => {
		if (!mounted) return;
		document.documentElement.dataset.theme = resolvedTheme;
		document.documentElement.style.colorScheme = resolvedTheme;
	});
</script>
