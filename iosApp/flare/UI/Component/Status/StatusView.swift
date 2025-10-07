import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct StatusView: View {
    @Environment(\.themeSettings) private var themeSettings
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentStatus
    let isDetail: Bool
    let isQuote: Bool
    let withLeadingPadding: Bool
    let showMedia: Bool
    @State private var expand = false
    @State private var expandMedia = false
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 0
        ) {
            if !data.parents.isEmpty {
                ForEach(data.parents, id: \.itemKey) { parent in
                    StatusView(data: parent, withLeadingPadding: true)
                    .id(parent.itemKey)
                    .overlay(alignment: .leading) {
                        Rectangle()
                            .fill(.separator)
                            .frame(minWidth: 1, maxWidth: 1, alignment: .leading)
                            .padding(.leading, 22)
                            .padding(.top, 44)

                    }
                }
            }
            VStack(
                alignment: .leading,
            ) {
                if let user = data.user {
                    if isQuote {
                        UserOnelineView(data: user) {
                            HStack {
                                switch onEnum(of: data.topEndContent) {
                                case .visibility(let visibility):
                                    StatusVisibilityView(data: visibility.visibility)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                case .none:
                                    EmptyView()
                                }
                                if !isDetail {
                                    DateTimeText(data: data.createdAt)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .onTapGesture {
                            user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                        }
                    } else {
                        UserCompatView(data: user) {
                            HStack {
                                switch onEnum(of: data.topEndContent) {
                                case .visibility(let visibility):
                                    StatusVisibilityView(data: visibility.visibility)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                case .none:
                                    EmptyView()
                                }
                                if !isDetail {
                                    DateTimeText(data: data.createdAt)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .onTapGesture {
                            user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                        }
                    }
                }
                VStack(
                    alignment: .leading,
                    spacing: 8,
                ) {
                    if let aboveTextContent = data.aboveTextContent {
                        switch onEnum(of: aboveTextContent) {
                        case .replyTo(let replyTo):
                            HStack {
                                Image("fa-reply")
                                Text("Reply to \(replyTo.handle)")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                    }
                    if let contentWarning = data.contentWarning, !contentWarning.isEmpty {
                        RichText(text: contentWarning)
                        Button {
                            withAnimation {
                                expand = !expand
                            }
                        } label: {
                            if expand {
                                Text("mastodon_item_show_less")
                            } else {
                                Text("mastodon_item_show_more")
                            }
                        }
                        .backport
                        .glassProminentButtonStyle()
                    }

                    if expand || data.contentWarning == nil || data.contentWarning?.isEmpty == true {
                        if !data.content.isEmpty {
                            RichText(text: data.content)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .if(isDetail) { richText in
                                    richText
                                } else: { richText in
                                    richText
                                        .lineLimit(5)
                                }

                        }
                    }
                    if let poll = data.poll, showMedia {
                        StatusPollView(data: poll)
                    }
                    
                    if !data.images.isEmpty, showMedia {
                        if themeSettings.appearanceSettings.showMedia || expandMedia {
                            StatusMediaView(data: data.images, sensitive: !(themeSettings.appearanceSettings.showSensitiveContent) && data.sensitive)
                        } else {
                            Button {
                                withAnimation {
                                    expandMedia = true
                                }
                            } label: {
                                Label {
                                    Text("show_media_button", comment: "Button to show media attachments" )
                                } icon: {
                                    Image("fa-image")
                                }
                            }
                            .backport
                            .glassButtonStyle(fallbackStyle: .bordered)
                        }
                    }

                    if let card = data.card, showMedia, data.images.isEmpty, data.quote.isEmpty, themeSettings.appearanceSettings.showLinkPreview {
                        if themeSettings.appearanceSettings.compatLinkPreview {
                            StatusCompatCardView(data: card)
                        } else {
                            StatusCardView(data: card)
                        }
                    }

                    if !data.quote.isEmpty {
                        VStack {
                            ForEach(data.quote, id: \.itemKey) { quote in
                                StatusView(data: quote, isQuote: true)
                                if data.quote.last != quote {
                                    Divider()
                                }
                            }
                        }
                        .padding()
                        .clipShape(.rect(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(.separator), lineWidth: 1)
                        )
                    }

                    if case .reaction(let reaction) = onEnum(of: data.bottomContent), showMedia {
                        if !reaction.emojiReactions.isEmpty {
                            StatusReactionView(data: reaction)
                        }
                    }

                    if isDetail {
                        DateTimeText(data: data.createdAt, fullTime: true)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if !isQuote, (themeSettings.appearanceSettings.showActions || isDetail) {
                        StatusActionsView(data: data.actions, useText: false)
                            .font(isDetail ? .body : .footnote)
                            .foregroundStyle(isDetail ? .primary : .secondary)
                    }
                }
                .if(withLeadingPadding, if: { stack in
                    stack.padding(.leading, 50)
                }, else: { stack in
                    stack
                })
            }
            .contextMenu {
                StatusActionsView(data: data.actions, useText: true)
            }
        }
        .contentShape(.rect)
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}

extension StatusView {
    init(data: UiTimeline.ItemContentStatus, isDetail: Bool) {
        self.data = data
        self.isDetail = isDetail
        self.isQuote = false
        self.withLeadingPadding = false
        self.showMedia = true
    }
    init(data: UiTimeline.ItemContentStatus, detailStatusKey: MicroBlogKey?) {
        self.data = data
        self.isDetail = data.statusKey == detailStatusKey
        self.isQuote = false
        self.withLeadingPadding = false
        self.showMedia = true
    }
    init(data: UiTimeline.ItemContentStatus, isQuote: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = isQuote
        self.withLeadingPadding = false
        self.showMedia = true
    }
    init(data: UiTimeline.ItemContentStatus, withLeadingPadding: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = false
        self.withLeadingPadding = withLeadingPadding
        self.showMedia = true
    }
    init(data: UiTimeline.ItemContentStatus, isQuote: Bool, showMedia: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = isQuote
        self.withLeadingPadding = false
        self.showMedia = showMedia
    }
}
