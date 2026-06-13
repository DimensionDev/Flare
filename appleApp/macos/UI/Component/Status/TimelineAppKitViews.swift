import AppKit
import AppleFontAwesome
import Combine
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

final class TimelineUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var statusAppearance = StatusAppKitAppearance(timeline: TimelineAppearance.companion.Default) {
        didSet {
            guard statusAppearance != oldValue else { return }
            rebuildIfNeeded()
        }
    }
    var detailStatusKey: MicroBlogKey? {
        didSet {
            guard String(describing: detailStatusKey) != String(describing: oldValue) else { return }
            rebuildIfNeeded()
        }
    }
    var showTranslate = true {
        didSet {
            guard showTranslate != oldValue else { return }
            rebuildIfNeeded()
        }
    }
    var aiTldrEnabled = false {
        didSet {
            guard aiTldrEnabled != oldValue else { return }
            rebuildIfNeeded()
        }
    }
    var onOpenURL: ((URL) -> Void)?
    var onLocalHeightInvalidated: (() -> Void)?

    private var data: UiTimelineV2?
    private var managedChildren: [NSView] = []

    private let messageView = StatusTopMessageUIView()
    private let feedView = FeedUIView()
    private let statusView = StatusUIKitView()
    private let userView = TimelineUserUIView()
    private let userListView = UserListUIView()

    private static let spacing: CGFloat = 8

    func configure(
        data: UiTimelineV2,
        appearance: StatusAppKitAppearance,
        detailStatusKey: MicroBlogKey?,
        showTranslate: Bool = true,
        aiTldrEnabled: Bool = false,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.data = data
        self.statusAppearance = appearance
        self.detailStatusKey = detailStatusKey
        self.showTranslate = showTranslate
        self.aiTldrEnabled = aiTldrEnabled
        self.onOpenURL = onOpenURL
        rebuild()
    }

    override func layout() {
        super.layout()
        manualLayoutVertical(views: managedChildren, x: 0, y: 0, width: bounds.width, spacing: Self.spacing)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(manualHeightForVertical(views: managedChildren, width: width, spacing: Self.spacing))
    }

    private func rebuildIfNeeded() {
        guard data != nil else { return }
        rebuild()
    }

    private func rebuild() {
        guard let data else {
            syncManagedSubviews(parent: self, current: &managedChildren, desired: [])
            return
        }

        let desired: [NSView]
        switch onEnum(of: data) {
        case .feed(let feed):
            feedView.configure(data: feed, appearance: statusAppearance, onOpenURL: onOpenURL)
            desired = [feedView]
        case .post(let post):
            statusView.configure(
                data: post,
                appearance: statusAppearance,
                detailStatusKey: detailStatusKey,
                showTranslate: showTranslate,
                aiTldrEnabled: aiTldrEnabled,
                onOpenURL: onOpenURL
            )
            statusView.onLocalHeightInvalidated = { [weak self] in
                self?.handleLocalHeightInvalidated()
            }
            if let message = post.message {
                messageView.configure(message: message, appearance: statusAppearance, topMessageOnly: false, onOpenURL: onOpenURL)
                desired = [messageView, statusView]
            } else {
                desired = [statusView]
            }
        case .user(let user):
            userView.configure(data: user, appearance: statusAppearance, onOpenURL: onOpenURL)
            if let message = user.message {
                messageView.configure(message: message, appearance: statusAppearance, topMessageOnly: false, onOpenURL: onOpenURL)
                desired = [messageView, userView]
            } else {
                desired = [userView]
            }
        case .userList(let userList):
            userListView.configure(data: userList, appearance: statusAppearance, onOpenURL: onOpenURL)
            userListView.onLocalHeightInvalidated = { [weak self] in
                self?.handleLocalHeightInvalidated()
            }
            if let message = userList.message {
                messageView.configure(message: message, appearance: statusAppearance, topMessageOnly: false, onOpenURL: onOpenURL)
                desired = [messageView, userListView]
            } else {
                desired = [userListView]
            }
        case .message(let message):
            messageView.configure(message: message, appearance: statusAppearance, topMessageOnly: true, onOpenURL: onOpenURL)
            desired = [messageView]
        }

        syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
        needsLayout = true
    }

    private func handleLocalHeightInvalidated() {
        needsLayout = true
        superview?.needsLayout = true
        onLocalHeightInvalidated?()
    }
}

final class TimelinePlaceholderUIView: FlippedView, TimelineHeightProviding {
    private let avatar = FlippedView()
    private let nameBar = FlippedView()
    private let handleBar = FlippedView()
    private let bodyBar = FlippedView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        [avatar, nameBar, handleBar, bodyBar].forEach { view in
            view.wantsLayer = true
            view.layer?.backgroundColor = NSColor.placeholderTextColor.withAlphaComponent(0.22).cgColor
            addSubview(view)
        }
        avatar.layer?.cornerRadius = 22
        nameBar.layer?.cornerRadius = 4
        handleBar.layer?.cornerRadius = 4
        bodyBar.layer?.cornerRadius = 6
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        avatar.frame = CGRect(x: 0, y: 0, width: 44, height: 44)
        nameBar.frame = CGRect(x: 52, y: 4, width: min(120, max(bounds.width - 52, 0)), height: 14)
        handleBar.frame = CGRect(x: 52, y: 24, width: min(80, max(bounds.width - 52, 0)), height: 10)
        bodyBar.frame = CGRect(x: 0, y: 56, width: bounds.width, height: 96)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        152
    }
}

final class ListEmptyUIView: FlippedView {
    private let icon = NSImageView()
    private let label = TimelineTextField(font: .preferredFont(forTextStyle: .headline), color: .labelColor)

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        icon.image = NSImage(systemSymbolName: "questionmark.text.page", accessibilityDescription: nil)
        icon.symbolConfiguration = .init(pointSize: 48, weight: .regular)
        label.alignment = .center
        label.stringValue = LocalizedStrings.string("list_empty_title", fallback: "No items")
        addSubview(icon)
        addSubview(label)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        let contentWidth = min(bounds.width - 32, 320)
        let labelHeight = childHeight(of: label, for: contentWidth)
        let totalHeight: CGFloat = 64 + 8 + labelHeight
        let originY = max((bounds.height - totalHeight) / 2, 16)
        icon.frame = CGRect(x: (bounds.width - 64) / 2, y: originY, width: 64, height: 64)
        label.frame = CGRect(x: (bounds.width - contentWidth) / 2, y: originY + 72, width: contentWidth, height: labelHeight)
    }
}

final class ListErrorUIView: FlippedView {
    var onOpenURL: ((URL) -> Void)?

    private let icon = NSImageView()
    private let title = TimelineTextField(font: .preferredFont(forTextStyle: .headline), color: .labelColor)
    private let message = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .secondaryLabelColor)
    private let detail = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor)
    private let button = NSButton()
    private var retry: (() -> Void)?
    private var opensLogin = false

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        icon.symbolConfiguration = .init(pointSize: 48, weight: .regular)
        title.alignment = .center
        message.alignment = .center
        detail.alignment = .center
        button.bezelStyle = .rounded
        button.target = self
        button.action = #selector(handleButton)
        [icon, title, message, button, detail].forEach(addSubview)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(error: KotlinThrowable, onRetry: @escaping () -> Void) {
        retry = onRetry
        message.stringValue = ""
        detail.stringValue = ""
        opensLogin = false

        if let expired = error as? LoginExpiredException {
            icon.image = NSImage(systemSymbolName: "person.badge.shield.exclamationmark", accessibilityDescription: nil)
            title.stringValue = String(
                format: LocalizedStrings.string("error_login_expired %@", fallback: "Login expired %@"),
                "\(expired.accountKey)"
            )
            button.title = LocalizedStrings.string("error_login_expired_action", fallback: "Log in again")
            opensLogin = true
        } else if error is RequireReLoginException {
            icon.image = NSImage(systemSymbolName: "person.badge.shield.exclamationmark", accessibilityDescription: nil)
            title.stringValue = LocalizedStrings.string("permission_denied_title", fallback: "Permission denied")
            message.stringValue = LocalizedStrings.string("permission_denied_message", fallback: "Please log in again.")
            button.title = LocalizedStrings.string("error_login_expired_action", fallback: "Log in again")
            opensLogin = true
        } else {
            icon.image = NSImage(systemSymbolName: "exclamationmark.triangle", accessibilityDescription: nil)
            title.stringValue = LocalizedStrings.string("error_generic", fallback: "Something went wrong")
            button.title = LocalizedStrings.string("action_retry", fallback: "Retry")
            detail.stringValue = error.message ?? ""
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let contentWidth = min(bounds.width - 48, 440)
        var views: [NSView] = [icon, title]
        if !message.stringValue.isEmpty { views.append(message) }
        views.append(button)
        if !detail.stringValue.isEmpty { views.append(detail) }

        let heights = views.map { view -> CGFloat in
            if view === icon { return 64 }
            if view === button { return 30 }
            return childHeight(of: view, for: contentWidth)
        }
        let totalHeight = heights.reduce(0, +) + CGFloat(max(views.count - 1, 0)) * 8
        var y = max((bounds.height - totalHeight) / 2, 16)
        for (index, view) in views.enumerated() {
            let height = heights[index]
            let width: CGFloat = view === icon ? 64 : (view === button ? min(button.intrinsicContentSize.width + 20, contentWidth) : contentWidth)
            view.frame = CGRect(x: (bounds.width - width) / 2, y: y, width: width, height: height)
            y += height + 8
        }
    }

    @objc private func handleButton() {
        if opensLogin, let url = URL(string: DeeplinkRoute.Login.shared.toUri()) {
            onOpenURL?(url)
        } else {
            retry?()
        }
    }
}

// MARK: - Timeline leaf views

final class RichTextUIView: FlippedView, TimelineHeightProviding {
    private let label = TimelineTextField()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        addSubview(label)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(_ text: UiRichText, font: NSFont, color: NSColor = .labelColor, maximumLines: Int = 0) {
        label.font = font
        label.textColor = color
        label.maximumLines = maximumLines
        label.stringValue = text.innerText.isEmpty ? text.raw : text.innerText
        needsLayout = true
    }

    func configure(rawText: String, font: NSFont, color: NSColor = .labelColor, maximumLines: Int = 0) {
        label.font = font
        label.textColor = color
        label.maximumLines = maximumLines
        label.stringValue = rawText
        needsLayout = true
    }

    override func layout() {
        super.layout()
        label.frame = bounds
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        childHeight(of: label, for: width)
    }
}

class RemoteImageView: FlippedView {
    private let imageView = NSImageView()
    private var currentURL: String?
    private var task: Task<Void, Never>?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.placeholderTextColor.withAlphaComponent(0.16).cgColor
        imageView.imageScaling = .scaleAxesIndependently
        addSubview(imageView)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(url: String?, cornerRadius: CGFloat = 0) {
        layer?.cornerRadius = cornerRadius
        guard currentURL != url else { return }
        currentURL = url
        imageView.image = nil
        task?.cancel()
        guard let urlString = url, let url = URL(string: urlString) else { return }
        task = Task { [weak self] in
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                guard !Task.isCancelled, let image = NSImage(data: data) else { return }
                await MainActor.run {
                    guard self?.currentURL == urlString else { return }
                    self?.imageView.image = image
                }
            } catch {
                await MainActor.run {
                    guard self?.currentURL == urlString else { return }
                    self?.imageView.image = NSImage(systemSymbolName: "photo", accessibilityDescription: nil)
                }
            }
        }
    }

    override func layout() {
        super.layout()
        imageView.frame = bounds
    }
}

final class AvatarUIView: RemoteImageView {
    func configure(profile: UiProfile?, appearance: StatusAppKitAppearance) {
        configure(url: profile?.avatar?.previewUrl ?? profile?.avatar?.url, cornerRadius: 22)
    }

    override func layout() {
        super.layout()
        layer?.cornerRadius = min(bounds.width, bounds.height) / 2
    }
}

final class UserOnelineUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let avatar = AvatarUIView()
    private let name = TimelineTextField(font: .boldSystemFont(ofSize: 14), color: .labelColor, maximumLines: 1)
    private let handle = TimelineTextField(font: .systemFont(ofSize: 12), color: .secondaryLabelColor, maximumLines: 1)
    private var trailing: NSView?
    private var showAvatar: Bool
    private var profile: UiProfile?
    private var onOpenURL: ((URL) -> Void)?

    init(showAvatar: Bool = true) {
        self.showAvatar = showAvatar
        super.init(frame: .zero)
        addSubview(avatar)
        addSubview(name)
        addSubview(handle)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        profile: UiProfile,
        appearance: StatusAppKitAppearance,
        showAvatar: Bool? = nil,
        trailing: NSView? = nil,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.profile = profile
        self.onOpenURL = onOpenURL
        if let showAvatar { self.showAvatar = showAvatar }
        if self.trailing !== trailing {
            self.trailing?.removeFromSuperview()
            self.trailing = trailing
            if let trailing {
                addSubview(trailing)
            }
        }
        avatar.configure(profile: profile, appearance: appearance)
        avatar.isHidden = !self.showAvatar
        name.font = appearance.bodyBoldFont
        handle.font = appearance.captionFont
        name.stringValue = profile.name.innerText.isEmpty ? profile.name.raw : profile.name.innerText
        handle.stringValue = "@\(profile.handleWithoutAt)"
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let rowHeight = preferredSingleLineHeight(width: bounds.width)
        let avatarSize: CGFloat = showAvatar ? 20 : 0
        if showAvatar {
            avatar.frame = CGRect(x: 0, y: (rowHeight - avatarSize) / 2, width: avatarSize, height: avatarSize).integral
        } else {
            avatar.frame = .zero
        }
        let labelX = showAvatar ? avatarSize + 8 : 0
        let trailingSize = preferredTrailingSize(height: rowHeight)
        if let trailing {
            trailing.frame = CGRect(
                x: max(bounds.width - trailingSize.width, labelX, 0),
                y: (rowHeight - trailingSize.height) / 2,
                width: trailingSize.width,
                height: trailingSize.height
            ).integral
        }

        let trailingReserved = trailingSize.width > 0 ? trailingSize.width + 8 : 0
        let available = max(bounds.width - labelX - trailingReserved, 1)
        let nameIntrinsic = childWidth(of: name, for: rowHeight)
        let handleIntrinsic = childWidth(of: handle, for: rowHeight)
        let nameSpacing: CGFloat = 4
        let nameWidth: CGFloat
        let handleWidth: CGFloat
        if nameIntrinsic + nameSpacing + handleIntrinsic <= available {
            nameWidth = nameIntrinsic
            handleWidth = handleIntrinsic
        } else if nameIntrinsic < available {
            nameWidth = nameIntrinsic
            handleWidth = max(available - nameWidth - nameSpacing, 0)
        } else {
            nameWidth = available
            handleWidth = 0
        }

        let nameHeight = childHeight(of: name, for: max(nameWidth, 1))
        name.frame = CGRect(x: labelX, y: (rowHeight - nameHeight) / 2, width: max(nameWidth, 1), height: nameHeight).integral
        let handleHeight = childHeight(of: handle, for: max(handleWidth, 1))
        handle.frame = CGRect(
            x: name.frame.maxX + (handleWidth > 0 ? nameSpacing : 0),
            y: (rowHeight - handleHeight) / 2,
            width: handleWidth,
            height: handleHeight
        ).integral
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        preferredSingleLineHeight(width: width)
    }

    @objc private func handleClick() {
        guard let profile else { return }
        profile.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }

    private func preferredSingleLineHeight(width: CGFloat) -> CGFloat {
        let fittingWidth = width > 0 ? width : 1
        let trailingHeight = trailing.map { $0.isHidden ? 0 : childHeight(of: $0, for: fittingWidth) } ?? 0
        let avatarHeight: CGFloat = showAvatar ? 20 : 0
        return ceil(max(avatarHeight, name.font?.flareLineHeight ?? 0, handle.font?.flareLineHeight ?? 0, trailingHeight))
    }

    private func preferredTrailingSize(height: CGFloat) -> CGSize {
        guard let trailing else { return .zero }
        if let topEnd = trailing as? StatusTopEndAppKitView {
            return topEnd.preferredSize()
        }
        let trailingHeight = max(childHeight(of: trailing, for: CGFloat.greatestFiniteMagnitude), 1)
        let trailingWidth = childWidth(of: trailing, for: max(height, trailingHeight))
        return CGSize(width: trailingWidth, height: trailingHeight)
    }
}

