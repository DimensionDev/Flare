import UIKit
import SwiftUI
import KotlinSharedUI

/// UIKit port of the SwiftUI `StatusView`.
///
/// Layout is fully manual (frame-based) for performance — no UIStackView or
/// Auto Layout constraints. The view hierarchy is flat: all visible children
/// are direct subviews of this view, positioned in `layoutSubviews()`.
///
/// `rebuild()` determines which children should be visible and updates the
/// managed arrays via `syncManagedSubviews`. `layoutSubviews()` then assigns
/// frames based on the current `bounds.width`.
final class StatusUIKitView: UIView, UIGestureRecognizerDelegate, ManualLayoutMeasurable, TimelineHeightProviding {
    // MARK: - Environment mirrors (inputs that were `@Environment` in SwiftUI)

    /// Forwarded to all URL-clicking machinery.
    var openURL: ((URL) -> Void)?
    var onLocalHeightInvalidated: (() -> Void)?

    // MARK: - SwiftUI `var` parameters

    private var data: UiTimelineV2.Post?
    private var boundStatusKey: String?
    private var isDetail: Bool = false
    private var isQuote: Bool = false
    private var withLeadingPadding: Bool = false
    private var showMediaInput: Bool = true
    private var maxLine: Int = 5
    private var showExpandTextButton: Bool = true
    private var forceHideActions: Bool = false
    private var showTranslate: Bool = true
    private var showParents: Bool = true
    private var appearance = StatusUIKitAppearance(settings: AppearanceSettings.companion.Default)
    private var lastConfigureSignature: ConfigureSignature?
    private var lastPreparedFittingWidthKey: Int?

    private struct ConfigureSignature: Equatable {
        let statusKey: String
        let renderHash: Int32
        let isDetail: Bool
        let isQuote: Bool
        let withLeadingPadding: Bool
        let showMedia: Bool
        let maxLine: Int
        let showExpandTextButton: Bool
        let forceHideActions: Bool
        let showTranslate: Bool
        let showParents: Bool
        let appearance: StatusUIKitAppearance

        init(
            data: UiTimelineV2.Post,
            appearance: StatusUIKitAppearance,
            isDetail: Bool,
            isQuote: Bool,
            withLeadingPadding: Bool,
            showMedia: Bool,
            maxLine: Int,
            showExpandTextButton: Bool,
            forceHideActions: Bool,
            showTranslate: Bool,
            showParents: Bool
        ) {
            statusKey = String(describing: data.statusKey)
            renderHash = data.renderHash
            self.isDetail = isDetail
            self.isQuote = isQuote
            self.withLeadingPadding = withLeadingPadding
            self.showMedia = showMedia
            self.maxLine = maxLine
            self.showExpandTextButton = showExpandTextButton
            self.forceHideActions = forceHideActions
            self.showTranslate = showTranslate
            self.showParents = showParents
            self.appearance = appearance
        }
    }

    // MARK: - @State

    private var expand: Bool = false

    // MARK: - Managed child arrays (replaces UIStackViews)

    /// Parents displayed before the main row (vertical, spacing 0)
    private var activeParents: [UIView] = []
    /// The current header view (one of userHeaderFull/userHeaderQuote/userHeaderCompat), or nil
    private var currentHeaderView: UIView?
    /// Content column children (vertical, spacing 8)
    private var contentColumnChildren: [UIView] = []
    /// Whether avatar is currently displayed
    private var wantsAvatar: Bool = false

    // MARK: - Layout constants

    private static let mainRowSpacing: CGFloat = 8
    private static let innerColumnSpacing: CGFloat = 8
    private static let contentColumnSpacing: CGFloat = 8
    private static let avatarSize: CGFloat = 44
    private static let replyIconSize: CGFloat = 12
    private static let sourceIconSize: CGFloat = 12

    // MARK: - Cached leaves (created on first use, reconfigured per `rebuild`)

    private var avatarViewStorage: AvatarUIView?
    private var userHeaderFullStorage: UserOnelineUIView?
    private var userHeaderQuoteStorage: UserOnelineUIView?
    private var userHeaderCompatStorage: UserCompatUIView?
    private var topEndViewStorage: StatusTopEndView?

    private var replyToContainerStorage: ReplyToRowView?

    private var contentWarningTextStorage: RichTextUIView?
    private var contentWarningToggleStorage: UIButton?

    private var bodyTextStorage: RichTextUIView?
    private var expandMoreButtonStorage: UIButton?

    private var translateViewStorage: StatusTranslateUIView?
    private var pollViewStorage: StatusPollUIView?
    private var mediaViewStorage: StatusMediaContentUIView?
    private var normalCardViewStorage: StatusCardUIView?
    private var compatCardViewStorage: StatusCompatCardUIView?

    private var quotesContainerStorage: QuotesContainerView?
    private var quoteChildren: [StatusUIKitView] = []
    private var quoteDividers: [UIView] = []

    private var sourceChannelContainerStorage: SourceChannelRowView?

    private var reactionViewStorage: StatusReactionUIView?
    private var detailTimestampViewStorage: DateTimeUILabel?

    private var actionsViewStorage: StatusActionsUIView?
    private var actionsContainerStorage: ActionsContainerView?

    private var parentContainers: [ParentContainerView] = []

