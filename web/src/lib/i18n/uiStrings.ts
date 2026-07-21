import type { UiStrings } from '@flare/web-presenters/homeTimelineWithTabs.svelte';
import { m } from '$lib/paraglide/messages.js';

export type { UiStrings };

export function localizedUiString(value: UiStrings): string {
	switch (value) {
		case 'Home':
			return m.homeTabHomeTitle();
		case 'Notifications':
			return m.homeTabNotificationsTitle();
		case 'Discover':
			return m.homeTabDiscoverTitle();
		case 'Me':
			return m.homeTabMeTitle();
		case 'Settings':
			return m.settingsTitle();
		case 'MastodonLocal':
			return m.mastodonTabLocalTitle();
		case 'MastodonPublic':
			return m.mastodonTabPublicTitle();
		case 'Featured':
			return m.homeTabFeaturedTitle();
		case 'Bookmark':
			return m.homeTabBookmarksTitle();
		case 'Favourite':
			return m.homeTabFavoriteTitle();
		case 'List':
			return m.homeTabListTitle();
		case 'Feeds':
			return m.homeTabFeedsTitle();
		case 'DirectMessage':
			return m.dmListTitle();
		case 'Rss':
			return m.rssTitle();
		case 'Antenna':
			return m.antennaTitle();
		case 'MixedTimeline':
			return m.homeTabMixedTimelineTitle();
		case 'Social':
			return m.homeTabSocialTimelineTitle();
		case 'Liked':
			return m.likedTitle();
		case 'AllRssFeeds':
			return m.allRssFeedsTitle();
		case 'Posts':
			return m.postsTitle();
		case 'PostsWithReplies':
			return m.profileTabTimelineWithReply();
		case 'Highlights':
			return m.profileTabHighlights();
		case 'Media':
			return m.profileTabMedia();
		case 'Channel':
			return m.homeTabChannelsTitle();
		case 'Default':
			return m.tabSettingsDefault();
		case 'Login':
			return m.loginButton();
		case 'Verify':
			return m.loginVerifying();
		case 'Cancel':
			return m.loginCancel();
		case 'Next':
			return m.loginNext();
		case 'Username':
			return m.loginUsername();
		case 'Password':
			return m.loginPassword();
		case 'Otp':
			return m.loginOtp();
		case 'OAuthLogin':
			return m.loginOauth();
		case 'PasswordLogin':
			return m.loginPassword();
		case 'QrConnect':
			return m.loginQrConnect();
		case 'CredentialImport':
			return m.loginCredentialImport();
		case 'ExternalSigner':
			return m.loginExternalSigner();
		case 'WebCookieLogin':
			return m.loginWebCookie();
		case 'PixivRankingWeek':
			return 'Weekly Ranking';
		case 'PixivRankingMonth':
			return 'Monthly Ranking';
		case 'PixivRankingDayMale':
			return 'Male Ranking';
		case 'PixivRankingDayFemale':
			return 'Female Ranking';
		case 'PixivRankingWeekOriginal':
			return 'Original Ranking';
		case 'PixivRankingWeekRookie':
			return 'Rookie Ranking';
		case 'PixivRankingDayManga':
			return 'Manga Ranking';
		case 'Illustrations':
			return m.illustrationsTitle();
		case 'Manga':
			return m.mangaTitle();
		case 'FanboxSupported':
			return 'Supported posts';
		case 'FanboxRecommendedCreators':
			return 'Recommended creators';
		case 'PixivPrivateFollowing':
			return 'Private Following';
		case 'PixivPrivateBookmarks':
		case 'PixivPrivateFavourites':
			return 'Private Favorites';
		default:
			return m.loginCredentialImport();
	}
}
