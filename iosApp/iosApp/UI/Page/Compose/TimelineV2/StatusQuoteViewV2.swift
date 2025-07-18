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
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    let item: TimelineItem // 使用Swift TimelineItem类型
    let onMediaClick: (Int, Media) -> Void // 使用Swift Media类型

    var body: some View {
        Button(action: {
            // 🔥 实现引用推文点击跳转到详情页面
            let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
            let statusKey = item.createMicroBlogKey()

            FlareLog.debug("QuotedStatus Navigate to status detail: \(item.id)")
            router.navigate(to: .statusDetail(
                accountType: accountType,
                statusKey: statusKey
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
                            .markdownInlineImageProvider(.emoji)
                        Text(user.handle)
                            .lineLimit(1)
                            .font(.subheadline)
                            .foregroundColor(.gray)
                        Spacer()
                        dateFormatter(item.timestamp) // 使用TimelineItem的timestamp
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal, 9)
                }

                // 原文和翻译
                FlareText(item.content.raw, item.content.markdown, style: FlareTextStyle.Style(
                    font: Font.scaledBodyFont,
                    textColor: UIColor(theme.labelColor),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ), isRTL: item.content.isRTL)
                    .onLinkTap { url in
                        openURL(url)
                    }
                    .font(.system(size: 16))

                if appSettings.appearanceSettings.autoTranslate {
                    TranslatableText(originalText: item.content.raw)
                }

                Spacer()
                    .frame(height: 8)
                if !item.images.isEmpty {
                    if appSettings.appearanceSettings.showMedia || showMedia {
                        // 使用V2版本的MediaComponent
                        MediaComponentV2(
                            hideSensitive: item.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                            medias: [], // 暂时为空，需要将item.images转换为UiMedia数组
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
