import { sveltekit } from '@sveltejs/kit/vite';
import { paraglideVitePlugin } from '@inlang/paraglide-js';
import tailwindcss from '@tailwindcss/vite';
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
