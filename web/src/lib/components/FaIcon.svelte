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
		faPen,
		faPenToSquare,
		faPhotoFilm,
		faPlus,
		faQrcode,
		faRectangleList,
		faReply,
		faRetweet,
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

	const icon = $derived(icons[name ?? ''] ?? icons.More);
</script>

<span
	class="fa-icon"
	style={`--fa-icon-size: ${size}px;`}
	title={title}
	aria-hidden={title ? undefined : 'true'}
>
	<FontAwesomeIcon {icon} />
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