final class UserCompatAppKitView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let avatar = AvatarUIView()
    private let name = TimelineTextField(font: .boldSystemFont(ofSize: 14), color: .labelColor, maximumLines: 1)
    private let handle = TimelineTextField(font: .systemFont(ofSize: 12), color: .secondaryLabelColor, maximumLines: 1)
    private var trailing: NSView?
    private var profile: UiProfile?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        addSubview(avatar)
        addSubview(name)
        addSubview(handle)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        profile: UiProfile,
        appearance: StatusAppKitAppearance,
        trailing: NSView? = nil,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.profile = profile
        self.onOpenURL = onOpenURL
        if self.trailing !== trailing {
            self.trailing?.removeFromSuperview()
            self.trailing = trailing
            if let trailing {
                addSubview(trailing)
            }
        }
        avatar.configure(profile: profile, appearance: appearance)
        name.font = appearance.bodyBoldFont
        handle.font = appearance.captionFont
        name.stringValue = profile.name.innerText.isEmpty ? profile.name.raw : profile.name.innerText
        handle.stringValue = "@\(profile.handleWithoutAt)"
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let avatarSize: CGFloat = 44
        avatar.frame = CGRect(x: 0, y: 0, width: avatarSize, height: avatarSize)
        let trailingSize = preferredTrailingSize()
        if let trailing {
            trailing.frame = CGRect(
                x: max(bounds.width - trailingSize.width, avatarSize + 8, 0),
                y: 0,
                width: trailingSize.width,
                height: trailingSize.height
            ).integral
        }

        let columnX = avatarSize + 8
        let trailingReserved = trailingSize.width > 0 ? trailingSize.width + 8 : 0
        let columnWidth = max(bounds.width - columnX - trailingReserved, 1)
        let nameHeight = childHeight(of: name, for: columnWidth)
        let handleHeight = childHeight(of: handle, for: columnWidth)
        name.frame = CGRect(x: columnX, y: 0, width: columnWidth, height: nameHeight).integral
        handle.frame = CGRect(x: columnX, y: name.frame.maxY + 2, width: columnWidth, height: handleHeight).integral
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        let columnHeight = (name.font?.flareLineHeight ?? 0) + 2 + (handle.font?.flareLineHeight ?? 0)
        return ceil(max(44, columnHeight, preferredTrailingSize().height))
    }

    @objc private func handleClick() {
        guard let profile else { return }
        profile.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }

    private func preferredTrailingSize() -> CGSize {
        guard let trailing else { return .zero }
        if let topEnd = trailing as? StatusTopEndAppKitView {
            return topEnd.preferredSize()
        }
        return trailing.intrinsicContentSize
    }
}

private final class StatusTopEndAppKitView: FlippedView, TimelineHeightProviding {
    private let visibility = NSImageView()
    private let translation = TranslateStatusStateAppKitView()
    private let platformLogo = NSImageView()
    private let time = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let insightButton = NSButton(title: "", target: nil, action: nil)
    private var onOpenURL: ((URL) -> Void)?
    private var post: UiTimelineV2.Post?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        [visibility, platformLogo].forEach { imageView in
            imageView.imageScaling = .scaleProportionallyUpOrDown
            imageView.contentTintColor = .secondaryLabelColor
            addSubview(imageView)
        }
        addSubview(translation)
        insightButton.image = NSImage.fontAwesome(.robot)
        insightButton.imagePosition = .imageOnly
        insightButton.isBordered = false
        insightButton.bezelStyle = .inline
        insightButton.contentTintColor = .secondaryLabelColor
        insightButton.target = self
        insightButton.action = #selector(openInsight)
        addSubview(time)
        addSubview(insightButton)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        post: UiTimelineV2.Post,
        appearance: StatusAppKitAppearance,
        isDetail: Bool,
        showAgentInsight: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.post = post
        self.onOpenURL = onOpenURL
        visibility.image = post.visibility.flatMap { visibilityIcon($0) }
        visibility.isHidden = post.visibility == nil
        translation.set(state: post.translationDisplayState)
        translation.isHidden = post.translationDisplayState == .hidden
        platformLogo.image = appearance.showPlatformLogo ? platformIcon(post.platformType) : nil
        platformLogo.isHidden = platformLogo.image == nil
        time.font = appearance.captionFont
        time.stringValue = formattedDateTime(post.createdAt, absoluteTimestamp: appearance.absoluteTimestamp)
        time.isHidden = isDetail
        insightButton.isHidden = !showAgentInsight
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        preferredSize().height
    }

    func preferredSize() -> CGSize {
        let items = visibleItems()
        let width = items.reduce(CGFloat(0)) { $0 + $1.size.width } + CGFloat(max(items.count - 1, 0)) * 8
        let height = items.reduce(CGFloat(0)) { max($0, $1.size.height) }
        return CGSize(width: ceil(width), height: ceil(height))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let items = visibleItems()
        let height = items.reduce(CGFloat(0)) { max($0, $1.size.height) }
        var x: CGFloat = 0
        for (index, item) in items.enumerated() {
            if assignFrames {
                item.view.frame = CGRect(
                    x: x,
                    y: (height - item.size.height) / 2,
                    width: item.size.width,
                    height: item.size.height
                )
            }
            x += item.size.width
            if index < items.count - 1 {
                x += 8
            }
        }
        return ceil(height)
    }

    private func visibleItems() -> [(view: NSView, size: CGSize)] {
        var items: [(NSView, CGSize)] = []
        if !visibility.isHidden {
            items.append((visibility, CGSize(width: 14, height: 14)))
        }
        if !translation.isHidden {
            items.append((translation, translation.preferredSize()))
        }
        if !platformLogo.isHidden {
            items.append((platformLogo, CGSize(width: 14, height: 14)))
        }
        if !time.isHidden {
            let width = childWidth(of: time, for: 18)
            items.append((time, CGSize(width: ceil(width), height: ceil(childHeight(of: time, for: width)))))
        }
        if !insightButton.isHidden {
            items.append((insightButton, CGSize(width: 16, height: 16)))
        }
        return items
    }

    @objc private func openInsight() {
        guard let post else { return }
        let route = DeeplinkRoute.StatusInsight(accountType: post.accountType, statusKey: post.statusKey)
        guard let url = URL(string: route.toUri()) else { return }
        onOpenURL?(url)
    }

    private func visibilityIcon(_ visibility: UiTimelineV2.PostVisibility) -> NSImage? {
        switch visibility {
        case .public:
            NSImage.fontAwesome(.globe)
        case .home:
            NSImage.fontAwesome(.lockOpen)
        case .followers:
            NSImage.fontAwesome(.lock)
        case .specified:
            NSImage.fontAwesome(.at)
        case .channel:
            NSImage.fontAwesome(.tv)
        default:
            nil
        }
    }

    private func platformIcon(_ platformType: PlatformType) -> NSImage? {
        switch platformType {
        case .mastodon:
            NSImage.fontAwesome(.mastodon)
        case .misskey:
            NSImage.fontAwesome(.misskey)
        case .bluesky:
            NSImage.fontAwesome(.bluesky)
        case .xQt:
            NSImage.fontAwesome(.xTwitter)
        case .vvo:
            NSImage.fontAwesome(.weibo)
        case .nostr:
            NSImage.fontAwesome(.nostr)
        default:
            nil
        }
    }
}

private final class TranslateStatusStateAppKitView: FlippedView, TimelineHeightProviding {
    private let langIcon = NSImageView()
    private let stateIcon = NSImageView()
    private let spinner = NSProgressIndicator()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        langIcon.image = NSImage.fontAwesome(.language)
        langIcon.contentTintColor = .secondaryLabelColor
        langIcon.imageScaling = .scaleProportionallyUpOrDown
        stateIcon.contentTintColor = .secondaryLabelColor
        stateIcon.imageScaling = .scaleProportionallyUpOrDown
        spinner.style = .spinning
        spinner.controlSize = .small
        spinner.isDisplayedWhenStopped = false
        [langIcon, stateIcon, spinner].forEach(addSubview)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func set(state: TranslationDisplayState) {
        switch state {
        case .failed:
            stateIcon.image = NSImage.fontAwesome(.circleExclamation)
            stateIcon.isHidden = false
            spinner.stopAnimation(nil)
            spinner.isHidden = true
        case .translating:
            stateIcon.isHidden = true
            spinner.isHidden = false
            spinner.startAnimation(nil)
        default:
            stateIcon.isHidden = true
            spinner.stopAnimation(nil)
            spinner.isHidden = true
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let size = preferredSize()
        let y = max((bounds.height - size.height) / 2, 0)
        langIcon.frame = CGRect(x: 0, y: y, width: 16, height: 16)
        let stateFrame = CGRect(x: 20, y: y + 2, width: 12, height: 12)
        stateIcon.frame = stateIcon.isHidden ? .zero : stateFrame
        spinner.frame = spinner.isHidden ? .zero : stateFrame
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        16
    }

    func preferredSize() -> CGSize {
        let hasState = !stateIcon.isHidden || !spinner.isHidden
        return CGSize(width: hasState ? 32 : 16, height: 16)
    }
}

final class StatusTopMessageUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let iconView = NSImageView()
    private let nameView = RichTextUIView()
    private let textLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private var message: UiTimelineV2.Message?
    private var topMessageOnly = false
    private var currentAppearance = StatusAppKitAppearance(timeline: TimelineAppearance.companion.Default)
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        iconView.imageScaling = .scaleProportionallyUpOrDown
        addSubview(iconView)
        addSubview(nameView)
        addSubview(textLabel)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        message: UiTimelineV2.Message,
        appearance: StatusAppKitAppearance,
        topMessageOnly: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.message = message
        self.topMessageOnly = topMessageOnly
        self.currentAppearance = appearance
        self.onOpenURL = onOpenURL
        iconView.image = NSImage.fontAwesome(message.icon.fontAwesomeIcon)
        iconView.contentTintColor = topMessageOnly ? .labelColor : .secondaryLabelColor

        if let user = message.user {
            nameView.isHidden = false
            nameView.configure(
                user.name,
                font: topMessageOnly ? appearance.bodyFont : appearance.captionFont,
                color: topMessageOnly ? .labelColor : .secondaryLabelColor,
                maximumLines: topMessageOnly ? 0 : 1
            )
        } else {
            nameView.isHidden = true
        }

        if let text = messageTypeText(message) {
            textLabel.isHidden = false
            textLabel.font = topMessageOnly ? appearance.bodyFont : appearance.captionFont
            textLabel.textColor = topMessageOnly ? .labelColor : .secondaryLabelColor
            textLabel.maximumLines = topMessageOnly ? 0 : 1
            textLabel.stringValue = text
        } else {
            textLabel.isHidden = true
            textLabel.stringValue = ""
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width.isFinite else { return nil }
        return ceil(layout(width: max(width, 1), assignFrames: false))
    }

    @objc private func handleClick() {
        guard let message else { return }
        message.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let iconSize = preferredIconSize()
        let spacing: CGFloat = 8
        let contentWidth = max(width, 1)
        var x: CGFloat = 0

        let visibleName = !nameView.isHidden
        let visibleText = !textLabel.isHidden
        let nameWidth = visibleName ? preferredNameWidth(maxWidth: max(contentWidth - iconSize - spacing, 1)) : 0
        let nameHeight = visibleName ? childHeight(of: nameView, for: max(nameWidth, 1)) : 0
        let nameBaselineOffset = visibleName ? preferredTextFont().ascender : 0

        let textWidth: CGFloat
        let textHeight: CGFloat
        let textBaselineOffset: CGFloat
        if visibleText {
            let leadingWidth = iconSize + (visibleName ? spacing + nameWidth : 0)
            let interItemSpacing = leadingWidth > 0 ? spacing : 0
            textWidth = max(contentWidth - leadingWidth - interItemSpacing, 1)
            textHeight = childHeight(of: textLabel, for: textWidth)
            textBaselineOffset = textLabel.font?.ascender ?? preferredTextFont().ascender
        } else {
            textWidth = 0
            textHeight = 0
            textBaselineOffset = 0
        }

        let baselineTop = max(nameBaselineOffset, textBaselineOffset)
        let baselineBottom = max(nameHeight - nameBaselineOffset, textHeight - textBaselineOffset)
        let textContentHeight = ceil(baselineTop + baselineBottom)
        let contentHeight = max(ceil(iconSize), textContentHeight)
        let baselineY = ((contentHeight - textContentHeight) / 2) + baselineTop
        let referenceFont = textLabel.font ?? preferredTextFont()
        let firstLineCenterY = (baselineY - referenceFont.ascender) + (referenceFont.flareLineHeight / 2)

        if assignFrames {
            iconView.frame = CGRect(x: x, y: firstLineCenterY - (iconSize / 2), width: iconSize, height: iconSize).integral
        }
        x += iconSize

        if visibleName {
            x += spacing
            if assignFrames {
                nameView.frame = CGRect(
                    x: x,
                    y: baselineY - nameBaselineOffset,
                    width: nameWidth,
                    height: nameHeight
                ).integral
            }
            x += nameWidth
        } else if assignFrames {
            nameView.frame = .zero
        }

        if visibleText {
            if x > 0 { x += spacing }
            if assignFrames {
                textLabel.frame = CGRect(
                    x: x,
                    y: baselineY - textBaselineOffset,
                    width: textWidth,
                    height: textHeight
                ).integral
            }
        } else if assignFrames {
            textLabel.frame = .zero
        }

        return contentHeight
    }

