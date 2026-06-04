<script lang="ts">
	import {
		provideEnvironmentSettings,
		useEnvironmentSettings,
		type EnvironmentSettingsContext,
		type TimelineAppearance,
	} from '$lib/environment/environmentSettings.svelte';

	let {
		appearance = null,
		children,
	}: {
		appearance?: TimelineAppearance | null;
		children: import('svelte').Snippet;
	} = $props();

	const parent = useEnvironmentSettings();
	let timelineAppearanceOverride = $state<TimelineAppearance | null>(null);

	const scopedEnvironmentSettings: EnvironmentSettingsContext = {
		...parent,
		timelineAppearance: () => {
			if (timelineAppearanceOverride) {
				return { type: 'Success', data: timelineAppearanceOverride };
			}
			if (appearance) {
				return { type: 'Success', data: appearance };
			}
			return parent.timelineAppearance();
		},
		timelineAppearanceOverride: () => timelineAppearanceOverride ?? parent.timelineAppearanceOverride(),
		setTimelineAppearanceOverride: (value: TimelineAppearance) => {
			timelineAppearanceOverride = value;
		},
		resetTimelineAppearanceOverride: () => {
			timelineAppearanceOverride = null;
		},
	};

	provideEnvironmentSettings(scopedEnvironmentSettings);
</script>

{@render children()}
