import UIKit
import KotlinSharedUI

/// UIKit port of `TimelineView`.
final class TimelineUIView: UIView {
    var appearance = StatusUIKitAppearance(settings: AppearanceSettings.companion.Default) {
        didSet { if !isBatchConfiguring, data != nil { rebuild() } }
    }
    var detailStatusKey: MicroBlogKey?
    var showTranslate: Bool = true {
        didSet { if !isBatchConfiguring, data != nil { rebuild() } }
    }
    var onOpenURL: ((URL) -> Void)? {
        didSet { if !isBatchConfiguring { forwardOpenURL() } }
    }

    private var data: UiTimelineV2?
    private var isBatchConfiguring = false
    private var lastRenderSignature: RenderSignature?

    private struct RenderSignature: Equatable {
        let itemKey: String
        let itemType: String
        let renderHash: Int32
        let detailStatusKey: String
        let showTranslate: Bool
        let appearance: StatusUIKitAppearance

        init(
            data: UiTimelineV2,
            appearance: StatusUIKitAppearance,
            detailStatusKey: MicroBlogKey?,
            showTranslate: Bool
        ) {
            itemKey = data.itemKey ?? ""
            itemType = data.itemType
            renderHash = data.renderHash
            self.detailStatusKey = detailStatusKey.map { String(describing: $0) } ?? ""
            self.showTranslate = showTranslate
            self.appearance = appearance
        }
    }

    private let stack: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .fill
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()
    private let messageView = StatusTopMessageUIView()
    private lazy var messageContainer = MutablePaddingContainerView(content: messageView)
    private let feedView = FeedUIView()
    private let statusView = StatusUIKitView()
    private let userView = TimelineUserUIView()
    private let userListView = UserListUIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiTimelineV2) {
        let signature = RenderSignature(
            data: data,
            appearance: appearance,
            detailStatusKey: detailStatusKey,
            showTranslate: showTranslate
        )
        self.data = data
        guard lastRenderSignature != signature else {
            forwardOpenURL()
            return
        }
        lastRenderSignature = signature
        rebuild()
    }

    func configure(
        data: UiTimelineV2,
        appearance: StatusUIKitAppearance,
        detailStatusKey: MicroBlogKey?,
        showTranslate: Bool = true,
        onOpenURL: ((URL) -> Void)?
    ) {
        isBatchConfiguring = true
        self.appearance = appearance
        self.detailStatusKey = detailStatusKey
        self.showTranslate = showTranslate
        self.onOpenURL = onOpenURL
        self.data = data
        isBatchConfiguring = false
        let signature = RenderSignature(
            data: data,
            appearance: appearance,
            detailStatusKey: detailStatusKey,
            showTranslate: showTranslate
        )
        guard lastRenderSignature != signature else {
            forwardOpenURL()
            return
        }
        lastRenderSignature = signature
        rebuild()
    }

    private func rebuild() {
        guard let data else {
            stack.flareSyncArrangedSubviews([])
            return
        }

        var desired: [UIView] = []
        switch onEnum(of: data) {
        case .feed(let feed):
            feedView.onOpenURL = onOpenURL
            feedView.configure(data: feed)
            desired.append(feedView)

        case .post(let post):
            if let message = post.message {
                configureMessage(message, topMessageOnly: false)
                desired.append(messageContainer)
            }
            statusView.openURL = onOpenURL
            statusView.configure(
                data: post,
                appearance: appearance,
                isDetail: detailStatusKey == post.statusKey,
                showTranslate: showTranslate,
            )
            desired.append(statusView)

        case .user(let user):
            if let message = user.message {
                configureMessage(message, topMessageOnly: false)
                desired.append(messageContainer)
            }
            userView.appearance = appearance
            userView.onOpenURL = onOpenURL
            userView.configure(data: user)
            desired.append(userView)

        case .userList(let userList):
            if let message = userList.message {
                configureMessage(message, topMessageOnly: false)
                desired.append(messageContainer)
            }
            userListView.appearance = appearance
            userListView.onOpenURL = onOpenURL
            userListView.configure(data: userList)
            desired.append(userListView)

        case .message(let message):
            configureMessage(message, topMessageOnly: true)
            desired.append(messageContainer)
        }
        stack.flareSyncArrangedSubviews(desired)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func configureMessage(_ message: UiTimelineV2.Message, topMessageOnly: Bool) {
        messageView.onOpenURL = onOpenURL
        messageView.configure(message: message, topMessageOnly: topMessageOnly)

        let leading: CGFloat
        if !appearance.fullWidthPost && !topMessageOnly {
            let iconSize = UIFontMetrics(forTextStyle: .caption1).scaledValue(for: 15)
            leading = 44 - iconSize
        } else {
            leading = 0
        }
        messageContainer.insets = UIEdgeInsets(top: 0, left: leading, bottom: 0, right: 0)
    }

    private func forwardOpenURL() {
        messageView.onOpenURL = onOpenURL
        feedView.onOpenURL = onOpenURL
        statusView.openURL = onOpenURL
        userView.onOpenURL = onOpenURL
        userListView.onOpenURL = onOpenURL
    }

    func autoplayCandidates(prefix: String) -> [TimelineVideoAutoplayCandidate] {
        guard let data, !isHidden, window != nil else { return [] }
        guard case .post = onEnum(of: data) else { return [] }
        return statusView.autoplayCandidates(prefix: prefix)
    }

}

extension UIView {
    static func padding(_ content: UIView, insets: UIEdgeInsets) -> UIView {
        PaddingContainerView(content: content, insets: insets)
    }
}

private final class PaddingContainerView: UIView {
    init(content: UIView, insets: UIEdgeInsets) {
        super.init(frame: .zero)
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: topAnchor, constant: insets.top),
            content.leadingAnchor.constraint(equalTo: leadingAnchor, constant: insets.left),
            content.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -insets.right),
            content.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -insets.bottom),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }
}

private final class MutablePaddingContainerView: UIView {
    var insets: UIEdgeInsets = .zero {
        didSet { applyInsets() }
    }

    private let content: UIView
    private var topConstraint: NSLayoutConstraint!
    private var leadingConstraint: NSLayoutConstraint!
    private var trailingConstraint: NSLayoutConstraint!
    private var bottomConstraint: NSLayoutConstraint!

    init(content: UIView) {
        self.content = content
        super.init(frame: .zero)
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)
        topConstraint = content.topAnchor.constraint(equalTo: topAnchor)
        leadingConstraint = content.leadingAnchor.constraint(equalTo: leadingAnchor)
        trailingConstraint = content.trailingAnchor.constraint(equalTo: trailingAnchor)
        bottomConstraint = content.bottomAnchor.constraint(equalTo: bottomAnchor)
        NSLayoutConstraint.activate([
            topConstraint,
            leadingConstraint,
            trailingConstraint,
            bottomConstraint,
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    private func applyInsets() {
        topConstraint.constant = insets.top
        leadingConstraint.constant = insets.left
        trailingConstraint.constant = -insets.right
        bottomConstraint.constant = -insets.bottom
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }
}
