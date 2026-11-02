import type {
	UiTimelineTabItem,
} from '@flare/web-presenters/secondaryTabs.svelte';
import { localizedUiText } from '$lib/i18n/uiStrings';

export function timelineTabTitle(tab: UiTimelineTabItem): string {
	return localizedUiText(tab.title);
}
