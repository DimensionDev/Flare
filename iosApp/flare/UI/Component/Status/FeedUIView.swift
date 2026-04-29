import UIKit
import Kingfisher
import KotlinSharedUI
import SwiftUI

/// UIKit port of `FeedView`.
///
/// The rest of the UIKit timeline uses manual frame layout, so this view does
/// the same to keep the date and thumbnail anchored to the full timeline width.
final class FeedUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?

    private var data: UiTimelineV2.Feed?

    private static let spacing: CGFloat = 8
    private static let sourceIconSize: CGFloat = 20
    private static let mediaSize: CGFloat = 72

    private let sourceIcon: UIImageView = {
        let v = UIImageView()
        v.contentMode = .scaleAspectFit
        v.clipsToBounds = true
        return v
    }()

    private let sourceName: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .footnote)
        l.numberOfLines = 0
        l.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return l
    }()

    private let translation = TranslateStatusStateView()

    private let dateLabel: DateTimeUILabel = {
        let l = DateTimeUILabel()
        l.font = .preferredFont(forTextStyle: .footnote)
        l.textColor = .secondaryLabel
        return l
    }()

    private let titleLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .body)
        l.numberOfLines = 0
        return l
    }()

    private let descriptionLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .caption1)
        l.textColor = .secondaryLabel
        l.numberOfLines = 5
        l.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return l
    }()

    private let mediaView: UIImageView = {
        let v = UIImageView()
        v.contentMode = .scaleAspectFill
        v.clipsToBounds = true
        v.layer.cornerRadius = 8
        return v
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(sourceIcon)
        addSubview(sourceName)
        addSubview(translation)
        addSubview(dateLabel)
        addSubview(titleLabel)
        addSubview(descriptionLabel)
        addSubview(mediaView)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTap))
        addGestureRecognizer(tap)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        performLayout(width: bounds.width, assignFrames: true)
    }

    override var intrinsicContentSize: CGSize {
        guard bounds.width > 0 else {
            return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
        }
        return CGSize(width: UIView.noIntrinsicMetric, height: estimatedHeight(for: bounds.width))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let fittingWidth = targetSize.width > 0 ? targetSize.width : bounds.width
        guard fittingWidth > 0, fittingWidth.isFinite else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        return CGSize(width: fittingWidth, height: estimatedHeight(for: fittingWidth))
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return estimatedHeight(for: width)
    }

    func prepareForFitting(width: CGFloat) {
        guard width > 0, width.isFinite else { return }
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        setNeedsLayout()
    }

    func estimatedHeight(for width: CGFloat) -> CGFloat {
        guard width > 0, width.isFinite else { return 0 }
        return ceil(performLayout(width: width, assignFrames: false))
    }

    @discardableResult
    private func performLayout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard width > 0, width.isFinite else { return 0 }

        let header = headerMetrics(width: width)
        var y: CGFloat = 0

        if assignFrames {
            if !sourceIcon.isHidden {
                sourceIcon.frame = CGRect(
                    x: 0,
                    y: y + (header.height - Self.sourceIconSize) / 2,
                    width: Self.sourceIconSize,
                    height: Self.sourceIconSize
                )
            }
            sourceName.frame = CGRect(
                x: header.sourceNameX,
                y: y + (header.height - header.sourceNameHeight) / 2,
                width: header.sourceNameWidth,
                height: header.sourceNameHeight
            )
            if !translation.isHidden {
                translation.frame = CGRect(
                    x: header.translationX,
                    y: y + (header.height - header.translationSize.height) / 2,
                    width: header.translationSize.width,
                    height: header.translationSize.height
                )
            }
            if !dateLabel.isHidden {
                dateLabel.frame = CGRect(
                    x: header.dateX,
                    y: y + (header.height - header.dateSize.height) / 2,
                    width: header.dateSize.width,
                    height: header.dateSize.height
                )
            }
        }
        y += header.height

        if !titleLabel.isHidden {
            y += Self.spacing
            let titleHeight = estimatedLabelHeight(titleLabel, width: width)
            if assignFrames {
                titleLabel.frame = CGRect(x: 0, y: y, width: width, height: titleHeight)
            }
            y += titleHeight
        }

        if hasVisibleBody {
            y += Self.spacing
            let descriptionWidth = bodyDescriptionWidth(for: width)
            let descriptionHeight = descriptionLabel.isHidden
                ? 0
                : estimatedLabelHeight(descriptionLabel, width: descriptionWidth)
            let bodyHeight = max(descriptionHeight, mediaView.isHidden ? 0 : Self.mediaSize)

            if assignFrames {
                if !descriptionLabel.isHidden {
                    descriptionLabel.frame = CGRect(
                        x: 0,
                        y: y,
                        width: descriptionWidth,
                        height: descriptionHeight
                    )
                }
                if !mediaView.isHidden {
                    mediaView.frame = CGRect(
                        x: max(width - Self.mediaSize, 0),
                        y: y,
                        width: Self.mediaSize,
                        height: Self.mediaSize
                    )
                }
            }
            y += bodyHeight
        }

        return ceil(y)
    }

    private var hasVisibleBody: Bool {
        !descriptionLabel.isHidden || !mediaView.isHidden
    }

    private struct HeaderMetrics {
        let height: CGFloat
        let sourceNameX: CGFloat
        let sourceNameWidth: CGFloat
        let sourceNameHeight: CGFloat
        let translationX: CGFloat
        let translationSize: CGSize
        let dateX: CGFloat
        let dateSize: CGSize
    }

    private func headerMetrics(width: CGFloat) -> HeaderMetrics {
        var leadingX: CGFloat = 0
        if !sourceIcon.isHidden {
            leadingX += Self.sourceIconSize + Self.spacing
        }

        let dateSize = dateLabel.isHidden ? .zero : dateLabel.intrinsicContentSize.ceilPositive()
        let translationSize = translation.isHidden
            ? .zero
            : translation.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize).ceilPositive()

        var trailingX = width
        let dateX: CGFloat
        if dateSize.width > 0 {
            trailingX -= dateSize.width
            dateX = trailingX
        } else {
            dateX = width
        }

        let translationX: CGFloat
        if translationSize.width > 0 {
            if dateSize.width > 0 {
                trailingX -= Self.spacing
            }
            trailingX -= translationSize.width
            translationX = trailingX
        } else {
            translationX = trailingX
        }

        let hasTrailingContent = dateSize.width > 0 || translationSize.width > 0
        let trailingSpacing = hasTrailingContent ? Self.spacing : 0
        let sourceNameWidth = max(trailingX - leadingX - trailingSpacing, 1)
        let sourceNameHeight = estimatedLabelHeight(sourceName, width: sourceNameWidth)
        let height = max(
            sourceIcon.isHidden ? 0 : Self.sourceIconSize,
            sourceNameHeight,
            translationSize.height,
            dateSize.height
        )

        return HeaderMetrics(
            height: ceil(height),
            sourceNameX: leadingX,
            sourceNameWidth: sourceNameWidth,
            sourceNameHeight: sourceNameHeight,
            translationX: translationX,
            translationSize: translationSize,
            dateX: dateX,
            dateSize: dateSize
        )
    }

    private func bodyDescriptionWidth(for width: CGFloat) -> CGFloat {
        if mediaView.isHidden {
            return width
        }
        return max(width - Self.mediaSize - Self.spacing, 1)
    }

    private func estimatedLabelHeight(_ label: UILabel, width: CGFloat) -> CGFloat {
        guard !label.isHidden, width > 0, width.isFinite, label.text?.isEmpty == false else { return 0 }
        label.preferredMaxLayoutWidth = width
        let size = label.sizeThatFits(
            CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)
        )
        if label.numberOfLines > 0 {
            let lineHeight = label.font.lineHeight
            return ceil(min(size.height, lineHeight * CGFloat(label.numberOfLines)))
        }
        return ceil(size.height)
    }

    func configure(data: UiTimelineV2.Feed) {
        self.data = data

        if let icon = data.source.icon, !icon.isEmpty, let url = URL(string: icon) {
            sourceIcon.isHidden = false
            sourceIcon.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage, .backgroundDecode])
        } else {
            sourceIcon.isHidden = true
            sourceIcon.image = nil
        }
        sourceName.text = data.source.name

        if data.translationDisplayState != .hidden {
            translation.isHidden = false
            translation.set(state: data.translationDisplayState)
        } else {
            translation.isHidden = true
        }

        if let date = data.actualCreatedAt {
            dateLabel.isHidden = false
            dateLabel.set(data: date)
        } else {
            dateLabel.isHidden = true
        }

        if let title = data.title, !title.isEmpty {
            titleLabel.isHidden = false
            titleLabel.text = title
        } else {
            titleLabel.isHidden = true
            titleLabel.text = nil
        }

        let description = data.description_ ?? data.description
        if !description.isEmpty {
            descriptionLabel.isHidden = false
            descriptionLabel.text = description
        } else {
            descriptionLabel.isHidden = true
            descriptionLabel.text = nil
        }

        if let media = data.media, let url = URL(string: media.url) {
            mediaView.isHidden = false
            mediaView.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage, .backgroundDecode])
        } else {
            mediaView.isHidden = true
            mediaView.image = nil
        }

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    @objc private func onTap() {
        guard let data = data else { return }
        data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })))
    }
}

private extension CGSize {
    func ceilPositive() -> CGSize {
        CGSize(width: ceil(max(width, 0)), height: ceil(max(height, 0)))
    }
}