    private func preferredNameWidth(maxWidth: CGFloat) -> CGFloat {
        guard !nameView.isHidden else { return 0 }
        let width = childWidth(of: nameView, for: preferredTextFont().flareLineHeight)
        guard width > 0 else { return min(maxWidth, 1) }
        return min(width, max(maxWidth, 1))
    }

    private func preferredIconSize() -> CGFloat {
        15
    }

    private func preferredTextFont() -> NSFont {
        topMessageOnly ? currentAppearance.bodyFont : currentAppearance.captionFont
    }

    private func messageTypeText(_ message: UiTimelineV2.Message) -> String? {
        if let raw = message.type as? UiTimelineV2.MessageTypeRaw {
            raw.content
        } else if let localized = message.type as? UiTimelineV2.MessageTypeLocalized {
            localizedMessageText(localized)
        } else if let unknown = message.type as? UiTimelineV2.MessageTypeUnknown {
            unknown.rawType.isEmpty ? nil : unknown.rawType
        } else {
            nil
        }
    }

    private func localizedMessageText(_ localized: UiTimelineV2.MessageTypeLocalized) -> String {
        switch localized.data {
        case .mention:
            return LocalizedStrings.string("mastodon_notification_mention", fallback: "mentioned you")
        case .newPost:
            return LocalizedStrings.string("mastodon_notification_status", fallback: "posted")
        case .repost:
            return LocalizedStrings.string("mastodon_notification_reblog", fallback: "reposted")
        case .follow:
            return LocalizedStrings.string("mastodon_notification_follow", fallback: "followed you")
        case .followRequest:
            return LocalizedStrings.string("mastodon_notification_follow_request", fallback: "requested to follow you")
        case .favourite:
            return LocalizedStrings.string("mastodon_notification_favourite", fallback: "liked")
        case .pollEnded:
            return LocalizedStrings.string("mastodon_notification_poll", fallback: "poll ended")
        case .postUpdated:
            return LocalizedStrings.string("mastodon_notification_update", fallback: "updated a post")
        case .reply:
            return LocalizedStrings.string("misskey_notification_reply", fallback: "replied")
        case .quote:
            return LocalizedStrings.string("misskey_notification_quote", fallback: "quoted")
        case .reaction:
            return LocalizedStrings.string("misskey_notification_reaction", fallback: "reacted")
        case .followRequestAccepted:
            return LocalizedStrings.string("misskey_notification_follow_request_accepted", fallback: "accepted your follow request")
        case .scheduledNotePosted:
            return LocalizedStrings.string("misskey_notification_scheduled_note_posted", fallback: "scheduled note posted")
        case .scheduledNotePostFailed:
            return LocalizedStrings.string("misskey_notification_scheduled_note_post_failed", fallback: "scheduled note failed")
        case .roleAssigned:
            return LocalizedStrings.string("misskey_notification_role_assigned", fallback: "assigned a role")
        case .chatRoomInvitationReceived:
            return LocalizedStrings.string("misskey_notification_chat_room_invitation_received", fallback: "invited you to chat")
        case .achievementEarned:
            return LocalizedStrings.string("misskey_notification_achievement_earned", fallback: "earned an achievement")
        case .app:
            return LocalizedStrings.string("misskey_notification_app", fallback: "app notification")
        case .exportCompleted:
            return LocalizedStrings.string("misskey_notification_export_completed", fallback: "export completed")
        case .test:
            return LocalizedStrings.string("misskey_notification_test", fallback: "test notification")
        case .login:
            return LocalizedStrings.string("misskey_notification_login", fallback: "logged in")
        case .createToken:
            return LocalizedStrings.string("misskey_notification_create_token", fallback: "created a token")
        case .starterpackJoined:
            return LocalizedStrings.string("bluesky_notification_starterpackJoined", fallback: "joined a starter pack")
        case .pinned:
            return LocalizedStrings.string("mastodon_item_pinned", fallback: "pinned")
        case .like:
            return LocalizedStrings.string("bluesky_notification_like", fallback: "liked")
        }
    }
}

final class FeedUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let sourceIcon = RemoteImageView()
    private let source = TimelineTextField(font: .preferredFont(forTextStyle: .footnote), color: .secondaryLabelColor, maximumLines: 1)
    private let date = TimelineTextField(font: .preferredFont(forTextStyle: .footnote), color: .secondaryLabelColor, maximumLines: 1)
    private let title = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .labelColor, maximumLines: 2)
    private let body = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 5)
    private let media = RemoteImageView()
    private var feed: UiTimelineV2.Feed?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        [sourceIcon, source, date, title, body, media].forEach(addSubview)
        media.wantsLayer = true
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiTimelineV2.Feed, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        feed = data
        self.onOpenURL = onOpenURL
        sourceIcon.configure(url: data.source.icon, cornerRadius: 4)
        source.stringValue = data.source.name
        date.stringValue = appearance.absoluteTimestamp ? data.createdAt.absolute : data.createdAt.relative
        title.stringValue = data.title ?? ""
        body.stringValue = data.description_ ?? data.description
        media.configure(url: data.media?.previewUrl ?? data.media?.url, cornerRadius: 8)
        media.isHidden = data.media == nil
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        sourceIcon.frame = CGRect(x: 0, y: 0, width: 20, height: 20)
        let dateWidth = min(childWidth(of: date, for: 18), width * 0.34)
        date.frame = CGRect(x: width - dateWidth, y: 1, width: dateWidth, height: 18)
        source.frame = CGRect(x: 28, y: 1, width: max(width - 36 - dateWidth, 0), height: 18)

        var y: CGFloat = 28
        if !title.stringValue.isEmpty {
            let height = childHeight(of: title, for: width)
            title.frame = CGRect(x: 0, y: y, width: width, height: height)
            y += height + 8
        } else {
            title.frame = .zero
        }

        let hasMedia = !media.isHidden
        let mediaWidth: CGFloat = hasMedia ? 72 : 0
        let textWidth = max(width - mediaWidth - (hasMedia ? 12 : 0), 0)
        let bodyHeight = childHeight(of: body, for: textWidth)
        body.frame = CGRect(x: 0, y: y, width: textWidth, height: bodyHeight)
        if hasMedia {
            media.frame = CGRect(x: width - mediaWidth, y: y, width: mediaWidth, height: 72)
        } else {
            media.frame = .zero
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        var height: CGFloat = 20 + 8
        if !title.stringValue.isEmpty {
            height += childHeight(of: title, for: width) + 8
        }
        let bodyWidth = media.isHidden ? width : max(width - 84, 0)
        height += max(childHeight(of: body, for: bodyWidth), media.isHidden ? 0 : 72)
        return ceil(height)
    }

    @objc private func handleClick() {
        guard let feed else { return }
        feed.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }
}

final class StatusUIKitView: FlippedView, NSGestureRecognizerDelegate, ManualLayoutMeasurable, TimelineHeightProviding {
    var onLocalHeightInvalidated: (() -> Void)?

    private let rootClickGesture = NSClickGestureRecognizer()
    private let avatar = AvatarUIView()
    private let headerFull = UserOnelineUIView(showAvatar: false)
    private let headerQuote = UserOnelineUIView(showAvatar: true)
    private let headerCompat = UserCompatAppKitView()
    private let topEnd = StatusTopEndAppKitView()
    private let replyRow = IconTextRowView(icon: .reply)
    private let warning = RichTextUIView()
    private let warningButton = NSButton(title: "", target: nil, action: nil)
    private let content = RichTextUIView()
    private let expandTextButton = NSButton(title: "", target: nil, action: nil)
    private let translate = StatusTranslateUIView()
    private let poll = StatusPollUIView()
    private let media = StatusMediaContentUIView()
    private let card = StatusCardUIView()
    private let quoteContainer = QuotesContainerView()
    private let sourceChannel = IconTextRowView(icon: .tv)
    private let reactions = StatusReactionUIView()
    private let timestamp = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let actions = StatusActionsUIView()
    private let actionsContainer = ActionsContainerAppKitView()

