import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import SwiftDate
import SwiftUI
import UIKit

struct StatusContentViewV2: View {
    let item: TimelineItem
    let isDetailView: Bool
    let enableGoogleTranslation: Bool
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction
    let onMediaClick: (Int, Media) -> Void
    let onPodcastCardTap: (Card) -> Void

    var body: some View {
        VStack(alignment: .leading) {
            if item.hasAboveTextContent, let aboveTextContent = item.aboveTextContent {
                StatusReplyViewV2(aboveTextContent: aboveTextContent)
            }

            if item.hasContentWarning, let cwText = item.contentWarning {
                StatusContentWarningViewV2(contentWarning: cwText, theme: theme, openURL: openURL)
            }

            Spacer().frame(height: 10)

            StatusMainContentViewV2(
                item: item,
                enableGoogleTranslation: enableGoogleTranslation,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL
            )

            if item.hasImages {
                StatusMediaViewV2(
                    timelineItem: item,
                    appSettings: appSettings,
                    onMediaClick: onMediaClick
                )
            }

            // Card (Podcast or Link Preview)
            if item.hasCard, let card = item.card {
                if item.isPodcastCard {
                    PodcastPreviewV2(card: card)
                        .onTapGesture {
                            onPodcastCardTap(card)
                        }
                } else if appSettings.appearanceSettings.showLinkPreview, item.shouldShowLinkPreview {
                    LinkPreviewV2(card: card)
                }
            }

            // Quote
            if item.hasQuote {
                StatusQuoteViewV2(quotes: item.quote, onMediaClick: onMediaClick)
            }

            // misskey 的+ 的emojis
            if item.hasBottomContent, let bottomContent = item.bottomContent {
                StatusBottomContentViewV2(bottomContent: bottomContent)
            }

            // Detail date
            if isDetailView {
                Spacer().frame(height: 4)
                HStack {
                    Text(item.timestamp, style: .date)
                    Text(item.timestamp, style: .time)
                }
                .opacity(0.6)
            }
        }
    }
}

struct StatusReplyViewV2: View {
    let aboveTextContent: AboveTextContent

    var body: some View {
        switch aboveTextContent {
        case let .replyTo(handle):
            Text(String(localized: "Reply to \(handle.removingHandleFirstPrefix("@"))"))
                .font(.caption)
                .opacity(0.6)
        }
        Spacer().frame(height: 4)
    }
}

struct StatusMediaViewV2: View {
    let timelineItem: TimelineItem
    let appSettings: AppSettings
    let onMediaClick: (Int, Media) -> Void

    var body: some View {
        Spacer().frame(height: 8)

        MediaComponentV2(
            hideSensitive: timelineItem.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
            medias: timelineItem.images,
            onMediaClick: { index, media in
                PhotoBrowserManagerV2.shared.showPhotoBrowser(
                    media: media,
                    images: timelineItem.images,
                    initialIndex: index
                )
            },
            sensitive: timelineItem.sensitive
        )
    }
}

struct StatusBottomContentViewV2: View {
    let bottomContent: BottomContent

    var body: some View {
        switch bottomContent {
        case let .reaction(emojiReactions):
            ScrollView(.horizontal) {
                LazyHStack {
                    if !emojiReactions.isEmpty {
                        ForEach(0 ..< emojiReactions.count, id: \.self) { index in
                            let reaction = emojiReactions[index]
                            Button(action: {
                                // reaction.onClicked()
                            }) {
                                HStack {
                                    if !reaction.url.isEmpty {
                                        KFImage(URL(string: reaction.url))
                                            .resizable()
                                            .scaledToFit()
                                    } else {
                                        Text(reaction.name)
                                    }
                                    Text("\(reaction.count)")
                                }
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
    }
}

struct StatusContentWarningViewV2: View {
    let contentWarning: RichText
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        Button(action: {
            // withAnimation {
            //     // expanded = !expanded
            // }
        }) {
            Image(systemName: "exclamationmark.triangle")
                .foregroundColor(theme.labelColor)

            FlareText(
                contentWarning.raw,
                contentWarning.markdown,
                style: FlareTextStyle.Style(
                    font: Font.scaledCaptionFont,
                    textColor: UIColor(.gray),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ),
                isRTL: contentWarning.isRTL
            )
            .onLinkTap { url in
                openURL(url)
            }
            .lineSpacing(CGFloat(theme.lineSpacing))
            .foregroundColor(theme.labelColor)
            // Markdown()
            //     .font(.caption2)
            //     .markdownInlineImageProvider(.emoji)
            // Spacer()
            // if expanded {
            //     Image(systemName: "arrowtriangle.down.circle.fill")
            // } else {
            //     Image(systemName: "arrowtriangle.left.circle.fill")
            // }
        }
        .opacity(0.6)
        .buttonStyle(.plain)
        // if expanded {
        //     Spacer()
        //         .frame(height: 8)
        // }
    }
}

struct StatusMainContentViewV2: View {
    let item: TimelineItem
    let enableGoogleTranslation: Bool
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        if item.hasContent {
            let content = item.content
            FlareText(
                content.raw,
                content.markdown,
                style: FlareTextStyle.Style(
                    font: Font.scaledBodyFont,
                    textColor: UIColor(theme.labelColor),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ),
                isRTL: content.isRTL
            )
            .onLinkTap { url in
                openURL(url)
            }
            .lineSpacing(CGFloat(theme.lineSpacing))
            .foregroundColor(theme.labelColor)

            if enableGoogleTranslation {
                TranslatableText(originalText: content.raw, forceTranslate: true)
            }
        } else {
            Text("")
                .font(.system(size: 16))
                .foregroundColor(theme.labelColor)
        }
    }
}
