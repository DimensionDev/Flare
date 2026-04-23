import UIKit
import Kingfisher
import KotlinSharedUI

// MARK: - StatusReactionUIView
// Mirrors StatusReactionView.swift:
// - isDetail: wrapping layout (multiple lines)
// - otherwise: horizontal scroll view.
final class StatusReactionUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onReactionTapped: ((UiTimelineV2.PostEmojiReaction) -> Void)?

    private let scroll = UIScrollView()
    private let wrap = WrappingStackView()

    private var isDetail: Bool = false
    private var chipPool: [ReactionChipView] = []
    private var scrollChips: [UIView] = []
    private static let chipHeight: CGFloat = 36
    private static let chipSpacing: CGFloat = 8

    override init(frame: CGRect) {
        super.init(frame: frame)

        scroll.showsHorizontalScrollIndicator = false
        scroll.showsVerticalScrollIndicator = false
        addSubview(scroll)
        addSubview(wrap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: [UiTimelineV2.PostEmojiReaction], isDetail: Bool) {
        self.isDetail = isDetail
        scroll.isHidden = isDetail
        wrap.isHidden = !isDetail

        while chipPool.count < data.count {
            chipPool.append(ReactionChipView())
        }
        let chips: [UIView] = data.enumerated().map { index, item in
            let chip = chipPool[index]
            chip.configure(item: item)
            chip.onTap = { [weak self] in self?.onReactionTapped?(item) }
            return chip
        }

        if isDetail {
            wrap.setViews(chips)
            // Remove scroll chips
            for chip in scrollChips { chip.removeFromSuperview() }
            scrollChips = []
        } else {
            syncManagedSubviews(parent: scroll, current: &scrollChips, desired: chips)
            wrap.setViews([])
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        if isDetail {
            wrap.frame = bounds
        } else {
            scroll.frame = CGRect(x: 0, y: 0, width: w, height: Self.chipHeight)
            // Layout chips horizontally inside scroll
            var x: CGFloat = 0
            for chip in scrollChips {
                let chipSize = chip.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: Self.chipHeight))
                chip.frame = CGRect(x: x, y: 0, width: ceil(chipSize.width), height: Self.chipHeight)
                x += ceil(chipSize.width) + Self.chipSpacing
            }
            scroll.contentSize = CGSize(width: max(x - Self.chipSpacing, 0), height: Self.chipHeight)
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        if isDetail {
            return wrap.timelineHeight(for: width)
        } else {
            return Self.chipHeight
        }
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }

    override var intrinsicContentSize: CGSize {
        let w = bounds.width > 0 ? bounds.width : UIView.noIntrinsicMetric
        guard w != UIView.noIntrinsicMetric else { return CGSize(width: w, height: Self.chipHeight) }
        return sizeThatFits(CGSize(width: w, height: .greatestFiniteMagnitude))
    }
}

private final class ReactionChipView: UIControl, ManualLayoutMeasurable, TimelineHeightProviding {
    var onTap: (() -> Void)?
    private let nameLabel = UILabel()
    private let countLabel = UILabel()
    private let imageView = UIImageView()
    private var showsImage: Bool = false
    private static let hPadding: CGFloat = 8
    private static let vPadding: CGFloat = 4
    private static let spacing: CGFloat = 4
    private static let imageSize: CGFloat = 20
    private static let totalHeight: CGFloat = 36

    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = 8
        layer.cornerCurve = .continuous
        clipsToBounds = true

        imageView.contentMode = .scaleAspectFit
        imageView.isUserInteractionEnabled = false
        addSubview(imageView)

        nameLabel.isUserInteractionEnabled = false
        addSubview(nameLabel)

        countLabel.font = .preferredFont(forTextStyle: .footnote)
        countLabel.isUserInteractionEnabled = false
        addSubview(countLabel)

