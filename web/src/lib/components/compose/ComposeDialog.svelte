<script lang="ts">
	import { goto } from '$app/navigation';
	import FaIcon from '$lib/components/FaIcon.svelte';
	import { tick } from 'svelte';
	import {
		createComposePresenter,
		type MicroBlogKey,
		type UiProfile,
		type UiTimelineV2PostVisibility,
	} from '@flare/web-presenters/compose.svelte';

	const compose = createComposePresenter(null, null, null);

	let text = $state('');
	let visibility = $state<UiTimelineV2PostVisibility>('Public');
	let contentWarningEnabled = $state(false);
	let spoilerText = $state('');
	let pollEnabled = $state(false);
	let pollOptions = $state(['', '']);
	let pollSingleChoice = $state(true);
	let pollExpiredAfter = $state(5 * 60 * 1000);
	let selectedLanguage = $state('en');
	let successHandled = $state(false);
	let showCloseConfirmDialog = $state(false);
	let languageButtonElement = $state<HTMLButtonElement | null>(null);
	let languagePopoverElement = $state<HTMLDivElement | null>(null);
	let languagePopoverLeft = $state(0);
	let languagePopoverTop = $state(0);
	let languagePopoverWidth = $state(240);
	let languagePopoverMaxHeight = $state(220);

	const maxLength = $derived(compose.textMaxLength);
	const remainingLength = $derived(maxLength === null ? null : maxLength - text.length);
	const selectedProfiles = $derived(successProfiles(compose.selectedUsers));
	const otherProfiles = $derived(successProfiles(compose.otherUsers));
	const sending = $derived(compose.directSendState.phase === 'Sending');
	const canSubmit = $derived(compose.canSend && !sending);
	const visiblePollOptions = $derived(pollOptions);
	const pollAllowed = $derived(compose.pollMaxOptions !== null);
	const maxPollOptions = $derived(compose.pollMaxOptions ?? 4);
	const hasTextContent = $derived(text.trim().length > 0 || spoilerText.trim().length > 0 || pollEnabled);
	const allVisibilities = $derived<UiTimelineV2PostVisibility[]>(
		compose.allVisibilities.length > 0
			? compose.allVisibilities
			: ['Public', 'Home', 'Followers', 'Specified']
	);

	$effect(() => {
		compose.setText(text);
		compose.setMediaSize(0);
	});

	$effect(() => {
		visibility = compose.visibility;
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
		pollEnabled = false;
		pollOptions = ['', ''];
		pollSingleChoice = true;
		pollExpiredAfter = 5 * 60 * 1000;
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

	function selectAccount(key: MicroBlogKey): void {
		compose.selectAccount(key);
	}

	function setVisibility(value: UiTimelineV2PostVisibility): void {
		visibility = value;
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

	function closeDialog(): void {
		if (hasTextContent) {
			showCloseConfirmDialog = true;
			return;
		}
		void goto('/');
	}

	function discardAndClose(): void {
		showCloseConfirmDialog = false;
		void goto('/');
	}

	function submit(): void {
		if (!canSubmit) return;
		successHandled = false;
		compose.clearDirectSendState();
		compose.sendDirect(
			text,
			[],
			visibility,
			selectedLanguage,
			false,
			contentWarningEnabled ? spoilerText.trim() || null : null,
			false,
			pollEnabled ? visiblePollOptions.map((item) => item.trim()).filter(Boolean).join('\n') : '',
			pollExpiredAfter,
			!pollSingleChoice
		);
	}

	function visibilityTitle(item: UiTimelineV2PostVisibility): string {
		switch (item) {
			case 'Public':
			case 'Channel':
				return 'Public';
			case 'Home':
				return 'Home';
			case 'Followers':
				return 'Followers';
			case 'Specified':
				return 'Specified';
		}
	}

	function visibilityDescription(item: UiTimelineV2PostVisibility): string {
		switch (item) {
			case 'Public':
			case 'Channel':
				return 'Visible to everyone';
			case 'Home':
				return 'Visible on home timelines';
			case 'Followers':
				return 'Only followers can see it';
			case 'Specified':
				return 'Only mentioned users can see it';
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
		const width = Math.min(240, window.innerWidth - 16);
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
			<button class="btn btn-ghost btn-square btn-sm" type="button" aria-label="Close" onclick={closeDialog}>
				<FaIcon name="Close" size={16} />
			</button>
		</div>

		{#if compose.directSendState.phase === 'Error'}
			<div class="alert alert-error">
				<FaIcon name="CircleExclamation" size={16} />
				<span>{compose.directSendState.errorMessage ?? 'Unable to publish.'}</span>
			</div>
		{:else if compose.directSendState.phase === 'Success'}
			<div class="alert alert-success">
				<FaIcon name="Check" size={16} />
				<span>Published.</span>
			</div>
		{/if}

		{#if showCloseConfirmDialog}
			<div class="alert close-confirm">
				<div>
					<strong>Discard post?</strong>
					<span>Your current compose content will be lost.</span>
				</div>
				<div class="confirm-actions">
					<button class="btn btn-ghost btn-sm" type="button" onclick={() => (showCloseConfirmDialog = false)}>Cancel</button>
					<button class="btn btn-error btn-sm" type="button" onclick={discardAndClose}>Discard</button>
				</div>
			</div>
		{/if}

		<div class="compose-accounts">
			{#if selectedProfiles.length > 0}
				<div class="account-row">
					{#each selectedProfiles as profile (profile.key.id + profile.key.host)}
						<button
							class="account-chip selected"
							type="button"
							aria-label={`Remove ${displayName(profile)}`}
							onclick={() => selectAccount(profile.key)}
						>
							<span class="avatar">
								{#if profile.avatar}
									<img src={profile.avatar} alt="" />
								{:else}
									<span>{initials(profile)}</span>
								{/if}
							</span>
							<span>
								<strong>{displayName(profile)}</strong>
								<small>{handleText(profile)}</small>
							</span>
						</button>
					{/each}
				</div>
			{:else}
				<div class="alert alert-warning">
					<span>No account selected.</span>
				</div>
			{/if}

			{#if otherProfiles.length > 0}
				<div class="dropdown dropdown-bottom">
					<button class="btn btn-ghost btn-sm" type="button" tabindex="0">
						<FaIcon name="Plus" size={13} />
						Account
					</button>
					<ul class="menu dropdown-content z-20 mt-1 w-64 rounded-box bg-base-100 p-2 shadow">
						{#each otherProfiles as profile (profile.key.id + profile.key.host)}
							<li>
								<button type="button" onclick={() => selectAccount(profile.key)}>
									<span class="min-w-0 truncate">{displayName(profile)}</span>
									<span class="text-xs opacity-60">{handleText(profile)}</span>
								</button>
							</li>
						{/each}
					</ul>
				</div>
			{/if}
		</div>

		{#if compose.contentWarningEnabled && contentWarningEnabled}
			<input
				class="input compose-inline-input"
				bind:value={spoilerText}
				placeholder="Content warning"
			/>
			<div class="compose-divider"></div>
		{/if}

		<textarea
			class="textarea compose-textarea"
			bind:value={text}
			placeholder="What's happening?"
		></textarea>

		{#if pollEnabled}
			<div class="poll-box">
				<div class="poll-header">
					<div class="join">
						<button
							class:btn-primary={pollSingleChoice}
							class="btn btn-sm join-item"
							type="button"
							onclick={() => (pollSingleChoice = true)}
						>
							Single choice
						</button>
						<button
							class:btn-primary={!pollSingleChoice}
							class="btn btn-sm join-item"
							type="button"
							onclick={() => (pollSingleChoice = false)}
						>
							Multiple choice
						</button>
					</div>
					<button
						class="btn btn-ghost btn-square btn-sm"
						type="button"
						disabled={pollOptions.length >= maxPollOptions}
						aria-label="Add option"
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
							placeholder={`Option ${index + 1}`}
							oninput={(event) => updatePollOption(index, event.currentTarget.value)}
						/>
						<button
							class="btn join-item"
							type="button"
							disabled={pollOptions.length <= 2}
							aria-label="Remove option"
							onclick={() => removePollOption(index)}
						>
							<FaIcon name="Close" size={13} />
						</button>
					</div>
				{/each}
				<div class="poll-actions">
					<select class="select select-bordered select-sm" bind:value={pollExpiredAfter}>
						<option value={5 * 60 * 1000}>Expires in 5 minutes</option>
						<option value={30 * 60 * 1000}>Expires in 30 minutes</option>
						<option value={60 * 60 * 1000}>Expires in 1 hour</option>
						<option value={6 * 60 * 60 * 1000}>Expires in 6 hours</option>
						<option value={12 * 60 * 60 * 1000}>Expires in 12 hours</option>
						<option value={24 * 60 * 60 * 1000}>Expires in 1 day</option>
						<option value={3 * 24 * 60 * 60 * 1000}>Expires in 3 days</option>
						<option value={7 * 24 * 60 * 60 * 1000}>Expires in 7 days</option>
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
			{#if compose.mediaEnabled}
				<button class="btn btn-ghost btn-square btn-sm" type="button" disabled aria-label="Add media">
					<FaIcon name="Image" size={16} />
				</button>
			{/if}
			{#if pollAllowed}
				<button
					class:btn-active={pollEnabled}
					class="btn btn-ghost btn-square btn-sm"
					type="button"
					disabled={!pollAllowed}
					aria-label="Poll"
					onclick={togglePoll}
				>
					<FaIcon name="Poll" size={16} />
				</button>
			{/if}
			<div class="dropdown dropdown-top">
				<button class="btn btn-ghost btn-square btn-sm" type="button" tabindex="0" aria-label="Visibility">
					<FaIcon name={visibilityIcon(visibility)} size={16} />
				</button>
				<ul class="menu dropdown-content z-30 mb-2 w-72 rounded-box bg-base-100 p-2 shadow">
					{#each allVisibilities as item}
						<li>
							<button type="button" onclick={() => setVisibility(item)}>
								<FaIcon name={visibilityIcon(item)} size={16} />
								<span>
									<strong>{visibilityTitle(item)}</strong>
									<small>{visibilityDescription(item)}</small>
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
					aria-label="Content warning"
					onclick={toggleContentWarning}
				>
					<FaIcon name="CircleExclamation" size={16} />
				</button>
			{/if}
			{#if compose.languageCodes.length > 0}
				<button
					bind:this={languageButtonElement}
					class="btn btn-ghost btn-sm language-button"
					type="button"
					aria-label="Language"
					onclick={toggleLanguagePopover}
				>
					{languageName(selectedLanguage)}
				</button>
			{/if}
			<span class="toolbar-spacer"></span>
			{#if remainingLength !== null}
				<span class:over-limit={remainingLength < 0} class="counter">{remainingLength}</span>
			{/if}
			<button class="btn btn-primary btn-square btn-sm" type="button" disabled={!canSubmit} aria-label="Post" onclick={submit}>
				{#if sending}
					<span class="loading loading-spinner loading-xs"></span>
				{:else}
					<FaIcon name="Send" size={15} />
				{/if}
			</button>
		</div>
	</div>
	<button class="modal-backdrop" type="button" aria-label="Close compose" onclick={closeDialog}></button>
</div>

{#if compose.languageCodes.length > 0}
	<div
		bind:this={languagePopoverElement}
		class="language-popover rounded-box bg-base-100 p-2 shadow"
		popover="auto"
		style={`left: ${languagePopoverLeft}px; top: ${languagePopoverTop}px; width: ${languagePopoverWidth}px; max-height: ${languagePopoverMaxHeight}px;`}
	>
		<ul class="menu p-0">
			{#each compose.languageCodes as code}
				<li>
					<button type="button" onclick={() => selectLanguage(code)}>
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
		padding: 0.5rem 0.75rem;
		gap: 1rem;
	}

	.compose-dialog > .alert,
	.compose-accounts,
	.poll-box,
	.compose-toolbar {
		margin-right: 1rem;
		margin-left: 1rem;
	}

	.close-confirm {
		align-items: center;
		justify-content: space-between;
	}

	.close-confirm > div:first-child {
		display: grid;
		gap: 0.15rem;
	}

	.close-confirm span,
	.menu small {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.75rem;
	}

	.confirm-actions,
	.compose-accounts,
	.poll-box {
		display: grid;
		gap: 0.65rem;
	}

	.account-row {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
	}

	.account-chip {
		display: grid;
		grid-template-columns: 2rem minmax(0, 1fr);
		align-items: center;
		max-width: 17rem;
		gap: 0.5rem;
		border: 1px solid var(--color-base-300);
		border-radius: var(--radius-box);
		background: var(--color-base-100);
		padding: 0.35rem 0.55rem 0.35rem 0.35rem;
		text-align: left;
	}

	.account-chip.selected {
		border-color: color-mix(in oklab, var(--color-primary) 34%, var(--color-base-300));
		background: color-mix(in oklab, var(--color-primary) 9%, var(--color-base-100));
	}

	.account-chip .avatar {
		display: grid;
		width: 2rem;
		height: 2rem;
		place-items: center;
		overflow: hidden;
		border-radius: 999px;
		background: var(--color-base-300);
		font-size: 0.72rem;
		font-weight: 800;
	}

	.account-chip img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.account-chip strong,
	.account-chip small {
		display: block;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.account-chip strong {
		font-size: 0.84rem;
		line-height: 1.15;
	}

	.account-chip small {
		color: color-mix(in oklab, var(--color-base-content) 58%, transparent);
		font-size: 0.72rem;
		line-height: 1.2;
	}

	.compose-inline-input {
		width: 100%;
		border: 0;
		border-radius: 0;
		background: transparent;
		padding-right: 1rem;
		padding-left: 1rem;
	}

	.compose-divider {
		height: 1px;
		background: var(--color-base-300);
	}

	.compose-textarea {
		min-height: 11rem;
		width: 100%;
		border: 0;
		border-radius: 0;
		background: transparent;
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

	.compose-toolbar {
		display: flex;
		min-height: 3rem;
		align-items: center;
		gap: 0.25rem;
		border-top: 1px solid var(--color-base-300);
		padding-top: 0.35rem;
		padding-bottom: 0.35rem;
	}

	.toolbar-spacer {
		flex: 1 1 auto;
		min-width: 0.5rem;
	}

	.language-button {
		max-width: min(8rem, 28vw);
		min-width: 4.75rem;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.language-popover {
		position: fixed;
		inset: unset;
		z-index: 9999;
		overflow-y: auto;
		border: 1px solid var(--color-base-300);
		color: var(--color-base-content);
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
