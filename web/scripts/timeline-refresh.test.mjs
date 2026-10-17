import { execFile } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { compile, compileModule, parse } from 'svelte/compiler';

const execFileAsync = promisify(execFile);

test('home timeline reports shared refresh, completion, retry and unmount without a paging refresh', async () => {
	const source = await fs.readFile(
		new URL('../src/lib/components/home/HomeTimelineTabPanel.svelte', import.meta.url), 'utf8'
	);
	// Run the real component script with Svelte reactivity; only presenter I/O and child rendering are excluded.
	const script = parse(source, { modern: true }).instance.content.body
		.filter((node) => node.type !== 'ImportDeclaration')
		.map((node) => source.slice(node.start, node.end)).join('\n');
	const component = compile(
		`<script lang="ts">import { useRetainedPresenter, createTimelineItemPresenterController } from './fixture.mjs';\n${script}</script>`,
		{ filename: 'HomeTimelineTabPanel.svelte', generate: 'client' }
	).js.code;
	const cache = fileURLToPath(new URL('../node_modules/.cache/', import.meta.url));
	await fs.mkdir(cache, { recursive: true });
	const tmp = await fs.mkdtemp(path.join(cache, 'timeline-refresh-'));
	try {
		await fs.writeFile(path.join(tmp, 'component.mjs'), component);
		await fs.writeFile(path.join(tmp, 'fixture.mjs'), compileModule(`
			export const timeline = $state({
				isRefreshing: false, listState: { type: 'Loading', isRefreshing: false },
				refreshSync() { refreshCalls += 1; }
			});
			export let refreshCalls = 0;
			export function createTimelineItemPresenterController() { return { state: timeline }; }
			export function useRetainedPresenter(key, create) { return create().state; }
		`, { filename: 'fixture.svelte.js' }).js.code);
		await fs.writeFile(path.join(tmp, 'run.mjs'), compileModule(`
			import assert from 'node:assert/strict';
			import { flushSync } from 'svelte';
			import Component from './component.mjs';
			import { timeline, refreshCalls } from './fixture.mjs';
			const notifications = [];
			const props = $state({
				tab: { key: 'home', loaderKey: 'home' }, refreshRequestId: 0,
				onRefreshingChange: (value) => notifications.push(value)
			});
			const dispose = $effect.root(() => { Component(null, props); });
			flushSync();
			assert.equal(notifications.at(-1), false, 'initial loading must not report a refresh');
			assert.equal(refreshCalls, 0, 'mount must not request another refresh');

			const cachedPage = { type: 'Success', isRefreshing: false };
			timeline.listState = cachedPage;
			props.refreshRequestId = 1;
			flushSync();
			assert.equal(refreshCalls, 1, 'home refresh request must reach the presenter');
			timeline.isRefreshing = true;
			flushSync();
			assert.equal(timeline.listState.isRefreshing, false);
			assert.equal(notifications.at(-1), true, 'reading refresh must show while paging stays idle');

			// Failure leaves the cached page intact; a subsequent request must work again.
			timeline.isRefreshing = false;
			flushSync();
			assert.equal(notifications.at(-1), false, 'failed refresh must clear the indicator');
			props.refreshRequestId = 2;
			flushSync();
			assert.equal(refreshCalls, 2, 'refresh after failure must be dispatched');
			timeline.isRefreshing = true;
			flushSync();
			assert.equal(notifications.at(-1), true, 'retry must show the indicator again');
			timeline.isRefreshing = false;
			flushSync();
			assert.equal(notifications.at(-1), false, 'completion must clear the indicator');

			// A complete refresh in one reactive batch must not leave a stuck indicator.
			timeline.isRefreshing = true;
			timeline.isRefreshing = false;
			flushSync();
			assert.equal(notifications.at(-1), false);
			props.refreshRequestId = 2;
			flushSync();
			assert.equal(refreshCalls, 2, 'the same request must not run twice');

			timeline.listState.isRefreshing = true;
			timeline.isRefreshing = true;
			flushSync();
			assert.equal(notifications.at(-1), true, 'ordinary paging refresh must still show');
			timeline.listState.isRefreshing = false;
			timeline.isRefreshing = false;
			flushSync();
			assert.equal(notifications.at(-1), false);

			timeline.isRefreshing = true;
			flushSync();
			dispose();
			assert.equal(notifications.at(-1), false, 'unmount must clear the parent indicator');
			const count = notifications.length;
			timeline.isRefreshing = false;
			flushSync();
			assert.equal(notifications.length, count, 'unmounted panels must stop notifying');
		`, { filename: 'run.svelte.js' }).js.code);
		await execFileAsync(process.execPath, ['--conditions=browser', path.join(tmp, 'run.mjs')]);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});