        addTarget(self, action: #selector(handleTap), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(item: UiTimelineV2.PostEmojiReaction) {
        if item.me {
            backgroundColor = .tintColor
            nameLabel.textColor = .white
            countLabel.textColor = .white
        } else {
            backgroundColor = .secondarySystemBackground
            nameLabel.textColor = .label
            countLabel.textColor = .label
        }

        if item.isUnicode {
            showsImage = false
            nameLabel.text = item.name
            nameLabel.isHidden = false
            imageView.kf.cancelDownloadTask()
            imageView.image = nil
            imageView.isHidden = true
        } else {
            showsImage = true
            nameLabel.text = nil
            nameLabel.isHidden = true
            imageView.isHidden = false
            imageView.kf.cancelDownloadTask()
            imageView.image = nil
            if let url = URL(string: item.url) {
                imageView.kf.setImage(with: url)
            }
        }

        countLabel.text = item.count.humanized
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let h = Self.totalHeight
        var x = Self.hPadding

        if showsImage {
            let imgY = (h - Self.imageSize) / 2
            imageView.frame = CGRect(x: x, y: imgY, width: Self.imageSize, height: Self.imageSize)
            x += Self.imageSize + Self.spacing
        } else if !nameLabel.isHidden {
            let labelSize = nameLabel.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: h))
            let labelY = (h - labelSize.height) / 2
            nameLabel.frame = CGRect(x: x, y: labelY, width: ceil(labelSize.width), height: ceil(labelSize.height))
            x += ceil(labelSize.width) + Self.spacing
        }

        let countSize = countLabel.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: h))
        let countY = (h - countSize.height) / 2
        countLabel.frame = CGRect(x: x, y: countY, width: ceil(countSize.width), height: ceil(countSize.height))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let width = timelineWidth()
        return CGSize(width: width, height: Self.totalHeight)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        Self.totalHeight
    }

    private func timelineWidth() -> CGFloat {
        var w = Self.hPadding
        if showsImage {
            w += Self.imageSize + Self.spacing
        } else if nameLabel.text != nil {
            let labelSize = nameLabel.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: Self.totalHeight))
            w += ceil(labelSize.width) + Self.spacing
        }
        let countSize = countLabel.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: Self.totalHeight))
        w += ceil(countSize.width) + Self.hPadding
        return ceil(w)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(targetSize)
    }

    override var intrinsicContentSize: CGSize {
        sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: Self.totalHeight))
    }

    @objc private func handleTap() { onTap?() }
}

// MARK: - WrappingStackView
// UIKit analogue of SwiftUI's WrappedHStack layout: lays out subviews left to
// right, wrapping to the next row when space runs out. Used in isDetail mode.
final class WrappingStackView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var horizontalSpacing: CGFloat = 8
    var verticalSpacing: CGFloat = 8

    private var managed: [UIView] = []

    func setViews(_ views: [UIView]) {
        let desiredIDs = Set(views.map { ObjectIdentifier($0) })
        for view in managed where !desiredIDs.contains(ObjectIdentifier(view)) {
            if view.superview === self {
                view.removeFromSuperview()
            }
        }
        managed = views
        managed.forEach {
            if $0.superview !== self {
                addSubview($0)
            }
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let w = size.width > 0 ? size.width : bounds.width
        return CGSize(width: w, height: timelineHeight(for: w) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return computeHeight(for: width)
    }

    override var intrinsicContentSize: CGSize {
        let w = bounds.width > 0 ? bounds.width : UIView.noIntrinsicMetric
        return CGSize(width: UIView.noIntrinsicMetric, height: computeHeight(for: w))
    }

    private func computeHeight(for width: CGFloat) -> CGFloat {
        guard !managed.isEmpty, width > 0 else { return 0 }
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowH: CGFloat = 0
        for v in managed {
            let s = v.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
            let sw = s.width > 0 ? s.width : childWidth(of: v, for: s.height)
            let sh = s.height > 0 ? s.height : childHeight(of: v, for: width)
            if x > 0, x + sw > width {
                y += rowH + verticalSpacing
                x = 0
                rowH = 0
            }
            x += sw + horizontalSpacing
            rowH = max(rowH, sh)
        }
        y += rowH
        return y
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowH: CGFloat = 0
        for v in managed {
            let s = v.sizeThatFits(CGSize(width: bounds.width, height: .greatestFiniteMagnitude))
            let sw = s.width > 0 ? s.width : childWidth(of: v, for: s.height)
            let sh = s.height > 0 ? s.height : childHeight(of: v, for: bounds.width)
            if x > 0, x + sw > bounds.width {
                y += rowH + verticalSpacing
                x = 0
                rowH = 0
            }
            v.frame = CGRect(x: x, y: y, width: sw, height: sh)
            x += sw + horizontalSpacing
            rowH = max(rowH, sh)
        }
        invalidateIntrinsicContentSize()
    }
}