    // MARK: - Init

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    private func commonInit() {
        let tap = UITapGestureRecognizer(target: self, action: #selector(onRootTapped))
        tap.cancelsTouchesInView = false
        tap.delegate = self
        addGestureRecognizer(tap)
    }

    private func setupAvatar(_ avatarView: AvatarUIView) {
        guard avatarView.gestureRecognizers?.isEmpty ?? true else { return }
        avatarView.isUserInteractionEnabled = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(onAvatarTapped))
        avatarView.addGestureRecognizer(tap)
    }

    private func setupContentWarningToggle(_ contentWarningToggle: UIButton) {
        guard contentWarningToggle.actions(forTarget: nil, forControlEvent: .touchUpInside)?.isEmpty ?? true else { return }
        contentWarningToggle.contentHorizontalAlignment = .leading
        contentWarningToggle.addAction(
            UIAction { [weak self] _ in self?.toggleExpand() },
            for: .touchUpInside
        )
    }

    private func setupExpandMoreButton(_ expandMoreButton: UIButton) {
        guard expandMoreButton.actions(forTarget: nil, forControlEvent: .touchUpInside)?.isEmpty ?? true else { return }
        expandMoreButton.contentHorizontalAlignment = .leading
        expandMoreButton.setTitle(String(localized: "mastodon_item_show_more"), for: .normal)
        expandMoreButton.addAction(
            UIAction { [weak self] _ in self?.expandOnly() },
            for: .touchUpInside
        )
    }

    private func resolvedAvatarView() -> AvatarUIView {
        if let avatarViewStorage { return avatarViewStorage }
        let view = AvatarUIView()
        setupAvatar(view)
        avatarViewStorage = view
        return view
    }

    private func resolvedUserHeaderFull() -> UserOnelineUIView {
        if let userHeaderFullStorage { return userHeaderFullStorage }
        let view = UserOnelineUIView(showAvatar: false)
        userHeaderFullStorage = view
        return view
    }

    private func resolvedUserHeaderQuote() -> UserOnelineUIView {
        if let userHeaderQuoteStorage { return userHeaderQuoteStorage }
        let view = UserOnelineUIView(showAvatar: true)
        userHeaderQuoteStorage = view
        return view
    }

    private func resolvedUserHeaderCompat() -> UserCompatUIView {
        if let userHeaderCompatStorage { return userHeaderCompatStorage }
        let view = UserCompatUIView()
        userHeaderCompatStorage = view
        return view
    }

    private func resolvedTopEndView() -> StatusTopEndView {
        if let topEndViewStorage { return topEndViewStorage }
        let view = StatusTopEndView()
        topEndViewStorage = view
        return view
    }

    private func resolvedReplyToContainer() -> ReplyToRowView {
        if let replyToContainerStorage { return replyToContainerStorage }
        let view = ReplyToRowView()
        replyToContainerStorage = view
        return view
    }

    private func resolvedContentWarningText() -> RichTextUIView {
        if let contentWarningTextStorage { return contentWarningTextStorage }
        let view = RichTextUIView()
        contentWarningTextStorage = view
        return view
    }

    private func resolvedContentWarningToggle() -> UIButton {
        if let contentWarningToggleStorage { return contentWarningToggleStorage }
        let button = UIButton(type: .system)
        setupContentWarningToggle(button)
        contentWarningToggleStorage = button
        return button
    }

    private func resolvedBodyText() -> RichTextUIView {
        if let bodyTextStorage { return bodyTextStorage }
        let view = RichTextUIView()
        bodyTextStorage = view
        return view
    }

    private func resolvedExpandMoreButton() -> UIButton {
        if let expandMoreButtonStorage { return expandMoreButtonStorage }
        let button = UIButton(type: .system)
        setupExpandMoreButton(button)
        expandMoreButtonStorage = button
        return button
    }

    private func resolvedTranslateView() -> StatusTranslateUIView {
        if let translateViewStorage { return translateViewStorage }
        let view = StatusTranslateUIView()
        translateViewStorage = view
        return view
    }

    private func resolvedPollView() -> StatusPollUIView {
        if let pollViewStorage { return pollViewStorage }
        let view = StatusPollUIView()
        pollViewStorage = view
        return view
    }

    private func resolvedMediaView() -> StatusMediaContentUIView {
        if let mediaViewStorage { return mediaViewStorage }
        let view = StatusMediaContentUIView()
        mediaViewStorage = view
        return view
    }

    private func resolvedNormalCardView(cornerRadius: CGFloat) -> StatusCardUIView {
        if let normalCardViewStorage { return normalCardViewStorage }
        let view = StatusCardUIView(cornerRadius: cornerRadius)
        normalCardViewStorage = view
        return view
    }

    private func resolvedCompatCardView(cornerRadius: CGFloat) -> StatusCompatCardUIView {
        if let compatCardViewStorage { return compatCardViewStorage }
        let view = StatusCompatCardUIView(cornerRadius: cornerRadius)
        compatCardViewStorage = view
        return view
    }

    private func resolvedQuotesContainer() -> QuotesContainerView {
        if let quotesContainerStorage { return quotesContainerStorage }
        let view = QuotesContainerView()
        quotesContainerStorage = view
        return view
    }

