import adapter from '@sveltejs/adapter-static';

const webSharedLibrary = process.env.FLARE_WEB_SHARED_LIBRARY ?? 'developmentLibrary';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	compilerOptions: {
		// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
		runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
	},
	kit: {
		adapter: adapter({
			pages: 'build',
			assets: 'build',
			fallback: 'index.html'
		}),
		alias: {
			'@flare/web-shared': `../web-shared/build/dist/wasmJs/${webSharedLibrary}`,
			'@flare/web-presenters': '../web-shared/build/generated/web-presenters/ts'
		}
	}
};

export default config;
