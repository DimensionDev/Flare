import UIKit
import Kingfisher
import KotlinSharedUI

final class StatusReactionUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onReactionTapped: ((UiTimelineV2.PostEmojiReaction) -> Void)?
    var onLocalHeightInvalidated: (() -> Void)?

    private var isDetail = false
    private var expanded = false
    private var chipPool: [ReactionChipView] = []
    private var chips: [UIView] = []
    private let overflowButton = UIButton(type: .custom)

    private static let spacing: CGFloat = 8
    private static let compactMaxLines = 2

    private struct Item {
        let view: UIView
        let size: CGSize
    }

    private struct Row {
        var items: [Item] = []
        var width: CGFloat = 0
        var height: CGFloat = 0

        mutating func append(_ item: Item) {
            if !items.isEmpty {
                width += StatusReactionUIView.spacing
            }
            items.append(item)
            width += item.size.width
            height = max(height, item.size.height)
        }

        mutating func removeLast() {
            let item = items.removeLast()
            width -= item.size.width
            if !items.isEmpty {
                width -= StatusReactionUIView.spacing
            }
            height = items.map(\.size.height).max() ?? 0
        }

        func canFit(_ item: Item, width: CGFloat) -> Bool {
            items.isEmpty || self.width + StatusReactionUIView.spacing + item.size.width <= width
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)

        overflowButton.setTitle(String(localized: "mastodon_item_show_more"), for: .normal)
        overflowButton.setTitleColor(.secondaryLabel, for: .normal)
        overflowButton.titleLabel?.font = .preferredFont(forTextStyle: .caption1)
        overflowButton.titleLabel?.adjustsFontForContentSizeCategory = true
        overflowButton.contentHorizontalAlignment = .leading
        overflowButton.addTarget(self, action: #selector(expand), for: .primaryActionTriggered)
        overflowButton.isHidden = true
        addSubview(overflowButton)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: [UiTimelineV2.PostEmojiReaction], isDetail: Bool) {
        if self.isDetail != isDetail {
            expanded = false
        }
        self.isDetail = isDetail

        while chipPool.count < data.count {
            chipPool.append(ReactionChipView())
        }
        let desired: [UIView] = data.enumerated().map { index, item in
            let chip = chipPool[index]
            chip.onImageSizeChanged = { [weak self] in
                self?.setNeedsHeightUpdate()
                self?.onLocalHeightInvalidated?()
            }
            chip.configure(item: item)
            chip.onTap = { [weak self] in self?.onReactionTapped?(item) }
            return chip
        }
        syncManagedSubviews(parent: self, current: &chips, desired: desired)
        setNeedsHeightUpdate()
    }

    func resetExpansion() {
        expanded = false
        setNeedsHeightUpdate()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        _ = performLayout(width: bounds.width, assignFrames: true)
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
        sizeThatFits(CGSize(width: targetSize.width, height: CGFloat.greatestFiniteMagnitude))
    }

    override var intrinsicContentSize: CGSize {
        let width = bounds.width > 0 ? bounds.width : UIView.noIntrinsicMetric
        guard width != UIView.noIntrinsicMetric else {
            return CGSize(width: width, height: 0)
        }
        return sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude))
    }

    private func performLayout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let rows = makeRows(width: width)
        if assignFrames {
            chips.forEach { $0.isHidden = true }
            overflowButton.isHidden = true
        }

        var rowY = CGFloat.zero
        for row in rows {
            var leading = CGFloat.zero
            for item in row.items {
                if assignFrames {
                    let itemX = if effectiveUserInterfaceLayoutDirection == .rightToLeft {
                        width - leading - item.size.width
                    } else {
                        leading
                    }
                    item.view.isHidden = false
                    item.view.frame = CGRect(
                        x: itemX,
                        y: rowY + (row.height - item.size.height) / 2,
                        width: item.size.width,
                        height: item.size.height
                    )
                }
                leading += item.size.width + Self.spacing
            }
            rowY += row.height + Self.spacing
        }
        return max(rowY - Self.spacing, 0)
    }

    private func makeRows(width: CGFloat) -> [Row] {
        let maxLines = isDetail || expanded ? Int.max : Self.compactMaxLines
        let content = chips.map {
            Item(
                view: $0,
                size: $0.sizeThatFits(UIView.layoutFittingExpandedSize)
            )
        }
        var rows: [Row] = []
        var placedCount = 0

        for item in content {
            if rows.isEmpty {
                rows.append(Row())
            }
            if !rows[rows.count - 1].canFit(item, width: width) {
                guard rows.count < maxLines else { break }
                rows.append(Row())
            }
            rows[rows.count - 1].append(item)
            placedCount += 1
        }

        if placedCount < content.count {
            let overflow = Item(
                view: overflowButton,
                size: overflowButton.sizeThatFits(UIView.layoutFittingExpandedSize)
            )
            while let last = rows.indices.last,
                  !rows[last].canFit(overflow, width: width),
                  !rows[last].items.isEmpty {
                rows[last].removeLast()
            }
            if rows.isEmpty {
                rows.append(Row())
            }
            rows[rows.count - 1].append(overflow)
        }

        assert(rows.count <= maxLines)
        return rows
    }

    private func setNeedsHeightUpdate() {
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        superview?.setNeedsLayout()
    }

    @objc private func expand() {
        expanded = true
        setNeedsHeightUpdate()
        onLocalHeightInvalidated?()
    }
}

