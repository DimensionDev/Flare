import { browser } from '$app/environment';
import { onMount, untrack } from 'svelte';

export type RetainedPresenterController<TState> = {
	readonly state: TState;
	mount: () => void;
	close: () => void;
};

export type RetainedPresenterOptions = {
	ttlMs?: number;
};

type RetainedPresenterEntry<TState> = {
	controller: RetainedPresenterController<TState>;
	refCount: number;
	ttlMs: number;
	closeTimer: ReturnType<typeof setTimeout> | null;
};

const defaultTtlMs = 5 * 60 * 1000;
const retainedPresenters = new Map<string, RetainedPresenterEntry<unknown>>();

export function useRetainedPresenter<TState>(
	key: string,
	create: () => RetainedPresenterController<TState>,
	options: RetainedPresenterOptions = {}
): TState {
	if (!browser) {
		return create().state;
	}

	const entry = getOrCreateEntry(key, create, options);

	onMount(() => {
		retainEntry(entry);
		entry.controller.mount();

		return () => {
			releaseEntry(key, entry);
		};
	});

	return entry.controller.state;
}

export function useReactiveRetainedPresenter<TState extends object>(
	key: () => string,
	create: () => RetainedPresenterController<TState>,
	options: RetainedPresenterOptions = {}
): TState {
	if (!browser) {
		return create().state;
	}

	const initialKey = key();
	const initialEntry = getOrCreateEntry(initialKey, create, options);
	const state = $state(clonePresenterState(initialEntry.controller.state)) as TState;
	let activeEntry = initialEntry;
	let activeKey = $state(initialKey);

	$effect(() => {
		const nextKey = key();
		const nextEntry = getOrCreateEntry(nextKey, create, options);
		activeEntry = nextEntry;
		activeKey = nextKey;

		retainEntry(nextEntry);
		nextEntry.controller.mount();

		return () => {
			releaseEntry(nextKey, nextEntry);
		};
	});

	$effect(() => {
		activeKey;
		const nextState = clonePresenterState(activeEntry.controller.state);
		untrack(() => {
			syncPresenterState(state, nextState);
		});
	});

	return state;
}

export function clearRetainedPresenters(match?: string | ((key: string) => boolean)): void {
	for (const [key, entry] of retainedPresenters) {
		if (!shouldClear(key, match)) continue;
		closeEntry(entry);
		retainedPresenters.delete(key);
	}
}

function getOrCreateEntry<TState>(
	key: string,
	create: () => RetainedPresenterController<TState>,
	options: RetainedPresenterOptions
): RetainedPresenterEntry<TState> {
	const existing = retainedPresenters.get(key) as RetainedPresenterEntry<TState> | undefined;
	if (existing) {
		if (options.ttlMs !== undefined) {
			existing.ttlMs = options.ttlMs;
		}
		return existing;
	}

	const entry: RetainedPresenterEntry<TState> = {
		controller: create(),
		refCount: 0,
		ttlMs: options.ttlMs ?? defaultTtlMs,
		closeTimer: null,
	};
	retainedPresenters.set(key, entry as RetainedPresenterEntry<unknown>);
	return entry;
}

function retainEntry(entry: RetainedPresenterEntry<unknown>): void {
	entry.refCount += 1;
	if (entry.closeTimer) {
		clearTimeout(entry.closeTimer);
		entry.closeTimer = null;
	}
}

function releaseEntry(key: string, entry: RetainedPresenterEntry<unknown>): void {
	entry.refCount = Math.max(entry.refCount - 1, 0);
	if (entry.refCount > 0 || entry.ttlMs === Infinity) return;

	if (entry.ttlMs <= 0) {
		closeEntry(entry);
		retainedPresenters.delete(key);
		return;
	}

	entry.closeTimer = setTimeout(() => {
		if (entry.refCount > 0) return;
		closeEntry(entry);
		retainedPresenters.delete(key);
	}, entry.ttlMs);
}

function closeEntry(entry: RetainedPresenterEntry<unknown>): void {
	if (entry.closeTimer) {
		clearTimeout(entry.closeTimer);
		entry.closeTimer = null;
	}
	entry.refCount = 0;
	entry.controller.close();
}

function clonePresenterState<TState extends object>(source: TState): TState {
	return { ...(source as Record<string, unknown>) } as TState;
}

function syncPresenterState<TState extends object>(target: TState, source: TState): void {
	const targetRecord = target as Record<string, unknown>;
	const sourceRecord = source as Record<string, unknown>;

	for (const key of Object.keys(targetRecord)) {
		if (!(key in sourceRecord)) {
			delete targetRecord[key];
		}
	}
	for (const key of Object.keys(sourceRecord)) {
		targetRecord[key] = sourceRecord[key];
	}
}

function shouldClear(key: string, match?: string | ((key: string) => boolean)): boolean {
	if (match === undefined) return true;
	if (typeof match === 'string') return key === match || key.startsWith(match);
	return match(key);
}

if (import.meta.hot) {
	import.meta.hot.dispose(() => {
		clearRetainedPresenters();
	});
}
