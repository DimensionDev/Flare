import UIKit
import KotlinSharedUI

// MARK: - StatusCardUIView
// Mirrors StatusCardView.swift: media on top, title (2 lines) + desc (2 lines)
// below, rounded border, tap opens URL.
final class StatusCardUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?

    private let mediaContainer = UIView()
    private let media = MediaUIView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()

    private var data: UiCard?
    private var aspectRatio: CGFloat = 16.0 / 9.0
    private var hasMedia: Bool = false
    private var textInsets = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)

    init(cornerRadius: CGFloat) {
        super.init(frame: .zero)
        clipsToBounds = true
        layer.cornerRadius = cornerRadius
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor

        mediaContainer.clipsToBounds = true
        mediaContainer.addSubview(media)
        addSubview(mediaContainer)

        titleLabel.numberOfLines = 2
        titleLabel.font = .preferredFont(forTextStyle: .body)
        titleLabel.adjustsFontForContentSizeCategory = true
        addSubview(titleLabel)

        subtitleLabel.numberOfLines = 2
        subtitleLabel.font = .preferredFont(forTextStyle: .caption1)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.adjustsFontForContentSizeCategory = true
        addSubview(subtitleLabel)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiCard) {
        self.data = data

        if let m = data.media {
            hasMedia = true
            mediaContainer.isHidden = false
            media.set(media: m, cornerRadius: 0)
            if let ratio = m.aspectRatio, ratio > 0 {
                aspectRatio = CGFloat(ratio)
            } else {
                aspectRatio = 16.0 / 9.0
            }
            textInsets = UIEdgeInsets(top: 4, left: 8, bottom: 8, right: 8)
        } else {
            hasMedia = false
            mediaContainer.isHidden = true
            textInsets = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        }

        titleLabel.text = data.title
        if let desc = data.description_, !desc.isEmpty {
            subtitleLabel.text = desc
        } else {
            subtitleLabel.text = data.url
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        var y: CGFloat = 0

        if hasMedia {
            let mediaH = w / aspectRatio
            mediaContainer.frame = CGRect(x: 0, y: 0, width: w, height: mediaH)
            media.frame = mediaContainer.bounds
            y = mediaH
        }

        let textWidth = w - textInsets.left - textInsets.right
        y += textInsets.top
        let titleH = titleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 2
        ).height
        titleLabel.frame = CGRect(x: textInsets.left, y: y, width: textWidth, height: ceil(titleH))
        y += ceil(titleH) + 4

        let subtitleH = subtitleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 2
        ).height
        subtitleLabel.frame = CGRect(x: textInsets.left, y: y, width: textWidth, height: ceil(subtitleH))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let w = width
        var h: CGFloat = 0
        if hasMedia {
            h += w / aspectRatio
        }
        let textWidth = w - textInsets.left - textInsets.right
        h += textInsets.top
        let titleH = titleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 2
        ).height
        h += ceil(titleH) + 4
        let subtitleH = subtitleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 2
        ).height
        h += ceil(subtitleH) + textInsets.bottom
        return ceil(h)
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
        guard w != UIView.noIntrinsicMetric else { return CGSize(width: w, height: w) }
        return sizeThatFits(CGSize(width: w, height: .greatestFiniteMagnitude))
    }

    @objc private func onTapFired() {
        guard let urlString = data?.url, let url = URL(string: urlString) else { return }
        onOpenURL?(url)
    }
}

// MARK: - StatusCompatCardUIView
// Mirrors StatusCompatCardView.swift: media on the left (72×72) + title/desc
// on the right, single-line text, rounded border, tap opens URL.
final class StatusCompatCardUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?

    private let media = MediaUIView()
    private let mediaWrap = UIView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()

    private var data: UiCard?
    private var hasMedia: Bool = false
    private var textInsets = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
    private static let mediaSize: CGFloat = 72
    private static let mediaSpacing: CGFloat = 8
    private static let textSpacing: CGFloat = 4

    init(cornerRadius: CGFloat) {
        super.init(frame: .zero)
        clipsToBounds = true
        layer.cornerRadius = cornerRadius
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor

        mediaWrap.clipsToBounds = true
        mediaWrap.addSubview(media)
        addSubview(mediaWrap)

        titleLabel.numberOfLines = 1
        titleLabel.font = .preferredFont(forTextStyle: .body)
        titleLabel.adjustsFontForContentSizeCategory = true
        addSubview(titleLabel)

        subtitleLabel.numberOfLines = 1
        subtitleLabel.font = .preferredFont(forTextStyle: .caption1)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.adjustsFontForContentSizeCategory = true
        addSubview(subtitleLabel)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiCard) {
        self.data = data
        if let m = data.media {
            hasMedia = true
            mediaWrap.isHidden = false
            media.set(media: m, cornerRadius: 0)
            textInsets = UIEdgeInsets(top: 12, left: 0, bottom: 12, right: 12)
        } else {
            hasMedia = false
            mediaWrap.isHidden = true
            textInsets = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        }
        titleLabel.text = data.title
        if let desc = data.description_, !desc.isEmpty {
            subtitleLabel.text = desc
        } else {
            subtitleLabel.text = data.url
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let ms = Self.mediaSize
        var textX: CGFloat = 0

        if hasMedia {
            mediaWrap.frame = CGRect(x: 0, y: 0, width: ms, height: ms)
            media.frame = mediaWrap.bounds
            textX = ms + Self.mediaSpacing
        }

        let textWidth = w - textX - textInsets.left - textInsets.right
        let titleH = titleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 1
        ).height
        let subtitleH = subtitleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 1
        ).height
        let totalTextH = ceil(titleH) + Self.textSpacing + ceil(subtitleH)
        let rowH = hasMedia ? max(ms, totalTextH + textInsets.top + textInsets.bottom) : totalTextH + textInsets.top + textInsets.bottom
        let textY = (rowH - totalTextH) / 2

        titleLabel.frame = CGRect(x: textX + textInsets.left, y: textY, width: textWidth, height: ceil(titleH))
        subtitleLabel.frame = CGRect(x: textX + textInsets.left, y: textY + ceil(titleH) + Self.textSpacing, width: textWidth, height: ceil(subtitleH))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let w = width
        let ms = Self.mediaSize
        var textX: CGFloat = 0
        if hasMedia { textX = ms + Self.mediaSpacing }

        let textWidth = w - textX - textInsets.left - textInsets.right
        let titleH = titleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 1
        ).height
        let subtitleH = subtitleLabel.textRect(
            forBounds: CGRect(x: 0, y: 0, width: textWidth, height: .greatestFiniteMagnitude),
            limitedToNumberOfLines: 1
        ).height
        let totalTextH = ceil(titleH) + Self.textSpacing + ceil(subtitleH) + textInsets.top + textInsets.bottom
        let h = hasMedia ? max(ms, totalTextH) : totalTextH
        return ceil(h)
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
        guard w != UIView.noIntrinsicMetric else { return CGSize(width: w, height: w) }
        return sizeThatFits(CGSize(width: w, height: .greatestFiniteMagnitude))
    }

    @objc private func onTapFired() {
        guard let urlString = data?.url, let url = URL(string: urlString) else { return }
        onOpenURL?(url)
    }
}
