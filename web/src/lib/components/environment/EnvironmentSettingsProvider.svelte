<script lang="ts">
	import { onMount } from 'svelte';
	import { createEnvironmentSettingsPresenter } from '@flare/web-presenters/environmentSettings.svelte';
	import {
		createEnvironmentSettingsContext,
		provideEnvironmentSettings,
	} from '$lib/environment/environmentSettings.svelte';
	import { installWebFormatter } from '$lib/formatter/webFormatter';

	let { children } = $props();

	const environmentSettings = createEnvironmentSettingsPresenter();
	provideEnvironmentSettings(createEnvironmentSettingsContext(environmentSettings));

	onMount(() => {
		installWebFormatter();
	});
</script>

{@render children()}