    private var activeParents: [ParentContainerView] = []
    private var contentChildren: [NSView] = []
    private var post: UiTimelineV2.Post?
    private var statusAppearance = StatusAppKitAppearance(timeline: TimelineAppearance.companion.Default)
    private var detailStatusKey: MicroBlogKey?
    private var showTranslate = true
    private var aiTldrEnabled = false
    private var forceHideActions = false
    private var isQuote = false
    private var withLeadingPadding = false
    private var expandedContentWarning = false
    private var expandedText = false
    private var boundStatusKey: String?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        warningButton.bezelStyle = .rounded
        warningButton.target = self
        warningButton.action = #selector(toggleWarning)
        expandTextButton.bezelStyle = .rounded
        expandTextButton.target = self
        expandTextButton.action = #selector(toggleExpandText)
        expandTextButton.title = LocalizedStrings.string("mastodon_item_show_more", fallback: "Show more")
        rootClickGesture.target = self
        rootClickGesture.action = #selector(handleRootClick)
        rootClickGesture.delegate = self
        addGestureRecognizer(rootClickGesture)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        data: UiTimelineV2.Post,
        appearance: StatusAppKitAppearance,
        detailStatusKey: MicroBlogKey?,
        showTranslate: Bool,
        aiTldrEnabled: Bool,
        forceHideActions: Bool = false,
        isQuote: Bool = false,
        withLeadingPadding: Bool = false,
        onOpenURL: ((URL) -> Void)?
    ) {
        let newStatusKey = String(describing: data.statusKey)
        if boundStatusKey != newStatusKey {
            expandedContentWarning = false
            expandedText = false
            boundStatusKey = newStatusKey
        }
        post = data
        self.statusAppearance = appearance
        self.detailStatusKey = detailStatusKey
        self.showTranslate = showTranslate
        self.aiTldrEnabled = aiTldrEnabled
        self.forceHideActions = forceHideActions
        self.isQuote = isQuote
        self.withLeadingPadding = withLeadingPadding
        self.onOpenURL = onOpenURL
        rebuild()
    }

    override func layout() {
        super.layout()
        let width = bounds.width
        var y: CGFloat = 0
        for parent in activeParents {
            let height = childHeight(of: parent, for: width)
            parent.frame = CGRect(x: 0, y: y, width: width, height: height)
            y += height
        }

        let showAvatar = shouldShowAvatar
        let avatarSize: CGFloat = showAvatar ? 44 : 0
        if showAvatar {
            avatar.frame = CGRect(x: 0, y: y, width: avatarSize, height: avatarSize)
        }
        let contentX = showAvatar ? avatarSize + 8 : 0
        let contentWidth = max(width - contentX, 0)
        let contentHeight = layoutContentColumn(x: contentX, y: y, width: contentWidth, assignFrames: true)
        if showAvatar {
            y += max(avatarSize, contentHeight)
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        var height: CGFloat = 0
        for parent in activeParents {
            height += childHeight(of: parent, for: width)
        }
        let contentWidth = shouldShowAvatar ? max(width - 52, 0) : width
        let rowHeight = max(
            shouldShowAvatar ? 44 : 0,
            layoutContentColumn(x: 0, y: 0, width: contentWidth, assignFrames: false)
        )
        height += rowHeight
        return ceil(height)
    }

    private var shouldShowAvatar: Bool {
        guard let post else { return false }
        let isDetail = detailStatusKey == post.statusKey
        return (!statusAppearance.fullWidthPost || withLeadingPadding) && !isQuote && !isDetail && post.user != nil
    }

    private var showAsFullWidth: Bool {
        guard let post else { return false }
        let isDetail = detailStatusKey == post.statusKey
        return (!statusAppearance.fullWidthPost || withLeadingPadding) && !isQuote && !isDetail && post.user != nil
    }

    private func rebuild() {
        guard let post else { return }

        let parents = Array(post.parents).map { parent -> ParentContainerView in
            let view = ParentContainerView()
            view.configure(data: parent, appearance: statusAppearance, onOpenURL: onOpenURL)
            view.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            return view
        }
        activeParents.forEach { $0.removeFromSuperview() }
        activeParents = parents
        activeParents.forEach(addSubview)

        if let user = post.user {
            let isDetail = detailStatusKey == post.statusKey
            topEnd.configure(
                post: post,
                appearance: statusAppearance,
                isDetail: isDetail,
                showAgentInsight: statusAppearance.aiAgentEnabled && !isQuote,
                onOpenURL: onOpenURL
            )
            avatar.configure(profile: user, appearance: statusAppearance)
        }

        var desired: [NSView] = []
        if let user = post.user {
            if showAsFullWidth {
                headerFull.configure(profile: user, appearance: statusAppearance, showAvatar: false, trailing: topEnd, onOpenURL: onOpenURL)
                desired.append(headerFull)
            } else if isQuote {
                headerQuote.configure(profile: user, appearance: statusAppearance, showAvatar: true, trailing: topEnd, onOpenURL: onOpenURL)
                desired.append(headerQuote)
            } else {
                headerCompat.configure(profile: user, appearance: statusAppearance, trailing: topEnd, onOpenURL: onOpenURL)
                desired.append(headerCompat)
            }
        }
        if let replyToHandle = post.replyToHandle {
            replyRow.configure(text: "Reply to \(replyToHandle)", appearance: statusAppearance)
            desired.append(replyRow)
        }
        if let warningText = post.contentWarning, !warningText.isEmpty {
            warning.configure(warningText, font: statusAppearance.bodyFont, color: .labelColor)
            desired.append(warning)
            if !statusAppearance.expandContentWarning {
                warningButton.title = expandedContentWarning
                    ? LocalizedStrings.string("mastodon_item_show_less", fallback: "Show less")
                    : LocalizedStrings.string("mastodon_item_show_more", fallback: "Show more")
                desired.append(warningButton)
            }
        }

        let canShowContent = expandedContentWarning || statusAppearance.expandContentWarning || post.contentWarning == nil || post.contentWarning?.isEmpty == true
        if canShowContent, !post.content.isEmpty {
            let isDetail = detailStatusKey == post.statusKey
            content.configure(
                post.content,
                font: statusAppearance.bodyFont,
                color: .labelColor,
                maximumLines: isDetail || post.shouldExpandTextByDefault || expandedText ? 0 : 5
            )
            desired.append(content)
            if !post.shouldExpandTextByDefault, !isDetail, !expandedText {
                desired.append(expandTextButton)
            }
        }

        if detailStatusKey == post.statusKey, showTranslate {
            translate.content = post.content
            translate.contentWarning = post.contentWarning
            translate.isSummaryAvailable = aiTldrEnabled
            translate.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            desired.append(translate)
        }

        if let pollData = post.poll {
            poll.configure(data: pollData, appearance: statusAppearance)
            poll.onVote = { [weak self] indices in
                guard let self else { return }
                pollData.onVote(
                    ClickContext(launcher: makeLauncher(self.onOpenURL)),
                    indices.map { KotlinInt(value: Int32($0)) }
                )
            }
            desired.append(poll)
        }

        if !post.images.isEmpty {
            let statusKey = post.statusKey
            let accountType = post.accountType
            media.configure(
                data: Array(post.images),
                sensitive: post.sensitive,
                cornerRadius: 16,
                appearance: statusAppearance
            )
            media.onMediaClicked = { [weak self] media, index in
                let route = DeeplinkRoute.MediaStatusMedia(
                    statusKey: statusKey,
                    accountType: accountType,
                    index: Int32(index),
                    preview: media.previewURLForMediaRoute
                )
                guard let url = URL(string: route.toUri()) else { return }
                self?.onOpenURL?(url)
            }
            media.onLocalHeightInvalidated = { [weak self] in
                self?.notifyLocalHeightInvalidated()
            }
            desired.append(media)
        }

        if let cardData = post.card, post.images.isEmpty, post.quote.isEmpty, statusAppearance.showLinkPreview {
            card.configure(data: cardData, appearance: statusAppearance, cornerRadius: isQuote ? 12 : 16, onOpenURL: onOpenURL)
            desired.append(card)
        }

        if !post.quote.isEmpty, !isQuote {
            quoteContainer.configure(
                posts: Array(post.quote),
                appearance: statusAppearance,
                onOpenURL: onOpenURL,
                onLocalHeightInvalidated: { [weak self] in
                    self?.notifyLocalHeightInvalidated()
                }
            )
            desired.append(quoteContainer)
        }

        if let channel = post.sourceChannel, !isQuote {
            sourceChannel.configure(text: channel.name, appearance: statusAppearance)
            desired.append(sourceChannel)
        }

        if !post.emojiReactions.isEmpty, !isQuote {
            let isDetail = detailStatusKey == post.statusKey
            reactions.configure(data: Array(post.emojiReactions), appearance: statusAppearance, isDetail: isDetail)
            reactions.onReactionTapped = { [weak self] reaction in
                reaction.onClicked(ClickContext(launcher: makeLauncher(self?.onOpenURL)))
            }
            desired.append(reactions)
        }

        let isDetail = detailStatusKey == post.statusKey
        if isDetail {
            timestamp.stringValue = formattedDateTime(post.createdAt, absoluteTimestamp: statusAppearance.absoluteTimestamp)
            desired.append(timestamp)
        }

        let hideActions = (statusAppearance.postActionStyle == .hidden && !isDetail) || forceHideActions
        if !hideActions {
            actions.configure(data: Array(post.actions), appearance: statusAppearance, onOpenURL: onOpenURL)
            actionsContainer.content = actions
            desired.append(actionsContainer)
        }

        if shouldShowAvatar, avatar.superview !== self {
            addSubview(avatar)
        }
        syncManagedSubviews(parent: self, current: &contentChildren, desired: desired)
        needsLayout = true
    }

    @objc private func toggleWarning() {
        expandedContentWarning.toggle()
        rebuild()
        notifyLocalHeightInvalidated()
    }

    @objc private func toggleExpandText() {
        expandedText = true
        rebuild()
        notifyLocalHeightInvalidated()
    }

    @objc private func handleRootClick() {
        guard let post else { return }
        post.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }

    func gestureRecognizer(_ gestureRecognizer: NSGestureRecognizer, shouldAttemptToRecognizeWith event: NSEvent) -> Bool {
        guard gestureRecognizer === rootClickGesture else { return true }
        let point = convert(event.locationInWindow, from: nil)
        guard let touchedView = hitTest(point) else { return true }
        if touchedView.firstSuperview(of: NSControl.self) != nil {
            return false
        }
        if touchedView.firstSuperview(of: MediaGridCellView.self) != nil {
            return false
        }
        if let nestedStatus = touchedView.firstSuperview(of: StatusUIKitView.self),
           nestedStatus !== self {
            return false
        }
        return true
    }

    private func notifyLocalHeightInvalidated() {
        needsLayout = true
        superview?.needsLayout = true
        onLocalHeightInvalidated?()
    }

    @discardableResult
    private func layoutContentColumn(x: CGFloat, y: CGFloat, width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard !contentChildren.isEmpty else { return 0 }
        var currentY = y
        let remaining: ArraySlice<NSView>
        if let header = contentChildren.first, isHeaderView(header) {
            let height = childHeight(of: header, for: width)
            if assignFrames {
                header.frame = CGRect(x: x, y: currentY, width: width, height: height)
            }
            currentY += height + 8
            remaining = contentChildren.dropFirst()
        } else {
            remaining = contentChildren[...]
        }

        for (offset, view) in remaining.enumerated() {
            let height = childHeight(of: view, for: width)
            if assignFrames {
                view.frame = CGRect(x: x, y: currentY, width: width, height: height)
            }
            currentY += height
            if offset < remaining.count - 1 {
                currentY += 8
            }
        }
        return currentY - y
    }

    private func isHeaderView(_ view: NSView) -> Bool {
        view === headerFull || view === headerQuote || view === headerCompat
    }
}

final class TimelineUserUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let user = UserCompatAppKitView()
    private let actions = StatusActionsUIView()
    private var managedChildren: [NSView] = []

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiTimelineV2.User, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        user.configure(profile: data.value, appearance: appearance, onOpenURL: onOpenURL)
        var desired: [NSView] = [user]
        if !data.button.isEmpty {
            actions.configureItems(data: Array(data.button), appearance: appearance, onOpenURL: onOpenURL, useText: true)
            desired.append(actions)
        }
        syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
        needsLayout = true
    }

    override func layout() {
        super.layout()
        manualLayoutVertical(views: managedChildren, x: 0, y: 0, width: bounds.width, spacing: 8)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        manualHeightForVertical(views: managedChildren, width: width, spacing: 8)
    }
}

final class UserListUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onLocalHeightInvalidated: (() -> Void)?

    private var chips: [UserChipView] = []
    private let status = StatusUIKitView()
    private var managedChildren: [NSView] = []

    func configure(data: UiTimelineV2.UserList, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        chips.forEach { $0.removeFromSuperview() }
        chips = Array(data.users.prefix(6)).map { profile in
            let chip = UserChipView()
            chip.configure(profile: profile, appearance: appearance, onOpenURL: onOpenURL)
            return chip
        }
        let chipRow = HorizontalChipRow(views: chips)
        var desired: [NSView] = [chipRow]
        if let post = data.post {
            status.configure(
                data: post,
                appearance: appearance,
                detailStatusKey: nil,
                showTranslate: false,
                aiTldrEnabled: false,
                onOpenURL: onOpenURL
            )
            status.onLocalHeightInvalidated = { [weak self] in
                self?.needsLayout = true
                self?.onLocalHeightInvalidated?()
            }
            let padded = BorderedPaddingView(content: status, insets: NSEdgeInsets(top: 8, left: 8, bottom: 8, right: 8), cornerRadius: 12)
            desired.append(padded)
        }
        managedChildren.forEach { $0.removeFromSuperview() }
        managedChildren = desired
        desired.forEach(addSubview)
        needsLayout = true
    }

    override func layout() {
        super.layout()
        manualLayoutVertical(views: managedChildren, x: 0, y: 0, width: bounds.width, spacing: 8)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        manualHeightForVertical(views: managedChildren, width: width, spacing: 8)
    }
}

private final class IconTextRowView: FlippedView, TimelineHeightProviding {
    private let icon = NSImageView()
    private let label = TimelineTextField(font: .preferredFont(forTextStyle: .footnote), color: .secondaryLabelColor, maximumLines: 0)
    private let iconSize: CGFloat = 12
    private let spacing: CGFloat = 4

    init(icon: FontAwesomeIcon) {
        super.init(frame: .zero)
        self.icon.image = NSImage.fontAwesome(icon)
        self.icon.contentTintColor = .secondaryLabelColor
        self.icon.imageScaling = .scaleProportionallyUpOrDown
        addSubview(self.icon)
        addSubview(label)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(text: String, appearance: StatusAppKitAppearance) {
        label.font = appearance.captionFont
        label.textColor = .secondaryLabelColor
        label.stringValue = text
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let labelX = iconSize + spacing
        let labelWidth = max(bounds.width - labelX, 0)
        let labelHeight = childHeight(of: label, for: labelWidth)
        icon.frame = CGRect(x: 0, y: (ceil(labelHeight) - iconSize) / 2, width: iconSize, height: iconSize)
        label.frame = CGRect(x: labelX, y: 0, width: labelWidth, height: labelHeight)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let labelWidth = max(width - iconSize - spacing, 0)
        return ceil(max(childHeight(of: label, for: labelWidth), iconSize))
    }
}

final class StatusTranslateUIView: FlippedView, TimelineHeightProviding {
    var content: UiRichText? {
        didSet { configureIfNeeded() }
    }
    var contentWarning: UiRichText? {
        didSet { configureIfNeeded() }
    }
    var isSummaryAvailable = false {
        didSet {
            guard isSummaryAvailable != oldValue else { return }
            if !isSummaryAvailable {
                isSummaryExpanded = false
                summaryPresenter = nil
            }
            updateButtonVisibility()
            setNeedsHeightUpdate()
        }
    }
    var onLocalHeightInvalidated: (() -> Void)?

    private let buttonRow = FlippedView()
    private let translateButton = NSButton(title: "", target: nil, action: nil)
    private let tldrButton = NSButton(title: "", target: nil, action: nil)
    private let contentTranslation = TranslationResultAppKitView()
    private let contentWarningTranslation = TranslationResultAppKitView()
    private let summaryResult = TextResultAppKitView()

    private var translationPresenter: KotlinPresenter<UiState<UiRichText>>?
    private var contentWarningPresenter: KotlinPresenter<UiState<UiRichText>>?
    private var summaryPresenter: KotlinPresenter<UiState<NSString>>?
    private var cancellables = Set<AnyCancellable>()
    private var isTranslateExpanded = false
    private var isSummaryExpanded = false
    private var lastContentSignature: String?
    private var lastContentWarningSignature: String?

    private static let verticalSpacing: CGFloat = 8
    private static let buttonSpacing: CGFloat = 12

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        configureButton(
            translateButton,
            title: LocalizedStrings.string("status_translate", fallback: "Translate"),
            action: #selector(toggleTranslate)
        )
        configureButton(
            tldrButton,
            title: LocalizedStrings.string("status_tldr", fallback: "TLDR"),
            action: #selector(toggleSummary)
        )
        addSubview(buttonRow)
        buttonRow.addSubview(translateButton)
        buttonRow.addSubview(tldrButton)
        addSubview(contentWarningTranslation)
        addSubview(contentTranslation)
        addSubview(summaryResult)
        contentWarningTranslation.isHidden = true
        contentTranslation.isHidden = true
        summaryResult.isHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    @MainActor
    deinit {
        cancellables.removeAll()
        translationPresenter = nil
        contentWarningPresenter = nil
        summaryPresenter = nil
    }

    override func layout() {
        super.layout()
        performLayout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard content != nil, width > 0, width.isFinite else { return nil }
        return ceil(performLayout(width: width, assignFrames: false))
    }

    private func configureButton(_ button: NSButton, title: String, action: Selector) {
        button.title = title
        button.isBordered = false
        button.bezelStyle = .inline
        button.alignment = .left
        button.font = .preferredFont(forTextStyle: .body)
        button.contentTintColor = .controlAccentColor
        button.target = self
        button.action = action
    }

    private func configureIfNeeded() {
        guard let content else {
            resetPresenters()
            lastContentSignature = nil
            lastContentWarningSignature = nil
            isTranslateExpanded = false
            isSummaryExpanded = false
            updateButtonVisibility()
            setNeedsHeightUpdate()
            return
        }

        let contentSignature = content.toTranslatableText()
        let contentWarningSignature = contentWarning?.toTranslatableText()
        guard contentSignature != lastContentSignature ||
              contentWarningSignature != lastContentWarningSignature else {
            updateButtonVisibility()
            return
        }

        lastContentSignature = contentSignature
        lastContentWarningSignature = contentWarningSignature
        resetPresenters()
        contentTranslation.reset()
        contentWarningTranslation.reset()
        summaryResult.reset()
        isTranslateExpanded = false
        isSummaryExpanded = false
        updateButtonVisibility()
        setNeedsHeightUpdate()
    }

    private func updateButtonVisibility() {
        let hasContent = content?.isEmpty == false
        buttonRow.isHidden = !hasContent
        translateButton.isHidden = !hasContent
        tldrButton.isHidden = !(hasContent && content?.isLongText == true && isSummaryAvailable)
        contentWarningTranslation.isHidden = !isTranslateExpanded || contentWarning == nil
        contentTranslation.isHidden = !isTranslateExpanded
        summaryResult.isHidden = !isSummaryExpanded || !isSummaryAvailable
    }

