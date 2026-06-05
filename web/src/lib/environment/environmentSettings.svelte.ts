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
	globalAppearanceOverride: () => GlobalAppearance | null;
	setGlobalAppearanceOverride: (appearance: GlobalAppearance) => void;
	resetGlobalAppearanceOverride: () => void;
	timelineAppearanceOverride: () => TimelineAppearance | null;
	setTimelineAppearanceOverride: (appearance: TimelineAppearance) => void;
	resetTimelineAppearanceOverride: () => void;
};

export const [useEnvironmentSettings, provideEnvironmentSettings] =
	createContext<EnvironmentSettingsContext>();

export function createEnvironmentSettingsContext(
	state: EnvironmentSettingsPresenterState
): EnvironmentSettingsContext {
	let globalAppearanceOverride = $state<GlobalAppearance | null>(null);
	let timelineAppearanceOverride = $state<TimelineAppearance | null>(null);

	return {
		state,
		appSettings: () => state.appSettings,
		globalAppearance: () => {
			if (globalAppearanceOverride) {
				return { type: 'Success', data: globalAppearanceOverride };
			}
			return state.globalAppearance;
		},
		timelineAppearance: () => {
			if (timelineAppearanceOverride) {
				return { type: 'Success', data: timelineAppearanceOverride };
			}
			return state.timelineAppearance;
		},
		globalAppearanceOverride: () => globalAppearanceOverride,
		setGlobalAppearanceOverride: (appearance: GlobalAppearance) => {
			globalAppearanceOverride = appearance;
		},
		resetGlobalAppearanceOverride: () => {
			globalAppearanceOverride = null;
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
