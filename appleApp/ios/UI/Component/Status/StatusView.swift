import SwiftUI
import FlareAppleUI
import KotlinSharedUI
import SwiftUIBackports
import FlareAppleCore
import AppleFontAwesome

struct StatusView: View {
    @Environment(\.timelineAppearance.fullWidthPost) private var fullWidthPost
    @Environment(\.timelineAppearance.showLinkPreview) private var showLinkPreview
    @Environment(\.timelineAppearance.compatLinkPreview) private var compatLinkPreview
    @Environment(\.timelineAppearance.postActionStyle) private var postActionStyle
    @Environment(\.timelineAppearance.showPlatformLogo) private var showPlatformLogo
    @Environment(\.timelineAppearance.expandContentWarning) private var expandContentWarning
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @Environment(\.openURL) private var openURL
    let data: UiTimelineV2.Post
    var isDetail: Bool = false
    var isQuote: Bool = false
    var withLeadingPadding: Bool = false
    var showMedia: Bool = true
    var maxLine: Int = 5
    var showExpandTextButton: Bool = true
    var forceHideActions: Bool = false
    var showTranslate: Bool = true
    var showParents: Bool = true
    @State private var expand = false
    private var showAsFullWidth: Bool {
        (!fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 0
        ) {
            if !data.parents.isEmpty, showParents {
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
                    AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
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
                        if let replyToHandle = data.replyToHandle {
                            HStack {
                                Image(fontAwesome: .reply)
                                Text("Reply to \(replyToHandle)")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        if let contentWarning = data.contentWarning, !contentWarning.isEmpty {
                            RichText(text: contentWarning)
                                .fixedSize(horizontal: false, vertical: true)
                                .if(isDetail) { view in
                                    view.textSelection(.enabled)
                                }
                            
                            if !expandContentWarning {
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
                        }

                        if expand || expandContentWarning || data.contentWarning == nil || data.contentWarning?.isEmpty == true {
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
                        
                        if isDetail, showTranslate {
                            StatusTranslateView(content: data.content, contentWarning: data.contentWarning)
                        }
                        
                        if let poll = data.poll, showMedia {
                            StatusPollView(data: poll)
                        }
                        
                        if !data.images.isEmpty, showMedia {
                            StatusMediaContent(data: data.images, sensitive: data.sensitive, cornerRadius: isQuote ? 12 : 16) { media, index in
                                let preview: String? = switch onEnum(of: media) {
                                case .image(let image):
                                    image.previewUrl
                                case .video(let video):
                                    video.thumbnailUrl
                                case .gif(let gif):
                                    gif.previewUrl
                                case .audio:
                                    nil
                                }
                                let route = DeeplinkRoute.MediaStatusMedia(
                                    statusKey: data.statusKey,
                                    accountType: data.accountType,
                                    index: Int32(index),
                                    preview: preview
                                )
                                if let url = URL(string: route.toUri()) {
                                    openURL(url)
                                }
                            }
                        }

                        if let card = data.card, showMedia, data.images.isEmpty, data.quote.isEmpty, showLinkPreview {
                            if compatLinkPreview {
                                StatusCompatCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                            } else {
                                StatusCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                            }
                        }

                        if !data.quote.isEmpty, !isQuote {
                            VStack {
                                ForEach(0..<data.quote.count, id: \.self) { index in
                                    let quote = data.quote[index]
                                    StatusView(data: quote, isQuote: true, forceHideActions: true)
                                    if data.quote.last != quote {
                                        Divider()
                                    }
                                }
                            }
                            .padding(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                        }

                        if showMedia, !isQuote {
                            if let channel = data.sourceChannel {
                                HStack {
                                    Image(fontAwesome: .tv)
                                    Text(channel.name)
                                }
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            }
                            if !data.emojiReactions.isEmpty {
                                StatusReactionView(data: Array(data.emojiReactions), isDetail: isDetail)
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
//                            StatusActionsView(data: data.actions, useText: true, allowSpacer: false)
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
            if let visibility = data.visibility {
                StatusVisibilityView(data: visibility)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if data.translationDisplayState != .hidden {
                TranslateStatusComponent(data: data.translationDisplayState)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if showPlatformLogo {
                switch data.platformType {
                case .mastodon:
                    Image(fontAwesome: .mastodon)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .misskey:
                    Image(fontAwesome: .misskey)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .bluesky:
                    Image(fontAwesome: .bluesky)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .xQt:
                    Image(fontAwesome: .xTwitter)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .vvo:
                    Image(fontAwesome: .weibo)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .nostr:
                    Image(fontAwesome: .nostr)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                case .pixiv:
                    Image(fontAwesome: .pixiv)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if !isDetail {
                DateTimeText(data: data.createdAt)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if agentEnabled, !isQuote {
                Button {
                    let route = DeeplinkRoute.StatusInsight(
                        accountType: data.accountType,
                        statusKey: data.statusKey
                    )
                    if let url = URL(string: route.toUri()) {
                        openURL(url)
                    }
                } label: {
                    Image(fontAwesome: .robot)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("status_insight_title"))
            }
        }
    }
}

struct StatusMediaContent: View {
    @Environment(\.timelineAppearance.showMedia) private var showMedia
    @Environment(\.timelineAppearance.showSensitiveContent) private var showSensitiveContent
    @State private var expandMedia = false
    let data: [any UiMedia]
    let sensitive: Bool
    let cornerRadius: CGFloat
    let onMediaClicked: (any UiMedia, Int) -> Void
    var body: some View {
        if showMedia || expandMedia {
            StatusMediaView(data: data, sensitive: !(showSensitiveContent) && sensitive, cornerRadius: cornerRadius, onMediaClicked: onMediaClicked)
        } else {
            Button {
                withAnimation {
                    expandMedia = true
                }
            } label: {
                Label {
                    Text("show_media_button", comment: "Button to show media attachments" )
                } icon: {
                    Image(fontAwesome: .image)
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
