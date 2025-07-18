import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

struct StatusContentView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction
    let onMediaClick: (Int, UiMedia) -> Void
    let onPodcastCardTap: (UiCard) -> Void

    var body: some View {
        VStack(alignment: .leading) {
            // Reply content
            if viewModel.hasAboveTextContent, let aboveTextContent = viewModel.statusData.aboveTextContent {
                StatusReplyView(aboveTextContent: aboveTextContent)
            }

            // Content warning
            if viewModel.hasContentWarning, let cwText = viewModel.statusData.contentWarning {
                StatusContentWarningView(contentWarning: cwText, theme: theme, openURL: openURL)
            }

            Spacer().frame(height: 10)

            // Main content
            StatusMainContentView(
                viewModel: viewModel,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL
            )

            // Media
            if viewModel.hasImages {
                StatusMediaView(
                    viewModel: viewModel,
                    appSettings: appSettings,
                    onMediaClick: onMediaClick
                )
            }

            // Card (Podcast or Link Preview)
            if viewModel.hasCard, let card = viewModel.statusData.card {
                StatusCardView(
                    card: card,
                    viewModel: viewModel,
                    appSettings: appSettings,
                    onPodcastCardTap: onPodcastCardTap
                )
            }

            // Quote
            if viewModel.hasQuote {
                StatusQuoteView(quotes: viewModel.statusData.quote, onMediaClick: onMediaClick)
            }

            // misskey 的+ 的emojis
            if viewModel.hasBottomContent, let bottomContent = viewModel.statusData.bottomContent {
                StatusBottomContentView(bottomContent: bottomContent)
            }

            // Detail date
            if viewModel.isDetailView {
                StatusDetailDateView(createdAt: viewModel.statusData.createdAt)
            }
        }
    }
}

struct StatusReplyView: View {
    let aboveTextContent: UiTimelineItemContentStatusAboveTextContent

    var body: some View {
        switch onEnum(of: aboveTextContent) {
        case let .replyTo(data):
            Text(String(localized: "Reply to \(data.handle.removingHandleFirstPrefix("@"))"))
                .font(.caption)
                .opacity(0.6)
        }
        Spacer().frame(height: 4)
    }
}

struct StatusMediaView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let onMediaClick: (Int, UiMedia) -> Void

    var body: some View {
        Spacer().frame(height: 8)

        MediaComponent(
            hideSensitive: viewModel.statusData.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
            medias: viewModel.statusData.images,
            onMediaClick: { index, media in
                PhotoBrowserManager.shared.showPhotoBrowser(
                    media: media,
                    images: viewModel.statusData.images,
                    initialIndex: index
                )
            },
            sensitive: viewModel.statusData.sensitive
        )
    }
}

struct StatusCardView: View {
    let card: UiCard
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let onPodcastCardTap: (UiCard) -> Void

    var body: some View {
        if viewModel.isPodcastCard {
            PodcastPreview(card: card)
                .onTapGesture {
                    onPodcastCardTap(card)
                }
        } else if appSettings.appearanceSettings.showLinkPreview, viewModel.shouldShowLinkPreview {
            LinkPreview(card: card)
        }
    }
}

struct StatusBottomContentView: View {
    let bottomContent: UiTimelineItemContentStatusBottomContent

    var body: some View {
        switch onEnum(of: bottomContent) {
        case let .reaction(data):
            ScrollView(.horizontal) {
                LazyHStack {
                    if !data.emojiReactions.isEmpty {
                        ForEach(0 ..< data.emojiReactions.count, id: \.self) { index in
                            let reaction = data.emojiReactions[index]
                            Button(action: {
                                reaction.onClicked()
                            }) {
                                HStack {
                                    if !reaction.url.isEmpty {
                                        KFImage(URL(string: reaction.url))
                                            .resizable()
                                            .scaledToFit()
                                    } else {
                                        Text(reaction.name)
                                    }
                                    Text(reaction.humanizedCount)
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

struct StatusDetailDateView: View {
    let createdAt: Date

    var body: some View {
        Spacer().frame(height: 4)
        HStack {
            Text(createdAt, style: .date)
            Text(createdAt, style: .time)
        }
        .opacity(0.6)
    }
}

struct StatusContentWarningView: View {
    let contentWarning: UiRichText
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

struct StatusMainContentView: View {
    let viewModel: StatusViewModel
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        if viewModel.hasContent {
            let content = viewModel.statusData.content
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

//appSettings.appearanceSettings.autoTranslate,
            if  viewModel.shouldShowTranslation {
                TranslatableText(originalText: content.raw)
            }
        } else {
            Text("")
                .font(.system(size: 16))
                .foregroundColor(theme.labelColor)
        }
    }
}
