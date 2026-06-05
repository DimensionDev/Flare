import type {
	TimelineTabItemV2,
} from '@flare/web-presenters/secondaryTabs.svelte';
import { localizedUiString } from '$lib/i18n/uiStrings';

export function timelineTabTitle(tab: TimelineTabItemV2): string {
	if (tab.title.type === 'Raw') return tab.title.string;
	return localizedUiString(tab.title.string);
}
