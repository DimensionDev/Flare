<script lang="ts">
	import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';
	import {
		faAt,
		faArrowLeft,
		faArrowRotateLeft,
		faBell,
		faBookBookmark,
		faBookmark,
		faCheck,
		faCat,
		faClockRotateLeft,
		faCircleExclamation,
		faCircleInfo,
		faCircleCheck,
		faCircleUser,
		faChevronRight,
		faChevronDown,
		faChevronUp,
		faCommentDots,
		faDatabase,
		faEllipsis,
		faEllipsisVertical,
		faFileExport,
		faFileImport,
		faFileLines,
		faFilter,
		faFaceSmile,
		faGear,
		faGlobe,
		faHeart,
		faHouse,
		faImage,
		faLanguage,
		faList,
		faLock,
		faLockOpen,
		faMagnifyingGlass,
		faMessage,
		faMinus,
		faNewspaper,
		faPalette,
		faPaperPlane,
		faPen,
		faPenToSquare,
		faPhotoFilm,
		faPlus,
		faQrcode,
		faRectangleList,
		faReply,
		faRetweet,
		faRotateRight,
		faRobot,
		faShareNodes,
		faSliders,
		faSquarePollHorizontal,
		faSquareRss,
		faStar,
		faThumbtack,
		faTrash,
		faTv,
		faTableList,
		faUserPlus,
		faUsers,
		faUserSlash,
		faVideo,
		faVolumeXmark,
		faXmark,
	} from '@fortawesome/free-solid-svg-icons';
	import * as brandIcons from '@fortawesome/free-brands-svg-icons';
	import { FontAwesomeIcon } from '@fortawesome/svelte-fontawesome';

	let {
		name,
		size = 18,
		title,
	}: {
		name: string | null | undefined;
		size?: number;
		title?: string;
	} = $props();

	const brands = brandIcons as unknown as Record<string, IconDefinition | undefined>;
	const icons: Record<string, IconDefinition> = {
		Home: faHouse,
		Back: faArrowLeft,
		Notification: faBell,
		Search: faMagnifyingGlass,
		Profile: faCircleUser,
		Settings: faGear,
		Local: faUsers,
		World: faGlobe,
		Featured: faRectangleList,
		Bookmark: faBookmark,
		Unbookmark: faBookmark,
		Heart: faHeart,
		Like: faHeart,
		Unlike: faHeart,
		Favourite: faStar,
		UnFavourite: faStar,
		List: faList,
		Feeds: faSquareRss,
		Rss: faSquareRss,
		Messages: faMessage,
		Reply: faReply,
		Comment: faCommentDots,
		Quote: faReply,
		Retweet: faRetweet,
		Unretweet: faRetweet,
		More: faEllipsis,
		MoreVerticel: faEllipsisVertical,
		Delete: faTrash,
		Report: faCircleInfo,
		Plus: faPlus,
		React: faPlus,
		UnReact: faMinus,
		Close: faXmark,
		QrCode: faQrcode,
		Reset: faArrowRotateLeft,
		Refresh: faRotateRight,
		Share: faShareNodes,
		Sliders: faSliders,
		Mute: faVolumeXmark,
		UnMute: faVolumeXmark,
		Block: faUserSlash,
		UnBlock: faUserSlash,
		Follow: faUserPlus,
		Mention: faAt,
		Lock: faLock,
		LockOpen: faLockOpen,
		Poll: faSquarePollHorizontal,
		Send: faPaperPlane,
		Edit: faPen,
		PenToSquare: faPenToSquare,
		ClockRotateLeft: faClockRotateLeft,
		Info: faCircleInfo,
		ChevronRight: faChevronRight,
		ChevronDown: faChevronDown,
		ChevronUp: faChevronUp,
		Pin: faThumbtack,
		Check: faCheck,
		Cat: faCat,
		Verified: faCircleCheck,
		Bot: faRobot,
		CircleExclamation: faCircleExclamation,
		FaceSmile: faFaceSmile,
		Translate: faLanguage,
		Mastodon: brands.faMastodon ?? faGlobe,
		Bluesky: brands.faBluesky ?? faGlobe,
		Misskey: brands.faMisskey ?? faCircleUser,
		Twitter: brands.faTwitter ?? faGlobe,
		X: brands.faXTwitter ?? faGlobe,
		Nostr: brands.faNostr ?? faGlobe,
		Weibo: brands.faWeibo ?? faGlobe,
		Image: faImage,
		Palette: faPalette,
		TableList: faTableList,
		Filter: faFilter,
		Database: faDatabase,
		FileExport: faFileExport,
		FileImport: faFileImport,
		FileLines: faFileLines,
		Github: brands.faGithub ?? faGlobe,
		Telegram: brands.faTelegram ?? faGlobe,
		Discord: brands.faDiscord ?? faGlobe,
		PhotoFilm: faPhotoFilm,
		Video: faVideo,
		Channel: faTv,
		News: faNewspaper,
		ChatMessage: faMessage,
		FeaturedFallback: faBookBookmark,
	};

	const misskeyPath =
		'M54.63 120.96c-1.9779 0-3.8618.3298-5.6513.9891-3.2023 1.1302-5.8396 3.1553-7.9117 6.0751-1.9779 2.8256-2.9667 5.9807-2.9667 9.4656v61.88c0 4.5209 1.601 8.4294 4.8033 11.726 3.2965 3.2023 7.2055 4.8038 11.726 4.8038 4.6151 0 8.5236-1.6015 11.726-4.8038 3.2965-3.2965 4.9449-7.205 4.9449-11.726v-11.253c.0356-2.4371 2.546-1.7977 3.8148 0 2.3763 4.1153 7.4142 7.6497 13.28 7.6295 5.8656-.0202 10.737-2.9202 13.28-7.6295.963-1.1358 3.6774-3.071 3.9558 0v11.253c0 4.5209 1.601 8.4294 4.8033 11.726 3.2965 3.2023 7.2055 4.8038 11.726 4.8038 4.6151 0 8.5236-1.6015 11.726-4.8038 3.2965-3.2965 4.9449-7.205 4.9449-11.726v-61.88c0-3.4849-1.0357-6.64-3.1078-9.4656-1.9779-2.9198-4.5683-4.9448-7.7706-6.0751-1.8837-.6593-3.7676-.9891-5.6513-.9891-5.086 0-9.3712 1.9782-12.856 5.934l-16.775 19.632c-.3767.2826-1.6248 2.4428-4.2757 2.4428s-3.7574-2.1602-4.1341-2.4428l-16.916-19.632c-3.3907-3.9558-7.6289-5.934-12.715-5.934zm104.53 0c-3.9558 0-7.3464 1.4129-10.172 4.2385-2.7314 2.7314-4.0969 6.0751-4.0969 10.031 0 3.9558 1.3655 7.3464 4.0969 10.172 2.8256 2.7314 6.2162 4.0974 10.172 4.0974s7.3464-1.366 10.172-4.0974c2.8256-2.8256 4.2385-6.2162 4.2385-10.172 0-3.9558-1.4129-7.2995-4.2385-10.031-2.8256-2.8256-6.2162-4.2385-10.172-4.2385zm.14107 31.364c-3.9558 0-7.3464 1.4129-10.172 4.2385s-4.238 6.2162-4.238 10.172v34.896c0 3.9558 1.4124 7.3464 4.238 10.172 2.8256 2.7314 6.2162 4.0974 10.172 4.0974s7.2995-1.366 10.031-4.0974c2.8256-2.8256 4.2385-6.2162 4.2385-10.172v-34.896c0-3.9558-1.4129-7.3464-4.2385-10.172-2.7314-2.8256-6.0751-4.2385-10.031-4.2385z';
	const icon = $derived(icons[name ?? ''] ?? icons.More);
</script>

<span
	class="fa-icon"
	style={`--fa-icon-size: ${size}px;`}
	title={title}
	aria-hidden={title ? undefined : 'true'}
>
	{#if name === 'Misskey'}
		<svg viewBox="0 0 135.47 135.47" role="img" aria-hidden="true">
			<path d={misskeyPath} transform="translate(-38.1 -100.7)" fill="currentColor" />
		</svg>
	{:else}
		<FontAwesomeIcon {icon} />
	{/if}
</span>

<style>
	.fa-icon {
		display: inline-grid;
		width: var(--fa-icon-size);
		height: var(--fa-icon-size);
		place-items: center;
		flex: 0 0 auto;
		line-height: 1;
	}

	.fa-icon :global(svg) {
		display: block;
		width: var(--fa-icon-size);
		height: var(--fa-icon-size);
	}
</style>
