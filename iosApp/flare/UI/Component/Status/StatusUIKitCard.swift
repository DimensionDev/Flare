import UIKit
import KotlinSharedUI

// MARK: - StatusCardUIView
// Mirrors StatusCardView.swift: media on top, title (2 lines) + desc (2 lines)
// below, rounded border, tap opens URL.
final class StatusCardUIView: UIView {
    var onOpenURL: ((URL) -> Void)?

    private let column = UIStackView()
    private let mediaContainer = UIView()
    private let media = MediaUIView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let textColumn = UIStackView()

    private var data: UiCard?
    private var mediaHeight: NSLayoutConstraint?

    init(cornerRadius: CGFloat) {
        super.init(frame: .zero)
        clipsToBounds = true
        layer.cornerRadius = cornerRadius
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor

        column.axis = .vertical
        column.alignment = .fill
        column.spacing = 0
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        mediaContainer.clipsToBounds = true
        mediaContainer.addSubview(media)
        media.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            media.topAnchor.constraint(equalTo: mediaContainer.topAnchor),
            media.leadingAnchor.constraint(equalTo: mediaContainer.leadingAnchor),
            media.trailingAnchor.constraint(equalTo: mediaContainer.trailingAnchor),
            media.bottomAnchor.constraint(equalTo: mediaContainer.bottomAnchor),
        ])
        // Default 16:9 before data arrives.
        mediaHeight = mediaContainer.heightAnchor.constraint(equalTo: mediaContainer.widthAnchor, multiplier: 9.0 / 16.0)
        mediaHeight?.isActive = true

        titleLabel.numberOfLines = 2
        titleLabel.font = .preferredFont(forTextStyle: .body)
        titleLabel.adjustsFontForContentSizeCategory = true

        subtitleLabel.numberOfLines = 2
        subtitleLabel.font = .preferredFont(forTextStyle: .caption1)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.adjustsFontForContentSizeCategory = true

        textColumn.axis = .vertical
        textColumn.alignment = .fill
        textColumn.spacing = 4
        textColumn.addArrangedSubview(titleLabel)
        textColumn.addArrangedSubview(subtitleLabel)
        textColumn.isLayoutMarginsRelativeArrangement = true

        column.addArrangedSubview(mediaContainer)
        column.addArrangedSubview(textColumn)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiCard) {
        self.data = data

        if let m = data.media {
            mediaContainer.isHidden = false
            media.set(media: m, cornerRadius: 0)
            if let ratio = m.aspectRatio, ratio > 0 {
                mediaHeight?.isActive = false
                mediaHeight = mediaContainer.heightAnchor.constraint(
                    equalTo: mediaContainer.widthAnchor, multiplier: CGFloat(1.0 / Float(ratio))
                )
                mediaHeight?.isActive = true
            }
            // When there's media: horizontal 8 padding, bottom 8.
            textColumn.layoutMargins = UIEdgeInsets(top: 4, left: 8, bottom: 8, right: 8)
        } else {
            mediaContainer.isHidden = true
            // Without media: uniform 8 padding.
            textColumn.layoutMargins = UIEdgeInsets(top: 8, left: 8, bottom: 8, right: 8)
        }

        titleLabel.text = data.title
        if let desc = data.description_, !desc.isEmpty {
            subtitleLabel.text = desc
        } else {
            subtitleLabel.text = data.url
        }
    }

    @objc private func onTapFired() {
        guard let urlString = data?.url, let url = URL(string: urlString) else { return }
        onOpenURL?(url)
    }
}

// MARK: - StatusCompatCardUIView
// Mirrors StatusCompatCardView.swift: media on the left (72×72) + title/desc
// on the right, single-line text, rounded border, tap opens URL.
final class StatusCompatCardUIView: UIView {
    var onOpenURL: ((URL) -> Void)?

    private let row = UIStackView()
    private let media = MediaUIView()
    private let mediaWrap = UIView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let textColumn = UIStackView()

    private var data: UiCard?

    init(cornerRadius: CGFloat) {
        super.init(frame: .zero)
        clipsToBounds = true
        layer.cornerRadius = cornerRadius
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor

        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 8
        row.translatesAutoresizingMaskIntoConstraints = false
        addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        mediaWrap.clipsToBounds = true
        mediaWrap.addSubview(media)
        media.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            media.topAnchor.constraint(equalTo: mediaWrap.topAnchor),
            media.leadingAnchor.constraint(equalTo: mediaWrap.leadingAnchor),
            media.trailingAnchor.constraint(equalTo: mediaWrap.trailingAnchor),
            media.bottomAnchor.constraint(equalTo: mediaWrap.bottomAnchor),
            mediaWrap.widthAnchor.constraint(equalToConstant: 72),
            mediaWrap.heightAnchor.constraint(equalToConstant: 72),
        ])

        titleLabel.numberOfLines = 1
        titleLabel.font = .preferredFont(forTextStyle: .body)
        titleLabel.adjustsFontForContentSizeCategory = true

        subtitleLabel.numberOfLines = 1
        subtitleLabel.font = .preferredFont(forTextStyle: .caption1)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.adjustsFontForContentSizeCategory = true

        textColumn.axis = .vertical
        textColumn.alignment = .fill
        textColumn.spacing = 4
        textColumn.addArrangedSubview(titleLabel)
        textColumn.addArrangedSubview(subtitleLabel)
        textColumn.isLayoutMarginsRelativeArrangement = true

        row.addArrangedSubview(mediaWrap)
        row.addArrangedSubview(textColumn)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapFired))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiCard) {
        self.data = data
        if let m = data.media {
            mediaWrap.isHidden = false
            media.set(media: m, cornerRadius: 0)
            // With media: vertical + trailing padding.
            textColumn.layoutMargins = UIEdgeInsets(top: 12, left: 0, bottom: 12, right: 12)
        } else {
            mediaWrap.isHidden = true
            textColumn.layoutMargins = UIEdgeInsets(top: 12, left: 12, bottom: 12, right: 12)
        }
        titleLabel.text = data.title
        if let desc = data.description_, !desc.isEmpty {
            subtitleLabel.text = desc
        } else {
            subtitleLabel.text = data.url
        }
    }

    @objc private func onTapFired() {
        guard let urlString = data?.url, let url = URL(string: urlString) else { return }
        onOpenURL?(url)
    }
}
