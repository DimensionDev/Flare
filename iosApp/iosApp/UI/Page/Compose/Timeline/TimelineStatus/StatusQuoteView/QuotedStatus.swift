import MarkdownUI
import shared
import SwiftUI

// 引用
struct QuotedStatus: View {
    @State private var showMedia: Bool = false

    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, UiMedia) -> Void

    var body: some View {
        Button(action: {
            router.navigate(to: .statusDetailV2(
                accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
                statusKey: data.statusKey
            ))
        }, label: {
            VStack(alignment: .leading) {
                if let user = data.user {
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
                        dateFormatter(data.createdAt)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal, 9)
                }

                // 原文和翻译
                FlareText(data.content.raw, data.content.markdown, style: FlareTextStyle.Style(
                    font: Font.scaledBodyFont,
                    textColor: UIColor(theme.labelColor),
                    linkColor: UIColor(theme.tintColor),
                    mentionColor: UIColor(theme.tintColor),
                    hashtagColor: UIColor(theme.tintColor),
                    cashtagColor: UIColor(theme.tintColor)
                ), isRTL: data.content.isRTL)
                    .onLinkTap { url in
                        router.handleDeepLink(url)
                    }
                    .font(.system(size: 16))

                // if appSettings.appearanceSettings.autoTranslate {
                // TranslatableText(originalText: data.content.raw)
                // }

                Spacer()
                    .frame(height: 8)
                if !data.images.isEmpty {
                    if appSettings.appearanceSettings.showMedia || showMedia {
                        MediaComponent(
                            hideSensitive: data.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                            medias: data.images,
                            onMediaClick: handleMediaClick,
                            sensitive: data.sensitive
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

    private func handleMediaClick(_ index: Int, _ media: UiMedia) {
        // Show preview
        PhotoBrowserManager.shared.showPhotoBrowser(
            media: media,
            images: data.images,
            initialIndex: index
        )
    }
}
