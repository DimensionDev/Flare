<script lang="ts">
	import { goto } from '$app/navigation';
	import { createDeepLinkPresenter } from '@flare/web-presenters/deepLink.svelte';
	import { createDeepLinkContext, provideDeepLink } from '$lib/deeplink/deepLink.svelte';

	let { children } = $props();

	const deepLink = createDeepLinkPresenter(handleRoute, openExternalLink);
	provideDeepLink(createDeepLinkContext(deepLink));

	function handleRoute(routeUrl: string): void {
		if (routeUrl.startsWith('/')) {
			const composeRouteEvent = new CustomEvent('flare:compose-route', {
				detail: routeUrl,
				cancelable: true,
			});
			if (!dispatchEvent(composeRouteEvent)) return;

			void goto(routeUrl);
			return;
		}

		console.info('Unhandled deeplink route', routeUrl);
	}

	function openExternalLink(url: string): void {
		const opened = globalThis.open?.(url, '_blank', 'noopener,noreferrer');
		if (!opened && globalThis.location) {
			globalThis.location.href = url;
		}
	}
</script>

{@render children()}
