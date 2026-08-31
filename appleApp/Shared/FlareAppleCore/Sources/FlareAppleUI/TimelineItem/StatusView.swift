import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import FlareAppleCore

public typealias TimelineMediaOpenAction = (UiTimelineV2.Post, any UiMedia, Int) -> Void
public enum TimelineMediaMenuAction {
    case download
    case downloadAll
    case shareImage
    case copyLink
}
public typealias TimelineMediaActionHandler = (UiTimelineV2.Post, any UiMedia, TimelineMediaMenuAction) -> Void

private struct TimelineMediaOpenActionKey: EnvironmentKey {
    static let defaultValue: TimelineMediaOpenAction? = nil
}

private struct TimelineMediaActionHandlerKey: EnvironmentKey {
    static let defaultValue: TimelineMediaActionHandler? = nil
}

public extension EnvironmentValues {
    var timelineMediaOpenAction: TimelineMediaOpenAction? {
        get { self[TimelineMediaOpenActionKey.self] }
        set { self[TimelineMediaOpenActionKey.self] = newValue }
    }

    var timelineMediaActionHandler: TimelineMediaActionHandler? {
        get { self[TimelineMediaActionHandlerKey.self] }
        set { self[TimelineMediaActionHandlerKey.self] = newValue }
    }
}

