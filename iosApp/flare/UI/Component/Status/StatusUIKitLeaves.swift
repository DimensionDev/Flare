import UIKit
import Kingfisher
import KotlinSharedUI

// MARK: - AvatarUIView
// Mirrors AvatarView.swift (NetworkImage + clipShape by avatarShape).
final class AvatarUIView: UIView {
    private let imageView: UIImageView = {
        let v = UIImageView()
        v.contentMode = .scaleAspectFill
        v.clipsToBounds = true
        v.backgroundColor = .secondarySystemBackground
        return v
    }()

    var avatarShape: AvatarShape = .circle {
        didSet { setNeedsLayout() }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(imageView)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(url: String?) {
        imageView.kf.cancelDownloadTask()
        imageView.image = nil
        if let url = url.flatMap(URL.init(string:)) {
            imageView.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage, .backgroundDecode])
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        imageView.frame = bounds
        switch avatarShape {
        case .circle:
            imageView.layer.cornerRadius = min(bounds.width, bounds.height) / 2
        default:
            imageView.layer.cornerRadius = 8
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        size
    }
}

// MARK: - DateTimeUILabel
// Mirrors DateTimeText.swift. For relative display we snapshot `data.relative`;
// SwiftUI's live-updating `Text(_:style:.relative)` would require a timer.
final class DateTimeUILabel: UILabel {
    var absoluteTimestamp: Bool = false
    var fullTime: Bool = false

    private static let fullFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .long
        f.timeStyle = .short
        return f
    }()

    private static let dateOnlyFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .none
        return f
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        font = .preferredFont(forTextStyle: .caption1)
        textColor = .secondaryLabel
        adjustsFontForContentSizeCategory = true
        numberOfLines = 1
        lineBreakMode = .byTruncatingTail
        setContentHuggingPriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .horizontal)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(data: UiDateTime) {
        if fullTime {
            text = Self.fullFormatter.string(from: data.platformValue)
        } else if data.shouldShowFull {
            text = Self.dateOnlyFormatter.string(from: data.platformValue)
        } else if absoluteTimestamp {
            text = data.absolute
        } else {
            text = data.relative
        }
    }
}

// MARK: - StatusVisibilityImageView
// Mirrors StatusVisibilityView.swift — asset mapping per enum case.
final class StatusVisibilityImageView: UIImageView {
    convenience init() {
        self.init(frame: .zero)
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentMode = .scaleAspectFit
        setContentHuggingPriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .horizontal)
        tintColor = .secondaryLabel
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(visibility: UiTimelineV2.PostVisibility) {
        let name: String
        switch visibility {
        case .public:    name = "fa-globe"
        case .home:      name = "fa-lock-open"
        case .followers: name = "fa-lock"
        case .specified: name = "fa-at"
        case .channel:   name = "fa-tv"
        default:         name = "fa-globe"
        }
        image = UIImage(named: name)
    }
}

