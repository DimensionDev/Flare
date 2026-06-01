import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

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

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
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
