import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct StatusView: View {
    @Environment(\.appearanceSettings.fullWidthPost) private var fullWidthPost
    @Environment(\.appearanceSettings.showLinkPreview) private var showLinkPreview
    @Environment(\.appearanceSettings.compatLinkPreview) private var compatLinkPreview
    @Environment(\.appearanceSettings.showActions) private var showActions
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentStatus
    let isDetail: Bool
    let isQuote: Bool
    let withLeadingPadding: Bool
    let showMedia: Bool
    @State private var expand = false
    private var showAsFullWidth: Bool {
        (!fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 0
        ) {
            if !data.parents.isEmpty {
                ForEach(data.parents, id: \.itemKey) { parent in
                    VStack(
                        spacing: nil
                    ) {
                        StatusView(data: parent, withLeadingPadding: true)
                        Spacer()
                            .frame(height: 8)
                    }
                    .overlay(alignment: .leading) {
                        Rectangle()
                            .fill(.separator)
                            .frame(minWidth: 1, maxWidth: 1, alignment: .leading)
                            .padding(.leading, 22)
                            .padding(.top, 44)
                    }
                }
            }
            HStack(
                alignment: .top,
                spacing: 8,
            ) {
                if showAsFullWidth, let user = data.user {
                    AvatarView(data: user.avatar)
                        .frame(width: 44, height: 44)
                        .onTapGesture {
                            user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                        }
                }
                VStack(
                    alignment: .leading,
                    spacing: nil,
                ) {
                    if let user = data.user {
                        if showAsFullWidth {
                            UserOnelineView(data: user, showAvatar: false) {
                                topEndContent
                            } onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        } else if isQuote {
                            UserOnelineView(data: user, showAvatar: true) {
                                topEndContent
                            } onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        } else {
                            UserCompatView(data: user) {
                                topEndContent
                            } onClicked: {
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
                                .if(isDetail) { richText in
                                    richText
                                        .textSelection(.enabled)
                                }
                            
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
                                            .textSelection(.enabled)
                                    } else: { richText in
                                        richText
                                            .if(data.shouldExpandTextByDefault || expand, if: { view in
                                                view.lineLimit(nil)
                                            }, else: { view in
                                                view.lineLimit(5)
                                            })
                                    }
                                if !data.shouldExpandTextByDefault && !isDetail && !expand {
                                    Button {
                                        withAnimation {
                                            expand = true
                                        }
                                    } label: {
                                        Text("mastodon_item_show_more")
                                    }
                                    .buttonStyle(.borderless)
                                }
                            }
                        }
                        
                        if isDetail {
                            StatusTranslateView(content: data.content, contentWarning: data.contentWarning)
                        }
                        
                        if let poll = data.poll, showMedia {
                            StatusPollView(data: poll)
                        }
                        
                        if !data.images.isEmpty, showMedia {
                            StatusMediaContent(data: data.images, sensitive: data.sensitive)
                        }

                        if let card = data.card, showMedia, data.images.isEmpty, data.quote.isEmpty, showLinkPreview {
                            if compatLinkPreview {
                                StatusCompatCardView(data: card)
                            } else {
                                StatusCardView(data: card)
                            }
                        }

                        if !data.quote.isEmpty, !isQuote {
                            VStack {
                                ForEach(data.quote, id: \.itemKey) { quote in
                                    StatusView(data: quote, isQuote: true)
                                    if data.quote.last != quote {
                                        Divider()
                                    }
                                }
                            }
                            .padding(8)
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

                        if !isQuote, (showActions || isDetail) {
                            StatusActionsView(data: data.actions, useText: false)
                                .font(isDetail ? .body : .footnote)
                                .foregroundStyle(isDetail ? .primary : .secondary)
                                .padding(.top, 4)
                        }
                    }
                }
//                .if(!isDetail) { view in
//                    view
//                        .contextMenu {
//                            StatusActionsView(data: data.actions, useText: true)
//                        }
//                }
            }
        }
        .contentShape(.rect)
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
    
    var topEndContent: some View {
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
}

struct StatusMediaContent: View {
    @Environment(\.appearanceSettings.showMedia) private var showMedia
    @Environment(\.appearanceSettings.showSensitiveContent) private var showSensitiveContent
    @State private var expandMedia = false
    let data: [any UiMedia]
    let sensitive: Bool
    var body: some View {
        if showMedia || expandMedia {
            StatusMediaView(data: data, sensitive: !(showSensitiveContent) && sensitive)
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
