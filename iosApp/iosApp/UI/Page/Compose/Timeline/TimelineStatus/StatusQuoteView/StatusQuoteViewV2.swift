import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
 
import SwiftUI
import UIKit

enum SwiftAccountType {
    case specific(accountKey: String)
    case active
    case guest
}

struct SwiftMicroBlogKey {
    let id: String
    let host: String

    init(id: String, host: String) {
        self.id = id
        self.host = host
    }
}

struct StatusQuoteViewV2: View {
    let quotes: [TimelineItem]
    let onMediaClick: (Int, Media) -> Void

    var body: some View {
        Spacer().frame(height: 10)

        VStack {
            ForEach(0 ..< quotes.count, id: \.self) { index in
                let quote = quotes[index]
                QuotedStatusV2(item: quote, onMediaClick: onMediaClick)
                    .foregroundColor(.gray)

                if index != quotes.count - 1 {
                    Divider()
                }
            }
        }
        .padding(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
        )
        .cornerRadius(8)
    }
}

// 引用
struct QuotedStatusV2: View {
    @State private var showMedia: Bool = false

    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    let item: TimelineItem
    let onMediaClick: (Int, Media) -> Void

    var body: some View {
        Button(action: {
            let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
            let statusKey = item.createMicroBlogKey()

            router.navigate(to: .statusDetailV2(
                accountType: accountType,
                statusKey: statusKey,
                preloadItem: item
            ))
        }, label: {
            VStack(alignment: .leading) {
                if let user = item.user {
                    Spacer()
                        .frame(height: 8)
                    HStack {
                        UserAvatar(data: user.avatar, size: 20)
                        Markdown(user.name.markdown)
                            .lineLimit(1)
                            .font(.subheadline)
                            .markdownTheme(.flareMarkdownStyle(using: theme.flareTextBodyTextStyle, fontScale: theme.fontSizeScale))
                            .markdownInlineImageProvider(.emoji)
                        Text(user.handle)
                            .lineLimit(1)
                            .font(.subheadline)
                            .foregroundColor(.gray)
                        Spacer()
                        dateFormatter(item.timestamp)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal, 9)
                }

                FlareText(
                    item.content.raw,
                    item.content.markdown,
                    textType: .flareTextTypeBody,
                    isRTL: item.content.isRTL
                )
                .onLinkTap { url in
                    router.handleDeepLink(url)
                }
                .font(.system(size: 16))

                // if appSettings.appearanceSettings.autoTranslate {
                //     TranslatableText(originalText: item.content.raw)
                // }

                Spacer()
                    .frame(height: 8)
                if !item.images.isEmpty {
                    if appSettings.appearanceSettings.showMedia || showMedia {
                        MediaComponentV2(
                            hideSensitive: item.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                            medias: [],
                            onMediaClick: handleMediaClick,
                            sensitive: item.sensitive
                        )
                    } else {
                        Button {
                            withAnimation {
                                showMedia = true
                            }
                        } label: {
                            Label("status_display_media", systemImage: "photo")
                        }
                        .padding()
                        .buttonStyle(.borderless)
                    }
                }
            }
        })
        .buttonStyle(.plain)
    }

    private func handleMediaClick(_ index: Int, _ media: Media) {
        PhotoBrowserManagerV2.shared.showPhotoBrowser(
            media: media,
            images: item.images,
            initialIndex: index
        )
    }
}