    private func resolvedSourceChannelContainer() -> SourceChannelRowView {
        if let sourceChannelContainerStorage { return sourceChannelContainerStorage }
        let view = SourceChannelRowView()
        sourceChannelContainerStorage = view
        return view
    }

    private func resolvedReactionView() -> StatusReactionUIView {
        if let reactionViewStorage { return reactionViewStorage }
        let view = StatusReactionUIView()
        reactionViewStorage = view
        return view
    }

    private func resolvedDetailTimestampView() -> DateTimeUILabel {
        if let detailTimestampViewStorage { return detailTimestampViewStorage }
        let view = DateTimeUILabel()
        view.fullTime = true
        detailTimestampViewStorage = view
        return view
    }

    private func resolvedActionsView() -> StatusActionsUIView {
        if let actionsViewStorage { return actionsViewStorage }
        let view = StatusActionsUIView()
        actionsViewStorage = view
        return view
    }

    private func resolvedActionsContainer() -> ActionsContainerView {
        if let actionsContainerStorage { return actionsContainerStorage }
        let container = ActionsContainerView()
        container.content = resolvedActionsView()
        actionsContainerStorage = container
        return container
    }

    // MARK: - Public API

    func configure(
        data: UiTimelineV2.Post,
        appearance: StatusUIKitAppearance,
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
        let newStatusKey = String(describing: data.statusKey)
        if boundStatusKey != newStatusKey {
            expand = false
            boundStatusKey = newStatusKey
        }
        let signature = ConfigureSignature(
            data: data,
            appearance: appearance,
            isDetail: isDetail,
            isQuote: isQuote,
            withLeadingPadding: withLeadingPadding,
            showMedia: showMedia,
            maxLine: maxLine,
            showExpandTextButton: showExpandTextButton,
            forceHideActions: forceHideActions,
            showTranslate: showTranslate,
            showParents: showParents
        )
        let signatureUnchanged = lastConfigureSignature == signature
        self.data = data
        if signatureUnchanged {
            forwardOpenURL()
            return
        }
        self.isDetail = isDetail
        self.isQuote = isQuote
        self.withLeadingPadding = withLeadingPadding
        self.showMediaInput = showMedia
        self.maxLine = maxLine
        self.showExpandTextButton = showExpandTextButton
        self.forceHideActions = forceHideActions
        self.showTranslate = showTranslate
        self.showParents = showParents
        self.appearance = appearance
        lastConfigureSignature = signature
        lastPreparedFittingWidthKey = nil
        rebuild()
    }

    func prepareForPoolRemoval() {
        data = nil
        lastConfigureSignature = nil
        lastPreparedFittingWidthKey = nil
        boundStatusKey = nil
        expand = false

        mediaViewStorage?.prepareForPoolRemoval()
        actionsViewStorage?.prepareForPoolRemoval()
        translateViewStorage?.prepareForPoolRemoval()

        // Remove all managed children
        for view in contentColumnChildren { view.removeFromSuperview() }
        contentColumnChildren = []
        currentHeaderView?.removeFromSuperview()
        currentHeaderView = nil
        avatarViewStorage?.removeFromSuperview()
        wantsAvatar = false

        for parent in activeParents { parent.removeFromSuperview() }
        activeParents = []

        for container in parentContainers {
            container.child.prepareForPoolRemoval()
            container.removeFromSuperview()
        }
        parentContainers.removeAll()

        for child in quoteChildren {
            child.prepareForPoolRemoval()
            child.removeFromSuperview()
        }
        quoteChildren.removeAll()

        for divider in quoteDividers {
            divider.removeFromSuperview()
        }
        quoteDividers.removeAll()

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > 0 else { return }
        let widthKey = Self.fittingWidthKey(width)
        guard lastPreparedFittingWidthKey != widthKey else { return }
        lastPreparedFittingWidthKey = widthKey
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)

