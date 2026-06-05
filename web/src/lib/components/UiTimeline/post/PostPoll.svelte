<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import type { UiPoll, UiPollOption } from '@flare/web-presenters/timeline.svelte';
	import { formatNumber, pollPercentage } from './postUtils';

	let {
		poll,
		selectedOptions,
		onToggleOption,
	}: {
		poll: UiPoll;
		selectedOptions: number[];
		onToggleOption: (index: number, multiple: boolean) => void;
	} = $props();
</script>

<div class="poll">
	<div class="poll-summary">
		<span>{poll.multiple ? m.pollMultipleChoice() : m.pollSingleChoice()}</span>
		{#if poll.expiredAt}
			<span>
				{poll.expired ? m.pollExpired() : m.pollExpires({ time: poll.expiredAt.relative })}
			</span>
		{/if}
	</div>
	<div class="poll-options">
		{#each poll.options as option, index}
			{@render PollOptionView(option, poll, index)}
		{/each}
	</div>
	{#if poll.canVote}
		<button
			class="btn btn-soft btn-sm rounded-box vote-button"
			type="button"
			disabled={selectedOptions.length === 0}
		>
			{m.pollVote()}
		</button>
	{/if}
</div>

{#snippet PollOptionView(option: UiPollOption, poll: UiPoll, index: number)}
	<button
		class:voted={poll.ownVotes.includes(index) || selectedOptions.includes(index)}
		class="poll-option"
		type="button"
		disabled={!poll.canVote}
		onclick={() => onToggleOption(index, poll.multiple)}
	>
		<div class="poll-option-top">
			<span>{option.title}</span>
			<span>{option.humanizedPercentage}</span>
		</div>
		<progress
			class="progress progress-primary poll-progress"
			value={pollPercentage(option)}
			max="100"
			aria-hidden="true"
		></progress>
		{#if option.votesCount > 0}
			<div class="poll-votes">{m.pollVotes({ count: formatNumber(option.votesCount) })}</div>
		{/if}
	</button>
{/snippet}

<style>
	.poll {
		display: grid;
		gap: 0.5rem;
	}

	.poll-summary {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		color: var(--post-text-muted);
		font-size: 0.78rem;
	}

	.poll-options {
		display: grid;
		gap: 0.45rem;
	}

	.poll-option {
		display: grid;
		gap: 0.35rem;
		width: 100%;
		border: 0;
		border-radius: var(--post-corner-radius);
		background: var(--post-bg-soft);
		color: inherit;
		cursor: pointer;
		padding: 0.55rem 0.65rem;
		text-align: left;
	}

	.poll-option:disabled {
		cursor: default;
	}

	.poll-option.voted {
		background: var(--post-primary-soft);
	}

	.poll-option-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.65rem;
		font-size: 0.86rem;
	}

	.poll-option-top span:first-child {
		min-width: 0;
		overflow-wrap: anywhere;
		font-weight: 650;
	}

	.poll-progress {
		width: 100%;
		height: 0.32rem;
	}

	.poll-votes {
		color: var(--post-text-weak);
		font-size: 0.74rem;
	}

	.vote-button {
		width: fit-content;
		color: var(--post-text-subtle);
		font-size: 0.82rem;
		font-weight: 700;
	}
</style>
