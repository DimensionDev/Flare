<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import EmojiPicker from '$lib/components/emoji/EmojiPicker.svelte';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import UiTimelinePost from '$lib/components/UiTimeline/Post.svelte';
	import UserDisplay from '$lib/components/user/UserDisplay.svelte';
	import { m } from '$lib/paraglide/messages.js';
	import { useReactiveRetainedPresenter } from '$lib/presenter/presenterStore.svelte';
	import { tick } from 'svelte';
	import {
		createComposePresenterController,
		type AccountType,
		type ComposeStatus,
		type MicroBlogKey,
		type UiEmoji,
		type UiProfile,
		type UiTimelineV2,
		type UiTimelineV2PostVisibility,
	} from '@flare/web-presenters/compose.svelte';
	import type { UiTimelineV2Post } from '@flare/web-presenters/timeline.svelte';
	import type { UiProfile as TimelineUiProfile } from '@flare/web-presenters/timeline.svelte';

	type SelectedMedia = {
		file: File;
		previewUrl: string;
		altText: string;
	};

	type MediaCompressionLimit = {
		maxSize: number;
		maxWidth: number;
		maxHeight: number;
	};

	const defaultMediaLimit: MediaCompressionLimit = {
		maxSize: 1 * 1024 * 1024,
		maxWidth: 2000,
		maxHeight: 2000,
	};

	let {
		onClose,
		routeUrl = null,
	}: {
		onClose?: () => void;
		routeUrl?: string | null;
	} = $props();

	const composeRouteUrl = $derived(routeUrl ? new URL(routeUrl, page.url.origin) : page.url);
	const routeAccount = $derived(composeRouteUrl.searchParams.get('account'));
	const routeStatus = $derived(composeRouteUrl.searchParams.get('status'));
	const routeComposeType = $derived(composeRouteUrl.searchParams.get('type'));
	const routeRootId = $derived(composeRouteUrl.searchParams.get('rootId'));
	const composeAccountType = $derived(routeAccount ? specificAccountType(parseMicroBlogKey(routeAccount)) : null);
	const composeStatus = $derived(parseComposeStatus(routeComposeType, routeStatus));
	const composePresenterKey = $derived(
		`compose:${routeAccount ?? ''}:${routeComposeType ?? ''}:${routeStatus ?? ''}:${routeRootId ?? ''}`
	);
	const compose = useReactiveRetainedPresenter(
		() => composePresenterKey,
		() => createComposePresenterController(composeAccountType, composeStatus, null),
		{ ttlMs: 0 }
	);

	let text = $state('');
	let visibilityOverride = $state<UiTimelineV2PostVisibility | null>(null);
	let contentWarningEnabled = $state(false);
	let spoilerText = $state('');
	let selectedMedia = $state<SelectedMedia[]>([]);
	let pollEnabled = $state(false);
	let pollOptions = $state(['', '']);
	let pollSingleChoice = $state(true);
	let pollExpiredAfter = $state(5 * 60 * 1000);
	let selectedLanguage = $state('en');
	let successHandled = $state(false);
	let showCloseConfirmDialog = $state(false);
	let mediaPreparing = $state(false);
	let mediaError = $state<string | null>(null);
	let accountMenuAnchorElement = $state<HTMLElement | null>(null);
	let accountPopoverElement = $state<HTMLDivElement | null>(null);
	let accountPopoverLeft = $state(0);
	let accountPopoverTop = $state(0);
	let accountPopoverWidth = $state(288);
	let accountPopoverMaxHeight = $state(260);
	let mediaInputElement = $state<HTMLInputElement | null>(null);
	let textAreaElement = $state<HTMLTextAreaElement | null>(null);
	let emojiButtonElement = $state<HTMLButtonElement | null>(null);
	let emojiPopoverElement = $state<HTMLDivElement | null>(null);
	let emojiPopoverLeft = $state(0);
	let emojiPopoverTop = $state(0);
	let emojiPopoverWidth = $state(384);
	let emojiPopoverMaxHeight = $state(320);
	let languageButtonElement = $state<HTMLButtonElement | null>(null);
	let languagePopoverElement = $state<HTMLDivElement | null>(null);
	let languagePopoverLeft = $state(0);
	let languagePopoverTop = $state(0);
	let languagePopoverWidth = $state(240);
	let languagePopoverMaxHeight = $state(220);

	const remainingLength = $derived(compose.remainingLength);
	const selectedProfiles = $derived(successProfiles(compose.selectedUsers));
	const otherProfiles = $derived(successProfiles(compose.otherUsers));
	const accountProfiles = $derived([...selectedProfiles, ...otherProfiles]);
	const sending = $derived(compose.directSendState.phase === 'Sending');
	const composeBusy = $derived(sending || mediaPreparing);
	const canSubmit = $derived(compose.canSend && !composeBusy);
	const visiblePollOptions = $derived(pollOptions);
	const currentVisibility = $derived(visibilityOverride ?? compose.visibility);
	const maxMediaCount = $derived(compose.mediaMaxCount > 0 ? compose.mediaMaxCount : 4);
	const selectedMediaLimit = $derived(mediaCompressionLimit(selectedProfiles));
	const pollAllowed = $derived(compose.pollMaxOptions !== null);
	const canPoll = $derived(pollAllowed && selectedMedia.length === 0);
	const canAddMedia = $derived(
		compose.mediaEnabled && selectedMedia.length < maxMediaCount && !pollEnabled
	);
	const maxPollOptions = $derived(compose.pollMaxOptions ?? 4);
	const hasTextContent = $derived(
		text.trim().length > 0 || spoilerText.trim().length > 0 || selectedMedia.length > 0 || pollEnabled
	);
	const allVisibilities = $derived<UiTimelineV2PostVisibility[]>(
		compose.allVisibilities.length > 0
			? compose.allVisibilities
			: ['Public', 'Home', 'Followers', 'Specified']
	);
	const emojiEnabled = $derived(compose.emojis.length > 0);
	const postReference = $derived(referencePost(compose.referencePost));

	$effect(() => {
		compose.setText(text);
		compose.setMediaSize(selectedMedia.length);
	});

	$effect(() => {
		if (compose.languageCodes.length > 0 && !compose.languageCodes.includes(selectedLanguage)) {
			selectedLanguage = compose.languageCodes[0];
		}
	});

	$effect(() => {
		if (compose.directSendState.phase !== 'Success' || successHandled) return;
		successHandled = true;
		text = '';
		contentWarningEnabled = false;
		spoilerText = '';
		clearMedia();
		pollEnabled = false;
		pollOptions = ['', ''];
		pollSingleChoice = true;
		pollExpiredAfter = 5 * 60 * 1000;
		navigateAway();
	});

	function successProfiles(
		state: typeof compose.selectedUsers | typeof compose.otherUsers
	): UiProfile[] {
		if (state.type !== 'Success') return [];
		return state.data.flatMap((item) => (item.type === 'Success' ? [item.data] : []));
	}

	function displayName(profile: UiProfile): string {
		return profile.name.innerText || profile.handle.canonical || profile.handle.raw;
	}

	function handleText(profile: UiProfile): string {
		return profile.handle.canonical || profile.handle.raw || profile.handleWithoutAtAndHost;
	}

	function initials(profile: UiProfile): string {
		return Array.from(displayName(profile).trim() || handleText(profile).trim() || '?')
			.slice(0, 2)
			.join('')
			.toUpperCase();
	}

	function profileKey(profile: UiProfile): string {
		return `${profile.key.id}:${profile.key.host}`;
	}

	function platformIcon(platformType: UiProfile['platformType']): string {
		switch (platformType) {
			case 'xQt':
				return 'World';
			case 'VVo':
				return 'World';
			default:
				return platformType;
		}
	}

	function platformMediaLimit(platformType: UiProfile['platformType']): MediaCompressionLimit {
		switch (platformType) {
			case 'Mastodon':
				return { maxSize: 16 * 1024 * 1024, maxWidth: 2880, maxHeight: 2880 };
			case 'Misskey':
				return { maxSize: 200 * 1024 * 1024, maxWidth: 8192, maxHeight: 8192 };
			case 'Bluesky':
				return { maxSize: 1 * 1024 * 1024, maxWidth: 2000, maxHeight: 2000 };
			case 'xQt':
			case 'VVo':
				return { maxSize: 5 * 1024 * 1024, maxWidth: 4096, maxHeight: 4096 };
			default:
				return defaultMediaLimit;
		}
	}

	function mediaCompressionLimit(profiles: UiProfile[]): MediaCompressionLimit {
		const limits = profiles.map((profile) => platformMediaLimit(profile.platformType));
		if (limits.length === 0) return defaultMediaLimit;
		return limits.reduce(
			(limit, item) => ({
				maxSize: Math.min(limit.maxSize, item.maxSize),
				maxWidth: Math.min(limit.maxWidth, item.maxWidth),
				maxHeight: Math.min(limit.maxHeight, item.maxHeight),
			})
		);
	}

	function isProfileSelected(profile: UiProfile): boolean {
		const key = profileKey(profile);
		return selectedProfiles.some((selectedProfile) => profileKey(selectedProfile) === key);
	}

	async function toggleAccountPopover(event: MouseEvent): Promise<void> {
		if (!accountPopoverElement) return;
		if (accountPopoverElement.matches(':popover-open')) {
			accountPopoverElement.hidePopover();
			return;
		}

		accountMenuAnchorElement = event.currentTarget as HTMLElement;
		const anchorRect = accountMenuAnchorElement.getBoundingClientRect();
		const width = Math.min(288, window.innerWidth - 16);
		const estimatedHeight = Math.min(
			260,
			accountProfiles.length * 48 + 16,
			window.innerHeight - 16
		);
		const left = Math.min(Math.max(8, anchorRect.left), window.innerWidth - width - 8);
		const top = Math.min(anchorRect.bottom + 8, window.innerHeight - estimatedHeight - 8);

		accountPopoverWidth = width;
		accountPopoverMaxHeight = estimatedHeight;
		accountPopoverLeft = left;
		accountPopoverTop = Math.max(8, top);
		await tick();
		accountPopoverElement.showPopover();
	}

	function selectAccount(key: MicroBlogKey): void {
		compose.selectAccount(key);
	}

	function setVisibility(value: UiTimelineV2PostVisibility): void {
		visibilityOverride = value;
	}

	function specificAccountType(accountKey: MicroBlogKey): AccountType {
		return { type: 'Specific', accountKey };
	}

	function parseComposeStatus(type: string | null, status: string | null): ComposeStatus | null {
		if (!type || !status) return null;
		const statusKey = parseMicroBlogKey(status);
		switch (type) {
			case 'quote':
				return { type: 'Quote', statusKey };
			case 'reply':
				return { type: 'Reply', statusKey };
			default:
				return null;
		}
	}

	function parseMicroBlogKey(value: string): MicroBlogKey {
		let escaping = false;
		let idFinished = false;
		let id = '';
		let host = '';

		for (const char of value) {
			if (escaping) {
				if (idFinished) host += char;
				else id += char;
				escaping = false;
				continue;
			}

			if (char === '\\') {
				escaping = true;
				continue;
			}

			if (char === '@' && !idFinished) {
				idFinished = true;
				continue;
			}

			if (char === ',') break;

			if (idFinished) host += char;
			else id += char;
		}

		return { id, host };
	}

	function referencePost(value: UiTimelineV2 | null): UiTimelineV2Post | null {
		return value?.type === 'Post' ? (value as unknown as UiTimelineV2Post) : null;
	}

	async function selectEmoji(emoji: UiEmoji): Promise<void> {
		if (composeBusy) return;
		const insertion = emoji.insertText;
		const start = textAreaElement?.selectionStart ?? text.length;
		const end = textAreaElement?.selectionEnd ?? start;
		text = `${text.slice(0, start)}${insertion}${text.slice(end)}`;
		await tick();
		textAreaElement?.focus();
		const cursor = start + insertion.length;
		textAreaElement?.setSelectionRange(cursor, cursor);
	}

	async function toggleEmojiPopover(): Promise<void> {
		if (!emojiButtonElement || !emojiPopoverElement || composeBusy) return;
		if (emojiPopoverElement.matches(':popover-open')) {
			emojiPopoverElement.hidePopover();
			return;
		}

		const buttonRect = emojiButtonElement.getBoundingClientRect();
		const width = Math.min(384, window.innerWidth - 16);
		const estimatedHeight = Math.min(320, window.innerHeight - 16);
		const left = Math.min(Math.max(8, buttonRect.left), window.innerWidth - width - 8);
		const hasRoomAbove = buttonRect.top >= estimatedHeight + 12;
		const top = hasRoomAbove
			? buttonRect.top - estimatedHeight - 8
			: Math.min(buttonRect.bottom + 8, window.innerHeight - estimatedHeight - 8);

		emojiPopoverWidth = width;
		emojiPopoverMaxHeight = estimatedHeight;
		emojiPopoverLeft = left;
		emojiPopoverTop = Math.max(8, top);
		await tick();
		emojiPopoverElement.showPopover();
	}

	function addPollOption(): void {
		if (pollOptions.length >= maxPollOptions) return;
		pollOptions = [...pollOptions, ''];
	}

	function removePollOption(index: number): void {
		if (pollOptions.length <= 2) return;
		pollOptions = pollOptions.filter((_, itemIndex) => itemIndex !== index);
	}

	function updatePollOption(index: number, value: string): void {
		pollOptions = pollOptions.map((item, itemIndex) => (itemIndex === index ? value : item));
	}

	function togglePoll(): void {
		if (!canPoll || composeBusy) return;
		pollEnabled = !pollEnabled;
		if (!pollEnabled) {
			pollOptions = ['', ''];
			pollSingleChoice = true;
			pollExpiredAfter = 5 * 60 * 1000;
		}
	}

	function toggleContentWarning(): void {
		contentWarningEnabled = !contentWarningEnabled;
		if (!contentWarningEnabled) {
			spoilerText = '';
		}
	}

	function openMediaPicker(): void {
		if (!canAddMedia || composeBusy) return;
		mediaInputElement?.click();
	}

	function addMedia(files: FileList | null): void {
		if (!files || !canAddMedia || composeBusy) return;
		mediaError = null;
		const remainingSlots = Math.max(0, maxMediaCount - selectedMedia.length);
		const nextMedia = Array.from(files)
			.filter((file) => file.type.startsWith('image/'))
			.slice(0, remainingSlots)
			.map((file) => ({
				file,
				previewUrl: URL.createObjectURL(file),
				altText: '',
			}));
		selectedMedia = [...selectedMedia, ...nextMedia];
		if (mediaInputElement) {
			mediaInputElement.value = '';
		}
	}

	function removeMedia(index: number): void {
		const item = selectedMedia[index];
		if (item) {
			URL.revokeObjectURL(item.previewUrl);
		}
		selectedMedia = selectedMedia.filter((_, itemIndex) => itemIndex !== index);
	}

	function clearMedia(): void {
		for (const item of selectedMedia) {
			URL.revokeObjectURL(item.previewUrl);
		}
		selectedMedia = [];
	}

	function updateMediaAltText(index: number, value: string): void {
		selectedMedia = selectedMedia.map((item, itemIndex) =>
			itemIndex === index ? { ...item, altText: value } : item
		);
	}

	function closeDialog(): void {
		if (hasTextContent) {
			showCloseConfirmDialog = true;
			return;
		}
		navigateAway();
	}

	function discardAndClose(): void {
		showCloseConfirmDialog = false;
		clearMedia();
		navigateAway();
	}

	function navigateAway(): void {
		if (onClose) {
			onClose();
			return;
		}
		void goto('/');
	}

	async function submit(): Promise<void> {
		if (!canSubmit) return;
		successHandled = false;
		mediaError = null;
		mediaPreparing = selectedMedia.length > 0;
		compose.clearDirectSendState();
		try {
			const mediaItemsJson = JSON.stringify(
				await Promise.all(selectedMedia.map((item) => mediaPayload(item, selectedMediaLimit)))
			);
			compose.sendDirect(
				text,
				[],
				currentVisibility,
				selectedLanguage,
				false,
				contentWarningEnabled ? spoilerText.trim() || null : null,
				false,
				pollEnabled ? visiblePollOptions.map((item) => item.trim()).filter(Boolean).join('\n') : '',
				pollExpiredAfter,
				!pollSingleChoice,
				mediaItemsJson
			);
		} catch (error) {
			mediaError = error instanceof Error ? error.message : m.composeUnableToPrepareMedia();
		} finally {
			mediaPreparing = false;
		}
	}

	async function mediaPayload(item: SelectedMedia, limit: MediaCompressionLimit): Promise<{
		name: string;
		mimeType: string;
		type: 'Image';
		bytesBase64: string;
		altText: string | null;
	}> {
		const blob = await compressImage(item.file, limit);
		return {
			name: jpegName(item.file.name),
			mimeType: blob.type || 'image/jpeg',
			type: 'Image',
			bytesBase64: await blobToBase64(blob),
			altText: item.altText.trim() || null,
		};
	}

	async function compressImage(file: File, limit: MediaCompressionLimit): Promise<Blob> {
		const image = await createImageBitmap(file);
		try {
			let canvas = drawImageToCanvas(image, limit);
			for (let attempt = 0; attempt < 5; attempt += 1) {
				const compressed = await compressCanvas(canvas, limit.maxSize);
				if (compressed.size <= limit.maxSize) {
					return compressed;
				}

				const nextWidth = Math.round(canvas.width * 0.8);
				const nextHeight = Math.round(canvas.height * 0.8);
				if (nextWidth < 50 || nextHeight < 50) {
					return compressed;
				}
				canvas = resizeCanvas(canvas, nextWidth, nextHeight);
			}
			return canvasToBlob(canvas, 0.05);
		} finally {
			image.close();
		}
	}

	function drawImageToCanvas(image: ImageBitmap, limit: MediaCompressionLimit): HTMLCanvasElement {
		const scale = Math.min(1, limit.maxWidth / image.width, limit.maxHeight / image.height);
		const width = Math.max(1, Math.round(image.width * scale));
		const height = Math.max(1, Math.round(image.height * scale));
		const canvas = createCanvas(width, height);
		canvas.getContext('2d')?.drawImage(image, 0, 0, width, height);
		return canvas;
	}

	function resizeCanvas(source: HTMLCanvasElement, width: number, height: number): HTMLCanvasElement {
		const canvas = createCanvas(width, height);
		canvas.getContext('2d')?.drawImage(source, 0, 0, width, height);
		return canvas;
	}

	function createCanvas(width: number, height: number): HTMLCanvasElement {
		const canvas = document.createElement('canvas');
		canvas.width = width;
		canvas.height = height;
		return canvas;
	}

	async function compressCanvas(canvas: HTMLCanvasElement, maxSize: number): Promise<Blob> {
		let minQuality = 0;
		let maxQuality = 100;
		let bestBlob: Blob | null = null;
		let smallestBlob: Blob | null = null;

		while (minQuality <= maxQuality) {
			const quality = Math.floor((minQuality + maxQuality) / 2);
			const blob = await canvasToBlob(canvas, quality / 100);
			if (!smallestBlob || blob.size < smallestBlob.size) {
				smallestBlob = blob;
			}

			if (blob.size <= maxSize) {
				bestBlob = blob;
				minQuality = quality + 1;
			} else {
				maxQuality = quality - 1;
			}
		}

		return bestBlob ?? smallestBlob ?? canvasToBlob(canvas, 0.05);
	}

	function canvasToBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
		return new Promise((resolve, reject) => {
			canvas.toBlob(
				(blob) => {
					if (blob) {
						resolve(blob);
					} else {
						reject(new Error(m.composeUnableToCompressMedia()));
					}
				},
				'image/jpeg',
				quality
			);
		});
	}

	function blobToBase64(blob: Blob): Promise<string> {
		return new Promise((resolve, reject) => {
			const reader = new FileReader();
			reader.onload = () => {
				const result = typeof reader.result === 'string' ? reader.result : '';
				resolve(result.includes(',') ? result.slice(result.indexOf(',') + 1) : result);
			};
			reader.onerror = () => reject(reader.error ?? new Error(m.composeUnableToReadMedia()));
			reader.readAsDataURL(blob);
		});
	}

	function jpegName(name: string): string {
		const baseName = name.replace(/\.[^/.]+$/, '');
		return `${baseName || 'image'}.jpg`;
	}

	function visibilityTitle(item: UiTimelineV2PostVisibility): string {
		switch (item) {
			case 'Public':
			case 'Channel':
				return m.composeVisibilityPublic();
			case 'Home':
				return m.composeVisibilityHome();
			case 'Followers':
				return m.composeVisibilityFollowers();
			case 'Specified':
				return m.composeVisibilitySpecified();
		}
	}

	function visibilityDescription(item: UiTimelineV2PostVisibility): string {
		switch (item) {
			case 'Public':
			case 'Channel':
				return m.composeVisibilityPublicDescription();
			case 'Home':
				return m.composeVisibilityHomeDescription();
			case 'Followers':
				return m.composeVisibilityFollowersDescription();
			case 'Specified':
				return m.composeVisibilitySpecifiedDescription();
		}
	}

	function visibilityIcon(item: UiTimelineV2PostVisibility): string {
		switch (item) {
			case 'Public':
			case 'Channel':
				return 'World';
			case 'Home':
				return 'Home';
			case 'Followers':
				return 'Lock';
			case 'Specified':
				return 'Mention';
		}
	}

	function languageName(code: string): string {
		return new Intl.DisplayNames([navigator.language || 'en'], { type: 'language' }).of(code) ?? code;
	}

	async function toggleLanguagePopover(): Promise<void> {
		if (!languageButtonElement || !languagePopoverElement) return;
		if (languagePopoverElement.matches(':popover-open')) {
			languagePopoverElement.hidePopover();
			return;
		}

		const buttonRect = languageButtonElement.getBoundingClientRect();
		const width = Math.min(172, window.innerWidth - 16);
		const estimatedHeight = Math.min(220, compose.languageCodes.length * 36 + 16, window.innerHeight - 16);
		const left = Math.min(Math.max(8, buttonRect.right - width), window.innerWidth - width - 8);
		const hasRoomAbove = buttonRect.top >= estimatedHeight + 12;
		const top = hasRoomAbove
			? buttonRect.top - estimatedHeight - 8
			: Math.min(buttonRect.bottom + 8, window.innerHeight - estimatedHeight - 8);

		languagePopoverWidth = width;
		languagePopoverMaxHeight = estimatedHeight;
		languagePopoverLeft = left;
		languagePopoverTop = Math.max(8, top);
		await tick();
		languagePopoverElement.showPopover();
	}

	function selectLanguage(code: string): void {
		selectedLanguage = code;
		languagePopoverElement?.hidePopover();
	}