private final class ReactionChipView: UIControl, ManualLayoutMeasurable, TimelineHeightProviding {
    var onTap: (() -> Void)?
    var onImageSizeChanged: (() -> Void)?

    private let nameLabel = UILabel()
    private let countLabel = UILabel()
    private let imageView = AnimatedImageView()
    private var traitRegistration: UITraitChangeRegistration?
    private var showsImage = false
    private var isMyReaction = false
    private var imageURL: URL?
    private var imageWidth: CGFloat = 0

    private static let horizontalPadding: CGFloat = 8
    private static let verticalPadding: CGFloat = 4
    private static let spacing: CGFloat = 4
    private static let imageHeight: CGFloat = 16
    private static let borderWidth: CGFloat = 0.8

    override init(frame: CGRect) {
        super.init(frame: frame)

        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = false
        imageView.needsPrescaling = false
        imageView.isUserInteractionEnabled = false
        addSubview(imageView)

        nameLabel.font = .preferredFont(forTextStyle: .body)
        nameLabel.adjustsFontForContentSizeCategory = true
        nameLabel.numberOfLines = 1
        nameLabel.isUserInteractionEnabled = false
        addSubview(nameLabel)

        countLabel.font = .preferredFont(forTextStyle: .body)
        countLabel.adjustsFontForContentSizeCategory = true
        countLabel.numberOfLines = 1
        countLabel.isUserInteractionEnabled = false
        addSubview(countLabel)

        traitRegistration = registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: ReactionChipView, _) in
            view.updateColors()
        }
        isAccessibilityElement = true
        addTarget(self, action: #selector(handleTap), for: .primaryActionTriggered)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    override var isHighlighted: Bool {
        didSet {
            alpha = isHighlighted ? 0.65 : 1
        }
    }

    func configure(item: UiTimelineV2.PostEmojiReaction) {
        isMyReaction = item.me
        showsImage = !item.isUnicode

        if item.isUnicode {
            imageURL = nil
            imageWidth = 0
            nameLabel.text = item.name
            nameLabel.isHidden = false
            imageView.kf.cancelDownloadTask()
            imageView.image = nil
            imageView.isHidden = true
        } else {
            nameLabel.text = nil
            nameLabel.isHidden = true
            imageView.isHidden = false
            let url = URL(string: item.url)
            if imageURL != url {
                imageURL = url
                imageWidth = 0
                imageView.kf.cancelDownloadTask()
                imageView.image = nil
            }
            if let url, imageView.image == nil {
                imageView.kf.setImage(with: url, options: [.backgroundDecode]) { [weak self] result in
                    guard let self, self.imageURL == url,
                          case .success(let value) = result else { return }
                    self.updateImageWidth(for: value.image)
                }
            }
        }

        countLabel.text = item.count.humanized
        accessibilityLabel = item.name
        accessibilityValue = item.count.humanized
        accessibilityTraits = item.me ? [.button, .selected] : .button
        updateColors()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func tintColorDidChange() {
        super.tintColorDidChange()
        updateColors()
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        layer.cornerRadius = bounds.height / 2
        var contentX = Self.horizontalPadding
        if showsImage {
            imageView.frame = CGRect(
                x: contentX,
                y: (bounds.height - Self.imageHeight) / 2,
                width: imageWidth,
                height: Self.imageHeight
            )
            contentX += imageWidth + Self.spacing
        } else {
            let size = nameSize
            nameLabel.frame = CGRect(
                x: contentX,
                y: (bounds.height - size.height) / 2,
                width: size.width,
                height: size.height
            )
            contentX += size.width + Self.spacing
        }

        let size = countSize
        countLabel.frame = CGRect(
            x: contentX,
            y: (bounds.height - size.height) / 2,
            width: size.width,
            height: size.height
        )
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: timelineWidth, height: chipHeight)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        chipHeight
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(targetSize)
    }

    override var intrinsicContentSize: CGSize {
        sizeThatFits(UIView.layoutFittingExpandedSize)
    }

    private var nameSize: CGSize {
        let size = nameLabel.sizeThatFits(UIView.layoutFittingExpandedSize)
        return CGSize(width: ceil(size.width), height: ceil(size.height))
    }

    private var countSize: CGSize {
        let size = countLabel.sizeThatFits(UIView.layoutFittingExpandedSize)
        return CGSize(width: ceil(size.width), height: ceil(size.height))
    }

    private var chipHeight: CGFloat {
        ceil(
            max(showsImage ? Self.imageHeight : nameSize.height, countSize.height)
                + Self.verticalPadding * 2
        )
    }

    private var timelineWidth: CGFloat {
        let leadingWidth = showsImage ? imageWidth : nameSize.width
        return ceil(
            Self.horizontalPadding
                + leadingWidth
                + Self.spacing
                + countSize.width
                + Self.horizontalPadding
        )
    }

    private func updateImageWidth(for image: UIImage) {
        guard image.size.height > 0 else { return }
        let width = Self.imageHeight * image.size.width / image.size.height
        guard width.isFinite, width > 0, width != imageWidth else { return }
        imageWidth = width
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        onImageSizeChanged?()
    }

    private func updateColors() {
        nameLabel.textColor = .label
        countLabel.textColor = .label
        backgroundColor = isMyReaction
            ? tintColor.withAlphaComponent(0.18)
            : .tertiarySystemGroupedBackground
        layer.borderWidth = isMyReaction ? Self.borderWidth : 0
        layer.borderColor = isMyReaction ? tintColor.cgColor : UIColor.clear.cgColor
    }

    @objc private func handleTap() {
        onTap?()
    }
}