// MARK: - TranslateStatusStateView
// Mirrors TranslateStatusComponent.swift — language icon + state icon/spinner.
final class TranslateStatusStateView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let langIcon = UIImageView(image: UIImage(named: "fa-language"))
    private let stateIcon = UIImageView()
    private let spinner = UIActivityIndicatorView(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        tintColor = .secondaryLabel
        langIcon.contentMode = .scaleAspectFit
        stateIcon.contentMode = .scaleAspectFit
        addSubview(langIcon)
        addSubview(stateIcon)
        addSubview(spinner)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(state: TranslationDisplayState) {
        switch state {
        case .failed:
            stateIcon.image = UIImage(named: "fa-circle-exclamation")
            stateIcon.isHidden = false
            spinner.stopAnimating()
            spinner.isHidden = true
        case .translating:
            stateIcon.isHidden = true
            spinner.isHidden = false
            spinner.startAnimating()
        default:
            stateIcon.isHidden = true
            spinner.stopAnimating()
            spinner.isHidden = true
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let size = preferredSize()
        let y = max((bounds.height - size.height) / 2, 0)
        langIcon.frame = CGRect(x: 0, y: y, width: 16, height: 16)
        let stateFrame = CGRect(x: 20, y: y + 2, width: 12, height: 12)
        stateIcon.frame = stateIcon.isHidden ? .zero : stateFrame
        spinner.frame = spinner.isHidden ? .zero : stateFrame
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        preferredSize()
    }

    override var intrinsicContentSize: CGSize {
        preferredSize()
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        preferredSize()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        16
    }

    private func preferredSize() -> CGSize {
        let hasState = !stateIcon.isHidden || !spinner.isHidden
        return CGSize(width: hasState ? 32 : 16, height: 16)
    }
}

// MARK: - UserOnelineUIView / UserCompatUIView
// Mirror UserOnelineView.swift / UserCompatView.swift.

final class UserOnelineUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let showsAvatar: Bool
    let avatar = AvatarUIView()
    let name = RichTextUIView()
    let handleLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .caption1)
        l.textColor = .secondaryLabel
        l.adjustsFontForContentSizeCategory = true
        l.numberOfLines = 1
        l.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return l
    }()
    private var trailingView: UIView?

    var onTapped: (() -> Void)?

    init(showAvatar: Bool) {
        self.showsAvatar = showAvatar
        super.init(frame: .zero)
        name.lineLimit = 1
        name.setContentHuggingPriority(.required, for: .horizontal)
        name.setContentCompressionResistancePriority(UILayoutPriority(900), for: .horizontal)
        handleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        handleLabel.setContentCompressionResistancePriority(UILayoutPriority(100), for: .horizontal)

        if showAvatar {
            addSubview(avatar)
            let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
            avatar.isUserInteractionEnabled = true
            avatar.addGestureRecognizer(tap)
        }
        addSubview(name)
        addSubview(handleLabel)

    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        layout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: preferredSingleLineHeight(width: bounds.width))
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width.isFinite else { return nil }
        return preferredSingleLineHeight(width: width)
    }

    private func preferredSingleLineHeight(width: CGFloat) -> CGFloat {
        let nameHeight = UIFont.preferredFont(forTextStyle: .body).lineHeight
        let handleHeight = handleLabel.font?.lineHeight ?? 0
        let fittingWidth = width > 0 ? width : 1
        let trailingHeight = trailingView.map { $0.isHidden ? 0 : childHeight(of: $0, for: fittingWidth) } ?? 0
        let avatarHeight: CGFloat = showsAvatar ? 20 : 0
        return ceil(max(avatarHeight, nameHeight, handleHeight, trailingHeight))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width > 0 && targetSize.width.isFinite
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : 1)
        return CGSize(width: horizontalFittingPriority == .required ? width : UIView.noIntrinsicMetric,
                      height: preferredSingleLineHeight(width: width))
    }

    func configure(data: UiProfile, trailing: UIView?, onClicked: (() -> Void)?) {
        avatar.set(url: data.avatar)
        name.text = data.name
        handleLabel.text = data.handle.canonical
        if trailingView !== trailing {
            trailingView?.removeFromSuperview()
            trailingView = trailing
            if let trailing {
                addSubview(trailing)
            }
        }
        if let trailing {
            trailing.setContentHuggingPriority(.required, for: .horizontal)
            trailing.setContentCompressionResistancePriority(.required, for: .horizontal)
        }
        onTapped = onClicked
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    @objc private func onTapFired() { onTapped?() }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let rowHeight = preferredSingleLineHeight(width: width)
        let rowWidth = max(width, 1)
        let edgeSpacing: CGFloat = 8
        let nameSpacing: CGFloat = 4

        var leadingX: CGFloat = 0
        if showsAvatar {
            if assignFrames {
                avatar.frame = CGRect(x: 0, y: (rowHeight - 20) / 2, width: 20, height: 20).integral
            }
            leadingX = 20 + edgeSpacing
        }

        let trailingSize = preferredTrailingSize(height: rowHeight)
        if let trailing = trailingView, !trailing.isHidden, assignFrames {
            trailing.frame = CGRect(
                x: max(rowWidth - trailingSize.width, leadingX),
                y: (rowHeight - trailingSize.height) / 2,
                width: trailingSize.width,
                height: trailingSize.height
            ).integral
        }

        let trailingReserved = trailingSize.width > 0 ? trailingSize.width + edgeSpacing : 0
        let available = max(rowWidth - leadingX - trailingReserved, 1)
        let handleIntrinsic = handleLabel.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: rowHeight)).width
        let nameIntrinsic = name.singleLineContentSize().width
        let handleWidth: CGFloat
        let nameWidth: CGFloat
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

        if assignFrames {
            let nameHeight = childHeight(of: name, for: max(nameWidth, 1))
            name.frame = CGRect(x: leadingX, y: (rowHeight - nameHeight) / 2, width: max(nameWidth, 1), height: nameHeight).integral
            handleLabel.frame = CGRect(
                x: name.frame.maxX + (handleWidth > 0 ? nameSpacing : 0),
                y: (rowHeight - (handleLabel.font?.lineHeight ?? rowHeight)) / 2,
                width: handleWidth,
                height: handleLabel.font?.lineHeight ?? rowHeight
            ).integral
        }

        return rowHeight
    }

    private func preferredTrailingSize(height: CGFloat) -> CGSize {
        guard let trailing = trailingView, !trailing.isHidden else { return .zero }
        let trailingHeight = max(childHeight(of: trailing, for: CGFloat.greatestFiniteMagnitude), 1)
        let trailingWidth = childWidth(of: trailing, for: max(height, trailingHeight))
        return CGSize(width: trailingWidth, height: trailingHeight)
    }
}

