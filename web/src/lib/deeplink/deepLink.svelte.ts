import { createContext } from 'svelte';
import type {
	ClickEvent as DeepLinkClickEvent,
	WebDeepLinkPresenterState,
} from '@flare/web-presenters/deepLink.svelte';
import type { ClickEvent } from '@flare/web-presenters/timeline.svelte';

export type DeepLinkContext = {
	state: WebDeepLinkPresenterState;
	handle: (url: string) => void;
	performClickEvent: (clickEvent: ClickEvent) => void;
	canPerformClickEvent: (clickEvent: unknown) => clickEvent is ClickEvent;
};

export const [useDeepLink, provideDeepLink] = createContext<DeepLinkContext>();

export function createDeepLinkContext(state: WebDeepLinkPresenterState): DeepLinkContext {
	return {
		state,
		handle: (url: string) => {
			state.handle(url);
		},
		performClickEvent: (clickEvent: ClickEvent) => {
			if (!canPerformClickEvent(clickEvent)) return;
			state.performClickEvent(clickEvent as unknown as DeepLinkClickEvent);
		},
		canPerformClickEvent,
	};
}

function canPerformClickEvent(clickEvent: unknown): clickEvent is ClickEvent {
	if (clickEvent === null || typeof clickEvent !== 'object') return false;

	const type = (clickEvent as { type?: unknown }).type;
	return type === 'Deeplink';
}
