import SwiftUI
import MarkdownUI
import shared
import NetworkImage

struct CommonStatusComponent<HeaderTrailing>: View where HeaderTrailing: View {
    @Environment(\.appSettings) private var appSettings
    @State var expanded: Bool
    @State var showMedia: Bool
    let content: String
    let contentWarning: String?
    let user: UiUser
    let medias: [UiMedia]
    let timestamp: Int64
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    let onMediaClick: (UiMedia) -> Void
    let sensitive: Bool
    let card: UiCard?
    init(
        content: String,
        contentWarning: String?,
        user: UiUser,
        medias: [UiMedia],
        timestamp: Int64,
        headerTrailing: @escaping () -> HeaderTrailing,
        onMediaClick: @escaping (UiMedia) -> Void,
        sensitive: Bool,
        card: UiCard?
    ) {
        self.content = content
        self.contentWarning = contentWarning
        self.user = user
        self.medias = medias
        self.timestamp = timestamp
        self.headerTrailing = headerTrailing
        self.onMediaClick = onMediaClick
        self.sensitive = sensitive
        self.card = card
        _expanded = State(initialValue: contentWarning == nil)
        showMedia = false
    }
    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                UserComponent(user: user)
                Spacer()
                HStack {
                    headerTrailing()
                    dateFormatter(Date(timeIntervalSince1970: .init(integerLiteral: timestamp)))
                }
                .foregroundColor(.gray)
                .font(.caption)
            }
            if let cwText = contentWarning, !cwText.isEmpty {
                Button(action: {
                    withAnimation {
                        expanded = !expanded
                    }
                }, label: {
                    Image(systemName: "exclamationmark.triangle")
                    Text(cwText)
                    Spacer()
                    if expanded {
                        Image(systemName: "arrowtriangle.down.circle.fill")
                    } else {
                        Image(systemName: "arrowtriangle.left.circle.fill")
                    }
                })
                .opacity(0.5)
                .buttonStyle(.plain)
                if expanded {
                    Spacer()
                        .frame(height: 8)
                }
            }
            if expanded {
                Markdown(content)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
            }
            if !medias.isEmpty {
                Spacer()
                    .frame(height: 8)
                if appSettings.appearanceSettings.showMedia || showMedia {
                    MediaComponent(
                        hideSensitive: sensitive && !appSettings.appearanceSettings.showSensitiveContent,
                        medias: medias,
                        onMediaClick: onMediaClick
                    )
                } else {
                    Button {
                        withAnimation {
                            showMedia = true
                        }
                    } label: {
                        Label(
                            title: { Text("status_display_media") },
                            icon: { Image(systemName: "photo") }
                        )
                    }
                    .buttonStyle(.borderless)
                }
            }
            if let card = card, appSettings.appearanceSettings.showLinkPreview {
                LinkPreview(card: card)
            }
        }.frame(alignment: .leading)
    }
}

func dateFormatter(_ date: Date) -> some View {
    let now = Date()
    let oneDayAgo = Calendar.current.date(byAdding: .day, value: -1, to: now)!
    if date > oneDayAgo {
        // If the date is within the last day, use the .timer style
        return Text(date, style: .relative)
    } else {
        // Otherwise, use the .dateTime style
        return Text(date, style: .date)
    }
}
