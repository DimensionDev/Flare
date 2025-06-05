# Flare
[![license](https://img.shields.io/github/license/DimensionDev/Flare)](https://github.com/DimensionDev/Flare/blob/master/LICENSE)
[![Crowdin](https://badges.crowdin.net/flareapp/localized.svg)](https://crowdin.com/project/flareapp)
[![Telegram](https://img.shields.io/badge/-telegram-blue?logo=telegram&color=white)](https://t.me/+0UtcP6_qcDoyOWE1)
[![Line](https://img.shields.io/badge/-Line_Group-green?logo=line&color=white)](https://line.me/ti/g/hf95HyGJ9k)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/DimensionDev/Flare)

![badge-Platform](https://img.shields.io/badge/Supported%20Platform-Mastodon%20|%20Misskey%20|%20Bluesky%20-black)

![badge-Android](https://img.shields.io/badge/Android-7.0-3DDC84)
![badge-iOS](https://img.shields.io/badge/iOS-18.0-black)


The ultimate next generation* open-sourced AI powered** decentralized social network client for Android/iOS, still in development.
*: _Just a joke_
**: _Not yet implemented_


<a href='https://testflight.apple.com/join/iYP7QZME'><img alt='Get it on Google Play' src='https://developer.apple.com/app-store/marketing/guidelines/images/badge-example-preferred_2x.png' height=50/></a>
<a href='https://play.google.com/store/apps/details?id=dev.dimension.flare&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='docs\src\assets\en_badge_web_generic.png' height=50/></a>

## Features
 - Consolidate all your social networks into one client, featuring Mastodon, Misskey, Bluesky and more to come.
 - Crosspost your content simultaneously across all your platforms.
 - It is FOSS and privacy-centric.

## Roadmap
Here're some features we're planning to implement in the future.
 - [ ] Mixed timeline
 - [ ] AI powered features
   - [x] Translation
   - [x] Summary
   - [ ] Quick reply
 - [ ] Support for Meta Threads
 - [x] Anonymous mode enhancement
   - Option to change data source
 - [ ] Auto thread
 - [x] Local history
 - [x] RSS feed support

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

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for more information.

## License
This project is licensed under the [AGPL-3.0](LICENSE) license.