    @objc private func toggleTranslate() {
        isTranslateExpanded.toggle()
        updateButtonVisibility()
        if isTranslateExpanded {
            ensureTranslationPresenters()
        }
        setNeedsHeightUpdate()
    }

    @objc private func toggleSummary() {
        guard isSummaryAvailable else { return }
        isSummaryExpanded.toggle()
        updateButtonVisibility()
        if isSummaryExpanded {
            ensureSummaryPresenter()
        }
        setNeedsHeightUpdate()
    }

    private func ensureTranslationPresenters() {
        guard let content else { return }
        if translationPresenter == nil {
            let presenter = KotlinPresenter(
                presenter: TranslatePresenter(source: content, targetLanguage: currentTargetLanguage())
            )
            translationPresenter = presenter
            presenter.$state
                .receive(on: DispatchQueue.main)
                .sink { [weak self] state in
                    self?.contentTranslation.render(state: state)
                    self?.setNeedsHeightUpdate()
                }
                .store(in: &cancellables)
        }

        if let contentWarning, contentWarningPresenter == nil {
            let presenter = KotlinPresenter(
                presenter: TranslatePresenter(source: contentWarning, targetLanguage: currentTargetLanguage())
            )
            contentWarningPresenter = presenter
            presenter.$state
                .receive(on: DispatchQueue.main)
                .sink { [weak self] state in
                    self?.contentWarningTranslation.render(state: state)
                    self?.setNeedsHeightUpdate()
                }
                .store(in: &cancellables)
        }
    }

    private func ensureSummaryPresenter() {
        guard let content, summaryPresenter == nil else { return }
        let source: String
        if let contentWarning, !contentWarning.isEmpty {
            source = "Content Warning:\n\(contentWarning.toTranslatableText())\n\nContent:\n\(content.toTranslatableText())"
        } else {
            source = "Content:\n\(content.toTranslatableText())"
        }
        let presenter = KotlinPresenter(
            presenter: AiTLDRPresenter(source: source, targetLanguage: currentTargetLanguage())
        )
        summaryPresenter = presenter
        presenter.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.summaryResult.render(state: state)
                self?.setNeedsHeightUpdate()
            }
            .store(in: &cancellables)
    }

    private func resetPresenters() {
        cancellables.removeAll()
        translationPresenter = nil
        contentWarningPresenter = nil
        summaryPresenter = nil
    }

    private func currentTargetLanguage() -> String {
        Locale.current.language.languageCode?.identifier ?? "en"
    }

    private func setNeedsHeightUpdate() {
        invalidateIntrinsicContentSize()
        needsLayout = true
        superview?.needsLayout = true
        onLocalHeightInvalidated?()
    }

    @discardableResult
    private func performLayout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard width > 0, width.isFinite, content?.isEmpty == false else { return 0 }
        var y: CGFloat = 0
        let rowHeight = buttonRowHeight()
        if assignFrames {
            buttonRow.frame = CGRect(x: 0, y: y, width: width, height: rowHeight)
            layoutButtons(width: width, height: rowHeight)
        }
        y += rowHeight

        for view in [contentWarningTranslation, contentTranslation, summaryResult] where !view.isHidden {
            y += Self.verticalSpacing
            let height = childHeight(of: view, for: width)
            if assignFrames {
                view.frame = CGRect(x: 0, y: y, width: width, height: height)
            }
            y += height
        }
        return y
    }

    private func buttonRowHeight() -> CGFloat {
        let translateHeight = translateButton.intrinsicContentSize.height
        let summaryHeight = tldrButton.isHidden ? 0 : tldrButton.intrinsicContentSize.height
        return ceil(max(translateHeight, summaryHeight, 1))
    }

    private func layoutButtons(width: CGFloat, height: CGFloat) {
        let translateWidth = min(translateButton.intrinsicContentSize.width, width)
        translateButton.frame = CGRect(x: 0, y: 0, width: translateWidth, height: height)
        if tldrButton.isHidden {
            tldrButton.frame = .zero
            return
        }
        let summaryX = min(translateWidth + Self.buttonSpacing, width)
        tldrButton.frame = CGRect(x: summaryX, y: 0, width: max(width - summaryX, 0), height: height)
    }
}

private final class TranslationResultAppKitView: FlippedView, TimelineHeightProviding {
    private let richText = RichTextUIView()
    private let textResult = TextResultAppKitView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        addSubview(richText)
        addSubview(textResult)
        richText.isHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func reset() {
        richText.isHidden = true
        textResult.isHidden = false
        textResult.reset()
        needsLayout = true
    }

    func render(state: UiState<UiRichText>) {
        switch onEnum(of: state) {
        case .success(let success):
            richText.configure(success.data, font: .preferredFont(forTextStyle: .body), color: .labelColor)
            richText.isHidden = false
            textResult.isHidden = true
        case .error(let error):
            richText.isHidden = true
            textResult.isHidden = false
            textResult.showText(error.throwable.message ?? "Unknown Error")
        case .loading:
            richText.isHidden = true
            textResult.isHidden = false
            textResult.showLoading()
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let target = richText.isHidden ? textResult : richText
        target.frame = bounds
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return richText.isHidden ? textResult.timelineHeight(for: width) : richText.timelineHeight(for: width)
    }
}

private final class TextResultAppKitView: FlippedView, TimelineHeightProviding {
    private let label = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .labelColor)
    private let spinner = NSProgressIndicator()
    private var isLoading = false

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        spinner.style = .spinning
        spinner.controlSize = .regular
        spinner.isDisplayedWhenStopped = false
        addSubview(label)
        addSubview(spinner)
        reset()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func reset() {
        showLoading()
    }

    func showLoading() {
        isLoading = true
        label.stringValue = ""
        spinner.startAnimation(nil)
        needsLayout = true
    }

    func showText(_ text: String) {
        isLoading = false
        spinner.stopAnimation(nil)
        label.stringValue = text
        needsLayout = true
    }

    func render(state: UiState<NSString>) {
        switch onEnum(of: state) {
        case .success(let success):
            showText(String(success.data))
        case .error(let error):
            showText(error.throwable.message ?? "Unknown Error")
        case .loading:
            showLoading()
        }
    }

    override func layout() {
        super.layout()
        if isLoading {
            spinner.frame = CGRect(x: 0, y: 0, width: 28, height: 28)
            label.frame = .zero
        } else {
            spinner.frame = .zero
            label.frame = bounds
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        if isLoading {
            return 28
        }
        return childHeight(of: label, for: width)
    }
}

final class StatusPollUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onVote: ((_ indices: [Int]) -> Void)?

    private let expiredLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let expiresAtLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let expiresAtTime = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let voteButton = NSButton(title: "", target: nil, action: nil)

    private var data: UiPoll?
    private var selectedOption: [Int] = []
    private var buttonPool: [PollOptionButton] = []
    private var resultPool: [PollOptionResultView] = []
    private var optionViews: [NSView] = []
    private var footerViews: [NSView] = []

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        expiredLabel.stringValue = LocalizedStrings.string("poll_expired", fallback: "Poll expired")
        expiresAtLabel.stringValue = LocalizedStrings.string("poll_expires_at", fallback: "Poll expires at")
        voteButton.title = LocalizedStrings.string("poll_vote", fallback: "Vote")
        voteButton.target = self
        voteButton.action = #selector(submitVote)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiPoll, appearance: StatusAppKitAppearance) {
        self.data = data
        selectedOption = []
        expiredLabel.font = appearance.captionFont
        expiresAtLabel.font = appearance.captionFont
        expiresAtTime.font = appearance.captionFont

        var optionDesired: [NSView] = []
        for index in 0..<Int(data.options.count) {
            let option = data.options[index]
            if data.canVote {
                while buttonPool.count <= index {
                    buttonPool.append(PollOptionButton())
                }
                let row = buttonPool[index]
                row.configure(index: index, title: option.title, appearance: appearance)
                row.onToggle = { [weak self] idx in
                    guard let self, let data = self.data else { return }
                    if data.multiple {
                        if self.selectedOption.contains(idx) {
                            self.selectedOption.removeAll { $0 == idx }
                        } else {
                            self.selectedOption.append(idx)
                        }
                    } else {
                        self.selectedOption = [idx]
                    }
                    self.reapplySelection()
                }
                optionDesired.append(row)
            } else {
                while resultPool.count <= index {
                    resultPool.append(PollOptionResultView())
                }
                let row = resultPool[index]
                row.configure(
                    title: option.title,
                    percentage: option.percentage,
                    humanizedPercentage: option.humanizedPercentage,
                    isOwnVote: data.ownVotes.contains(KotlinInt(value: Int32(index))),
                    appearance: appearance
                )
                optionDesired.append(row)
            }
        }
        syncManagedSubviews(parent: self, current: &optionViews, desired: optionDesired)

        var footerDesired: [NSView] = []
        if data.expired {
            footerDesired.append(expiredLabel)
        } else if let expiredAt = data.expiredAt {
            expiresAtTime.stringValue = formattedDateTime(expiredAt, absoluteTimestamp: appearance.absoluteTimestamp, fullTime: true)
            footerDesired.append(expiresAtLabel)
            footerDesired.append(expiresAtTime)
        }
        if data.canVote {
            footerDesired.append(voteButton)
        }
        syncManagedSubviews(parent: self, current: &footerViews, desired: footerDesired)

        reapplySelection()
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    private func reapplySelection() {
        for view in optionViews {
            guard let button = view as? PollOptionButton else { continue }
            button.setSelected(selectedOption.contains(button.index))
        }
        needsLayout = true
    }

    @objc private func submitVote() {
        onVote?(selectedOption)
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let fittingWidth = max(width, 1)
        var y: CGFloat = 0
        let allViews = optionViews + footerViews
        for (index, view) in allViews.enumerated() {
            let height = childHeight(of: view, for: fittingWidth)
            let viewWidth: CGFloat
            let x: CGFloat
            if optionViews.contains(where: { $0 === view }) {
                viewWidth = fittingWidth
                x = 0
            } else {
                viewWidth = min(childWidth(of: view, for: height), fittingWidth)
                x = fittingWidth - viewWidth
            }
            if assignFrames {
                view.frame = CGRect(x: x, y: y, width: viewWidth, height: height).integral
            }
            y += height
            if index < allViews.count - 1 {
                y += 8
            }
        }
        return y
    }
}

private final class PollOptionButton: NSControl, ManualLayoutMeasurable, TimelineHeightProviding {
    override var isFlipped: Bool { true }

    private(set) var index: Int = 0
    var onToggle: ((Int) -> Void)?

    private let titleLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .labelColor)
    private let checkmark = NSImageView()
    private let bg = FlippedView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        bg.wantsLayer = true
        bg.layer?.cornerRadius = 8
        checkmark.image = NSImage(systemSymbolName: "checkmark.circle", accessibilityDescription: nil)
        checkmark.contentTintColor = .secondaryLabelColor
        checkmark.imageScaling = .scaleProportionallyUpOrDown
        addSubview(bg)
        addSubview(titleLabel)
        addSubview(checkmark)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(onTapped)))
        setSelected(false)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(index: Int, title: String, appearance: StatusAppKitAppearance) {
        self.index = index
        titleLabel.font = appearance.captionFont
        titleLabel.stringValue = title
        setSelected(false)
        needsLayout = true
    }

    func setSelected(_ selected: Bool) {
        checkmark.isHidden = !selected
        bg.layer?.backgroundColor = selected
            ? NSColor.controlAccentColor.withAlphaComponent(0.2).cgColor
            : NSColor.controlBackgroundColor.cgColor
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    @objc private func onTapped() {
        onToggle?(index)
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let inset: CGFloat = 8
        let spacing: CGFloat = checkmark.isHidden ? 0 : 8
        let checkWidth: CGFloat = checkmark.isHidden ? 0 : 16
        let titleWidth = max(width - inset * 2 - spacing - checkWidth, 1)
        let titleHeight = childHeight(of: titleLabel, for: titleWidth)
        let contentHeight = max(ceil(titleHeight), checkmark.isHidden ? 0 : 16)
        let totalHeight = contentHeight + inset * 2

        if assignFrames {
            bg.frame = CGRect(x: 0, y: 0, width: width, height: totalHeight).integral
            titleLabel.frame = CGRect(
                x: inset,
                y: inset + (contentHeight - titleHeight) / 2,
                width: titleWidth,
                height: ceil(titleHeight)
            ).integral
            if checkmark.isHidden {
                checkmark.frame = .zero
            } else {
                checkmark.frame = CGRect(
                    x: width - inset - 16,
                    y: inset + (contentHeight - 16) / 2,
                    width: 16,
                    height: 16
                ).integral
            }
        }
        return totalHeight
    }
}

private final class PollOptionResultView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let titleLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .labelColor)
    private let check = NSImageView()
    private let pct = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 1)
    private let progress = NSProgressIndicator()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        check.image = NSImage(systemSymbolName: "checkmark.circle", accessibilityDescription: nil)
        check.contentTintColor = .labelColor
        check.imageScaling = .scaleProportionallyUpOrDown
        progress.style = .bar
        progress.isIndeterminate = false
        progress.minValue = 0
        progress.maxValue = 1
        progress.controlTint = .defaultControlTint
        addSubview(titleLabel)
        addSubview(check)
        addSubview(pct)
        addSubview(progress)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        title: String,
        percentage: Float,
        humanizedPercentage: String,
        isOwnVote: Bool,
        appearance: StatusAppKitAppearance
    ) {
        titleLabel.font = appearance.captionFont
        pct.font = appearance.captionFont
        titleLabel.stringValue = title
        pct.stringValue = humanizedPercentage
        progress.doubleValue = Double(percentage)
        check.isHidden = !isOwnVote
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let spacing: CGFloat = 8
        let progressSpacing: CGFloat = 4
        let checkWidth: CGFloat = check.isHidden ? 0 : 16
        let pctSize = CGSize(width: childWidth(of: pct, for: 18), height: childHeight(of: pct, for: width))
        let titleWidth = max(width - pctSize.width - checkWidth - spacing * (check.isHidden ? 1 : 2), 1)
        let titleHeight = childHeight(of: titleLabel, for: titleWidth)
        let rowHeight = max(ceil(titleHeight), pctSize.height, check.isHidden ? 0 : 16)
        let progressHeight = max(progress.intrinsicContentSize.height, 4)
        let totalHeight = rowHeight + progressSpacing + ceil(progressHeight)

        if assignFrames {
            var x: CGFloat = 0
            titleLabel.frame = CGRect(x: x, y: (rowHeight - titleHeight) / 2, width: titleWidth, height: ceil(titleHeight)).integral
            x += titleWidth + spacing
            if !check.isHidden {
                check.frame = CGRect(x: x, y: (rowHeight - 16) / 2, width: 16, height: 16).integral
                x += 16 + spacing
            } else {
                check.frame = .zero
            }
            pct.frame = CGRect(x: x, y: (rowHeight - pctSize.height) / 2, width: ceil(pctSize.width), height: ceil(pctSize.height)).integral
            progress.frame = CGRect(x: 0, y: rowHeight + progressSpacing, width: width, height: ceil(progressHeight)).integral
        }
        return totalHeight
    }
}

