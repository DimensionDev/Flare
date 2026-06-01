import { createContext } from 'svelte';
import type {
	AppSettings,
	EnvironmentSettingsPresenterState,
	GlobalAppearance,
	TimelineAppearance,
	UiState,
} from '@flare/web-presenters/environmentSettings.svelte';

export type {
	AppSettings,
	AppSettingsAiConfig,
	AppSettingsAiConfigType,
	AppSettingsTranslateConfig,
	AppSettingsTranslateConfigProvider,
	AvatarShape,
	BottomBarBehavior,
	BottomBarStyle,
	GlobalAppearance,
	PostActionStyle,
	Theme,
	TimelineAppearance,
	TimelineAppearanceAiConfig,
	TimelineDisplayMode,
	UiState,
	VideoAutoplay,
} from '@flare/web-presenters/environmentSettings.svelte';

export type EnvironmentSettingsContext = {
	state: EnvironmentSettingsPresenterState;
	appSettings: () => UiState<AppSettings>;
	globalAppearance: () => UiState<GlobalAppearance>;
	timelineAppearance: () => UiState<TimelineAppearance>;
	timelineAppearanceOverride: () => TimelineAppearance | null;
	setTimelineAppearanceOverride: (appearance: TimelineAppearance) => void;
	resetTimelineAppearanceOverride: () => void;
};

export const [useEnvironmentSettings, provideEnvironmentSettings] =
	createContext<EnvironmentSettingsContext>();

export function createEnvironmentSettingsContext(
	state: EnvironmentSettingsPresenterState
): EnvironmentSettingsContext {
	let timelineAppearanceOverride = $state<TimelineAppearance | null>(null);

	return {
		state,
		appSettings: () => state.appSettings,
		globalAppearance: () => state.globalAppearance,
		timelineAppearance: () => {
			if (timelineAppearanceOverride) {
				return { type: 'Success', data: timelineAppearanceOverride };
			}
			return state.timelineAppearance;
		},
		timelineAppearanceOverride: () => timelineAppearanceOverride,
		setTimelineAppearanceOverride: (appearance: TimelineAppearance) => {
			timelineAppearanceOverride = appearance;
		},
		resetTimelineAppearanceOverride: () => {
			timelineAppearanceOverride = null;
		},
	};
}
