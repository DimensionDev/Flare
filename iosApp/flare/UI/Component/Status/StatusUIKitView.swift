import UIKit
import SwiftUI
import KotlinSharedUI

/// UIKit port of the SwiftUI `StatusView`.
///
/// The layout skeleton (outer / main / inner / content stacks) and every leaf
/// view are built once and cached on this instance. `rebuild()` computes the
/// desired arranged-subview list for each stack and runs a structural diff
/// (`syncArrangedSubviews`) so that cell reuse doesn't tear down and rebuild
/// RichText / Media / Poll / Action subtrees every configure — it just
/// reconfigures the existing instances in place.
///
/// `parents` and `quotes` are backed by simple object pools
/// (`parentContainers` / `quoteChildren`). `@State expand` maps to a stored
/// property with explicit `rebuild()` calls.
final class StatusUIKitView: UIView, UIGestureRecognizerDelegate {
    // MARK: - Environment mirrors (inputs that were `@Environment` in SwiftUI)

    /// Forwarded to all URL-clicking machinery.
    var openURL: ((URL) -> Void)?

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

    // MARK: - Skeleton stacks

    private let outerStack: UIStackView = {
        let s = UIStackView()
        s.axis = .vertical
        s.alignment = .fill
        s.spacing = 0
        s.translatesAutoresizingMaskIntoConstraints = false
        return s
    }()
    private let mainRow: UIStackView = {
        let s = UIStackView()
        s.axis = .horizontal
        s.alignment = .top
        s.spacing = 8
        return s
    }()
    private let innerColumn: UIStackView = {
        let s = UIStackView()
        s.axis = .vertical
        s.alignment = .fill
        s.spacing = 8
        return s
    }()
    private let contentColumn: UIStackView = {
        let s = UIStackView()
        s.axis = .vertical
        s.alignment = .fill
        s.spacing = 8
        return s
    }()

    // MARK: - Cached leaves (built once, reconfigured per `rebuild`)

    private let avatarView = AvatarUIView()
    private let userHeaderFull = UserOnelineUIView(showAvatar: false)
    private let userHeaderQuote = UserOnelineUIView(showAvatar: true)
    private let userHeaderCompat = UserCompatUIView()
    private let topEndView = StatusTopEndView()

    private let replyToRow = UIStackView()
    private let replyToIcon = UIImageView(image: UIImage(named: "fa-reply"))
    private let replyToLabel = UILabel()

    private let contentWarningText = RichTextUIView()
    private let contentWarningToggle = UIButton(type: .system)

    private let bodyText = RichTextUIView()
    private let expandMoreButton = UIButton(type: .system)

    private let translateView = StatusTranslateUIView()
    private let pollView = StatusPollUIView()
    private let mediaView = StatusMediaContentUIView()
    private let normalCardView = StatusCardUIView(cornerRadius: 16)
    private let compatCardView = StatusCompatCardUIView(cornerRadius: 16)

    private let quotesContainer = UIView()
    private let quotesStack = UIStackView()
    private var quoteChildren: [StatusUIKitView] = []
    private var quoteDividers: [UIView] = []

    private let sourceChannelRow = UIStackView()
    private let sourceChannelIcon = UIImageView(image: UIImage(named: "fa-tv"))
    private let sourceChannelLabel = UILabel()

    private let reactionView = StatusReactionUIView()
    private let detailTimestampView: DateTimeUILabel = {
        let v = DateTimeUILabel()
        v.fullTime = true
        return v
    }()

