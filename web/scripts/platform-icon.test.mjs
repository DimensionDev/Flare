import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { pathToFileURL } from 'node:url';
import { compile } from 'svelte/compiler';
import { render } from 'svelte/server';

let directory;
let PlatformIcon;

before(async () => {
	directory = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-platform-icon-'));
	const component = await fs.readFile(new URL('../src/lib/components/PlatformIcon.svelte', import.meta.url), 'utf8');
	for (const [name, source] of [
		['PlatformIcon', component.replace("'./FaIcon.svelte'", "'./FaIcon.mjs'")],
		['FaIcon', '<script>let { name, size } = $props();</script><span data-icon={name} data-size={size}></span>'],
	]) {
		const { js } = compile(source, { filename: `${name}.svelte`, generate: 'server' });
		const code = js.code.replaceAll("'svelte/internal/server'", JSON.stringify(import.meta.resolve('svelte/internal/server')));
		await fs.writeFile(path.join(directory, `${name}.mjs`), code);
	}
	({ default: PlatformIcon } = await import(pathToFileURL(path.join(directory, 'PlatformIcon.mjs'))));
});

after(async () => {
	if (directory) await fs.rm(directory, { recursive: true, force: true });
});

for (const [icon, expected] of [
	[{ type: 'Material', icon: 'Mastodon' }, 'Mastodon'],
	[{ type: 'Mixed', icon: 'Bluesky', accountKey: null }, 'Bluesky'],
	[{ type: 'Avatar', accountKey: { id: 'reader', host: 'example.invalid' } }, 'Profile'],
]) {
	test(`${icon.type} platform icons use the matching vector glyph`, () => {
		const { body } = render(PlatformIcon, { props: { icon, size: 24 } });
		assert.match(body, new RegExp(`data-icon="${expected}"`));
		assert.match(body, /data-size="24"/);
		assert.doesNotMatch(body, /<img\b/);
	});
}

for (const [icon, expected] of [
	[{ type: 'Url', url: 'https://plugin.invalid/icon.png' }, 'https://plugin.invalid/icon.png'],
	[{ type: 'FavIcon', host: 'social.invalid' }, 'https://icons.duckduckgo.com/ip3/social.invalid.ico'],
]) {
	test(`${icon.type} platform icons render the supplied image source`, () => {
		const { body } = render(PlatformIcon, { props: { icon, size: 24 } });
		assert.ok(body.includes(`src="${expected}"`));
		assert.match(body, /width="24"/);
		assert.match(body, /height="24"/);
		assert.match(body, /alt=""/);
	});
}
