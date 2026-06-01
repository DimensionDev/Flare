<script lang="ts">
	import { createEnvironmentSettingsPresenter } from '@flare/web-presenters/environmentSettings.svelte';
	import {
		createEnvironmentSettingsContext,
		provideEnvironmentSettings,
	} from '$lib/environment/environmentSettings.svelte';
	import { installWebFormatter } from '$lib/formatter/webFormatter';

	let { children } = $props();

	installWebFormatter();
	const environmentSettings = createEnvironmentSettingsPresenter();
	provideEnvironmentSettings(createEnvironmentSettingsContext(environmentSettings));
</script>

{@render children()}