final class UserCompatUIView: UIStackView {
    let avatar = AvatarUIView()
    let name = RichTextUIView()
    let handleLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .caption1)
        l.textColor = .secondaryLabel
        l.adjustsFontForContentSizeCategory = true
        l.numberOfLines = 1
        return l
    }()

    private let column = UIStackView()
    private let flexibleSpace = UIView()
    private let trailingContainer = UIStackView()

    var onTapped: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        axis = .horizontal
        spacing = 8
        alignment = .top

        name.lineLimit = 1
        name.setContentHuggingPriority(.required, for: .horizontal)
        name.setContentCompressionResistancePriority(UILayoutPriority(900), for: .horizontal)
        handleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        handleLabel.setContentCompressionResistancePriority(UILayoutPriority(100), for: .horizontal)

        NSLayoutConstraint.activate([
            avatar.widthAnchor.constraint(equalToConstant: 44),
            avatar.heightAnchor.constraint(equalToConstant: 44),
        ])
        addArrangedSubview(avatar)

        column.axis = .vertical
        column.alignment = .leading
        column.spacing = 2
        column.setContentHuggingPriority(.required, for: .horizontal)
        column.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        column.addArrangedSubview(name)
        column.addArrangedSubview(handleLabel)
        addArrangedSubview(column)

        flexibleSpace.setContentHuggingPriority(.defaultLow, for: .horizontal)
        flexibleSpace.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        addArrangedSubview(flexibleSpace)

        trailingContainer.axis = .horizontal
        trailingContainer.spacing = 8
        trailingContainer.alignment = .top
        trailingContainer.setContentHuggingPriority(.required, for: .horizontal)
        trailingContainer.setContentCompressionResistancePriority(.required, for: .horizontal)
        addArrangedSubview(trailingContainer)

        let avatarTap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        avatar.isUserInteractionEnabled = true
        avatar.addGestureRecognizer(avatarTap)

        let colTap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        column.isUserInteractionEnabled = true
        column.addGestureRecognizer(colTap)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiProfile, trailing: UIView?, onClicked: (() -> Void)?) {
        avatar.set(url: data.avatar)
        name.text = data.name
        handleLabel.text = data.handle.canonical
        if let trailing = trailing {
            trailing.setContentHuggingPriority(.required, for: .horizontal)
            trailing.setContentCompressionResistancePriority(.required, for: .horizontal)
            trailingContainer.flareSyncArrangedSubviews([trailing])
        } else {
            trailingContainer.flareSyncArrangedSubviews([])
        }
        onTapped = onClicked
    }

    @objc private func onTapFired() { onTapped?() }
}