final class StatusMediaContentUIView: FlippedView, TimelineHeightProviding {
    var onMediaClicked: ((UiMedia, Int) -> Void)?
    var onLocalHeightInvalidated: (() -> Void)?

    private let grid = StatusMediaGridView()
    private let showButton = NSButton(title: "", target: nil, action: nil)
    private var items: [UiMedia] = []
    private var sensitive = false
    private var cornerRadius: CGFloat = 16
    private var expanded = false
    private var appearanceShowMedia = true
    private var appearanceShowSensitive = false
    private var appearanceExpandMediaSize = true
    private var lastConfigureSignature: ConfigureSignature?

    private struct ConfigureSignature: Equatable {
        let items: [MediaItemSignature]
        let sensitive: Bool
        let cornerRadius: CGFloat
        let appearanceShowMedia: Bool
        let appearanceShowSensitive: Bool
        let appearanceExpandMediaSize: Bool

        init(data: [UiMedia], sensitive: Bool, cornerRadius: CGFloat, appearance: StatusAppKitAppearance) {
            items = data.map(MediaItemSignature.init)
            self.sensitive = sensitive
            self.cornerRadius = cornerRadius
            appearanceShowMedia = appearance.showMedia
            appearanceShowSensitive = appearance.showSensitiveContent
            appearanceExpandMediaSize = appearance.expandMediaSize
        }
    }

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        showButton.bezelStyle = .rounded
        showButton.imagePosition = .imageLeading
        showButton.image = NSImage.fontAwesome(.image)
        showButton.target = self
        showButton.action = #selector(onShowTapped)
        showButton.title = LocalizedStrings.string("show_media_button", fallback: "Show media")
        grid.onMediaClicked = { [weak self] media, index in
            self?.onMediaClicked?(media, index)
        }
        addSubview(grid)
        addSubview(showButton)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        data: [UiMedia],
        sensitive: Bool,
        cornerRadius: CGFloat,
        appearance: StatusAppKitAppearance
    ) {
        let signature = ConfigureSignature(
            data: data,
            sensitive: sensitive,
            cornerRadius: cornerRadius,
            appearance: appearance
        )
        guard lastConfigureSignature != signature else { return }
        let shouldResetExpanded =
            lastConfigureSignature?.items != signature.items ||
            lastConfigureSignature?.sensitive != signature.sensitive
        lastConfigureSignature = signature
        items = data
        self.sensitive = sensitive
        self.cornerRadius = cornerRadius
        appearanceShowMedia = appearance.showMedia
        appearanceShowSensitive = appearance.showSensitiveContent
        appearanceExpandMediaSize = appearance.expandMediaSize
        if shouldResetExpanded {
            expanded = false
        }
        applyVisibility()
    }

    override func layout() {
        super.layout()
        if grid.isHidden {
            let size = showButton.intrinsicContentSize
            showButton.frame = CGRect(x: 0, y: 0, width: ceil(size.width), height: ceil(size.height))
            grid.frame = .zero
        } else {
            grid.frame = bounds
            showButton.frame = .zero
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        if !grid.isHidden {
            return grid.timelineHeight(for: width)
        }
        let height = showButton.intrinsicContentSize.height
        return height > 0 && height.isFinite ? ceil(height) : nil
    }

    private func applyVisibility() {
        if appearanceShowMedia || expanded {
            grid.isHidden = false
            showButton.isHidden = true
            grid.configure(
                data: items,
                sensitive: !appearanceShowSensitive && sensitive,
                cornerRadius: cornerRadius,
                singleFollowsImageAspect: appearanceExpandMediaSize
            )
        } else {
            grid.isHidden = true
            showButton.isHidden = false
            grid.configure(
                data: [],
                sensitive: false,
                cornerRadius: cornerRadius,
                singleFollowsImageAspect: appearanceExpandMediaSize
            )
        }
        needsLayout = true
        superview?.needsLayout = true
    }

    @objc private func onShowTapped() {
        expanded = true
        applyVisibility()
        onLocalHeightInvalidated?()
    }
}

private final class StatusMediaGridView: FlippedView, TimelineHeightProviding {
    private static let maxVisibleMediaCount = 9

    var onMediaClicked: ((UiMedia, Int) -> Void)?

    private let blurView = NSVisualEffectView()
    private let toggleButton = NSButton(title: "", target: nil, action: nil)
    private let overflowView = MediaOverflowView()
    private var cells: [MediaGridCellView] = []
    private var items: [UiMedia] = []
    private var sensitive = false
    private var isBlurred = false
    private var cornerRadius: CGFloat = 16
    private var singleFollowsImageAspect = true
    private var lastConfigureSignature: ConfigureSignature?
    private let spacing: CGFloat = 4

    private struct ConfigureSignature: Equatable {
        let items: [MediaItemSignature]
        let sensitive: Bool
        let cornerRadius: CGFloat
        let singleFollowsImageAspect: Bool

        init(data: [UiMedia], sensitive: Bool, cornerRadius: CGFloat, singleFollowsImageAspect: Bool) {
            items = data.map(MediaItemSignature.init)
            self.sensitive = sensitive
            self.cornerRadius = cornerRadius
            self.singleFollowsImageAspect = singleFollowsImageAspect
        }
    }

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.masksToBounds = true
        blurView.blendingMode = .withinWindow
        blurView.material = .contentBackground
        blurView.state = .active
        blurView.isHidden = true
        toggleButton.bezelStyle = .rounded
        toggleButton.imagePosition = .imageLeading
        toggleButton.target = self
        toggleButton.action = #selector(toggleBlur)
        overflowView.isHidden = true
        addSubview(blurView)
        addSubview(overflowView)
        addSubview(toggleButton)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: [UiMedia], sensitive: Bool, cornerRadius: CGFloat, singleFollowsImageAspect: Bool) {
        let signature = ConfigureSignature(
            data: data,
            sensitive: sensitive,
            cornerRadius: cornerRadius,
            singleFollowsImageAspect: singleFollowsImageAspect
        )
        guard lastConfigureSignature != signature else { return }
        let shouldResetBlur =
            lastConfigureSignature?.items != signature.items ||
            lastConfigureSignature?.sensitive != signature.sensitive
        lastConfigureSignature = signature
        items = data
        self.sensitive = sensitive
        self.cornerRadius = cornerRadius
        self.singleFollowsImageAspect = singleFollowsImageAspect
        if shouldResetBlur {
            isBlurred = sensitive
        }
        layer?.cornerRadius = cornerRadius
        rebuildGrid()
        updateBlurUI()
    }

    override func layout() {
        super.layout()
        layer?.cornerRadius = cornerRadius
        let frames = gridFrames(for: bounds.width)
        for (cell, frame) in zip(cells.prefix(visibleItemCount), frames) {
            cell.frame = frame
        }
        if overflowCount > 0, let lastFrame = frames.last {
            overflowView.frame = lastFrame
            addSubview(overflowView, positioned: .above, relativeTo: nil)
        } else {
            overflowView.frame = .zero
        }
        blurView.frame = bounds
        layoutToggleButton()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(gridHeight(for: width))
    }

    private func rebuildGrid() {
        let count = visibleItemCount
        while cells.count < count {
            let cell = MediaGridCellView()
            cell.onTap = { [weak self] index in
                self?.handleCellTap(index: index)
            }
            cells.append(cell)
            addSubview(cell, positioned: .below, relativeTo: blurView)
        }
        for index in 0..<count {
            let cell = cells[index]
            if cell.superview == nil {
                addSubview(cell, positioned: .below, relativeTo: blurView)
            }
            cell.isHidden = false
            cell.configure(media: items[index], index: index)
        }
        if count < cells.count {
            for cell in cells[count..<cells.count] {
                cell.isHidden = true
            }
        }
        if overflowCount > 0 {
            overflowView.configure(count: overflowCount)
            overflowView.isHidden = false
        } else {
            overflowView.isHidden = true
        }
        needsLayout = true
    }

    private func gridHeight(for width: CGFloat) -> CGFloat {
        guard !items.isEmpty, width > 0 else { return 0 }
        switch visibleItemCount {
        case 1:
            return width / singleAspectRatio()
        case 2, 3, 4:
            return width * 9 / 16
        default:
            let columns = 3
            let rows = Int(ceil(Double(visibleItemCount) / Double(columns)))
            let cellWidth = (width - CGFloat(columns - 1) * spacing) / CGFloat(columns)
            return CGFloat(rows) * cellWidth + CGFloat(max(rows - 1, 0)) * spacing
        }
    }

    private func gridFrames(for width: CGFloat) -> [CGRect] {
        guard !items.isEmpty, width > 0 else { return [] }
        let height = gridHeight(for: width)
        switch visibleItemCount {
        case 1:
            return [CGRect(x: 0, y: 0, width: width, height: height)]
        case 2:
            let cellWidth = (width - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: cellWidth, height: height),
                CGRect(x: cellWidth + spacing, y: 0, width: cellWidth, height: height),
            ]
        case 3:
            let halfWidth = (width - spacing) / 2
            let rightHeight = (height - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: halfWidth, height: height),
                CGRect(x: halfWidth + spacing, y: 0, width: halfWidth, height: rightHeight),
                CGRect(x: halfWidth + spacing, y: rightHeight + spacing, width: halfWidth, height: rightHeight),
            ]
        case 4:
            let cellWidth = (width - spacing) / 2
            let cellHeight = (height - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: cellWidth, height: cellHeight),
                CGRect(x: cellWidth + spacing, y: 0, width: cellWidth, height: cellHeight),
                CGRect(x: 0, y: cellHeight + spacing, width: cellWidth, height: cellHeight),
                CGRect(x: cellWidth + spacing, y: cellHeight + spacing, width: cellWidth, height: cellHeight),
            ]
        default:
            return multiRowGridFrames(width: width)
        }
    }

    private func multiRowGridFrames(width: CGFloat) -> [CGRect] {
        let columns = 3
        let cellWidth = (width - CGFloat(columns - 1) * spacing) / CGFloat(columns)
        let count = visibleItemCount
        let fullRows = count / columns
        let remainder = count % columns
        var frames: [CGRect] = []
        frames.reserveCapacity(count)
        var y: CGFloat = 0

        for row in 0..<fullRows {
            for column in 0..<columns {
                frames.append(CGRect(
                    x: CGFloat(column) * (cellWidth + spacing),
                    y: y,
                    width: cellWidth,
                    height: cellWidth
                ))
            }
            y += cellWidth
            if row < fullRows - 1 || remainder > 0 {
                y += spacing
            }
        }

        if remainder > 0 {
            let tailWidth = (width - CGFloat(remainder - 1) * spacing) / CGFloat(remainder)
            for column in 0..<remainder {
                frames.append(CGRect(
                    x: CGFloat(column) * (tailWidth + spacing),
                    y: y,
                    width: tailWidth,
                    height: cellWidth
                ))
            }
        }
        return frames
    }

    private func singleAspectRatio() -> CGFloat {
        guard singleFollowsImageAspect else { return 16 / 9 }
        if let ratio = items.first?.aspectRatio, ratio > 0 {
            return max(9 / 21, ratio)
        }
        return 1
    }

    private func handleCellTap(index: Int) {
        if sensitive, isBlurred { return }
        guard items.indices.contains(index) else { return }
        onMediaClicked?(items[index], index)
    }

    private var visibleItemCount: Int {
        min(items.count, Self.maxVisibleMediaCount)
    }

    private var overflowCount: Int {
        max(0, items.count - visibleItemCount)
    }

    private func updateBlurUI() {
        blurView.isHidden = !(sensitive && isBlurred)
        if !sensitive {
            toggleButton.isHidden = true
            return
        }
        toggleButton.isHidden = false
        toggleButton.title = isBlurred
            ? LocalizedStrings.string("sensitive_button_show", fallback: "Show sensitive media")
            : ""
        toggleButton.image = NSImage.fontAwesome(isBlurred ? .eye : .eyeSlash)
        needsLayout = true
    }

    private func layoutToggleButton() {
        guard !toggleButton.isHidden else {
            toggleButton.frame = .zero
            return
        }
        let size = toggleButton.intrinsicContentSize
        let buttonSize = CGSize(width: ceil(max(size.width, 30)), height: ceil(max(size.height, 30)))
        if isBlurred {
            toggleButton.frame = CGRect(
                x: (bounds.width - buttonSize.width) / 2,
                y: (bounds.height - buttonSize.height) / 2,
                width: buttonSize.width,
                height: buttonSize.height
            )
        } else {
            toggleButton.frame = CGRect(x: 12, y: 12, width: buttonSize.width, height: buttonSize.height)
        }
    }

    @objc private func toggleBlur() {
        isBlurred.toggle()
        updateBlurUI()
    }
}

private final class MediaGridCellView: FlippedView {
    var onTap: ((Int) -> Void)?

    private let image = RemoteImageView()
    private let altButton = NSButton(title: "ALT", target: nil, action: nil)
    private let playBadge = NSImageView()
    private var index = 0

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.masksToBounds = true
        playBadge.image = NSImage.fontAwesome(.circlePlay)
        playBadge.contentTintColor = .white
        playBadge.imageScaling = .scaleProportionallyUpOrDown
        altButton.bezelStyle = .rounded
        altButton.font = .boldSystemFont(ofSize: 10)
        altButton.isEnabled = false
        [image, playBadge, altButton].forEach(addSubview)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleTap)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(media: UiMedia, index: Int) {
        self.index = index
        image.configure(url: media.previewURLForTimeline, cornerRadius: 0)
        playBadge.isHidden = !media.isPlayableTimelineMedia
        altButton.isHidden = (media.description_ ?? "").isEmpty
        needsLayout = true
    }

    override func layout() {
        super.layout()
        image.frame = bounds
        let badgeSize: CGFloat = 34
        playBadge.frame = CGRect(
            x: (bounds.width - badgeSize) / 2,
            y: (bounds.height - badgeSize) / 2,
            width: badgeSize,
            height: badgeSize
        )
        altButton.frame = CGRect(x: 8, y: max(bounds.height - 30, 0), width: 42, height: 22)
    }

    @objc private func handleTap() {
        onTap?(index)
    }
}

