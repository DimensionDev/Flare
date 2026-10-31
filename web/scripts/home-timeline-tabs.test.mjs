import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { compile } from 'svelte/compiler';
import { render } from 'svelte/server';

let directory;
let HomePage;
let setTabs;

before(async () => {
	directory = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-home-tabs-'));
	const compileComponent = async (source, name) => {
		const { js } = compile(source, { filename: `${name}.svelte`, generate: 'server' });
		const code = js.code
			.replaceAll("'svelte/internal/server'", JSON.stringify(import.meta.resolve('svelte/internal/server')))
			.replaceAll("'svelte'", JSON.stringify(import.meta.resolve('svelte')));
		await fs.writeFile(path.join(directory, `${name}.mjs`), code);
	};

	// Render the real page with presenter data and shell components supplied by the fixture.
	let source = await fs.readFile(new URL('../src/routes/+page.svelte', import.meta.url), 'utf8');
	const imports = {
		'@flare/web-presenters/homeTimelineWithTabs.svelte': './fixture.mjs',
		'$lib/components/AppTopBar.svelte': './TopBar.mjs',
		'$lib/components/FaIcon.svelte': './Empty.mjs',
		'$lib/components/home/HomeTimelineTabPanel.svelte': './Empty.mjs',
		'$lib/i18n/uiStrings': './fixture.mjs',
		'$lib/paraglide/messages.js': './fixture.mjs',
		'$lib/components/UiTimeline/TimelineLoadingPlaceholderList.svelte': './Empty.mjs',
		'$lib/presenter/presenterStore.svelte': './fixture.mjs',
		'$lib/environment/environmentSettings.svelte': './fixture.mjs'
	};
	for (const [original, replacement] of Object.entries(imports)) {
		source = source.replaceAll(`'${original}'`, `'${replacement}'`);
	}
	await compileComponent(source, 'HomePage');
	await compileComponent(
		'<script>let { start, end, bottom } = $props();</script>{@render start?.()}{@render end?.()}{@render bottom?.()}',
		'TopBar'
	);
	await compileComponent('', 'Empty');
	await fs.writeFile(path.join(directory, 'fixture.mjs'), `
		let tabs = [];
		export function setTabs(value) { tabs = value; }
		export function createHomeTimelineWithTabsPresenterController() {
			return { state: { tabState: { type: 'Success', data: tabs } } };
		}
		export function useRetainedPresenter(key, create) { return create().state; }
		export function useEnvironmentSettings() {
			return { timelineAppearance: () => ({ type: 'Loading' }) };
		}
		export function localizedUiText(value) { return value.string; }
		export const m = new Proxy({}, { get: (_, key) => () => key });
	`);
	({ default: HomePage } = await import(pathToFileURL(path.join(directory, 'HomePage.mjs'))));
	({ setTabs } = await import(pathToFileURL(path.join(directory, 'fixture.mjs'))));
});

after(async () => {
	if (directory) await fs.rm(directory, { recursive: true, force: true });
});

for (const type of ['UiSourceTimelineTabItem', 'UiGroupTimelineTabItem']) {
	for (const enabled of [true, false]) {
		test(`${type} stays selectable with mixed inclusion ${enabled}`, () => {
			setTabs([{
				type,
				id: 'target',
				key: 'target',
				title: { type: 'Raw', string: 'Target timeline' },
				icon: { type: 'Material', icon: 'Home' },
				enabled
			}]);
			const { body } = render(HomePage);
			const button = body.match(/<button\b[^>]*role="tab"[^>]*>[\s\S]*?<\/button>/)?.[0];
			assert.ok(button, 'The timeline tab must be rendered');
			assert.match(button, /Target timeline/);
			assert.doesNotMatch(button, /\s(?:disabled|inert)(?:\s|=|>)/);
			assert.doesNotMatch(button, /aria-disabled="true"/);
		});
	}
}