    private let actionsView = StatusActionsUIView()
    private lazy var actionsContainer: UIView = UIView.padding(
        actionsView,
        insets: UIEdgeInsets(top: 4, left: 0, bottom: 0, right: 0)
    )

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
        addSubview(outerStack)
        NSLayoutConstraint.activate([
            outerStack.topAnchor.constraint(equalTo: topAnchor),
            outerStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            outerStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            outerStack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        setupAvatar()
        setupReplyToRow()
        setupContentWarningToggle()
        setupExpandMoreButton()
        setupSourceChannelRow()
        setupQuotesContainer()

        // Assemble empty skeleton — `rebuild()` syncs actual arranged children.
        mainRow.addArrangedSubview(innerColumn)
        innerColumn.addArrangedSubview(contentColumn)
        outerStack.addArrangedSubview(mainRow)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onRootTapped))
        tap.cancelsTouchesInView = false
        tap.delegate = self
        addGestureRecognizer(tap)
    }

    private func setupAvatar() {
        avatarView.widthAnchor.constraint(equalToConstant: 44).isActive = true
        avatarView.heightAnchor.constraint(equalToConstant: 44).isActive = true
        avatarView.isUserInteractionEnabled = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(onAvatarTapped))
        avatarView.addGestureRecognizer(tap)
    }

    private func setupReplyToRow() {
        replyToRow.axis = .horizontal
        replyToRow.alignment = .center
        replyToRow.spacing = 4
        replyToIcon.tintColor = .secondaryLabel
        replyToIcon.contentMode = .scaleAspectFit
        replyToIcon.widthAnchor.constraint(equalToConstant: 12).isActive = true
        replyToIcon.heightAnchor.constraint(equalToConstant: 12).isActive = true
        replyToLabel.font = .preferredFont(forTextStyle: .caption1)
        replyToLabel.textColor = .secondaryLabel
        replyToLabel.adjustsFontForContentSizeCategory = true
        replyToLabel.numberOfLines = 0
        replyToRow.addArrangedSubview(replyToIcon)
        replyToRow.addArrangedSubview(replyToLabel)
    }

    private func setupContentWarningToggle() {
        contentWarningToggle.contentHorizontalAlignment = .leading
        contentWarningToggle.addAction(
            UIAction { [weak self] _ in self?.toggleExpand() },
            for: .touchUpInside
        )
    }

    private func setupExpandMoreButton() {
        expandMoreButton.contentHorizontalAlignment = .leading
        expandMoreButton.setTitle(String(localized: "mastodon_item_show_more"), for: .normal)
        expandMoreButton.addAction(
            UIAction { [weak self] _ in self?.expandOnly() },
            for: .touchUpInside
        )
    }

    private func setupSourceChannelRow() {
        sourceChannelRow.axis = .horizontal
        sourceChannelRow.alignment = .center
        sourceChannelRow.spacing = 4
        sourceChannelIcon.tintColor = .secondaryLabel
        sourceChannelIcon.contentMode = .scaleAspectFit
        sourceChannelIcon.widthAnchor.constraint(equalToConstant: 12).isActive = true
        sourceChannelIcon.heightAnchor.constraint(equalToConstant: 12).isActive = true
        sourceChannelLabel.font = .preferredFont(forTextStyle: .footnote)
        sourceChannelLabel.textColor = .secondaryLabel
        sourceChannelLabel.adjustsFontForContentSizeCategory = true
        sourceChannelLabel.numberOfLines = 0
        sourceChannelRow.addArrangedSubview(sourceChannelIcon)
        sourceChannelRow.addArrangedSubview(sourceChannelLabel)
    }

    private func setupQuotesContainer() {
        quotesContainer.layer.cornerRadius = 16
        quotesContainer.layer.borderWidth = 1
        quotesContainer.layer.borderColor = UIColor.separator.cgColor

        quotesStack.axis = .vertical
        quotesStack.alignment = .fill
        quotesStack.spacing = 8
        quotesStack.translatesAutoresizingMaskIntoConstraints = false
        quotesContainer.addSubview(quotesStack)
        NSLayoutConstraint.activate([
            quotesStack.topAnchor.constraint(equalTo: quotesContainer.topAnchor, constant: 8),
            quotesStack.leadingAnchor.constraint(equalTo: quotesContainer.leadingAnchor, constant: 8),
            quotesStack.trailingAnchor.constraint(equalTo: quotesContainer.trailingAnchor, constant: -8),
            quotesStack.bottomAnchor.constraint(equalTo: quotesContainer.bottomAnchor, constant: -8),
        ])
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
        self.data = data
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
        guard lastConfigureSignature != signature else {
            forwardOpenURL()
            return
        }
        lastConfigureSignature = signature
        rebuild()
    }

    func prepareForPoolRemoval() {
        data = nil
        lastConfigureSignature = nil
        boundStatusKey = nil
        expand = false

        mediaView.prepareForPoolRemoval()
        actionsView.prepareForPoolRemoval()
        syncArrangedSubviews(quotesStack, [])
        syncArrangedSubviews(contentColumn, [])
        syncArrangedSubviews(innerColumn, [])
        syncArrangedSubviews(mainRow, [])
        syncArrangedSubviews(outerStack, [])

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
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)

        let contentWidth = fittingContentColumnWidth(for: width)
        contentWarningText.prepareForFitting(width: contentWidth)
        bodyText.prepareForFitting(width: contentWidth)
        mediaView.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: mediaView.bounds.height)
        normalCardView.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: normalCardView.bounds.height)
        compatCardView.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: compatCardView.bounds.height)

        for container in parentContainers where container.superview != nil {
            container.prepareForFitting(width: width)
        }

        let quoteWidth = max(contentWidth - 16, 1)
        quotesContainer.bounds = CGRect(x: 0, y: 0, width: contentWidth, height: quotesContainer.bounds.height)
        for child in quoteChildren where child.superview != nil {
            child.prepareForFitting(width: quoteWidth)
        }

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        guard horizontalFittingPriority == .required,
              targetSize.width.isFinite,
              targetSize.width > 0 else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }

        prepareForFitting(width: targetSize.width)
        let size = outerStack.systemLayoutSizeFitting(
            CGSize(width: targetSize.width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: verticalFittingPriority
        )
        return CGSize(width: targetSize.width, height: ceil(size.height))
    }

    // MARK: - SwiftUI-equivalent computed

    /// `(!fullWidthPost || withLeadingPadding) && !isQuote && !isDetail`
    private var showAsFullWidth: Bool {
        (!appearance.fullWidthPost || withLeadingPadding) && !isQuote && !isDetail
    }

    private func fittingContentColumnWidth(for width: CGFloat) -> CGFloat {
        let wantsAvatar = showAsFullWidth && data?.user != nil
        let avatarWidth = wantsAvatar ? 44 + mainRow.spacing : 0
        return max(width - avatarWidth, 1)
    }

    // MARK: - Rebuild (incremental diff)

    private func rebuild() {
        guard let data = data else {
            syncArrangedSubviews(outerStack, [])
            return
        }

        // Avatar
        let wantsAvatar = showAsFullWidth && data.user != nil
        if wantsAvatar, let user = data.user {
            avatarView.avatarShape = appearance.avatarShape
            avatarView.set(url: user.avatar)
        }

        // Header
        let headerView = selectAndConfigureHeader(data: data)

        // Content column children
        let contentItems = buildContentColumnList(data: data)
        syncArrangedSubviews(contentColumn, contentItems)

        // innerColumn: [header?, contentColumn]
        var innerDesired: [UIView] = []
        if let headerView { innerDesired.append(headerView) }
        innerDesired.append(contentColumn)
        syncArrangedSubviews(innerColumn, innerDesired)

        // mainRow: [avatar?, innerColumn]
        var mainRowDesired: [UIView] = []
        if wantsAvatar { mainRowDesired.append(avatarView) }
        mainRowDesired.append(innerColumn)
        syncArrangedSubviews(mainRow, mainRowDesired)

        // outerStack: [parents..., mainRow]
        let parents = activeParentContainers(data: data)
        var outerDesired: [UIView] = parents
        outerDesired.append(mainRow)
        syncArrangedSubviews(outerStack, outerDesired)

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func selectAndConfigureHeader(data: UiTimelineV2.Post) -> UIView? {
        guard let user = data.user else { return nil }
        topEndView.configure(
            post: data,
            showPlatformLogo: appearance.showPlatformLogo,
            absoluteTimestamp: appearance.absoluteTimestamp,
            isDetail: isDetail
        )
        let onClicked: () -> Void = { [weak self] in self?.onUserTapped() }

        if showAsFullWidth {
            userHeaderFull.avatar.avatarShape = appearance.avatarShape
            userHeaderFull.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderFull
        } else if isQuote {
            userHeaderQuote.avatar.avatarShape = appearance.avatarShape
            userHeaderQuote.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderQuote
        } else {
            userHeaderCompat.avatar.avatarShape = appearance.avatarShape
            userHeaderCompat.configure(data: user, trailing: topEndView, onClicked: onClicked)
            return userHeaderCompat
        }
    }

    private func buildContentColumnList(data: UiTimelineV2.Post) -> [UIView] {
        var items: [UIView] = []

        // reply-to
        if let replyToHandle = data.replyToHandle {
            replyToLabel.text = String(localized: "Reply to \(replyToHandle)")
            items.append(replyToRow)
        }

        // content warning
        let hasCW = (data.contentWarning != nil) && (data.contentWarning?.isEmpty == false)
        if hasCW, let cw = data.contentWarning {
            contentWarningText.configure(
                text: cw,
                lineLimit: nil,
                isTextSelectionEnabled: isDetail,
                onOpenURL: openURL,
                contentKey: Int(data.renderHash)
            )
            items.append(contentWarningText)
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
                    items.append(expandMoreButton)
                }
            }
        }

        // translate (detail-only)
        if isDetail, showTranslate {
            translateView.content = data.content
            translateView.contentWarning = data.contentWarning
            items.append(translateView)
        }

        // poll
        if let poll = data.poll, showMediaInput {
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
                compatCardView.layer.cornerRadius = corner
                compatCardView.configure(data: card)
                compatCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
                items.append(compatCardView)
            } else {
                normalCardView.layer.cornerRadius = corner
                normalCardView.configure(data: card)
                normalCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
                items.append(normalCardView)
            }
        }

        // quotes (not when self is quote)
        if !data.quote.isEmpty, !isQuote {
            updateQuotes(quotes: data.quote)
            items.append(quotesContainer)
        } else {
            updateQuotes(quotes: [])
        }

        // source channel + reactions
        if showMediaInput, !isQuote {
            if let channel = data.sourceChannel {
                sourceChannelLabel.text = channel.name
                items.append(sourceChannelRow)
            }
            if !data.emojiReactions.isEmpty {
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
            detailTimestampView.absoluteTimestamp = appearance.absoluteTimestamp
            detailTimestampView.set(data: data.createdAt)
            items.append(detailTimestampView)
        }

        // actions
        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if !hideActions {
            actionsView.onOpenURL = { [weak self] in self?.openURL?($0) }
            actionsView.configure(
                data: Array(data.actions),
                useText: false,
                allowSpacer: true,
                postActionStyle: appearance.postActionStyle,
                showNumbers: appearance.showNumbers,
                isDetail: isDetail
            )
            items.append(actionsContainer)
        }

        return items
    }

    // MARK: - Pools

    private func activeParentContainers(data: UiTimelineV2.Post) -> [ParentContainerView] {
        let parentData = showParents ? Array(data.parents) : []
        while parentContainers.count < parentData.count {
            parentContainers.append(ParentContainerView())
        }
        for (i, parent) in parentData.enumerated() {
            let container = parentContainers[i]
//            container.child.appearance = appearance
            container.child.openURL = openURL
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
            d.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale).isActive = true
            quoteDividers.append(d)
        }
        var desired: [UIView] = []
        for (i, quote) in quotes.enumerated() {
            let child = quoteChildren[i]
//            child.appearance = appearance
            child.openURL = openURL
            child.configure(data: quote, appearance: appearance, isQuote: true, forceHideActions: true)
            desired.append(child)
            if i != quotes.count - 1 {
                desired.append(quoteDividers[i])
            }
        }
        syncArrangedSubviews(quotesStack, desired)
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
            mediaView.performLightweightPoolCleanup()
        } else {
            mediaView.prepareForPoolRemoval()
        }

        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if data != nil, !hideActions {
            actionsView.performLightweightPoolCleanup()
        } else {
            actionsView.prepareForPoolRemoval()
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
            mediaView.performDeferredPoolCleanup()
        } else {
            mediaView.prepareForPoolRemoval()
        }

        let hideActions = (appearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if data != nil, !hideActions {
            actionsView.performDeferredPoolCleanup()
        } else {
            actionsView.prepareForPoolRemoval()
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
        contentWarningText.onOpenURL = openURL
        bodyText.onOpenURL = openURL
        normalCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
        compatCardView.onOpenURL = { [weak self] in self?.openURL?($0) }
        actionsView.onOpenURL = { [weak self] in self?.openURL?($0) }
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
            candidates.append(contentsOf: mediaView.autoplayCandidates(prefix: statusPrefix))
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

    // MARK: - Arranged-subview diff

    /// Reorders `stack.arrangedSubviews` to match `desired`, reusing the same
    /// `UIView` instances rather than recreating them. Views in the current
    /// stack that don't appear in `desired` are removed (both from the
    /// arranged list and from the view hierarchy).
    private func syncArrangedSubviews(_ stack: UIStackView, _ desired: [UIView]) {
        let desiredIDs = Set(desired.map { ObjectIdentifier($0) })
        // 1. Remove arranged subviews no longer wanted.
        for view in stack.arrangedSubviews where !desiredIDs.contains(ObjectIdentifier(view)) {
            stack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }
        // 2. Reorder / insert so the arranged list matches `desired` element-wise.
        for (i, target) in desired.enumerated() {
            let arranged = stack.arrangedSubviews
            if arranged.indices.contains(i), arranged[i] === target {
                continue
            }
            if arranged.contains(where: { $0 === target }) {
                stack.removeArrangedSubview(target)
            }
            stack.insertArrangedSubview(target, at: min(i, stack.arrangedSubviews.count))
        }
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
                    // Scoped invalidation: only this cell re-measures.
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

    fileprivate func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: OpenURLAction { [weak self] url in
            self?.openURL?(url)
            return .handled
        })
    }
}

// MARK: - Parent container

/// Wraps a `StatusUIKitView` with SwiftUI-equivalent leading line overlay and
/// an 8pt trailing spacer. Pooled per `StatusUIKitView` instance so parent
/// chains reuse these wrappers across configures.
private final class ParentContainerView: UIView {
    let child = StatusUIKitView()
    private let line = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        line.backgroundColor = .separator
        line.translatesAutoresizingMaskIntoConstraints = false
        child.translatesAutoresizingMaskIntoConstraints = false
        addSubview(line)
        addSubview(child)
        NSLayoutConstraint.activate([
            child.topAnchor.constraint(equalTo: topAnchor),
            child.leadingAnchor.constraint(equalTo: leadingAnchor),
            child.trailingAnchor.constraint(equalTo: trailingAnchor),
            // 8pt spacer after child
            child.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),

            line.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 22),
            line.widthAnchor.constraint(equalToConstant: 1),
            line.topAnchor.constraint(equalTo: topAnchor, constant: 44),
            line.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

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