private final class MediaOverflowView: FlippedView {
    private let label = TimelineTextField(font: .preferredFont(forTextStyle: .headline), color: .white, maximumLines: 1)

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.black.withAlphaComponent(0.55).cgColor
        label.alignment = .center
        addSubview(label)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(count: Int) {
        label.stringValue = count >= 100 ? "99+" : "+\(count)"
    }

    override func layout() {
        super.layout()
        label.frame = bounds
    }
}

final class StatusCardUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let image = RemoteImageView()
    private let title = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .labelColor, maximumLines: 2)
    private let subtitle = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .secondaryLabelColor, maximumLines: 3)
    private var data: UiCard?
    private var onOpenURL: ((URL) -> Void)?
    private var hasMedia = false
    private var isCompat = false
    private var aspectRatio: CGFloat = 16 / 9

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.masksToBounds = true
        layer?.borderWidth = 1
        layer?.borderColor = NSColor.separatorColor.cgColor
        layer?.cornerRadius = 16
        [image, title, subtitle].forEach(addSubview)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleClick)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiCard, appearance: StatusAppKitAppearance, cornerRadius: CGFloat, onOpenURL: ((URL) -> Void)?) {
        self.data = data
        self.onOpenURL = onOpenURL
        isCompat = appearance.compatLinkPreview
        layer?.cornerRadius = cornerRadius
        title.font = appearance.bodyFont
        subtitle.font = appearance.captionFont
        title.stringValue = data.title
        subtitle.stringValue = (data.description_?.isEmpty == false ? data.description_ : data.url) ?? data.url
        hasMedia = data.media != nil
        aspectRatio = {
            guard let ratio = data.media?.aspectRatio, ratio > 0 else { return 16 / 9 }
            return ratio
        }()
        image.configure(url: data.media?.previewURLForTimeline, cornerRadius: 8)
        image.isHidden = !hasMedia
        title.maximumLines = isCompat ? 1 : 2
        subtitle.maximumLines = isCompat ? 1 : 2
        needsLayout = true
    }

    override func layout() {
        super.layout()
        if isCompat {
            layoutCompat(width: bounds.width, assignFrames: true)
        } else {
            layoutNormal(width: bounds.width, assignFrames: true)
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(isCompat ? layoutCompat(width: width, assignFrames: false) : layoutNormal(width: width, assignFrames: false))
    }

    @objc private func handleClick() {
        guard let data, let url = URL(string: data.url) else { return }
        onOpenURL?(url)
    }

    @discardableResult
    private func layoutNormal(width: CGFloat, assignFrames: Bool) -> CGFloat {
        var y: CGFloat = 0
        if hasMedia {
            let mediaHeight = width / aspectRatio
            if assignFrames {
                image.frame = CGRect(x: 0, y: 0, width: width, height: mediaHeight)
            }
            y = mediaHeight
        } else if assignFrames {
            image.frame = .zero
        }

        let inset = NSEdgeInsets(top: hasMedia ? 4 : 8, left: 8, bottom: 8, right: 8)
        let textWidth = max(width - inset.left - inset.right, 1)
        y += inset.top
        let titleHeight = childHeight(of: title, for: textWidth)
        if assignFrames {
            title.frame = CGRect(x: inset.left, y: y, width: textWidth, height: titleHeight).integral
        }
        y += titleHeight + 4
        let subtitleHeight = childHeight(of: subtitle, for: textWidth)
        if assignFrames {
            subtitle.frame = CGRect(x: inset.left, y: y, width: textWidth, height: subtitleHeight).integral
        }
        y += subtitleHeight + inset.bottom
        return y
    }

    @discardableResult
    private func layoutCompat(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let mediaSize: CGFloat = 72
        let mediaSpacing: CGFloat = 8
        let textSpacing: CGFloat = 4
        let inset = hasMedia
            ? NSEdgeInsets(top: 12, left: 0, bottom: 12, right: 12)
            : NSEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        var textX: CGFloat = 0
        if hasMedia {
            if assignFrames {
                image.frame = CGRect(x: 0, y: 0, width: mediaSize, height: mediaSize)
            }
            textX = mediaSize + mediaSpacing
        } else if assignFrames {
            image.frame = .zero
        }

        let textWidth = max(width - textX - inset.left - inset.right, 1)
        let titleHeight = childHeight(of: title, for: textWidth)
        let subtitleHeight = childHeight(of: subtitle, for: textWidth)
        let totalTextHeight = titleHeight + textSpacing + subtitleHeight
        let rowHeight = hasMedia ? max(mediaSize, totalTextHeight + inset.top + inset.bottom) : totalTextHeight + inset.top + inset.bottom
        let textY = (rowHeight - totalTextHeight) / 2

        if assignFrames {
            title.frame = CGRect(x: textX + inset.left, y: textY, width: textWidth, height: titleHeight).integral
            subtitle.frame = CGRect(x: textX + inset.left, y: title.frame.maxY + textSpacing, width: textWidth, height: subtitleHeight).integral
        }
        return rowHeight
    }
}

final class StatusReactionUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onReactionTapped: ((UiTimelineV2.PostEmojiReaction) -> Void)?

    private let scroll = NSScrollView()
    private let scrollContent = FlippedView()
    private let wrap = WrappingStackAppKitView()
    private var isDetail = false
    private var chipPool: [ReactionChipView] = []
    private var scrollChips: [NSView] = []
    private static let chipHeight: CGFloat = 36
    private static let chipSpacing: CGFloat = 8

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        scroll.drawsBackground = false
        scroll.borderType = .noBorder
        scroll.hasHorizontalScroller = false
        scroll.hasVerticalScroller = false
        scroll.documentView = scrollContent
        addSubview(scroll)
        addSubview(wrap)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: [UiTimelineV2.PostEmojiReaction], appearance: StatusAppKitAppearance, isDetail: Bool) {
        self.isDetail = isDetail
        scroll.isHidden = isDetail
        wrap.isHidden = !isDetail

        while chipPool.count < data.count {
            chipPool.append(ReactionChipView())
        }
        let chips: [NSView] = data.enumerated().map { index, item in
            let chip = chipPool[index]
            chip.configure(item: item, appearance: appearance)
            chip.onTap = { [weak self] in
                self?.onReactionTapped?(item)
            }
            return chip
        }

        if isDetail {
            wrap.setViews(chips)
            for chip in scrollChips {
                chip.removeFromSuperview()
            }
            scrollChips = []
        } else {
            syncManagedSubviews(parent: scrollContent, current: &scrollChips, desired: chips)
            wrap.setViews([])
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        if isDetail {
            wrap.frame = bounds
        } else {
            scroll.frame = CGRect(x: 0, y: 0, width: bounds.width, height: Self.chipHeight)
            var x: CGFloat = 0
            for chip in scrollChips {
                let chipWidth = childWidth(of: chip, for: Self.chipHeight)
                chip.frame = CGRect(x: x, y: 0, width: ceil(chipWidth), height: Self.chipHeight)
                x += ceil(chipWidth) + Self.chipSpacing
            }
            let contentWidth = max(x - Self.chipSpacing, bounds.width, 0)
            scrollContent.frame = CGRect(x: 0, y: 0, width: contentWidth, height: Self.chipHeight)
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        if isDetail {
            return wrap.timelineHeight(for: width)
        }
        return Self.chipHeight
    }
}

private final class ReactionChipView: NSControl, ManualLayoutMeasurable, TimelineHeightProviding {
    override var isFlipped: Bool { true }

    var onTap: (() -> Void)?
    private let nameLabel = TimelineTextField(font: .preferredFont(forTextStyle: .body), color: .labelColor, maximumLines: 1)
    private let countLabel = TimelineTextField(font: .preferredFont(forTextStyle: .caption1), color: .labelColor, maximumLines: 1)
    private let imageView = RemoteImageView()
    private var showsImage = false
    private static let hPadding: CGFloat = 8
    private static let spacing: CGFloat = 4
    private static let imageSize: CGFloat = 20
    private static let totalHeight: CGFloat = 36

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.cornerRadius = 8
        layer?.masksToBounds = true
        addSubview(imageView)
        addSubview(nameLabel)
        addSubview(countLabel)
        addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(handleTap)))
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(item: UiTimelineV2.PostEmojiReaction, appearance: StatusAppKitAppearance) {
        nameLabel.font = appearance.bodyFont
        countLabel.font = appearance.captionFont
        if item.me {
            layer?.backgroundColor = NSColor.controlAccentColor.cgColor
            nameLabel.textColor = .white
            countLabel.textColor = .white
        } else {
            layer?.backgroundColor = NSColor.controlBackgroundColor.cgColor
            nameLabel.textColor = .labelColor
            countLabel.textColor = .labelColor
        }

        if item.isUnicode {
            showsImage = false
            nameLabel.stringValue = item.name
            nameLabel.isHidden = false
            imageView.configure(url: nil, cornerRadius: 0)
            imageView.isHidden = true
        } else {
            showsImage = true
            nameLabel.stringValue = ""
            nameLabel.isHidden = true
            imageView.isHidden = false
            imageView.configure(url: item.url, cornerRadius: 0)
        }

        countLabel.stringValue = item.count.humanized
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let height = Self.totalHeight
        var x = Self.hPadding
        if showsImage {
            imageView.frame = CGRect(
                x: x,
                y: (height - Self.imageSize) / 2,
                width: Self.imageSize,
                height: Self.imageSize
            )
            x += Self.imageSize + Self.spacing
        } else if !nameLabel.isHidden {
            let labelWidth = childWidth(of: nameLabel, for: height)
            let labelHeight = childHeight(of: nameLabel, for: labelWidth)
            nameLabel.frame = CGRect(
                x: x,
                y: (height - labelHeight) / 2,
                width: ceil(labelWidth),
                height: ceil(labelHeight)
            )
            x += ceil(labelWidth) + Self.spacing
        }

        let countWidth = childWidth(of: countLabel, for: height)
        let countHeight = childHeight(of: countLabel, for: countWidth)
        countLabel.frame = CGRect(
            x: x,
            y: (height - countHeight) / 2,
            width: ceil(countWidth),
            height: ceil(countHeight)
        )
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        Self.totalHeight
    }

    override var intrinsicContentSize: NSSize {
        CGSize(width: timelineWidth(), height: Self.totalHeight)
    }

    private func timelineWidth() -> CGFloat {
        var width = Self.hPadding
        if showsImage {
            width += Self.imageSize + Self.spacing
        } else if !nameLabel.stringValue.isEmpty {
            width += childWidth(of: nameLabel, for: Self.totalHeight) + Self.spacing
        }
        width += childWidth(of: countLabel, for: Self.totalHeight) + Self.hPadding
        return ceil(width)
    }

    @objc private func handleTap() {
        onTap?()
    }
}

private final class WrappingStackAppKitView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var horizontalSpacing: CGFloat = 8
    var verticalSpacing: CGFloat = 8
    private var managed: [NSView] = []
    private static let chipHeight: CGFloat = 36

    func setViews(_ views: [NSView]) {
        syncManagedSubviews(parent: self, current: &managed, desired: views)
        needsLayout = true
    }

    override func layout() {
        super.layout()
        layout(width: bounds.width, assignFrames: true)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return layout(width: width, assignFrames: false)
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard !managed.isEmpty, width > 0 else { return 0 }
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for view in managed {
            let viewWidth = childWidth(of: view, for: Self.chipHeight)
            let viewHeight = childHeight(of: view, for: viewWidth)
            if x > 0, x + viewWidth > width {
                y += rowHeight + verticalSpacing
                x = 0
                rowHeight = 0
            }
            if assignFrames {
                view.frame = CGRect(x: x, y: y, width: viewWidth, height: viewHeight)
            }
            x += viewWidth + horizontalSpacing
            rowHeight = max(rowHeight, viewHeight)
        }
        y += rowHeight
        return y
    }
}

private final class ActionsContainerAppKitView: FlippedView, TimelineHeightProviding {
    var content: NSView? {
        didSet {
            guard oldValue !== content else { return }
            oldValue?.removeFromSuperview()
            if let content {
                addSubview(content)
            }
            needsLayout = true
        }
    }
    private static let topPadding: CGFloat = 4

    override func layout() {
        super.layout()
        guard let content else { return }
        content.frame = CGRect(
            x: 0,
            y: Self.topPadding,
            width: bounds.width,
            height: max(bounds.height - Self.topPadding, 0)
        )
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        guard let content else { return 0 }
        return ceil(childHeight(of: content, for: width) + Self.topPadding)
    }
}