public struct StatusView: View {
    @Environment(\.timelineAppearance.fullWidthPost) private var fullWidthPost
    @Environment(\.timelineAppearance.showLinkPreview) private var showLinkPreview
    @Environment(\.timelineAppearance.compatLinkPreview) private var compatLinkPreview
    @Environment(\.timelineAppearance.postActionStyle) private var postActionStyle
    @Environment(\.timelineAppearance.showPlatformLogo) private var showPlatformLogo
    @Environment(\.timelineAppearance.expandContentWarning) private var expandContentWarning
    @Environment(\.timelineAppearance.lineLimit) private var appearanceLineLimit
    @Environment(\.timelineAppearance.mediaLayout) private var mediaLayout
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineMediaOpenAction) private var timelineMediaOpenAction
    private let data: UiTimelineV2.Post
    private let isDetail: Bool
    private let isQuote: Bool
    private let isClickable: Bool
    private let withLeadingPadding: Bool
    private let showMedia: Bool
    private let maxLine: Int?
    private let showExpandTextButton: Bool
    private let forceHideActions: Bool
    private let showTranslate: Bool
    private let showParents: Bool
    private let inlineParents: [UiTimelineV2.Post]
    private let quotes: [UiTimelineV2.Post]
    private let allowsMediaCarousel: Bool
    private let carouselOuterHorizontalPadding: CGFloat
    @State private var contentWarningExpanded = false
    @State private var textExpanded = false
    @State private var overflowingTextIndexes: Set<Int> = []

    public init(
        data: UiTimelineV2.Post,
        isDetail: Bool = false,
        isQuote: Bool = false,
        isClickable: Bool = true,
        withLeadingPadding: Bool = false,
        showMedia: Bool = true,
        maxLine: Int? = nil,
        showExpandTextButton: Bool = true,
        forceHideActions: Bool = false,
        showTranslate: Bool = true,
        showParents: Bool = true,
        inlineParents: [UiTimelineV2.Post] = [],
        quotes: [UiTimelineV2.Post] = [],
        allowsMediaCarousel: Bool = false,
        carouselOuterHorizontalPadding: CGFloat = 0
    ) {
        self.data = data
        self.isDetail = isDetail
        self.isQuote = isQuote
        self.isClickable = isClickable
        self.withLeadingPadding = withLeadingPadding
        self.showMedia = showMedia
        self.maxLine = maxLine
        self.showExpandTextButton = showExpandTextButton
        self.forceHideActions = forceHideActions
        self.showTranslate = showTranslate
        self.showParents = showParents
        self.inlineParents = inlineParents
        self.quotes = quotes
        self.allowsMediaCarousel = allowsMediaCarousel
        self.carouselOuterHorizontalPadding = carouselOuterHorizontalPadding
    }

    private var showAsFullWidth: Bool {
        (!fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }
    public var body: some View {
        let parents = inlineParents
        let user = data.user
        let carouselEdgePadding: CGFloat = isQuote ? 8 : carouselOuterHorizontalPadding
        let carouselLeadingPadding = carouselEdgePadding + (showAsFullWidth && user != nil ? 52 : 0)
        let replyToHandle = data.replyToHandle
        let translationDisplayed = data.translationDisplayState == .translated
        let contentWarnings: [UiRichText] = if let warning = data.contentWarning {
            if translationDisplayed, let translation = warning.translation {
                translateConfig.showOriginalWithTranslation ? [warning.original, translation] : [translation]
            } else {
                [warning.original]
            }
        } else {
            []
        }
        let contentWarningIsEmpty = contentWarnings.allSatisfy(\.isEmpty)
        let contents: [UiRichText] = if translationDisplayed, let translation = data.content.translation {
            translateConfig.showOriginalWithTranslation ? [data.content.original, translation] : [translation]
        } else {
            [data.content.original]
        }
        let shouldExpandTextByDefault = contentWarningIsEmpty && contents.reduce(0) { $0 + $1.innerText.count } <= 500
        let poll = data.poll
        let images = Array(data.images)
        let hasImages = !images.isEmpty
        let usesSideAvatarCarousel =
            showAsFullWidth &&
            user != nil &&
            showMedia &&
            allowsMediaCarousel &&
            mediaLayout == .carousel &&
            images.count > 1
        let sensitive = data.sensitive
        let card = data.card
        let quoteItems = self.quotes
        let hasQuotes = !quoteItems.isEmpty
        let sourceChannelName = data.sourceChannel?.name
        let emojiReactions = Array(data.emojiReactions)
        let hasEmojiReactions = !emojiReactions.isEmpty
        let visibility = data.visibility
        let translationDisplayState = data.translationDisplayState
        let createdAt = data.createdAt
        let actions = Array(data.actions)
        let accountType = data.accountType
        let statusKey = data.statusKey
        let effectiveLineLimit = max(maxLine ?? Int(appearanceLineLimit), 1)
        let usesExplicitShortLineLimit = maxLine != nil && effectiveLineLimit < 5
        let contentLineLimit: Int? =
            if isDetail || ((shouldExpandTextByDefault || textExpanded) && !usesExplicitShortLineLimit) {
                nil
            } else {
                effectiveLineLimit
            }
        let canExpandLineLimitedContent = contentLineLimit != nil && !isDetail && !textExpanded && showExpandTextButton
        let hasPreMediaBody =
            replyToHandle != nil ||
            !contentWarningIsEmpty ||
            contents.contains { !$0.isEmpty } ||
            (isDetail && showTranslate) ||
            (poll != nil && showMedia)

        VStack(
            alignment: .leading,
            spacing: 0
        ) {
            if !parents.isEmpty, showParents {
                ForEach(parents, id: \.itemKey) { parent in
                    VStack(
                        spacing: 0
                    ) {
                        StatusView(
                            data: parent,
                            withLeadingPadding: true,
                            allowsMediaCarousel: allowsMediaCarousel,
                            carouselOuterHorizontalPadding: carouselOuterHorizontalPadding
                        )
                        Spacer()
                            .frame(height: 18)
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
                    Button {
                        user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                    } label: {
                        AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(
                        Text(
                            verbatim: FlareAppleUILocalization.string(
                                "profile_open_user",
                                fallback: "Open profile for %@",
                                arguments: [user.handle.canonical]
                            )
                        )
                    )
                }
                VStack(
                    alignment: .leading,
                    spacing: 0,
                ) {
                    if user != nil || hasPreMediaBody {
                        VStack(
                            alignment: .leading,
                            spacing: 0,
                        ) {
                            if let user {
                                if showAsFullWidth {
                                    UserOnelineView(data: user, showAvatar: false) {
                                        topEndContent(
                                            visibility: visibility,
                                            translationDisplayState: translationDisplayState,
                                            platformIcon: data.platformIcon,
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
                                            platformIcon: data.platformIcon,
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
                                            platformIcon: data.platformIcon,
                                            createdAt: createdAt,
                                            accountType: accountType,
                                            statusKey: statusKey
                                        )
                                    } onClicked: {
                                        user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                                    }
                                    Spacer()
                                        .frame(height: 4)
                                }
                            }
                            if hasPreMediaBody {
                                VStack(
                                    alignment: .leading,
                                    spacing: 0,
                                ) {
                                    if let replyToHandle {
                                        HStack(spacing: 4) {
                                            Image(fontAwesome: .reply)
                                            Text("Reply to \(replyToHandle)", bundle: FlareAppleUILocalization.bundle)
                                        }
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .padding(.top, 4)
                                    }
                                    if !contentWarningIsEmpty {
                                        VStack(alignment: .leading, spacing: 4) {
                                            ForEach(Array(contentWarnings.enumerated()), id: \.offset) { _, contentWarning in
                                                if !contentWarning.isEmpty {
                                                    RichText(text: contentWarning)
                                                        .fixedSize(horizontal: false, vertical: true)
                                                        .if(isDetail) { view in
                                                            view.textSelection(.enabled)
                                                        }
                                                    }
                                            }

                                            if !expandContentWarning {
                                                Button {
                                                    withAnimation {
                                                        contentWarningExpanded.toggle()
                                                        if !contentWarningExpanded {
                                                            textExpanded = false
                                                            overflowingTextIndexes.removeAll()
                                                        }
                                                    }
                                                } label: {
                                                    if contentWarningExpanded {
                                                        Text("mastodon_item_show_less", bundle: FlareAppleUILocalization.bundle)
                                                    } else {
                                                        Text("mastodon_item_show_more", bundle: FlareAppleUILocalization.bundle)
                                                    }
                                                }
                                                .backport
                                                .glassProminentButtonStyle()
                                            }
                                        }
                                        .padding(.vertical, 4)
                                    }

                                    if contentWarningExpanded || expandContentWarning || contentWarningIsEmpty {
                                        VStack(alignment: .leading, spacing: 4) {
                                            ForEach(Array(contents.enumerated()), id: \.offset) { index, content in
                                                if !content.isEmpty {
                                                    CollapsibleRichText(
                                                        text: content,
                                                        lineLimit: contentLineLimit,
                                                        isExpanded: textExpanded,
                                                        isTextSelectionEnabled: isDetail
                                                    ) { overflows in
                                                        if overflows {
                                                            overflowingTextIndexes.insert(index)
                                                        } else {
                                                            overflowingTextIndexes.remove(index)
                                                        }
                                                    }
                                                }
                                            }
                                            if !overflowingTextIndexes.isEmpty, canExpandLineLimitedContent {
                                                Button {
                                                    withAnimation {
                                                        textExpanded = true
                                                    }
                                                } label: {
                                                    Text("mastodon_item_show_more", bundle: FlareAppleUILocalization.bundle)
                                                }
                                                .buttonStyle(.borderless)
                                            }

                                            if let poll, showMedia {
                                                Spacer()
                                                    .frame(height: 8)
                                                StatusPollView(data: poll)
                                            }
                                        }
                                    }

                                    if isDetail, showTranslate {
                                        StatusTranslateView(
                                            content: data.content.original,
                                            contentWarning: data.contentWarning?.original
                                        )
                                    }
                                }
                            }
                        }
                        .frame(
                            minHeight: usesSideAvatarCarousel ? 44 : nil,
                            alignment: .topLeading
                        )
                    }
                    VStack(
                        alignment: .leading,
                        spacing: 0,
                    ) {
                        if hasImages, showMedia {
                            StatusMediaContent(
                                post: data,
                                data: images,
                                sensitive: sensitive,
                                cornerRadius: isQuote ? 12 : 16,
                                allowsCarousel: allowsMediaCarousel,
                                carouselLeadingPadding: carouselLeadingPadding,
                                carouselTrailingPadding: carouselEdgePadding
                            ) { media, index in
                                if let timelineMediaOpenAction {
                                    timelineMediaOpenAction(data, media, index)
                                } else {
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
                                    let previewIsImage: Bool
                                    if case .image = onEnum(of: media) {
                                        previewIsImage = true
                                    } else {
                                        previewIsImage = false
                                    }
                                    let route = DeeplinkRoute.MediaStatusMedia(
                                        statusKey: statusKey,
                                        accountType: accountType,
                                        index: Int32(index),
                                        preview: preview,
                                        aspectRatio: Float(media.aspectRatio ?? 0),
                                        previewIsImage: previewIsImage
                                    )
                                    if let url = URL(string: route.toUri()) {
                                        openURL(url)
                                    }
                                }
                            }
                            .padding(.top, 8)
                        }

                        if let card, showMedia, !hasImages, !hasQuotes, showLinkPreview {
                            if compatLinkPreview {
                                StatusCompatCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                                    .padding(.top, 8)
                            } else {
                                StatusCardView(data: card, cornerRadius: isQuote ? 12 : 16)
                                    .padding(.top, 8)
                            }
                        }

                        if hasQuotes, !isQuote {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(Array(quoteItems.enumerated()), id: \.offset) { index, quote in
                                    StatusView(
                                        data: quote,
                                        isQuote: true,
                                        forceHideActions: true,
                                        allowsMediaCarousel: allowsMediaCarousel
                                    )
                                    if index < quoteItems.count - 1 {
                                        Divider()
                                    }
                                }
                            }
                            .padding(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.flareSeparator, lineWidth: 1)
                            )
                            .padding(.top, 4)
                        }

                        if showMedia, !isQuote {
                            if sourceChannelName != nil || hasEmojiReactions {
                                VStack(alignment: .leading, spacing: 4) {
                                    if let sourceChannelName {
                                        HStack(spacing: 4) {
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
                                .padding(.top, 4)
                            }
                        }

                        if isDetail {
                            DateTimeText(data: createdAt, fullTime: true)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .padding(.top, 8)
                        }

                        if (postActionStyle != .hidden || isDetail) && !forceHideActions {
                            StatusActionsView(data: actions, useText: false)
                            #if os(macOS)
                                .font(isDetail ? .body : .callout)
                            #else
                                .font(isDetail ? .body : .footnote)
                            #endif
                                .padding(.top, 8)
                                .foregroundStyle(isDetail ? .primary : .secondary)
                        }
                    }
                }
                #if os(macOS)
                .contextMenu {
                    StatusActionsView(data: data.actions, useText: true, allowSpacer: false)
                }
                #endif
//                .if(!isDetail) { view in
//                    view
//                        .contextMenu {
//                            StatusActionsView(data: data.actions, useText: true, allowSpacer: false)
//                        }
//                }
            }
        }
        .contentShape(.rect)
        .if(isClickable) { view in
            #if os(macOS)
            view
                .onTapGesture(perform: openPost)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(
                    Text("status_open_post", bundle: FlareAppleUILocalization.bundle)
                )
                .accessibilityAddTraits(.isButton)
                .accessibilityAction {
                    openPost()
                }
                .focusable()
                .onKeyPress(.return) {
                    openPost()
                    return .handled
                }
                .onKeyPress(.space) {
                    openPost()
                    return .handled
                }
            #else
            view
                .onTapGesture(perform: openPost)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(
                    Text("status_open_post", bundle: FlareAppleUILocalization.bundle)
                )
                .accessibilityAddTraits(.isButton)
                .accessibilityAction {
                    openPost()
                }
            #endif
        }
        .onChange(of: data.renderHash) { _, _ in
            contentWarningExpanded = false
            textExpanded = false
            overflowingTextIndexes.removeAll()
        }
        .onChange(of: effectiveLineLimit) { _, _ in
            textExpanded = false
            overflowingTextIndexes.removeAll()
        }
        .onChange(of: translateConfig.showOriginalWithTranslation) { _, _ in
            textExpanded = false
            overflowingTextIndexes.removeAll()
        }
    }

    private func openPost() {
        data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
    }
    
    private func topEndContent(
        visibility: UiTimelineV2.PostVisibility?,
        translationDisplayState: TranslationDisplayState,
        platformIcon: UiIcon,
        createdAt: UiDateTime,
        accountType: AccountType,
        statusKey: MicroBlogKey
    ) -> some View {
        HStack(spacing: 4) {
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
                Image(fontAwesome: platformIcon.fontAwesomeIcon)
                    .font(.caption)
                    .foregroundStyle(.secondary)
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
                .accessibilityLabel(Text("status_insight_title", bundle: FlareAppleUILocalization.bundle))
            }
        }
    }
}

private struct CollapsibleRichText: View {
    let text: UiRichText
    let lineLimit: Int?
    let isExpanded: Bool
    let isTextSelectionEnabled: Bool
    let onOverflowChanged: (Bool) -> Void

    @ScaledMetric(relativeTo: .body) private var fallbackLineHeight: CGFloat = 20
    @State private var fullHeight: CGFloat = 0
    @State private var lineHeight: CGFloat = 0

    private var effectiveLineHeight: CGFloat {
        max(lineHeight, fallbackLineHeight)
    }

    private var collapsedHeight: CGFloat? {
        guard let lineLimit, !isExpanded else { return nil }
        return ceil(effectiveLineHeight * CGFloat(max(lineLimit, 1)))
    }

    var body: some View {
        richText
            .fixedSize(horizontal: false, vertical: true)
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: RichTextFullHeightPreferenceKey.self,
                        value: proxy.size.height
                    )
                }
            }
            .overlay(alignment: .topLeading) {
                Text(verbatim: "A")
                    .fixedSize()
                    .hidden()
                    .background {
                        GeometryReader { proxy in
                            Color.clear.preference(
                                key: RichTextLineHeightPreferenceKey.self,
                                value: proxy.size.height
                            )
                        }
                    }
                    .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(maxHeight: collapsedHeight, alignment: .top)
            .clipped()
            .onPreferenceChange(RichTextFullHeightPreferenceKey.self) { value in
                fullHeight = value
                publishOverflow(fullHeight: value, lineHeight: lineHeight)
            }
            .onPreferenceChange(RichTextLineHeightPreferenceKey.self) { value in
                lineHeight = value
                publishOverflow(fullHeight: fullHeight, lineHeight: value)
            }
            .onChange(of: lineLimit) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
            .onChange(of: isExpanded) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
            .onChange(of: text.raw) { _, _ in
                fullHeight = 0
                onOverflowChanged(false)
            }
    }

    @ViewBuilder
    private var richText: some View {
        if isTextSelectionEnabled {
            RichText(text: text)
                .textSelection(.enabled)
        } else {
            RichText(text: text)
        }
    }

    private func publishOverflow(fullHeight: CGFloat, lineHeight: CGFloat) {
        guard let lineLimit, !isExpanded else {
            onOverflowChanged(false)
            return
        }
        let limitHeight = ceil(max(lineHeight, fallbackLineHeight) * CGFloat(max(lineLimit, 1)))
        onOverflowChanged(fullHeight > limitHeight + 1)
    }
}