        let contentWidth = fittingContentColumnWidth(for: width)
        contentWarningTextStorage?.prepareForFitting(width: contentWidth)
        bodyTextStorage?.prepareForFitting(width: contentWidth)
        if let mediaViewStorage {
            mediaViewStorage.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: mediaViewStorage.bounds.height)
        }
        if let normalCardViewStorage {
            normalCardViewStorage.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: normalCardViewStorage.bounds.height)
        }
        if let compatCardViewStorage {
            compatCardViewStorage.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: compatCardViewStorage.bounds.height)
        }

        for container in parentContainers where container.superview != nil {
            container.prepareForFitting(width: width)
        }

        let quoteWidth = max(contentWidth - 16, 1)
        if let quotesContainerStorage {
            quotesContainerStorage.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: quotesContainerStorage.bounds.height)
        }
        for child in quoteChildren where child.superview != nil {
            child.prepareForFitting(width: quoteWidth)
        }

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private static func fittingWidthKey(_ width: CGFloat) -> Int {
        Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
    }

    // MARK: - Layout

    override func layoutSubviews() {
        super.layoutSubviews()
        performLayout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(performLayout(width: width, assignFrames: false))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let w: CGFloat
        if horizontalFittingPriority == .required, targetSize.width.isFinite, targetSize.width > 0 {
            w = targetSize.width
        } else if bounds.width > 0 {
            w = bounds.width
        } else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        prepareForFitting(width: w)
        let h = performLayout(width: w, assignFrames: false)
        return CGSize(width: w, height: ceil(h))
    }

    override var intrinsicContentSize: CGSize {
        guard bounds.width > 0 else { return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric) }
        return sizeThatFits(CGSize(width: bounds.width, height: .greatestFiniteMagnitude))
    }

    /// Core layout routine. When `assignFrames` is true, sets child frames.
    /// Always returns the total height.
    @discardableResult
    private func performLayout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard width > 0 else { return 0 }
        var y: CGFloat = 0

        // Parents (vertical, spacing 0)
        for parent in activeParents {
            let h = childHeight(of: parent, for: width)
            if assignFrames { parent.frame = CGRect(x: 0, y: y, width: width, height: h) }
            y += h // spacing 0
        }

        // Main row
        let contentX: CGFloat = wantsAvatar ? Self.avatarSize + Self.mainRowSpacing : 0
        let contentWidth = max(width - contentX, 1)

        if wantsAvatar, let avatarView = avatarViewStorage, assignFrames {
            avatarView.frame = CGRect(x: 0, y: y, width: Self.avatarSize, height: Self.avatarSize)
        }

        var innerY = y

        // Header
        if let header = currentHeaderView {
            let h = childHeight(of: header, for: contentWidth)
            if assignFrames { header.frame = CGRect(x: contentX, y: innerY, width: contentWidth, height: h) }
            innerY += h + Self.innerColumnSpacing
        }

        // Content column (vertical, spacing 8)
        for (i, child) in contentColumnChildren.enumerated() {
            let h = childHeight(of: child, for: contentWidth)
            if assignFrames { child.frame = CGRect(x: contentX, y: innerY, width: contentWidth, height: h) }
            innerY += h
            if i < contentColumnChildren.count - 1 {
                innerY += Self.contentColumnSpacing
            }
        }

        return max(innerY, y)
    }

    // MARK: - SwiftUI-equivalent computed

    private var showAsFullWidth: Bool {
        (!appearance.fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }

    private func fittingContentColumnWidth(for width: CGFloat) -> CGFloat {
        let wantsAvatar = showAsFullWidth && data?.user != nil
        let avatarWidth = wantsAvatar ? Self.avatarSize + Self.mainRowSpacing : 0
        return max(width - avatarWidth, 1)
    }

    // MARK: - Rebuild (incremental diff)

    private func rebuild() {
        guard let data = data else {
            removeAllManaged()
            return
        }

        // Avatar
        let newWantsAvatar = showAsFullWidth && data.user != nil
        if newWantsAvatar, let user = data.user {
            let avatarView = resolvedAvatarView()
            avatarView.avatarShape = appearance.avatarShape
            avatarView.set(url: user.avatar)
            if avatarView.superview !== self { addSubview(avatarView) }
        } else if avatarViewStorage?.superview === self {
            avatarViewStorage?.removeFromSuperview()
        }
        wantsAvatar = newWantsAvatar

        // Header
        let newHeader = selectAndConfigureHeader(data: data)
        if currentHeaderView !== newHeader {
            currentHeaderView?.removeFromSuperview()
            currentHeaderView = newHeader
            if let h = newHeader, h.superview !== self { addSubview(h) }
        }

        // Content column children
        let contentItems = buildContentColumnList(data: data)
        syncManagedSubviews(parent: self, current: &contentColumnChildren, desired: contentItems)

        // Parents
        let parents = resolveActiveParentContainers(data: data)
        syncManagedSubviews(parent: self, current: &activeParents, desired: parents)

        lastPreparedFittingWidthKey = nil
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func removeAllManaged() {
        for view in contentColumnChildren { view.removeFromSuperview() }
        contentColumnChildren = []
        currentHeaderView?.removeFromSuperview()
        currentHeaderView = nil
        avatarViewStorage?.removeFromSuperview()
        wantsAvatar = false
        for parent in activeParents { parent.removeFromSuperview() }
        activeParents = []
        lastPreparedFittingWidthKey = nil
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func selectAndConfigureHeader(data: UiTimelineV2.Post) -> UIView? {
        guard let user = data.user else { return nil }
        let topEndView = resolvedTopEndView()
        topEndView.configure(
            post: data,
            showPlatformLogo: appearance.showPlatformLogo,
            absoluteTimestamp: appearance.absoluteTimestamp,
            isDetail: isDetail
        )
        let onClicked: () -> Void = { [weak self] in self?.onUserTapped() }

        if showAsFullWidth {
            let userHeaderFull = resolvedUserHeaderFull()
            userHeaderFull.avatar.avatarShape = appearance.avatarShape
            userHeaderFull.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderFull
        } else if isQuote {
            let userHeaderQuote = resolvedUserHeaderQuote()
            userHeaderQuote.avatar.avatarShape = appearance.avatarShape
            userHeaderQuote.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderQuote
        } else {
            let userHeaderCompat = resolvedUserHeaderCompat()
            userHeaderCompat.avatar.avatarShape = appearance.avatarShape
            userHeaderCompat.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderCompat
        }
    }

    private func buildContentColumnList(data: UiTimelineV2.Post) -> [UIView] {
        var items: [UIView] = []

        // reply-to
        if let replyToHandle = data.replyToHandle {
            let replyToContainer = resolvedReplyToContainer()
            replyToContainer.configure(text: String(localized: "Reply to \(replyToHandle)"))
            items.append(replyToContainer)
        }

        // content warning
        let hasCW = (data.contentWarning != nil) && (data.contentWarning?.isEmpty == false)
        if hasCW, let cw = data.contentWarning {
            let contentWarningText = resolvedContentWarningText()
            contentWarningText.configure(
                text: cw,
                lineLimit: nil,
                isTextSelectionEnabled: isDetail,
                onOpenURL: openURL,
                contentKey: Int(data.renderHash)
            )
            items.append(contentWarningText)
            let contentWarningToggle = resolvedContentWarningToggle()
            contentWarningToggle.setTitle(
                expand
                    ? String(localized: "mastodon_item_show_less")
                    : String(localized: "mastodon_item_show_more"),
                for: .normal
            )
            items.append(contentWarningToggle)
        }

        // main body
        if expand || !hasCW {
            if !data.content.isEmpty {
                let bodyText = resolvedBodyText()
                let bodyLineLimit: Int?
                let bodySelectionEnabled: Bool
                if isDetail {
                    bodySelectionEnabled = true
                    bodyLineLimit = nil
                } else if (data.shouldExpandTextByDefault || expand) && maxLine >= 5 {
                    bodySelectionEnabled = false
                    bodyLineLimit = nil
                } else {
                    bodySelectionEnabled = false
                    bodyLineLimit = Int(maxLine)
                }
                bodyText.configure(
                    text: data.content,
                    lineLimit: bodyLineLimit,
                    isTextSelectionEnabled: bodySelectionEnabled,
                    onOpenURL: openURL,
                    contentKey: Int(data.renderHash)
                )
                items.append(bodyText)
                if !data.shouldExpandTextByDefault, !isDetail, !expand, showExpandTextButton {
                    items.append(resolvedExpandMoreButton())
                }
            }
        }

        // translate (detail-only)
        if isDetail, showTranslate {
            let translateView = resolvedTranslateView()
            translateView.content = data.content
            translateView.contentWarning = data.contentWarning
            translateView.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            items.append(translateView)
        }

        // poll
        if let poll = data.poll, showMediaInput {
            let pollView = resolvedPollView()
            pollView.configure(data: poll, absoluteTimestamp: appearance.absoluteTimestamp)
            pollView.onVote = { [weak self] indices in
                guard let self = self else { return }
                poll.onVote(
                    ClickContext(launcher: self.makeLauncher()),
                    indices.map { KotlinInt(value: Int32($0)) }
                )
            }
            items.append(pollView)
        }

        // media grid
        if !data.images.isEmpty, showMediaInput {
            let corner: CGFloat = isQuote ? 12 : 16
            let statusKey = data.statusKey
            let accountType = data.accountType
            let mediaView = resolvedMediaView()
            mediaView.configure(
                data: data.images,
                sensitive: data.sensitive,
                cornerRadius: corner,
                appearanceShowMedia: appearance.showMedia,
                appearanceShowSensitive: appearance.showSensitiveContent,
                appearanceExpandMediaSize: appearance.expandMediaSize
            )
            mediaView.onMediaClicked = { [weak self] media, index in
                let preview: String? = switch onEnum(of: media) {
                case .image(let image): image.previewUrl
                case .video(let video): video.thumbnailUrl
                case .gif(let gif): gif.previewUrl
                case .audio: nil
                }
                let route = DeeplinkRoute.MediaStatusMedia(
                    statusKey: statusKey,
                    accountType: accountType,
                    index: Int32(index),
                    preview: preview
                )
                if let url = URL(string: route.toUri()) {
                    self?.openURL?(url)
                }
            }
            mediaView.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            items.append(mediaView)
        }

        // card (only when no images & no quote & enabled)
        if let card = data.card,
           showMediaInput,
           data.images.isEmpty,
           data.quote.isEmpty,
           appearance.showLinkPreview {
            let corner: CGFloat = isQuote ? 12 : 16
            if appearance.compatLinkPreview {
                let compatCardView = resolvedCompatCardView(cornerRadius: corner)
                compatCardView.layer.cornerRadius = corner
                compatCardView.configure(data: card)
                compatCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
                items.append(compatCardView)
            } else {
                let normalCardView = resolvedNormalCardView(cornerRadius: corner)
                normalCardView.layer.cornerRadius = corner
                normalCardView.configure(data: card)
                normalCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
                items.append(normalCardView)
            }
        }

        // quotes (not when self is quote)
        if !data.quote.isEmpty, !isQuote {
            updateQuotes(quotes: data.quote)
            items.append(resolvedQuotesContainer())
        } else {
            updateQuotes(quotes: [])
        }

        // source channel + reactions
        if showMediaInput, !isQuote {
            if let channel = data.sourceChannel {
                let sourceChannelContainer = resolvedSourceChannelContainer()
                sourceChannelContainer.configure(text: channel.name)
                items.append(sourceChannelContainer)
            }
            if !data.emojiReactions.isEmpty {
                let reactionView = resolvedReactionView()
                reactionView.configure(data: Array(data.emojiReactions), isDetail: isDetail)
                reactionView.onReactionTapped = { [weak self] reaction in
                    guard let self = self else { return }
                    reaction.onClicked(ClickContext(launcher: self.makeLauncher()))
                }
                items.append(reactionView)
            }
        }

        // detail-only full timestamp
        if isDetail {
            let detailTimestampView = resolvedDetailTimestampView()
            detailTimestampView.absoluteTimestamp = appearance.absoluteTimestamp
            detailTimestampView.set(data: data.createdAt)
            items.append(detailTimestampView)
        }

        // actions
        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if !hideActions {
            let actionsView = resolvedActionsView()
            actionsView.onOpenURL = { [weak self] in self?.openURL?($0) }
            actionsView.configure(
                data: Array(data.actions),
                useText: false,
                allowSpacer: true,
                postActionStyle: appearance.postActionStyle,
                showNumbers: appearance.showNumbers,
                isDetail: isDetail
            )
            let actionsContainer = resolvedActionsContainer()
            actionsContainer.content = actionsView
            items.append(actionsContainer)
        }

        return items
    }

    // MARK: - Pools

    private func resolveActiveParentContainers(data: UiTimelineV2.Post) -> [ParentContainerView] {
        let parentData = showParents ? Array(data.parents) : []
        while parentContainers.count < parentData.count {
            parentContainers.append(ParentContainerView())
        }
        for (i, parent) in parentData.enumerated() {
            let container = parentContainers[i]
            container.child.openURL = openURL
            container.child.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            container.child.configure(data: parent, appearance: appearance, withLeadingPadding: true)
        }
        return Array(parentContainers.prefix(parentData.count))
    }

    private func updateQuotes(quotes: [UiTimelineV2.Post]) {
        while quoteChildren.count < quotes.count {
            quoteChildren.append(StatusUIKitView())
        }
        while quoteDividers.count < max(0, quotes.count - 1) {
            let d = UIView()
            d.backgroundColor = .separator
            quoteDividers.append(d)
        }
        var desired: [UIView] = []
        for (i, quote) in quotes.enumerated() {
            let child = quoteChildren[i]
            child.openURL = openURL
            child.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            child.configure(data: quote, appearance: appearance, isQuote: true, forceHideActions: true)
            desired.append(child)
            if i != quotes.count - 1 {
                desired.append(quoteDividers[i])
            }
        }
        if !desired.isEmpty || quotesContainerStorage != nil {
            resolvedQuotesContainer().setChildren(desired)
        }
    }

    func performLightweightPoolCleanup() {
        let activeParentCount: Int
        let activeQuoteCount: Int
        if let data {
            activeParentCount = showParents ? data.parents.count : 0
            activeQuoteCount = !isQuote ? data.quote.count : 0
        } else {
            activeParentCount = 0
            activeQuoteCount = 0
        }

        if let data, !data.images.isEmpty, showMediaInput {
            mediaViewStorage?.performLightweightPoolCleanup()
        } else {
            mediaViewStorage?.prepareForPoolRemoval()
        }

        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if data != nil, !hideActions {
            actionsViewStorage?.performLightweightPoolCleanup()
        } else {
            actionsViewStorage?.prepareForPoolRemoval()
        }
        if !(data != nil && isDetail && showTranslate) {
            translateViewStorage?.prepareForPoolRemoval()
        }
        trimParentContainers(activeCount: activeParentCount)
        trimQuoteChildren(activeCount: activeQuoteCount)
        trimQuoteDividers(activeCount: max(0, activeQuoteCount - 1))
    }

    func performDeferredPoolCleanup() {
        let activeParentCount: Int
        let activeQuoteCount: Int
        if let data {
            activeParentCount = showParents ? data.parents.count : 0
            activeQuoteCount = !isQuote ? data.quote.count : 0
        } else {
            activeParentCount = 0
            activeQuoteCount = 0
        }

        for container in parentContainers.prefix(activeParentCount) {
            container.child.performDeferredPoolCleanup()
        }
        for child in quoteChildren.prefix(activeQuoteCount) {
            child.performDeferredPoolCleanup()
        }
        if let data, !data.images.isEmpty, showMediaInput {
            mediaViewStorage?.performDeferredPoolCleanup()
        } else {
            mediaViewStorage?.prepareForPoolRemoval()
        }

        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if data != nil, !hideActions {
            actionsViewStorage?.performDeferredPoolCleanup()
        } else {
            actionsViewStorage?.prepareForPoolRemoval()
        }
        if !(data != nil && isDetail && showTranslate) {
            translateViewStorage?.prepareForPoolRemoval()
        }
        trimParentContainers(activeCount: activeParentCount)
        trimQuoteChildren(activeCount: activeQuoteCount)
        trimQuoteDividers(activeCount: max(0, activeQuoteCount - 1))
    }

    private func trimParentContainers(activeCount: Int) {
        guard parentContainers.count > activeCount else { return }
        for container in parentContainers[activeCount...] {
            container.child.prepareForPoolRemoval()
            container.removeFromSuperview()
        }
        parentContainers.removeLast(parentContainers.count - activeCount)
    }

    private func trimQuoteChildren(activeCount: Int) {
        guard quoteChildren.count > activeCount else { return }
        for child in quoteChildren[activeCount...] {
            child.prepareForPoolRemoval()
            child.removeFromSuperview()
        }
        quoteChildren.removeLast(quoteChildren.count - activeCount)
    }

    private func trimQuoteDividers(activeCount: Int) {
        guard quoteDividers.count > activeCount else { return }
        for divider in quoteDividers[activeCount...] {
            divider.removeFromSuperview()
        }
        quoteDividers.removeLast(quoteDividers.count - activeCount)
    }

    private func forwardOpenURL() {
        contentWarningTextStorage?.onOpenURL = openURL
        bodyTextStorage?.onOpenURL = openURL
        normalCardViewStorage?.onOpenURL = { [weak self] in self?.openURL?($0) }
        compatCardViewStorage?.onOpenURL = { [weak self] in self?.openURL?($0) }
        actionsViewStorage?.onOpenURL = { [weak self] in self?.openURL?($0) }
        for container in parentContainers {
            container.child.openURL = openURL
            container.child.forwardOpenURL()
        }
        for child in quoteChildren {
            child.openURL = openURL
            child.forwardOpenURL()
        }
    }

    func autoplayCandidates(prefix: String = "status") -> [TimelineVideoAutoplayCandidate] {
        guard let data, !isHidden, window != nil else { return [] }

        let statusPrefix = "\(prefix):\(String(describing: data.statusKey))"
        var candidates: [TimelineVideoAutoplayCandidate] = []

        if showMediaInput {
            candidates.append(contentsOf: mediaViewStorage?.autoplayCandidates(prefix: statusPrefix) ?? [])
        }

        if showParents {
            for container in parentContainers where container.superview != nil {
                candidates.append(contentsOf: container.child.autoplayCandidates(prefix: "\(statusPrefix):parent"))
            }
        }

        if !isQuote {
            for child in quoteChildren where child.superview != nil {
                candidates.append(contentsOf: child.autoplayCandidates(prefix: "\(statusPrefix):quote"))
            }
        }

        return candidates
    }

    // MARK: - Actions & state transitions

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard gestureRecognizer.view === self,
              let touchedView = touch.view else {
            return true
        }

        if touchedView.firstSuperview(of: UIControl.self) != nil {
            return false
        }

        if let nestedStatus = touchedView.firstSuperview(of: StatusUIKitView.self),
           nestedStatus !== self {
            return false
        }

        return true
    }

    @objc private func onRootTapped() {
        guard let data = data else { return }
        data.onClicked(ClickContext(launcher: makeLauncher()))
    }

    @objc private func onAvatarTapped() {
        guard let user = data?.user else { return }
        user.onClicked(ClickContext(launcher: makeLauncher()))
    }

    private func onUserTapped() {
        guard let user = data?.user else { return }
        user.onClicked(ClickContext(launcher: makeLauncher()))
    }

    private func toggleExpand() {
        UIView.animate(withDuration: 0.25) { [weak self] in
            guard let self = self else { return }
            self.expand.toggle()
            self.rebuild()
            self.notifyLocalHeightInvalidated()
            self.layoutIfNeeded()
        } completion: { [weak self] _ in
            self?.invalidateContainingCollectionLayout()
        }
    }

    private func expandOnly() {
        UIView.animate(withDuration: 0.25) { [weak self] in
            guard let self = self else { return }
            self.expand = true
            self.rebuild()
            self.notifyLocalHeightInvalidated()
            self.layoutIfNeeded()
        } completion: { [weak self] _ in
            self?.invalidateContainingCollectionLayout()
        }
    }

    private func invalidateContainingCollectionLayout() {
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        var responder: UIResponder? = self
        var cellRef: UICollectionViewCell?
        while let current = responder {
            if cellRef == nil, let cell = current as? UICollectionViewCell {
                cellRef = cell
            }
            if let collectionView = current as? UICollectionView {
                if let cell = cellRef, let indexPath = collectionView.indexPath(for: cell) {
                    let context = UICollectionViewLayoutInvalidationContext()
                    context.invalidateItems(at: [indexPath])
                    collectionView.collectionViewLayout.invalidateLayout(with: context)
                }
                collectionView.performBatchUpdates(nil)
                return
            }
            responder = current.next
        }
    }

    private func notifyLocalHeightInvalidated() {
        lastPreparedFittingWidthKey = nil
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        onLocalHeightInvalidated?()
    }

    fileprivate func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: OpenURLAction { [weak self] url in
            self?.openURL?(url)
            return .handled
        })
    }
}

