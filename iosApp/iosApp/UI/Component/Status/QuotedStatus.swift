import SwiftUI
import shared
import MarkdownUI

struct QuotedStatus: View {
    @State private var showMedia: Bool = false
    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    let data: UiTimelineItemContentStatus
    let onMediaClick: (Int, UiMedia) -> Void
    var body: some View {
        Button(action: {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openURL: openURL)))
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
                    .padding(.horizontal, 8)
                }
                Markdown(data.content.markdown)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
                    .padding(.horizontal, 8)
                Spacer()
                    .frame(height: 8)
                if !data.images.isEmpty {
                    if appSettings.appearanceSettings.showMedia || showMedia {
                        MediaComponent(
                            hideSensitive: data.sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                            medias: data.images,
                            onMediaClick: onMediaClick,
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
}