private struct RichTextFullHeightPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

private struct RichTextLineHeightPreferenceKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

struct StatusMediaContent: View {
    @Environment(\.timelineAppearance.showMedia) private var showMedia
    @Environment(\.timelineAppearance.showSensitiveContent) private var showSensitiveContent
    @State private var expandMedia = false
    let post: UiTimelineV2.Post
    let data: [any UiMedia]
    let sensitive: Bool
    let cornerRadius: CGFloat
    let allowsCarousel: Bool
    let carouselLeadingPadding: CGFloat
    let carouselTrailingPadding: CGFloat
    let onMediaClicked: (any UiMedia, Int) -> Void
    var body: some View {
        if showMedia || expandMedia {
            StatusMediaView(
                post: post,
                data: data,
                sensitive: !(showSensitiveContent) && sensitive,
                cornerRadius: cornerRadius,
                allowsCarousel: allowsCarousel,
                carouselLeadingPadding: carouselLeadingPadding,
                carouselTrailingPadding: carouselTrailingPadding,
                onMediaClicked: onMediaClicked
            )
        } else {
            Button {
                withAnimation {
                    expandMedia = true
                }
            } label: {
                Label {
                    Text(
                        "appearance_show_media",
                        bundle: FlareAppleUILocalization.bundle,
                        comment: "Button to show media attachments"
                    )
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
