# Flare
[![license](https://img.shields.io/github/license/DimensionDev/Flare)](https://github.com/DimensionDev/Flare/blob/master/LICENSE)
[![Crowdin](https://badges.crowdin.net/flareapp/localized.svg)](https://crowdin.com/project/flareapp)
[![Telegram](https://img.shields.io/badge/-telegram-blue?logo=telegram&color=white)](https://t.me/+0UtcP6_qcDoyOWE1)
[![Discord](https://img.shields.io/badge/-discord-blue?logo=discord&color=white)](https://discord.gg/De9NhXBryT)
[![Line](https://img.shields.io/badge/-Line_Group-green?logo=line&color=white)](https://line.me/ti/g/hf95HyGJ9k)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/DimensionDev/Flare)

![badge-Platform](https://img.shields.io/badge/Supported%20Platform-Mastodon%20|%20Misskey%20|%20Bluesky%20|%20X%20-black)

![badge-Android](https://img.shields.io/badge/Android-6.0-3DDC84)
![badge-iOS](https://img.shields.io/badge/iOS-18.0-black)
![badge-macOS](https://img.shields.io/badge/macOS-Monterey-black)

Flare is an open-source social client that merges your feeds from Mastodon, Misskey, Bluesky, X, and RSS into a single timeline, turning it into your personal information hub, with Android/iOS and macOS/Windows Support.



<a href='https://testflight.apple.com/join/iYP7QZME'><img alt='Get it on Google Play' src='https://developer.apple.com/app-store/marketing/guidelines/images/badge-example-preferred_2x.png' width=150/></a>
<a href='https://play.google.com/store/apps/details?id=dev.dimension.flare&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://upload.wikimedia.org/wikipedia/commons/thumb/7/78/Google_Play_Store_badge_EN.svg/2880px-Google_Play_Store_badge_EN.svg.png' width=170/></a>
<a href='https://f-droid.org/packages/dev.dimension.flare'><img alt='Get it on F-Droid' src='https://f-droid.org/badge/get-it-on.svg' width=150/></a>


## Features
 - Consolidate all your social networks into one client, featuring Mastodon, Misskey, Bluesky and more to come.
 - Crosspost your content simultaneously across all your platforms.
 - It is FOSS and privacy-centric.

## Roadmap
Here're some features we're planning to implement in the future.
 - [ ] Grouped Mixed timeline
 - [ ] Showing instance's announcement
 - [ ] Crossposting for repost
 - [ ] Auto thread
 - [ ] AI powered features
   - [ ] Personal trends of the day
   - [ ] Quick reply
 - [ ] Support for Meta Threads
 - [ ] Support for Discourse forum
 - [x] Desktop Client
 - [ ] Web Client(?)

Here're some features we've done before.
 - [x] Mixed timeline
 - [x] AI powered features
   - [x] Translation
   - [x] Summary
 - [x] Anonymous mode enhancement, option to change data source
 - [x] Local history
 - [x] RSS feed support
 - [x] Support for vvo platform
 - [x] Anonymous mode, no need to login
 - [x] Customizable tabs
 - [x] Local filtering
 - [x] Crossposting
 - [x] Translation

### Mastodon
 - [x] Support for polls
 - [x] Support global/local timelines
 - [x] Support for lists
 - [x] Support for bookmarks/faovrites timelines

### Misskey
 - [x] Support for polls
 - [x] Support for lists
 - [x] Support for antennas
 - [x] Support for faovrites timeline

### Bluesky
 - [x] Support for lists
 - [x] Support for feeds
 - [x] Support DM

## Building
### Android
 - Make sure you have JDK 21 installed
 - Run `./gradlew installDebug` to build and install the debug version of the app
 - You can open the project in Android Studio or IntelliJ IDEA if you want

### iOS
 - Make sure you have a Mac with Xcode 16 installed
 - open `iosApp/iosApp.xcodeproj` in Xcode
 - Build and run the app

### Server
 - Flare Server uses Ktor with Kotlin Native, which only works on Linux X64 and MacOS X64/ARM64
 - Make sure you have JDK 21 installed
 - Run `./gradlew :server:runDebugExecutableMacosArm64 -PrunArgs="--config-path=path/to/server/src/commonMain/resources/application.yaml"` to build and run the server, remember to replace `path/to/server/src/commonMain/resources/application.yaml` with the path to your config file
 - The server will run on `http://localhost:8080` by default
#### Docker
If you prefer using Docker, you can use Docker Compose to run prebuild Server Image.
 - Rename `.env.sample` to `.env`, and update the environment variables in the file.
 - If you're deploying into a production server, you might need to update the `docker-compose.yml` file with these lines:
   ```diff
   environment:
   -   # STAGE: local
   +   STAGE: 'production'
   -   DOMAINS: api.flareapp.moe -> http://flare-backend:8080
   +   DOMAINS: your_domain_here -> http://flare-backend:8080
   ```
 - Run `docker compose up -d`

### Desktop
 - Make sure you have JDK 21 installed, JBR-21 is recommended.
 - Depends on your build target, you might need to have Xcode 16 (macOS target) or .Net 9 (Windows target) installed.
 - Run `./gradlew runDistributable` to build and run the debug version of the desktop app.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for more information.

## License
This project is licensed under the [AGPL-3.0](LICENSE) license.
