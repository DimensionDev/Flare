# Flare
[![license](https://img.shields.io/github/license/DimensionDev/Flare)](https://github.com/DimensionDev/Flare/blob/master/LICENSE)
[![Crowdin](https://badges.crowdin.net/flareapp/localized.svg)](https://crowdin.com/project/flareapp)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/DimensionDev/Flare)
[![Telegram](https://img.shields.io/badge/-telegram-blue?logo=telegram&color=white)](https://t.me/+0UtcP6_qcDoyOWE1)
[![Discord](https://img.shields.io/badge/-discord-blue?logo=discord&color=white)](https://discord.gg/De9NhXBryT)

![badge-Android](https://img.shields.io/badge/Android-8.0-3DDC84)
![badge-iOS](https://img.shields.io/badge/iOS-17.0-black)
![badge-Windows](https://img.shields.io/badge/Windows_10-1809-blue)
![badge-macOS](https://img.shields.io/badge/macOS-Sonoma-black)
![badge-Linux](https://img.shields.io/badge/Linux-AppImage-black)

Flare is an open-source, privacy-first social client that brings Mastodon, Misskey, Bluesky, X, Nostr, and RSS into one unified timeline. It supports cross-posting, lists, feeds, DMs, RSS management, and AI-powered features such as translation and summaries. Built with Kotlin Multiplatform, Flare shares its core logic across Android, iOS, macOS, Windows, and Linux, turning fragmented social feeds into a personal information hub.

<a href="https://apps.microsoft.com/detail/9NLRN0BKZ357?referrer=appbadge&mode=direct">
	<img src="https://get.microsoft.com/images/en-us%20dark.svg" width="190"/>
</a>
<a href='https://apps.apple.com/us/app/flare-social-network-client/id6476077738'><img alt='Get it on Google Play' src='https://developer.apple.com/app-store/marketing/guidelines/images/badge-example-preferred_2x.png' width=150/></a>
<a href='https://play.google.com/store/apps/details?id=dev.dimension.flare&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='docs/GetItOnGooglePlay_Badge_Web_color_English.svg' width=170/></a>
<a href='https://f-droid.org/packages/dev.dimension.flare'><img alt='Get it on F-Droid' src='https://f-droid.org/badge/get-it-on.svg' width=150/></a>
<a href='https://github.com/DimensionDev/Flare/releases/latest'><img alt='Download AppImage' src='docs/appimage_badge.svg' width=150/></a>

## Features
 - Unified social inbox: Flare brings Mastodon, Misskey, Bluesky, X, and RSS together in one place, so users can follow fragmented communities through a single timeline.
 - Mixed timeline experience: It merges content from multiple accounts and platforms into a coherent feed, reducing context switching between apps.
 - Cross-platform by design: Built with Kotlin Multiplatform, Flare shares core logic across Android, iOS, macOS, Windows, and Linux.
 - Rich platform support: Beyond basic timelines, it supports features such as polls, lists, bookmarks/favorites, Misskey antennas, Bluesky feeds and DMs, and RSS management.
 - Cross-posting workflow: Users can publish to multiple platforms at once, making it practical for creators and heavy social media users.
 - AI-assisted reading: Flare includes AI-powered capabilities such as translation and summaries to help users catch up on content faster.
 - Privacy-first approach: As a FOSS client, it emphasizes user control with features like anonymous mode, local filtering, local history, and transparent data handling.

## Roadmap
Here're some features we're planning to implement in the future.
 - [x] Grouped Mixed timeline
 - [ ] Showing instance's announcement
 - [ ] Crossposting for repost
 - [ ] Auto thread
 - [ ] AI powered features
   - [ ] Personal trends of the day
   - [ ] Quick reply
 - [ ] Support for Meta Threads
 - [ ] Support for Discourse forum
 - [x] Support for Nostr
 - [x] Desktop Client
 - [ ] Web Client(?)

## Building
### Android
 - Make sure you have JDK 25 installed
 - Run `./gradlew installDebug` to build and install the debug version of the app
 - You can open the project in Android Studio or IntelliJ IDEA if you want

### iOS
 - Make sure you have JDK 25 installed
 - Make sure you have a Mac with Xcode 26 installed
 - open `iosApp/Flare.xcodeproj` in Xcode
 - Build and run the app

### Desktop
 - Make sure you have JDK 25 installed
 - Run `./gradlew run` to build and run the debug version of the desktop app.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for more information.

## License
This project is licensed under the [AGPL-3.0](LICENSE) license.
