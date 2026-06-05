import { sveltekit } from '@sveltejs/kit/vite';
import { paraglideVitePlugin } from '@inlang/paraglide-js';
import tailwindcss from '@tailwindcss/vite';
import fs from 'node:fs';
import { defineConfig } from 'vite';
import type { Plugin, PreviewServer, ViteDevServer } from 'vite';

const workspaceRoot = decodeURI(new URL('..', import.meta.url).pathname);
const sqliteWebWorker = decodeURI(
	new URL('../shared/sqlite-web-worker/worker.js', import.meta.url).pathname
);
const sqliteWasm = decodeURI(
	new URL('./node_modules/@sqlite.org/sqlite-wasm/index.mjs', import.meta.url).pathname
);
const jsJoda = decodeURI(
	new URL('./node_modules/@js-joda/core/dist/js-joda.esm.js', import.meta.url).pathname
);
const wsBrowser = decodeURI(new URL('./node_modules/ws/browser.js', import.meta.url).pathname);
const crossOriginIsolationHeaders = {
	'Cross-Origin-Opener-Policy': 'same-origin',
	'Cross-Origin-Embedder-Policy': 'credentialless'
};
const fdroidPropertiesPath = new URL('../fdroid.properties', import.meta.url);

function readFdroidVersion(): string | undefined {
	const text = fs.readFileSync(fdroidPropertiesPath, 'utf8');
	const properties = Object.fromEntries(
		text
			.split(/\r?\n/)
			.map((line) => line.trim())
			.filter((line) => line && !line.startsWith('#'))
			.map((line) => {
				const separatorIndex = line.indexOf('=');
				if (separatorIndex === -1) return [line, ''];
				return [line.slice(0, separatorIndex).trim(), line.slice(separatorIndex + 1).trim()];
			})
	);
	const versionName = properties.versionName;
	const versionCode = properties.versionCode;
	if (!versionName || !versionCode) return undefined;
	return `${versionName}(${versionCode})`;
}

function applyCrossOriginIsolationHeaders(server: ViteDevServer | PreviewServer): void {
	server.middlewares.use((_request, response, next) => {
		for (const [name, value] of Object.entries(crossOriginIsolationHeaders)) {
			response.setHeader(name, value);
		}
		next();
	});
}

function crossOriginIsolation(): Plugin {
	return {
		name: 'flare-cross-origin-isolation',
		configureServer: applyCrossOriginIsolationHeaders,
		configurePreviewServer: applyCrossOriginIsolationHeaders
	};
}

export default defineConfig({
	define: {
		__FLARE_APP_VERSION__: JSON.stringify(readFdroidVersion())
	},
	plugins: [
		crossOriginIsolation(),
		tailwindcss(),
		paraglideVitePlugin({
			project: './project.inlang',
			outdir: './src/lib/paraglide',
			strategy: ['localStorage', 'preferredLanguage', 'baseLocale']
		}),
		sveltekit()
	],
	resolve: {
		alias: {
			'@androidx/sqlite-web-worker/worker.js': sqliteWebWorker,
			'@js-joda/core': jsJoda,
			'@sqlite.org/sqlite-wasm': sqliteWasm,
			ws: wsBrowser
		}
	},
	optimizeDeps: {
		exclude: ['@sqlite.org/sqlite-wasm']
	},
	server: {
		fs: {
			allow: [workspaceRoot]
		}
	}
});
