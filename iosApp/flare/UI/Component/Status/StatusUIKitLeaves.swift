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
            imageView.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage])
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
final class TranslateStatusStateView: UIStackView {
    private let langIcon = UIImageView(image: UIImage(named: "fa-language"))
    private let stateIcon = UIImageView()
    private let spinner = UIActivityIndicatorView(style: .medium)

    override init(frame: CGRect) {
        super.init(frame: frame)
        axis = .horizontal
        spacing = 4
        alignment = .center
        tintColor = .secondaryLabel
        langIcon.contentMode = .scaleAspectFit
        stateIcon.contentMode = .scaleAspectFit
        addArrangedSubview(langIcon)
        addArrangedSubview(stateIcon)
        addArrangedSubview(spinner)
        NSLayoutConstraint.activate([
            langIcon.widthAnchor.constraint(equalToConstant: 16),
            langIcon.heightAnchor.constraint(equalToConstant: 16),
            stateIcon.widthAnchor.constraint(equalToConstant: 12),
            stateIcon.heightAnchor.constraint(equalToConstant: 12),
            spinner.widthAnchor.constraint(equalToConstant: 12),
            spinner.heightAnchor.constraint(equalToConstant: 12),
        ])
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
}

// MARK: - UserOnelineUIView / UserCompatUIView
// Mirror UserOnelineView.swift / UserCompatView.swift.

final class UserOnelineUIView: UIStackView {
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
    private let nameHandleRow = UIStackView()
    private let flexibleSpace = UIView()
    private let trailingContainer = UIStackView()

    var onTapped: (() -> Void)?

    init(showAvatar: Bool) {
        super.init(frame: .zero)
        axis = .horizontal
        spacing = 8
        alignment = .center
        name.lineLimit = 1
        name.setContentHuggingPriority(.required, for: .horizontal)
        name.setContentCompressionResistancePriority(UILayoutPriority(900), for: .horizontal)
        handleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)
        handleLabel.setContentCompressionResistancePriority(UILayoutPriority(100), for: .horizontal)

        if showAvatar {
            addArrangedSubview(avatar)
            NSLayoutConstraint.activate([
                avatar.widthAnchor.constraint(equalToConstant: 20),
                avatar.heightAnchor.constraint(equalToConstant: 20),
            ])
            let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
            avatar.isUserInteractionEnabled = true
            avatar.addGestureRecognizer(tap)
        }

        nameHandleRow.axis = .horizontal
        nameHandleRow.alignment = .firstBaseline
        nameHandleRow.spacing = 4
        nameHandleRow.setContentHuggingPriority(.required, for: .horizontal)
        nameHandleRow.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        nameHandleRow.addArrangedSubview(name)
        nameHandleRow.addArrangedSubview(handleLabel)
        addArrangedSubview(nameHandleRow)

        flexibleSpace.setContentHuggingPriority(.defaultLow, for: .horizontal)
        flexibleSpace.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        addArrangedSubview(flexibleSpace)

        trailingContainer.axis = .horizontal
        trailingContainer.spacing = 8
        trailingContainer.alignment = .center
        trailingContainer.setContentHuggingPriority(.required, for: .horizontal)
        trailingContainer.setContentCompressionResistancePriority(.required, for: .horizontal)
        addArrangedSubview(trailingContainer)
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
        if onClicked != nil {
            let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
            nameHandleRow.isUserInteractionEnabled = true
            nameHandleRow.gestureRecognizers?.forEach { nameHandleRow.removeGestureRecognizer($0) }
            nameHandleRow.addGestureRecognizer(tap)
        }
    }

    @objc private func onTapFired() { onTapped?() }
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
final class StatusTopEndView: UIStackView {
    private let visibility = StatusVisibilityImageView()
    private let translation = TranslateStatusStateView()
    private let platformLogo = UIImageView()
    private let time = DateTimeUILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        axis = .horizontal
        spacing = 8
        alignment = .center
        setContentHuggingPriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .horizontal)
        platformLogo.contentMode = .scaleAspectFit
        platformLogo.tintColor = .secondaryLabel
        platformLogo.setContentHuggingPriority(.required, for: .horizontal)
        platformLogo.setContentCompressionResistancePriority(.required, for: .horizontal)
        translation.setContentHuggingPriority(.required, for: .horizontal)
        translation.setContentCompressionResistancePriority(.required, for: .horizontal)
        NSLayoutConstraint.activate([
            platformLogo.widthAnchor.constraint(equalToConstant: 14),
            platformLogo.heightAnchor.constraint(equalToConstant: 14),
            visibility.widthAnchor.constraint(equalToConstant: 14),
            visibility.heightAnchor.constraint(equalToConstant: 14),
        ])
        addArrangedSubview(visibility)
        addArrangedSubview(translation)
        addArrangedSubview(platformLogo)
        addArrangedSubview(time)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

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
    }
}
