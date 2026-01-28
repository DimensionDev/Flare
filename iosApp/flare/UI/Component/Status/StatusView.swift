import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct StatusView: View {
    @Environment(\.appearanceSettings.fullWidthPost) private var fullWidthPost
    @Environment(\.appearanceSettings.showLinkPreview) private var showLinkPreview
    @Environment(\.appearanceSettings.compatLinkPreview) private var compatLinkPreview
    @Environment(\.appearanceSettings.postActionStyle) private var postActionStyle
    @Environment(\.appearanceSettings.showPlatformLogo) private var showPlatformLogo
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentStatus
    var isDetail: Bool = false
    var isQuote: Bool = false
    var withLeadingPadding: Bool = false
    var showMedia: Bool = true
    var maxLine: Int = 5
    var showExpandTextButton: Bool = true
    var forceHideActions: Bool = false
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
                                .fixedSize(horizontal: false, vertical: true)
                            
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
                                            .if((data.shouldExpandTextByDefault || expand) && maxLine >= 5, if: { view in
                                                view.lineLimit(nil)
                                            }, else: { view in
                                                view.lineLimit(maxLine)
                                            })
                                    }
                                    .fixedSize(horizontal: false, vertical: true)
                                if !data.shouldExpandTextByDefault, !isDetail, !expand, showExpandTextButton {
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
                            StatusMediaContent(data: data.images, sensitive: data.sensitive) { media, index in
                                data.onMediaClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)), media, .init(int: .init(index)))
                            }
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
                                    StatusView(data: quote, isQuote: true, forceHideActions: true)
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

                        if (postActionStyle != .hidden || isDetail) && !forceHideActions {
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
            if showPlatformLogo {
                switch data.platformType {
                case .mastodon:
                    Image("fa-mastodon")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .misskey:
                    Image("fa-misskey")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .bluesky:
                    Image("fa-bluesky")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .xQt:
                    Image("fa-x-twitter")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .vvo:
                    Image("fa-weibo")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
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
    let onMediaClicked: (any UiMedia, Int) -> Void
    var body: some View {
        if showMedia || expandMedia {
            StatusMediaView(data: data, sensitive: !(showSensitiveContent) && sensitive, onMediaClicked: onMediaClicked)
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

//extension StatusView {
//    init(
//        data: UiTimeline.ItemContentStatus, 
//        isDetail: Bool = false, 
//        isQuote: Bool = false,
//        withLeadingPadding: Bool = false,
//        showMedia: Bool = true,
//        maxLine: Int = 5, 
//        showExpandTextButton: Bool = true, 
//        forceHideActions: Bool = false
//    ) {
//        self.data = data
//        self.isDetail = isDetail
//        self.isQuote = isQuote
//        self.withLeadingPadding = withLeadingPadding
//        self.showMedia = showMedia
//        self.maxLine = maxLine
//        self.showExpandTextButton = showExpandTextButton
//        self.forceHideActions = forceHideActions
//    }
//}
