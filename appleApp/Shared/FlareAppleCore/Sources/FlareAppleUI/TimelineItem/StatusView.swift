import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import FlareAppleCore
import AppleFontAwesome

public struct StatusView: View {
    @Environment(\.timelineAppearance.fullWidthPost) private var fullWidthPost
    @Environment(\.timelineAppearance.showLinkPreview) private var showLinkPreview
    @Environment(\.timelineAppearance.compatLinkPreview) private var compatLinkPreview
    @Environment(\.timelineAppearance.postActionStyle) private var postActionStyle
    @Environment(\.timelineAppearance.showPlatformLogo) private var showPlatformLogo
    @Environment(\.timelineAppearance.expandContentWarning) private var expandContentWarning
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @Environment(\.openURL) private var openURL
    private let data: UiTimelineV2.Post
    private let isDetail: Bool
    private let isQuote: Bool
    private let withLeadingPadding: Bool
    private let showMedia: Bool
    private let maxLine: Int
    private let showExpandTextButton: Bool
    private let forceHideActions: Bool
    private let showTranslate: Bool
    private let showParents: Bool
    @State private var expand = false

    public init(
        data: UiTimelineV2.Post,
        isDetail: Bool = false,
        isQuote: Bool = false,
        withLeadingPadding: Bool = false,
        showMedia: Bool = true,
        maxLine: Int = 5,
        showExpandTextButton: Bool = true,
        forceHideActions: Bool = false,
        showTranslate: Bool = true,
        showParents: Bool = true
    ) {
        self.data = data
        self.isDetail = isDetail
        self.isQuote = isQuote
        self.withLeadingPadding = withLeadingPadding
        self.showMedia = showMedia
        self.maxLine = maxLine
        self.showExpandTextButton = showExpandTextButton
        self.forceHideActions = forceHideActions
        self.showTranslate = showTranslate
        self.showParents = showParents
    }