// MARK: - ReplyToRowView (replaces replyToRow UIStackView)

private final class ReplyToRowView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let icon = UIImageView(image: UIImage(named: "fa-reply"))
    private let label = UILabel()
    private static let iconSize: CGFloat = 12
    private static let spacing: CGFloat = 4

    override init(frame: CGRect) {
        super.init(frame: frame)
        icon.tintColor = .secondaryLabel
        icon.contentMode = .scaleAspectFit
        addSubview(icon)

        label.font = .preferredFont(forTextStyle: .caption1)
        label.textColor = .secondaryLabel
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 0
        addSubview(label)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(text: String) {
        label.text = text
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let s = Self.iconSize
        let labelX = s + Self.spacing
        let labelW = max(bounds.width - labelX, 0)
        let labelH = label.textRect(
            forBounds: CGRect(x: 0, y: 0, width: labelW, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 0
        ).height
        icon.frame = CGRect(x: 0, y: (ceil(labelH) - s) / 2, width: s, height: s)
        label.frame = CGRect(x: labelX, y: 0, width: labelW, height: ceil(labelH))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let labelW = max(width - Self.iconSize - Self.spacing, 0)
        let labelH = label.textRect(
            forBounds: CGRect(x: 0, y: 0, width: labelW, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 0
        ).height
        return ceil(max(labelH, Self.iconSize))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

// MARK: - SourceChannelRowView (replaces sourceChannelRow UIStackView)

private final class SourceChannelRowView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let icon = UIImageView(image: UIImage(named: "fa-tv"))
    private let label = UILabel()
    private static let iconSize: CGFloat = 12
    private static let spacing: CGFloat = 4

    override init(frame: CGRect) {
        super.init(frame: frame)
        icon.tintColor = .secondaryLabel
        icon.contentMode = .scaleAspectFit
        addSubview(icon)

        label.font = .preferredFont(forTextStyle: .footnote)
        label.textColor = .secondaryLabel
        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 0
        addSubview(label)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(text: String) {
        label.text = text
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let s = Self.iconSize
        let labelX = s + Self.spacing
        let labelW = max(bounds.width - labelX, 0)
        let labelH = label.textRect(
            forBounds: CGRect(x: 0, y: 0, width: labelW, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 0
        ).height
        icon.frame = CGRect(x: 0, y: (ceil(labelH) - s) / 2, width: s, height: s)
        label.frame = CGRect(x: labelX, y: 0, width: labelW, height: ceil(labelH))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let labelW = max(width - Self.iconSize - Self.spacing, 0)
        let labelH = label.textRect(
            forBounds: CGRect(x: 0, y: 0, width: labelW, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 0
        ).height
        return ceil(max(labelH, Self.iconSize))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

// MARK: - ActionsContainerView (replaces PaddingContainerView for actions with 4pt top padding)

private final class ActionsContainerView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var content: UIView? {
        didSet {
            guard oldValue !== content else { return }
            oldValue?.removeFromSuperview()
            if let c = content, c.superview !== self { addSubview(c) }
            setNeedsLayout()
        }
    }
    private static let topPadding: CGFloat = 4

    override func layoutSubviews() {
        super.layoutSubviews()
        guard let content else { return }
        content.frame = CGRect(
            x: 0,
            y: Self.topPadding,
            width: bounds.width,
            height: max(bounds.height - Self.topPadding, 0)
        )
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        guard let content else { return .zero }
        let h = childHeight(of: content, for: width)
        return ceil(h + Self.topPadding)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

// MARK: - QuotesContainerView (replaces quotesContainer + quotesStack)

private final class QuotesContainerView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private var managedChildren: [UIView] = []
    private static let padding: CGFloat = 8
    private static let spacing: CGFloat = 8
    private static let dividerHeight: CGFloat = 1.0 / UIScreen.main.scale

    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = 16
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        layer.borderColor = UIColor.separator.cgColor
    }

    func setChildren(_ desired: [UIView]) {
        syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let p = Self.padding
        let contentWidth = max(bounds.width - p * 2, 0)
        var y = p
        for (i, child) in managedChildren.enumerated() {
            // Dividers get fixed height
            if child.backgroundColor == .separator && !(child is StatusUIKitView) {
                child.frame = CGRect(x: p, y: y, width: contentWidth, height: Self.dividerHeight)
                y += Self.dividerHeight
            } else {
                let h = childHeight(of: child, for: contentWidth)
                child.frame = CGRect(x: p, y: y, width: contentWidth, height: h)
                y += h
            }
            if i < managedChildren.count - 1 {
                y += Self.spacing
            }
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let p = Self.padding
        let contentWidth = max(width - p * 2, 0)
        var h = p
        for (i, child) in managedChildren.enumerated() {
            if child.backgroundColor == .separator && !(child is StatusUIKitView) {
                h += Self.dividerHeight
            } else {
                h += childHeight(of: child, for: contentWidth)
            }
            if i < managedChildren.count - 1 {
                h += Self.spacing
            }
        }
        h += p
        return ceil(h)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

// MARK: - Parent container

/// Wraps a `StatusUIKitView` with SwiftUI-equivalent leading line overlay and
/// an 8pt trailing spacer.
private final class ParentContainerView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    let child = StatusUIKitView()
    private let line = UIView()
    private static let bottomSpacing: CGFloat = 8

    override init(frame: CGRect) {
        super.init(frame: frame)
        line.backgroundColor = .separator
        addSubview(line)
        addSubview(child)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let childH = max(bounds.height - Self.bottomSpacing, 0)
        child.frame = CGRect(x: 0, y: 0, width: w, height: childH)
        line.frame = CGRect(x: 22, y: 44, width: 1, height: max(bounds.height - 44, 0))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let childH = childHeight(of: child, for: width)
        return ceil(childH + Self.bottomSpacing)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }

    func prepareForFitting(width: CGFloat) {
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        child.prepareForFitting(width: width)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }
}

// MARK: - Helpers

private extension UIView {
    func firstSuperview<T: UIView>(of type: T.Type) -> T? {
        var current: UIView? = self
        while let view = current {
            if let matched = view as? T {
                return matched
            }
            current = view.superview
        }
        return nil
    }
}