</script>

<div class="modal modal-open compose-modal" role="presentation">
	<div class="modal-box compose-dialog">
		<div class="compose-app-bar">
			<button class="btn btn-ghost btn-square btn-sm" type="button" disabled={composeBusy} aria-label={m.close()} onclick={closeDialog}>
				<FaIcon name="Close" size={16} />
			</button>
			{#if accountProfiles.length > 0}
				<div class="title-account-selector">
					{#if selectedProfiles.length > 0}
						<button class="account-avatar-list" type="button" disabled={composeBusy} aria-label={m.composeSelectAccounts()} onclick={toggleAccountPopover}>
							{#each selectedProfiles as profile (profileKey(profile))}
								<span class="account-avatar-button">
									<span class="account-avatar">
										{#if profile.avatar?.url}
											<img src={profile.avatar?.url} alt="" />
										{:else}
											<span>{initials(profile)}</span>
										{/if}
									</span>
									<span class="platform-badge bg-base-100 text-base-content">
										<FaIcon name={platformIcon(profile.platformType)} size={10} />
									</span>
								</span>
							{/each}
						</button>
					{/if}
					{#if otherProfiles.length > 0}
						<button
							class="btn btn-ghost btn-square btn-sm account-plus-button"
							type="button"
							disabled={composeBusy}
							aria-label={m.composeAddAccount()}
							onclick={toggleAccountPopover}
						>
							<FaIcon name="Plus" size={13} />
						</button>
					{/if}
				</div>
			{/if}
		</div>

		{#if compose.directSendState.phase === 'Error'}
			<div class="alert alert-error">
				<FaIcon name="CircleExclamation" size={16} />
				<span>{compose.directSendState.errorMessage ?? m.composeUnableToPublish()}</span>
			</div>
		{:else if mediaError}
			<div class="alert alert-error">
				<FaIcon name="CircleExclamation" size={16} />
				<span>{mediaError}</span>
			</div>
		{/if}

		{#if compose.contentWarningEnabled && contentWarningEnabled}
			<input
				class="input compose-inline-input"
				bind:value={spoilerText}
				disabled={composeBusy}
				autocapitalize="sentences"
				inputmode="text"
				placeholder={m.composeContentWarningHint()}
			/>
			<div class="compose-divider"></div>
		{/if}

		<textarea
			bind:this={textAreaElement}
			class="textarea compose-textarea"
			bind:value={text}
			disabled={composeBusy}
			autocapitalize="sentences"
			inputmode="text"
			placeholder={m.composeHint()}
		></textarea>

		{#if postReference}
			<div class="compose-reference-post">
				<UiTimelinePost
					post={postReference}
					isQuote={true}
					forceHideActions={true}
					showParents={false}
					showMedia={false}
				/>
			</div>
		{/if}

		{#if selectedMedia.length > 0}
			<div class="media-preview-list">
				{#each selectedMedia as item, index}
					<div class="media-preview-item">
						<img src={item.previewUrl} alt="" />
						<button
							class="btn btn-ghost btn-square btn-xs media-remove-button"
							type="button"
							disabled={composeBusy}
							aria-label={m.composeRemoveMedia()}
							onclick={() => removeMedia(index)}
						>
							<FaIcon name="Close" size={12} />
						</button>
						<input
							class="input input-sm media-alt-input"
							value={item.altText}
							disabled={composeBusy}
							autocapitalize="sentences"
							inputmode="text"
							placeholder={m.composeAltText()}
							oninput={(event) => updateMediaAltText(index, event.currentTarget.value)}
						/>
					</div>
				{/each}
			</div>
		{/if}

		{#if pollEnabled}
			<div class="poll-box">
				<div class="poll-header">
					<div class="join">
						<button
							class:btn-primary={pollSingleChoice}
							class="btn btn-sm join-item"
							type="button"
							disabled={composeBusy}
							onclick={() => (pollSingleChoice = true)}
						>
							{m.composePollSingleChoice()}
						</button>
						<button
							class:btn-primary={!pollSingleChoice}
							class="btn btn-sm join-item"
							type="button"
							disabled={composeBusy}
							onclick={() => (pollSingleChoice = false)}
						>
							{m.composePollMultipleChoice()}
						</button>
					</div>
					<button
						class="btn btn-ghost btn-square btn-sm"
						type="button"
						disabled={composeBusy || pollOptions.length >= maxPollOptions}
						aria-label={m.composeAddPollOption()}
						onclick={addPollOption}
					>
						<FaIcon name="Plus" size={13} />
					</button>
				</div>
				{#each visiblePollOptions as option, index}
					<div class="join w-full">
						<input
							class="input input-bordered join-item min-w-0 flex-1"
							value={option}
							disabled={composeBusy}
							autocapitalize="sentences"
							inputmode="text"
							placeholder={m.composePollOptionHint({ option: index + 1 })}
							oninput={(event) => updatePollOption(index, event.currentTarget.value)}
						/>
						<button
							class="btn join-item"
							type="button"
							disabled={composeBusy || pollOptions.length <= 2}
							aria-label={m.composeRemovePollOption()}
							onclick={() => removePollOption(index)}
						>
							<FaIcon name="Close" size={13} />
						</button>
					</div>
				{/each}
				<div class="poll-actions">
					<select class="select select-bordered select-sm" bind:value={pollExpiredAfter} disabled={composeBusy}>
						<option value={5 * 60 * 1000}>{m.composePollExpiration5Minutes()}</option>
						<option value={30 * 60 * 1000}>{m.composePollExpiration30Minutes()}</option>
						<option value={60 * 60 * 1000}>{m.composePollExpiration1Hour()}</option>
						<option value={6 * 60 * 60 * 1000}>{m.composePollExpiration6Hours()}</option>
						<option value={12 * 60 * 60 * 1000}>{m.composePollExpiration12Hours()}</option>
						<option value={24 * 60 * 60 * 1000}>{m.composePollExpiration1Day()}</option>
						<option value={3 * 24 * 60 * 60 * 1000}>{m.composePollExpiration3Days()}</option>
						<option value={7 * 24 * 60 * 60 * 1000}>{m.composePollExpiration7Days()}</option>
					</select>
				</div>
			</div>
		{/if}

		{#if sending}
			<progress
				class="progress progress-primary"
				value={compose.directSendState.current}
				max={Math.max(compose.directSendState.max, 1)}
			></progress>
		{/if}

		<div class="compose-toolbar">
			<input
				bind:this={mediaInputElement}
				class="media-file-input"
				type="file"
				accept="image/*"
				multiple
				disabled={composeBusy}
				onchange={(event) => addMedia(event.currentTarget.files)}
			/>
			{#if compose.mediaEnabled}
				<button
					class="btn btn-ghost btn-square btn-sm"
					type="button"
					disabled={composeBusy || !canAddMedia}
					aria-label={m.composeAddMedia()}
					onclick={openMediaPicker}
				>
					<FaIcon name="Image" size={16} />
				</button>
			{/if}
			{#if pollAllowed}
				<button
					class:btn-active={pollEnabled}
					class="btn btn-ghost btn-square btn-sm"
					type="button"
					disabled={composeBusy || !canPoll}
					aria-label={m.composePoll()}
					onclick={togglePoll}
				>
					<FaIcon name="Poll" size={16} />
				</button>
			{/if}
			<div class="dropdown dropdown-top">
				<button class="btn btn-ghost btn-square btn-sm" type="button" disabled={composeBusy} tabindex="0" aria-label={m.composeVisibility()}>
					{#key currentVisibility}
						<FaIcon name={visibilityIcon(currentVisibility)} size={16} />
					{/key}
				</button>
				<ul class="menu dropdown-content z-30 mb-2 w-72 rounded-box bg-base-100 p-2 shadow">
					{#each allVisibilities as item}
						<li>
							<button type="button" disabled={composeBusy} onclick={() => setVisibility(item)}>
								<FaIcon name={visibilityIcon(item)} size={16} />
								<span>
									<strong>{visibilityTitle(item)}</strong>
									<small>{visibilityDescription(item)}</small>
								</span>
								<span class="visibility-menu-check" aria-hidden="true">
									{#if currentVisibility === item}
										<FaIcon name="Check" size={14} />
									{/if}
								</span>
							</button>
						</li>
					{/each}
				</ul>
			</div>
			{#if compose.contentWarningEnabled}
				<button
					class:btn-active={contentWarningEnabled}
					class="btn btn-ghost btn-square btn-sm"
					type="button"
					disabled={composeBusy}
					aria-label={m.composeContentWarningHint()}
					onclick={toggleContentWarning}
				>
					<FaIcon name="CircleExclamation" size={16} />
				</button>
			{/if}
			{#if emojiEnabled}
				<button
					bind:this={emojiButtonElement}
					class="btn btn-ghost btn-square btn-sm"
					type="button"
					disabled={composeBusy}
					aria-label={m.composeEmoji()}
					onclick={toggleEmojiPopover}
				>
					<FaIcon name="FaceSmile" size={16} />
				</button>
			{/if}
			{#if compose.languageCodes.length > 0}
				<button
					bind:this={languageButtonElement}
					class="btn btn-ghost btn-sm language-button"
					type="button"
					disabled={composeBusy}
					aria-label={m.composeLanguage()}
					onclick={toggleLanguagePopover}
				>
					{languageName(selectedLanguage)}
				</button>
			{/if}
			<span class="toolbar-spacer"></span>
			{#if remainingLength !== null}
				<span class:over-limit={remainingLength < 0} class="counter">{remainingLength}</span>
			{/if}
			<button class="btn btn-primary btn-square btn-sm" type="button" disabled={!canSubmit} aria-label={m.draftBoxSend()} onclick={submit}>
				{#if sending || mediaPreparing}
					<span class="loading loading-spinner loading-xs"></span>
				{:else}
					<FaIcon name="Send" size={15} />
				{/if}
			</button>
		</div>
	</div>
	<button class="modal-backdrop" type="button" disabled={composeBusy} aria-label={m.composeClose()} onclick={closeDialog}></button>
</div>

{#if accountProfiles.length > 0}
	<div
		bind:this={accountPopoverElement}
		class="account-popover rounded-box bg-base-100 p-0 shadow"
		popover="auto"
		style={`left: ${accountPopoverLeft}px; top: ${accountPopoverTop}px; width: ${accountPopoverWidth}px; max-height: ${accountPopoverMaxHeight}px;`}
	>
		<ul class="menu account-menu p-0">
			{#each accountProfiles as profile (profileKey(profile))}
				{@const selected = isProfileSelected(profile)}
				<li>
					<button type="button" disabled={composeBusy} onclick={() => selectAccount(profile.key)}>
						<span class="account-menu-user">
							<UserDisplay
								user={profile as unknown as TimelineUiProfile}
								variant="account"
								clickable={false}
							/>
						</span>
						<span class:checked={selected} class="account-menu-checkbox" aria-hidden="true">
							{#if selected}
								<FaIcon name="Check" size={11} />
							{/if}
						</span>
					</button>
				</li>
			{/each}
		</ul>
	</div>
{/if}

{#if emojiEnabled}
	<div
		bind:this={emojiPopoverElement}
		class="emoji-picker rounded-box bg-base-100 p-2 shadow"
		popover="auto"
		style={`left: ${emojiPopoverLeft}px; top: ${emojiPopoverTop}px; width: ${emojiPopoverWidth}px; max-height: ${emojiPopoverMaxHeight}px;`}
	>
			<EmojiPicker
				accountType={compose.emojiAccountType}
				emojis={compose.emojis}
				disabled={composeBusy}
				onSelect={selectEmoji}
			/>
	</div>
{/if}

{#if showCloseConfirmDialog}
	<dialog class="modal" open>
		<div class="modal-box discard-dialog">
			<h3>{m.composeDiscardConfirmTitle()}</h3>
			<p>{m.composeDiscardConfirmMessage()}</p>
			<div class="modal-action">
				<button
					class="btn btn-ghost btn-sm"
					type="button"
					disabled={composeBusy}
					onclick={() => (showCloseConfirmDialog = false)}
				>
					{m.actionCancel()}
				</button>
				<button
					class="btn btn-error btn-sm"
					type="button"
					disabled={composeBusy}
					onclick={discardAndClose}
				>
					{m.composeCloseDiscard()}
				</button>
			</div>
		</div>
		<button
			class="modal-backdrop"
			type="button"
			disabled={composeBusy}
			aria-label={m.composeCancelDiscard()}
			onclick={() => (showCloseConfirmDialog = false)}
		></button>
	</dialog>
{/if}

{#if compose.languageCodes.length > 0}
	<div
		bind:this={languagePopoverElement}
		class="language-popover rounded-box bg-base-100 p-2 shadow"
		popover="auto"
		style={`left: ${languagePopoverLeft}px; top: ${languagePopoverTop}px; width: ${languagePopoverWidth}px; max-height: ${languagePopoverMaxHeight}px;`}
	>
		<ul class="menu language-menu p-0">
			{#each compose.languageCodes as code}
				<li>
					<button type="button" disabled={composeBusy} onclick={() => selectLanguage(code)}>
						<span>{languageName(code)}</span>
						{#if selectedLanguage === code}
							<FaIcon name="Check" size={14} />
						{/if}
					</button>
				</li>
			{/each}
		</ul>
	</div>
{/if}

<style>
	.compose-modal {
		align-items: flex-start;
		padding-top: max(1rem, env(safe-area-inset-top));
	}

	.compose-dialog {
		display: grid;
		width: min(100vw - 1rem, 38rem);
		max-height: calc(100vh - 2rem);
		gap: 0.75rem;
		overflow-y: auto;
		border-radius: var(--radius-box);
		padding: 0;
	}

	.compose-app-bar {
		display: flex;
		min-height: 3.5rem;
		align-items: center;
		justify-content: flex-start;
		border-bottom: 1px solid var(--color-base-300);
		padding: 0.5rem 0.75rem;
		gap: 1rem;
	}

	.compose-dialog > .alert,
	.compose-reference-post,
	.poll-box,
	.compose-toolbar {
		margin-right: 1rem;
		margin-left: 1rem;
	}

	.menu small {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.75rem;
	}

	.poll-box {
		display: grid;
		gap: 0.65rem;
	}

	.discard-dialog {
		display: grid;
		gap: 0.5rem;
	}

	.discard-dialog h3,
	.discard-dialog p {
		margin: 0;
	}

	.discard-dialog h3 {
		font-size: 1rem;
		font-weight: 700;
	}

	.discard-dialog p {
		color: color-mix(in oklab, var(--color-base-content) 62%, transparent);
		font-size: 0.875rem;
	}

	.title-account-selector {
		display: flex;
		min-width: 0;
		align-items: center;
		gap: 0.25rem;
	}

	.account-avatar-list {
		display: flex;
		width: fit-content;
		max-width: 100%;
		align-items: center;
		gap: 0.35rem;
		overflow-x: auto;
		border: 0;
		border-radius: var(--radius-box);
		background: transparent;
		padding: 0;
		text-align: left;
	}

	.account-avatar-list:focus-visible {
		outline: 2px solid var(--color-primary);
		outline-offset: 2px;
	}

	.account-plus-button {
		flex: 0 0 auto;
	}

	.account-avatar-button {
		position: relative;
		display: block;
		flex: 0 0 auto;
		width: 2.25rem;
		height: 2.25rem;
	}

	.account-avatar {
		display: grid;
		place-items: center;
		overflow: hidden;
		border-radius: 999px;
		background: var(--color-base-300);
		font-size: 0.72rem;
		font-weight: 800;
	}

	.account-avatar {
		width: 2.25rem;
		height: 2.25rem;
	}

	.account-avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.platform-badge {
		position: absolute;
		right: -0.1rem;
		bottom: -0.1rem;
		display: grid;
		width: 1rem;
		height: 1rem;
		place-items: center;
		border: 1px solid var(--color-base-100);
		border-radius: 999px;
		box-shadow: 0 1px 2px color-mix(in oklab, var(--color-base-content) 14%, transparent);
	}

	.account-popover {
		position: fixed;
		inset: unset;
		z-index: 9999;
		box-sizing: border-box;
		overflow-y: auto;
		border: 1px solid var(--color-base-300);
		color: var(--color-base-content);
	}

	.account-popover .account-menu {
		width: 100%;
	}

	.account-popover .account-menu li,
	.account-popover .account-menu button {
		box-sizing: border-box;
		width: 100%;
	}

	.account-popover .account-menu button {
		grid-template-columns: minmax(0, 1fr) 1.125rem;
	}

	.account-menu-user {
		min-width: 0;
	}

	.account-menu-checkbox {
		display: grid;
		width: 1.125rem;
		height: 1.125rem;
		place-items: center;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-selector);
		color: var(--color-primary-content);
	}

	.account-menu-checkbox.checked {
		border-color: var(--color-primary);
		background: var(--color-primary);
	}

	.visibility-menu-check {
		display: grid;
		width: 1rem;
		place-items: center;
		color: var(--color-primary);
	}

	.compose-inline-input {
		width: 100%;
		border: 0;
		border-width: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		padding-bottom: 0;
		padding-right: 1rem;
		padding-left: 1rem;
	}

	.compose-divider {
		height: 1px;
		background: var(--color-base-300);
	}

	.compose-reference-post {
		overflow: hidden;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		padding: 0.5rem 1rem;
	}

	.compose-textarea {
		min-height: 11rem;
		width: 100%;
		border: 0;
		border-width: 0;
		border-radius: 0;
		background: transparent;
		box-shadow: none;
		padding-bottom: 0;
		padding-right: 1rem;
		padding-left: 1rem;
		resize: vertical;
		font-size: 1rem;
		line-height: 1.45;
	}

	.compose-textarea:focus,
	.compose-inline-input:focus {
		outline: none;
	}

	.poll-header,
	.poll-actions {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
		flex-wrap: wrap;
	}

	.media-file-input {
		display: none;
	}

	.media-preview-list {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(7.5rem, 1fr));
		gap: 0.5rem;
		margin-right: 1rem;
		margin-left: 1rem;
	}

	.media-preview-item {
		position: relative;
		display: grid;
		gap: 0.35rem;
		min-width: 0;
	}

	.media-preview-item img {
		width: 100%;
		aspect-ratio: 1 / 1;
		border-radius: var(--radius-box);
		background: var(--color-base-200);
		object-fit: cover;
	}

	.media-remove-button {
		position: absolute;
		top: 0.25rem;
		right: 0.25rem;
		background: color-mix(in oklab, var(--color-base-100) 86%, transparent);
	}

	.media-alt-input {
		width: 100%;
	}

	.compose-toolbar {
		display: flex;
		min-height: 3rem;
		align-items: center;
		gap: 0.25rem;
		border-top: 1px solid var(--color-base-300);
		padding-top: 0;
		padding-bottom: 0.35rem;
	}

	.toolbar-spacer {
		flex: 1 1 auto;
		min-width: 0.5rem;
	}

	.emoji-picker {
		position: fixed;
		inset: unset;
		z-index: 9999;
		box-sizing: border-box;
		display: grid;
		grid-template-rows: auto minmax(0, 1fr);
		gap: 0.5rem;
		overflow: hidden;
		border: 1px solid var(--color-base-300);
		color: var(--color-base-content);
	}

	.emoji-picker:not(:popover-open) {
		display: none;
	}

	.language-button {
		max-width: min(8rem, 28vw);
		min-width: 0;
		overflow: hidden;
		padding-right: 0.5rem;
		padding-left: 0.5rem;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.language-popover {
		position: fixed;
		inset: unset;
		z-index: 9999;
		box-sizing: border-box;
		overflow-y: auto;
		border: 1px solid var(--color-base-300);
		color: var(--color-base-content);
		padding-right: 0;
	}

	.language-popover .language-menu li,
	.language-popover .language-menu button {
		width: 100%;
	}

	.language-popover .language-menu button {
		grid-template-columns: minmax(0, 1fr) auto;
	}

	.language-popover .language-menu button span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.menu button {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: center;
		gap: 0.65rem;
		text-align: left;
	}

	.menu strong,
	.menu small {
		display: block;
	}

	.counter {
		color: color-mix(in oklab, var(--color-base-content) 60%, transparent);
		font-size: 0.85rem;
		font-variant-numeric: tabular-nums;
	}

	.counter.over-limit {
		color: var(--color-error);
		font-weight: 700;
	}

	@media (max-width: 560px) {
		.compose-modal {
			align-items: stretch;
			padding: 0;
		}

		.compose-dialog {
			width: 100vw;
			max-height: 100vh;
			min-height: 100vh;
			border-radius: 0;
		}
	}
</style>
