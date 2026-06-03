import { browser } from '$app/environment';

export type StorageSnapshot = {
	exportedAt: string;
	origin: string;
	localStorage: Record<string, string>;
};

export type StorageOverview = {
	usageBytes: number | null;
	quotaBytes: number | null;
	indexedDatabaseNames: string[];
	localStorageItemCount: number;
	localStorageBytes: number;
};

export async function getStorageOverview(): Promise<StorageOverview> {
	const estimate = browser && navigator.storage?.estimate ? await navigator.storage.estimate() : {};
	return {
		usageBytes: typeof estimate.usage === 'number' ? estimate.usage : null,
		quotaBytes: typeof estimate.quota === 'number' ? estimate.quota : null,
		indexedDatabaseNames: await listIndexedDatabaseNames(),
		localStorageItemCount: browser ? localStorage.length : 0,
		localStorageBytes: estimateLocalStorageBytes(),
	};
}

export function createStorageSnapshot(): StorageSnapshot {
	const data: Record<string, string> = {};
	if (browser) {
		for (let index = 0; index < localStorage.length; index += 1) {
			const key = localStorage.key(index);
			if (key) data[key] = localStorage.getItem(key) ?? '';
		}
	}
	return {
		exportedAt: new Date().toISOString(),
		origin: browser ? location.origin : '',
		localStorage: data,
	};
}

export function downloadStorageExport(): void {
	downloadJson('flare-web-data.json', createStorageSnapshot());
}

export function importStorageSnapshot(snapshot: StorageSnapshot): number {
	if (!browser || !snapshot.localStorage) return 0;
	let imported = 0;
	for (const [key, value] of Object.entries(snapshot.localStorage)) {
		localStorage.setItem(key, value);
		imported += 1;
	}
	return imported;
}

export async function parseStorageSnapshotFile(file: File): Promise<StorageSnapshot> {
	const text = await file.text();
	const parsed = JSON.parse(text) as Partial<StorageSnapshot>;
	if (!parsed.localStorage || typeof parsed.localStorage !== 'object') {
		throw new Error('Invalid Flare web data export.');
	}
	return {
		exportedAt: parsed.exportedAt ?? new Date().toISOString(),
		origin: parsed.origin ?? '',
		localStorage: parsed.localStorage as Record<string, string>,
	};
}

export function formatBytes(value: number | null): string {
	if (value === null) return '-';
	if (value < 1024) return `${value} B`;
	const units = ['KB', 'MB', 'GB'];
	let amount = value / 1024;
	let unitIndex = 0;
	while (amount >= 1024 && unitIndex < units.length - 1) {
		amount /= 1024;
		unitIndex += 1;
	}
	return `${amount.toFixed(amount >= 10 ? 1 : 2)} ${units[unitIndex]}`;
}

async function listIndexedDatabaseNames(): Promise<string[]> {
	if (!browser || !indexedDB.databases) return [];
	const databases = await indexedDB.databases();
	return databases.map((database) => database.name).filter((name): name is string => Boolean(name));
}

function estimateLocalStorageBytes(): number {
	if (!browser) return 0;
	let bytes = 0;
	for (let index = 0; index < localStorage.length; index += 1) {
		const key = localStorage.key(index);
		if (!key) continue;
		const value = localStorage.getItem(key) ?? '';
		bytes += new Blob([key, value]).size;
	}
	return bytes;
}

function downloadJson(fileName: string, data: unknown): void {
	const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement('a');
	anchor.href = url;
	anchor.download = fileName;
	anchor.click();
	URL.revokeObjectURL(url);
}
