import UIKit
import Kingfisher
import KotlinSharedUI

// MARK: - StatusReactionUIView
// Mirrors StatusReactionView.swift:
// - isDetail: wrapping layout (multiple lines)
// - otherwise: horizontal scroll view.
final class StatusReactionUIView: UIView {
    var onReactionTapped: ((UiTimelineV2.PostEmojiReaction) -> Void)?

    private let scroll = UIScrollView()
    private let scrollStack = UIStackView()
    private let wrap = WrappingStackView()

    private var isDetail: Bool = false
    private var chipPool: [ReactionChipView] = []

    override init(frame: CGRect) {
        super.init(frame: frame)

        scroll.showsHorizontalScrollIndicator = false
        scroll.showsVerticalScrollIndicator = false
        scroll.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(scrollStack)

        scrollStack.axis = .horizontal
        scrollStack.alignment = .center
        scrollStack.spacing = 8
        scrollStack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            scrollStack.topAnchor.constraint(equalTo: scroll.topAnchor),
            scrollStack.bottomAnchor.constraint(equalTo: scroll.bottomAnchor),
            scrollStack.leadingAnchor.constraint(equalTo: scroll.leadingAnchor),
            scrollStack.trailingAnchor.constraint(equalTo: scroll.trailingAnchor),
            scrollStack.heightAnchor.constraint(equalTo: scroll.heightAnchor),
        ])

        wrap.translatesAutoresizingMaskIntoConstraints = false

        addSubview(scroll)
        addSubview(wrap)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: topAnchor),
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor),
            scroll.heightAnchor.constraint(equalToConstant: 36),
            wrap.topAnchor.constraint(equalTo: topAnchor),
            wrap.leadingAnchor.constraint(equalTo: leadingAnchor),
            wrap.trailingAnchor.constraint(equalTo: trailingAnchor),
            wrap.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
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
            scrollStack.flareSyncArrangedSubviews([])
        } else {
            scrollStack.flareSyncArrangedSubviews(chips)
            wrap.setViews([])
        }
    }
}

private final class ReactionChipView: UIControl {
    var onTap: (() -> Void)?
    private let nameLabel = UILabel()
    private let countLabel = UILabel()
    private let imageView = UIImageView()
    private let stack = UIStackView()
    private var imageWidthConstraint: NSLayoutConstraint!
    private var imageHeightConstraint: NSLayoutConstraint!

    override init(frame: CGRect) {
        super.init(frame: frame)
        translatesAutoresizingMaskIntoConstraints = false
        layer.cornerRadius = 8
        layer.cornerCurve = .continuous
        clipsToBounds = true

        stack.axis = .horizontal
        stack.alignment = .center
        stack.spacing = 4
        stack.isUserInteractionEnabled = false
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)

        imageView.contentMode = .scaleAspectFit
        imageWidthConstraint = imageView.widthAnchor.constraint(equalToConstant: 20)
        imageHeightConstraint = imageView.heightAnchor.constraint(equalToConstant: 20)
        NSLayoutConstraint.activate([
            imageWidthConstraint,
            imageHeightConstraint,
        ])

        countLabel.font = .preferredFont(forTextStyle: .footnote)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            heightAnchor.constraint(equalToConstant: 36),
        ])
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
            nameLabel.text = item.name
            imageView.kf.cancelDownloadTask()
            imageView.image = nil
            stack.flareSyncArrangedSubviews([nameLabel, countLabel])
        } else {
            nameLabel.text = nil
            imageView.kf.cancelDownloadTask()
            imageView.image = nil
            if let url = URL(string: item.url) {
                imageView.kf.setImage(with: url)
            }
            stack.flareSyncArrangedSubviews([imageView, countLabel])
        }

        countLabel.text = item.count.humanized
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    @objc private func handleTap() { onTap?() }
}

// MARK: - WrappingStackView
// UIKit analogue of SwiftUI's WrappedHStack layout: lays out subviews left to
// right, wrapping to the next row when space runs out. Used in isDetail mode.
final class WrappingStackView: UIView {
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
            $0.translatesAutoresizingMaskIntoConstraints = true
            if $0.superview !== self {
                addSubview($0)
            }
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
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
            let s = v.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .defaultLow,
                verticalFittingPriority: .fittingSizeLevel
            )
            if x > 0, x + s.width > width {
                y += rowH + verticalSpacing
                x = 0
                rowH = 0
            }
            x += s.width + horizontalSpacing
            rowH = max(rowH, s.height)
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
            let s = v.systemLayoutSizeFitting(
                CGSize(width: bounds.width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .defaultLow,
                verticalFittingPriority: .fittingSizeLevel
            )
            if x > 0, x + s.width > bounds.width {
                y += rowH + verticalSpacing
                x = 0
                rowH = 0
            }
            v.frame = CGRect(x: x, y: y, width: s.width, height: s.height)
            x += s.width + horizontalSpacing
            rowH = max(rowH, s.height)
        }
        invalidateIntrinsicContentSize()
    }
}