final class StatusActionsUIView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private var buttons: [NSButton] = []
    private var actions: [ActionMenu] = []
    private var onOpenURL: ((URL) -> Void)?
    private var statusAppearance = StatusAppKitAppearance(timeline: TimelineAppearance.companion.Default)
    private var useText = false
    private var isStretch = false
    private var spacerIndex: Int?

    func configure(
        data: [ActionMenu],
        appearance: StatusAppKitAppearance,
        onOpenURL: ((URL) -> Void)?,
        useText: Bool = false
    ) {
        actions = data
        self.statusAppearance = appearance
        self.onOpenURL = onOpenURL
        self.useText = useText
        buttons.forEach { $0.removeFromSuperview() }
        isStretch = !useText && appearance.postActionStyle == .stretch
        spacerIndex = nil
        var nextButtons: [NSButton] = []
        for (index, action) in data.enumerated() {
            if !useText,
               ((index == data.count - 1 && appearance.postActionStyle == .leftAligned) ||
                (index == 0 && appearance.postActionStyle == .rightAligned)) {
                spacerIndex = nextButtons.count
            }
            if let button = makeButton(for: action, isFixedWidth: !useText && index != data.count - 1) {
                nextButtons.append(button)
            }
        }
        buttons = nextButtons
        buttons.forEach(addSubview)
        needsLayout = true
    }

    func configureItems(
        data: [ActionMenu.Item],
        appearance: StatusAppKitAppearance,
        onOpenURL: ((URL) -> Void)?,
        useText: Bool = false
    ) {
        configure(data: data.map { $0 as ActionMenu }, appearance: appearance, onOpenURL: onOpenURL, useText: useText)
    }

    override func layout() {
        super.layout()
        let height: CGFloat = 26
        guard !buttons.isEmpty else { return }
        let spacing: CGFloat = 12
        let widths = buttons.map { button in
            max(button.intrinsicContentSize.width, useText ? 64 : 32)
        }

        if isStretch {
            let totalButtonWidth = widths.reduce(0, +)
            let gapCount = max(buttons.count - 1, 1)
            let gap = max(bounds.width - totalButtonWidth, 0) / CGFloat(gapCount)
            var x: CGFloat = 0
            for (index, button) in buttons.enumerated() {
                let width = widths[index]
                button.frame = CGRect(x: x, y: 0, width: width, height: height)
                x += width + gap
            }
            return
        }

        if let spacerIndex {
            var x: CGFloat = 0
            for index in 0..<spacerIndex {
                let width = widths[index]
                buttons[index].frame = CGRect(x: x, y: 0, width: width, height: height)
                x += width + spacing
            }
            let trailingRange = spacerIndex..<buttons.count
            let trailingWidth = trailingRange.reduce(CGFloat(0)) { partial, index in
                partial + widths[index] + (index == buttons.count - 1 ? 0 : spacing)
            }
            x = max(bounds.width - trailingWidth, 0)
            for index in trailingRange {
                let width = widths[index]
                buttons[index].frame = CGRect(x: x, y: 0, width: width, height: height)
                x += width + spacing
            }
            return
        }

        let totalWidth = widths.reduce(0, +) + spacing * CGFloat(max(buttons.count - 1, 0))
        var x = statusAppearance.postActionStyle == .rightAligned ? max(bounds.width - totalWidth, 0) : 0
        for (index, button) in buttons.enumerated() {
            let width = widths[index]
            button.frame = CGRect(x: x, y: 0, width: width, height: height)
            x += width + spacing
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        buttons.isEmpty ? 0 : 26
    }

    private func makeButton(for action: ActionMenu, isFixedWidth: Bool) -> NSButton? {
        switch onEnum(of: action) {
        case .item(let item):
            let title = useText
                ? (item.text?.resolvedString ?? item.count?.humanized ?? "")
                : (statusAppearance.showNumbers ? item.count?.humanized ?? "" : "")
            let button = NSButton(title: title.isEmpty ? " " : title, target: self, action: #selector(handleAction(_:)))
            button.image = item.icon.flatMap { NSImage.fontAwesome($0.fontAwesomeIcon) }
            button.imagePosition = title.isEmpty ? .imageOnly : .imageLeading
            button.bezelStyle = .inline
            button.isBordered = false
            button.font = statusAppearance.captionFont
            button.contentTintColor = item.color?.nsColor ?? .secondaryLabelColor
            button.identifier = NSUserInterfaceItemIdentifier(item.updateKey.isEmpty ? UUID().uuidString : item.updateKey)
            if isFixedWidth, !title.isEmpty {
                button.setContentHuggingPriority(.defaultLow, for: .horizontal)
            }
            return button
        case .group(let group):
            let title = useText
                ? (group.displayItem.text?.resolvedString ?? group.displayItem.count?.humanized ?? "")
                : (statusAppearance.showNumbers ? group.displayItem.count?.humanized ?? "" : "")
            let button = NSButton(title: title, target: self, action: #selector(handleAction(_:)))
            button.image = group.displayItem.icon.flatMap { NSImage.fontAwesome($0.fontAwesomeIcon) }
            button.imagePosition = title.isEmpty ? .imageOnly : .imageLeading
            button.bezelStyle = .inline
            button.isBordered = false
            button.font = statusAppearance.captionFont
            button.contentTintColor = group.displayItem.color?.nsColor ?? .secondaryLabelColor
            button.identifier = NSUserInterfaceItemIdentifier(group.displayItem.updateKey.isEmpty ? UUID().uuidString : group.displayItem.updateKey)
            return button
        case .divider:
            return nil
        }
    }

    @objc private func handleAction(_ sender: NSButton) {
        let index = buttons.firstIndex(of: sender) ?? 0
        guard actions.indices.contains(index) else { return }
        if case .group(let group) = onEnum(of: actions[index]) {
            let menu = buildMenu(from: Array(group.actions))
            if let event = NSApp.currentEvent {
                NSMenu.popUpContextMenu(menu, with: event, for: sender)
            }
            return
        }
        click(actions[index])
    }

    private func click(_ action: ActionMenu) {
        switch onEnum(of: action) {
        case .item(let item):
            item.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
        case .group(let group):
            group.displayItem.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
        case .divider:
            break
        }
    }

    private func buildMenu(from actions: [ActionMenu]) -> NSMenu {
        let menu = NSMenu()
        for action in actions {
            switch onEnum(of: action) {
            case .item(let item):
                let menuItem = NSMenuItem(
                    title: item.text?.resolvedString ?? "",
                    action: #selector(handleMenuItem(_:)),
                    keyEquivalent: ""
                )
                menuItem.image = item.icon.flatMap { NSImage.fontAwesome($0.fontAwesomeIcon) }
                menuItem.representedObject = item
                menu.addItem(menuItem)
            case .group(let group):
                let menuItem = NSMenuItem(title: group.displayItem.text?.resolvedString ?? "", action: nil, keyEquivalent: "")
                menuItem.image = group.displayItem.icon.flatMap { NSImage.fontAwesome($0.fontAwesomeIcon) }
                menuItem.submenu = buildMenu(from: Array(group.actions))
                menu.addItem(menuItem)
            case .divider:
                menu.addItem(.separator())
            }
        }
        return menu
    }

    @objc private func handleMenuItem(_ sender: NSMenuItem) {
        guard let item = sender.representedObject as? ActionMenu.Item else { return }
        item.onClicked(ClickContext(launcher: makeLauncher(onOpenURL)))
    }
}

private final class QuotesContainerView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private var children: [NSView] = []
    private static let padding: CGFloat = 8
    private static let spacing: CGFloat = 8
    private var dividerHeight: CGFloat {
        1 / (window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2)
    }

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.borderWidth = 1
        layer?.borderColor = NSColor.separatorColor.cgColor
        layer?.cornerRadius = 16
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        posts: [UiTimelineV2.Post],
        appearance: StatusAppKitAppearance,
        onOpenURL: ((URL) -> Void)?,
        onLocalHeightInvalidated: (() -> Void)?
    ) {
        children.forEach { $0.removeFromSuperview() }
        var desired: [NSView] = []
        for (index, post) in posts.enumerated() {
            let status = StatusUIKitView()
            status.configure(
                data: post,
                appearance: appearance,
                detailStatusKey: nil,
                showTranslate: false,
                aiTldrEnabled: false,
                forceHideActions: true,
                isQuote: true,
                onOpenURL: onOpenURL
            )
            status.onLocalHeightInvalidated = { [weak self] in
                self?.needsLayout = true
                onLocalHeightInvalidated?()
            }
            desired.append(status)
            if index != posts.count - 1 {
                let divider = FlippedView()
                divider.wantsLayer = true
                divider.layer?.backgroundColor = NSColor.separatorColor.cgColor
                desired.append(divider)
            }
        }
        children = desired
        children.forEach(addSubview)
        needsLayout = true
    }

    override func layout() {
        super.layout()
        let padding = Self.padding
        let contentWidth = max(bounds.width - padding * 2, 0)
        var y = padding
        for (index, child) in children.enumerated() {
            let height: CGFloat
            if isDivider(child) {
                height = dividerHeight
            } else {
                height = childHeight(of: child, for: contentWidth)
            }
            child.frame = CGRect(x: padding, y: y, width: contentWidth, height: height)
            y += height
            if index < children.count - 1 {
                y += Self.spacing
            }
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let padding = Self.padding
        let contentWidth = max(width - padding * 2, 0)
        var height = padding
        for (index, child) in children.enumerated() {
            if isDivider(child) {
                height += dividerHeight
            } else {
                height += childHeight(of: child, for: contentWidth)
            }
            if index < children.count - 1 {
                height += Self.spacing
            }
        }
        height += padding
        return ceil(height)
    }

    private func isDivider(_ view: NSView) -> Bool {
        !(view is StatusUIKitView)
    }
}

private final class ParentContainerView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onLocalHeightInvalidated: (() -> Void)?

    private let status = StatusUIKitView()
    private let line = FlippedView()
    private static let bottomSpacing: CGFloat = 8

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        line.wantsLayer = true
        line.layer?.backgroundColor = NSColor.separatorColor.cgColor
        addSubview(line)
        addSubview(status)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(data: UiTimelineV2.Post, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        status.configure(
            data: data,
            appearance: appearance,
            detailStatusKey: nil,
            showTranslate: false,
            aiTldrEnabled: false,
            withLeadingPadding: true,
            onOpenURL: onOpenURL
        )
        status.onLocalHeightInvalidated = { [weak self] in
            self?.needsLayout = true
            self?.onLocalHeightInvalidated?()
        }
        needsLayout = true
    }

    override func layout() {
        super.layout()
        status.frame = CGRect(x: 0, y: 0, width: bounds.width, height: max(bounds.height - Self.bottomSpacing, 0))
        line.frame = CGRect(x: 22, y: 44, width: 1, height: max(bounds.height - 44, 0))
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil((status.timelineHeight(for: width) ?? 0) + Self.bottomSpacing)
    }
}

private final class UserChipView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let user = UserCompatAppKitView()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.borderWidth = 1
        layer?.borderColor = NSColor.separatorColor.cgColor
        layer?.cornerRadius = 12
        addSubview(user)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(profile: UiProfile, appearance: StatusAppKitAppearance, onOpenURL: ((URL) -> Void)?) {
        user.configure(profile: profile, appearance: appearance, onOpenURL: onOpenURL)
    }

    override func layout() {
        super.layout()
        user.frame = bounds.insetBy(dx: 8, dy: 8)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        60
    }
}

private final class HorizontalChipRow: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let views: [NSView]

    init(views: [NSView]) {
        self.views = views
        super.init(frame: .zero)
        views.forEach(addSubview)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        let spacing: CGFloat = 8
        let itemWidth = min(260, max(bounds.width, 0))
        var x: CGFloat = 0
        for view in views {
            view.frame = CGRect(x: x, y: 0, width: itemWidth, height: 60)
            x += itemWidth + spacing
        }
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        views.isEmpty ? 0 : 60
    }
}

private final class BorderedPaddingView: FlippedView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let content: NSView
    private let insets: NSEdgeInsets

    init(content: NSView, insets: NSEdgeInsets, cornerRadius: CGFloat) {
        self.content = content
        self.insets = insets
        super.init(frame: .zero)
        wantsLayer = true
        layer?.borderWidth = 1
        layer?.borderColor = NSColor.separatorColor.cgColor
        layer?.cornerRadius = cornerRadius
        addSubview(content)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override func layout() {
        super.layout()
        content.frame = CGRect(
            x: insets.left,
            y: insets.top,
            width: max(bounds.width - insets.left - insets.right, 0),
            height: max(bounds.height - insets.top - insets.bottom, 0)
        )
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        childHeight(of: content, for: max(width - insets.left - insets.right, 0)) + insets.top + insets.bottom
    }
}

private extension UiMedia {
    var aspectRatio: CGFloat? {
        switch onEnum(of: self) {
        case .image(let image):
            CGFloat(image.aspectRatio)
        case .video(let video):
            CGFloat(video.aspectRatio)
        case .gif(let gif):
            CGFloat(gif.aspectRatio)
        case .audio:
            nil
        }
    }

    var previewURLForTimeline: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl.isEmpty ? image.url : image.previewUrl
        case .video(let video):
            video.thumbnailUrl.isEmpty ? video.url : video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl.isEmpty ? gif.url : gif.previewUrl
        case .audio(let audio):
            audio.previewUrl
        }
    }

    var previewURLForMediaRoute: String? {
        switch onEnum(of: self) {
        case .image(let image):
            image.previewUrl
        case .video(let video):
            video.thumbnailUrl
        case .gif(let gif):
            gif.previewUrl
        case .audio:
            nil
        }
    }

    var isPlayableTimelineMedia: Bool {
        switch onEnum(of: self) {
        case .video, .gif:
            true
        case .image, .audio:
            false
        }
    }
}

private enum AppKitDateTimeFormatter {
    static let full: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        return formatter
    }()

    static let dateOnly: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter
    }()
}

private func formattedDateTime(
    _ data: UiDateTime,
    absoluteTimestamp: Bool,
    fullTime: Bool = false
) -> String {
    if fullTime {
        return AppKitDateTimeFormatter.full.string(from: data.platformValue)
    }
    if data.shouldShowFull {
        return AppKitDateTimeFormatter.dateOnly.string(from: data.platformValue)
    }
    return absoluteTimestamp ? data.absolute : data.relative
}

private func makeLauncher(_ onOpenURL: ((URL) -> Void)?) -> AppleUriLauncher {
    AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { url in
        onOpenURL?(url)
        return .handled
    })
}

private struct MediaItemSignature: Equatable {
    let kind: String
    let primaryURL: String
    let altText: String
    let aspectRatio: CGFloat?

    init(media: UiMedia) {
        altText = media.description_ ?? ""
        aspectRatio = media.aspectRatio
        switch onEnum(of: media) {
        case .image(let image):
            kind = "image"
            primaryURL = image.previewUrl
        case .video(let video):
            kind = "video"
            primaryURL = video.thumbnailUrl
        case .gif(let gif):
            kind = "gif"
            primaryURL = gif.url
        case .audio:
            kind = "audio"
            primaryURL = ""
        }
    }
}

private extension ActionMenu.ItemColor {
    var nsColor: NSColor? {
        switch self {
        case .red:
            .systemRed
        case .contentColor:
            .labelColor
        case .primaryColor:
            .controlAccentColor
        default:
            nil
        }
    }
}

private extension NSView {
    func firstSuperview<T: NSView>(of type: T.Type) -> T? {
        var current: NSView? = self
        while let view = current {
            if let typed = view as? T {
                return typed
            }
            current = view.superview
        }
        return nil
    }
}