    private var showAsFullWidth: Bool {
        (!fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }
    public var body: some View {
        let parents = Array(data.parents)
        let user = data.user
        let replyToHandle = data.replyToHandle
        let contentWarning = data.contentWarning
        let contentWarningIsEmpty = contentWarning?.isEmpty ?? true
        let content = data.content
        let contentIsEmpty = content.isEmpty
        let shouldExpandTextByDefault = data.shouldExpandTextByDefault
        let poll = data.poll
        let images = Array(data.images)
        let hasImages = !images.isEmpty
        let sensitive = data.sensitive
        let card = data.card
        let quotes = Array(data.quote)
        let hasQuotes = !quotes.isEmpty
        let sourceChannelName = data.sourceChannel?.name
        let emojiReactions = Array(data.emojiReactions)
        let hasEmojiReactions = !emojiReactions.isEmpty
        let visibility = data.visibility
        let translationDisplayState = data.translationDisplayState
        let platformType = data.platformType
        let createdAt = data.createdAt
        let actions = Array(data.actions)
        let accountType = data.accountType
        let statusKey = data.statusKey

        VStack(
            alignment: .leading,
            spacing: 0
        ) {
            if !parents.isEmpty, showParents {
                ForEach(parents, id: \.itemKey) { parent in
                    VStack(
                        spacing: nil
                    ) {
                        StatusView(data: parent, withLeadingPadding: true)
                        Spacer()
                            .frame(height: 8)
                    }
                    .overlay(alignment: .leading) {
                        Rectangle()
                            .fill(Color.flareSeparator)
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
                if showAsFullWidth, let user {
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
                    if let user {
                        if showAsFullWidth {
                            UserOnelineView(data: user, showAvatar: false) {
                                topEndContent(
                                    visibility: visibility,
                                    translationDisplayState: translationDisplayState,
                                    platformType: platformType,
                                    createdAt: createdAt,
                                    accountType: accountType,
                                    statusKey: statusKey
                                )
                            } onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        } else if isQuote {
                            UserOnelineView(data: user, showAvatar: true) {
                                topEndContent(
                                    visibility: visibility,
                                    translationDisplayState: translationDisplayState,
                                    platformType: platformType,
                                    createdAt: createdAt,
                                    accountType: accountType,
                                    statusKey: statusKey
                                )
                            } onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        } else {
                            UserCompatView(data: user) {
                                topEndContent(
                                    visibility: visibility,
                                    translationDisplayState: translationDisplayState,
                                    platformType: platformType,
                                    createdAt: createdAt,
                                    accountType: accountType,
                                    statusKey: statusKey
                                )
                            } onClicked: {
                                user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            }
                        }
                    }
                    VStack(
                        alignment: .leading,
                        spacing: 8,
                    ) {
                        if let replyToHandle {
                            HStack {
                                Image(fontAwesome: .reply)
                                Text("Reply to \(replyToHandle)")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        if let contentWarning, !contentWarningIsEmpty {
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

                        if expand || expandContentWarning || contentWarningIsEmpty {
                            if !contentIsEmpty {
                                RichText(text: content)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .if(isDetail) { richText in
                                        richText
                                            .textSelection(.enabled)
                                    } else: { richText in
                                        richText
                                            .if((shouldExpandTextByDefault || expand) && maxLine >= 5, if: { view in
                                                view.lineLimit(nil)
                                            }, else: { view in
                                                view.lineLimit(maxLine)
                                            })
                                    }
                                    .fixedSize(horizontal: false, vertical: true)
                                if !shouldExpandTextByDefault, !isDetail, !expand, showExpandTextButton {
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
                            StatusTranslateView(content: content, contentWarning: contentWarning)
                        }
                        
                        if let poll, showMedia {
                            StatusPollView(data: poll)
                        }
                        
                        if hasImages, showMedia {
                            StatusMediaContent(data: images, sensitive: sensitive, cornerRadius: isQuote ? 12 : 16) { media, index in
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
                                    statusKey: statusKey,
                                    accountType: accountType,
                                    index: Int32(index),
                                    preview: preview
                                )
                                if let url = URL(string: route.toUri()) {
                                    openURL(url)
                                }
                            }
                        }

                        if let card, showMedia, !hasImages, !hasQuotes, showLinkPreview {
                            if compatLinkPreview {
                                StatusCompatCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                            } else {
                                StatusCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                            }
                        }

                        if hasQuotes, !isQuote {
                            VStack {
                                ForEach(quotes, id: \.itemKey) { quote in
                                    StatusView(data: quote, isQuote: true, forceHideActions: true)
                                    if quotes.last != quote {
                                        Divider()
                                    }
                                }
                            }
                            .padding(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.flareSeparator, lineWidth: 1)
                            )
                        }

                        if showMedia, !isQuote {
                            if let sourceChannelName {
                                HStack {
                                    Image(fontAwesome: .tv)
                                    Text(sourceChannelName)
                                }
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                            }
                            if hasEmojiReactions {
                                StatusReactionView(data: emojiReactions, isDetail: isDetail)
                            }
                        }

                        if isDetail {
                            DateTimeText(data: createdAt, fullTime: true)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        if (postActionStyle != .hidden || isDetail) && !forceHideActions {
                            StatusActionsView(data: actions, useText: false)
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
    
    private func topEndContent(
        visibility: UiTimelineV2.PostVisibility?,
        translationDisplayState: TranslationDisplayState,
        platformType: PlatformType,
        createdAt: UiDateTime,
        accountType: AccountType,
        statusKey: MicroBlogKey
    ) -> some View {
        HStack {
            if let visibility {
                StatusVisibilityView(data: visibility)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if translationDisplayState != .hidden {
                TranslateStatusComponent(data: translationDisplayState)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if showPlatformLogo {
                switch platformType {
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
                case .fanbox:
                    Image(fontAwesome: .pixiv)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if !isDetail {
                DateTimeText(data: createdAt)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if agentEnabled, !isQuote {
                Button {
                    let route = DeeplinkRoute.StatusInsight(
                        accountType: accountType,
                        statusKey: statusKey
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
