<script lang="ts">
    import { tick } from "svelte";
    import type { UiRichText } from "@flare/web-presenters/timeline.svelte";
    import RichText from "$lib/components/RichText.svelte";
    import { m } from "$lib/paraglide/messages.js";

    let {
        contents,
        lineLimit = 5,
        isDetail = false,
        expanded = $bindable(false),
    }: {
        contents: UiRichText[];
        lineLimit?: number;
        isDetail?: boolean;
        expanded?: boolean;
    } = $props();

    let bodyElement = $state<HTMLDivElement | null>(null);
    let collapsedHeights = $state<(number | null)[]>([]);
    const visibleContents = $derived(contents.filter((content) => !content.isEmpty));
    const hasOverflow = $derived(collapsedHeights.some((height) => height !== null));

    $effect(() => {
        const element = bodyElement;
        const limit = Math.max(lineLimit, 1);
        visibleContents;
        if (!element || isDetail) {
            collapsedHeights = [];
            return;
        }

        let disposed = false;
        let observer: ResizeObserver | undefined;
        tick().then(() => {
            if (disposed) return;
            const blocks = Array.from(element.querySelectorAll<HTMLElement>(".post-text-measure"));
            const measure = () => {
                collapsedHeights = blocks.map((block) => {
                    const lineHeight = Number.parseFloat(getComputedStyle(block).lineHeight);
                    const thresholdHeight = Math.ceil(lineHeight * Math.max(15, limit));
                    return block.getBoundingClientRect().height > thresholdHeight + 1
                        ? Math.ceil(lineHeight * limit)
                        : null;
                });
            };
            observer = new ResizeObserver(measure);
            blocks.forEach((block) => observer?.observe(block));
            measure();
        });

        return () => {
            disposed = true;
            observer?.disconnect();
        };
    });
</script>

<div class="post-body" bind:this={bodyElement}>
    {#each visibleContents as content, index}
        <div
            class="post-text-block"
            style:max-height={!expanded && !isDetail && collapsedHeights[index] != null
                ? `${collapsedHeights[index]}px`
                : undefined}
        >
            <div class="post-text-measure">
                <RichText text={content} className="rich-body" />
            </div>
        </div>
    {/each}
</div>
{#if !expanded && !isDetail && hasOverflow}
    <button
        class="btn btn-link btn-xs h-auto min-h-0 rounded-box p-0 expand-button"
        type="button"
        onclick={() => (expanded = true)}
    >
        {m.postShowMore()}
    </button>
{/if}

<style>
    .post-body {
        display: grid;
        gap: 0.25rem;
        min-width: 0;
    }

    .post-text-block {
        overflow: hidden;
    }

    .post-text-measure {
        display: flow-root;
    }

    .expand-button {
        width: fit-content;
        color: var(--post-primary);
        font-size: 0.84rem;
        font-weight: 700;
    }

    .expand-button:hover {
        text-decoration: underline;
    }
</style>
