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
    // 修改参数：使用TimelineItem替代StatusViewModel，去掉shared引用
    let item: TimelineItem
    let isDetailView: Bool
    let enableTranslation: Bool
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction
    let onMediaClick: (Int, Media) -> Void      // 使用Swift Media类型
    let onPodcastCardTap: (Card) -> Void        // 使用Swift Card类型
    
    var body: some View {
        VStack(alignment: .leading) {
            // Reply content
            if item.hasAboveTextContent, let aboveTextContent = item.aboveTextContent {
                StatusReplyViewV2(aboveTextContent: aboveTextContent)
            }

            // Content warning
            if item.hasContentWarning, let cwText = item.contentWarning {
                StatusContentWarningViewV2(contentWarning: cwText, theme: theme, openURL: openURL)
            }

            Spacer().frame(height: 10)

            // Main content
            StatusMainContentViewV2(
                item: item,
                enableTranslation: enableTranslation,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL
            )
            
            // Media
            let _ = print("📱 [StatusContentViewV2] 检查媒体显示")
            let _ = print("📱 [StatusContentViewV2] item.hasImages: \(item.hasImages)")
            let _ = print("📱 [StatusContentViewV2] item.images.count: \(item.images.count)")

            if item.hasImages {
                let _ = print("📱 [StatusContentViewV2] 显示StatusMediaViewV2")
                StatusMediaViewV2(
                    item: item,
                    appSettings: appSettings,
                    onMediaClick: onMediaClick
                )
            } else {
                let _ = print("📱 [StatusContentViewV2] 跳过媒体显示 - hasImages为false")
            }

            // Card (Podcast or Link Preview)
            if item.hasCard, let card = item.card {
                StatusCardViewV2(
                    card: card,
                    item: item,
                    appSettings: appSettings,
                    onPodcastCardTap: onPodcastCardTap
                )
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
                StatusDetailDateViewV2(createdAt: item.timestamp)
            }
        }
    }
}

struct StatusReplyViewV2: View {
    let aboveTextContent: AboveTextContent  // 使用Swift AboveTextContent类型

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
    let item: TimelineItem           // 使用TimelineItem替代StatusViewModel
    let appSettings: AppSettings
    let onMediaClick: (Int, Media) -> Void  // 使用Swift Media类型

    var body: some View {
        // 添加详细日志
        let _ = print("🖼️ [StatusMediaViewV2] 开始渲染媒体")
        let _ = print("🖼️ [StatusMediaViewV2] item.hasImages: \(item.hasImages)")
        let _ = print("🖼️ [StatusMediaViewV2] item.images.count: \(item.images.count)")
        let _ = print("🖼️ [StatusMediaViewV2] item.images: \(item.images)")
        let _ = print("🖼️ [StatusMediaViewV2] item.sensitive: \(item.sensitive)")
        let _ = print("🖼️ [StatusMediaViewV2] showSensitiveContent: \(appSettings.appearanceSettings.showSensitiveContent)")

        Spacer().frame(height: 8)

        // 使用V2版本的MediaComponent，传递正确的媒体数据
        MediaComponentV2(
            hideSensitive: item.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
            medias: item.images, // ✅ 修复：使用item.images而不是空数组
            onMediaClick: { index, media in
                print("🖼️ [StatusMediaViewV2] 媒体点击: index=\(index), media=\(media)")
                PhotoBrowserManagerV2.shared.showPhotoBrowser(
                    media: media,
                    images: item.images, // ✅ 修复：使用item.images而不是空数组
                    initialIndex: index
                )
            },
            sensitive: item.sensitive
        )
    }
}

struct StatusCardViewV2: View {
    let card: Card                   // 使用Swift Card类型
    let item: TimelineItem          // 使用TimelineItem替代StatusViewModel
    let appSettings: AppSettings
    let onPodcastCardTap: (Card) -> Void  // 使用Swift Card类型

    var body: some View {
        if item.isPodcastCard {
            // 使用V2版本的PodcastPreview，直接传递Swift Card
            PodcastPreviewV2(card: card)
                .onTapGesture {
                    onPodcastCardTap(card)
                }
        } else if appSettings.appearanceSettings.showLinkPreview, item.shouldShowLinkPreview {
            // 使用V2版本的LinkPreview，直接传递Swift Card
            LinkPreviewV2(card: card)
        }
    }
}

struct StatusBottomContentViewV2: View {
    let bottomContent: BottomContent  // 使用Swift BottomContent类型

    var body: some View {
        switch bottomContent {
        case let .reaction(emojiReactions):
            ScrollView(.horizontal) {
                LazyHStack {
                    if !emojiReactions.isEmpty {
                        ForEach(0 ..< emojiReactions.count, id: \.self) { index in
                            let reaction = emojiReactions[index]
                            Button(action: {
                                // TODO: 需要实现Swift版本的点击处理
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
                                    Text("\(reaction.count)")  // 使用Swift Int类型
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

struct StatusDetailDateViewV2: View {
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

struct StatusContentWarningViewV2: View {
    let contentWarning: RichText     // 使用Swift RichText类型
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
    let item: TimelineItem           // 使用TimelineItem替代StatusViewModel
    let enableTranslation: Bool      // 从StatusViewModel中提取
    let appSettings: AppSettings
    let theme: FlareTheme
    let openURL: OpenURLAction

    var body: some View {
        if item.hasContent {
            let content = item.content  // 使用Swift RichText类型
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

            if appSettings.appearanceSettings.autoTranslate, enableTranslation {
                TranslatableText(originalText: content.raw)
            }
        } else {
            Text("")
                .font(.system(size: 16))
                .foregroundColor(theme.labelColor)
        }
    }
}