// MARK: - StatusTopEndView
// Mirrors `topEndContent` in StatusView.swift — visibility + translation state
// + optional platform logo + timestamp in an HStack.
final class StatusTopEndView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let visibility = StatusVisibilityImageView()
    private let translation = TranslateStatusStateView()
    private let platformLogo = UIImageView()
    private let time = DateTimeUILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setContentHuggingPriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .horizontal)
        platformLogo.contentMode = .scaleAspectFit
        platformLogo.tintColor = .secondaryLabel
        platformLogo.setContentHuggingPriority(.required, for: .horizontal)
        platformLogo.setContentCompressionResistancePriority(.required, for: .horizontal)
        translation.setContentHuggingPriority(.required, for: .horizontal)
        translation.setContentCompressionResistancePriority(.required, for: .horizontal)
        addSubview(visibility)
        addSubview(translation)
        addSubview(platformLogo)
        addSubview(time)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        layout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let preferred = preferredSize()
        let width = size.width.isFinite && size.width < CGFloat.greatestFiniteMagnitude / 2 ? size.width : preferred.width
        return CGSize(width: width, height: preferred.height)
    }

    override var intrinsicContentSize: CGSize {
        preferredSize()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width.isFinite else { return nil }
        return preferredSingleLineHeight()
    }

    private func preferredSingleLineHeight() -> CGFloat {
        var height: CGFloat = 0
        if !visibility.isHidden {
            height = max(height, 14)
        }
        if !translation.isHidden {
            height = max(height, 16)
        }
        if !platformLogo.isHidden {
            height = max(height, 14)
        }
        if !time.isHidden {
            height = max(height, time.font?.lineHeight ?? 0)
        }
        return ceil(height)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width > 0 && targetSize.width.isFinite
            ? targetSize.width
            : preferredSize().width
        return CGSize(width: horizontalFittingPriority == .required ? width : UIView.noIntrinsicMetric,
                      height: preferredSingleLineHeight())
    }

    func configure(
        post: UiTimelineV2.Post,
        showPlatformLogo: Bool,
        absoluteTimestamp: Bool,
        isDetail: Bool
    ) {
        if let v = post.visibility {
            visibility.isHidden = false
            visibility.set(visibility: v)
        } else {
            visibility.isHidden = true
        }

        if post.translationDisplayState != .hidden {
            translation.isHidden = false
            translation.set(state: post.translationDisplayState)
        } else {
            translation.isHidden = true
        }

        if showPlatformLogo {
            platformLogo.isHidden = false
            let name: String
            switch post.platformType {
            case .mastodon: name = "fa-mastodon"
            case .misskey:  name = "fa-misskey"
            case .bluesky:  name = "fa-bluesky"
            case .xQt:      name = "fa-x-twitter"
            case .vvo:      name = "fa-weibo"
            case .nostr:    name = "fa-nostr"
            default:        name = ""
            }
            platformLogo.image = UIImage(named: name)
        } else {
            platformLogo.isHidden = true
        }

        if isDetail {
            time.isHidden = true
        } else {
            time.isHidden = false
            time.absoluteTimestamp = absoluteTimestamp
            time.fullTime = false
            time.set(data: post.createdAt)
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let height = preferredSingleLineHeight()
        let items = visibleItems()
        var x: CGFloat = 0
        for (index, item) in items.enumerated() {
            let size = item.size
            if assignFrames {
                item.view.frame = CGRect(x: x, y: (height - size.height) / 2, width: size.width, height: size.height).integral
            }
            x += size.width
            if index < items.count - 1 {
                x += 8
            }
        }
        return height
    }

    private func preferredSize() -> CGSize {
        let items = visibleItems()
        let width = items.reduce(CGFloat(0)) { partial, item in
            partial + item.size.width
        } + CGFloat(max(items.count - 1, 0)) * 8
        return CGSize(width: ceil(width), height: preferredSingleLineHeight())
    }

    private func visibleItems() -> [(view: UIView, size: CGSize)] {
        var items: [(UIView, CGSize)] = []
        if !visibility.isHidden {
            items.append((visibility, CGSize(width: 14, height: 14)))
        }
        if !translation.isHidden {
            items.append((translation, translation.sizeThatFits(.zero)))
        }
        if !platformLogo.isHidden {
            items.append((platformLogo, CGSize(width: 14, height: 14)))
        }
        if !time.isHidden {
            let size = time.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
            items.append((time, CGSize(width: ceil(size.width), height: ceil(size.height))))
        }
        return items
    }
}
