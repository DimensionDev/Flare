import UIKit
import Kingfisher
import KotlinSharedUI

/// UIKit port of `FeedView`.
///
/// Layout mirrors the SwiftUI body exactly:
///   VStack {
///     HStack { icon · name · Spacer · translation · date }
///     Text(title)?
///     HStack { description (maxLines 5) · 72×72 media? }
///   }
final class FeedUIView: UIView {
    var onOpenURL: ((URL) -> Void)?

    private var data: UiTimelineV2.Feed?

    private let sourceIcon: UIImageView = {
        let v = UIImageView()
        v.contentMode = .scaleAspectFit
        v.clipsToBounds = true
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()
    private let sourceName: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .footnote)
        l.numberOfLines = 0
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
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    private let headerRow = UIStackView()
    private let bodyRow = UIStackView()
    private let stack = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        translatesAutoresizingMaskIntoConstraints = false

        headerRow.axis = .horizontal
        headerRow.alignment = .center
        headerRow.spacing = 8
        headerRow.addArrangedSubview(sourceIcon)
        headerRow.addArrangedSubview(sourceName)
        let spacer = UIView()
        spacer.setContentHuggingPriority(.defaultLow, for: .horizontal)
        headerRow.addArrangedSubview(spacer)
        headerRow.addArrangedSubview(translation)
        headerRow.addArrangedSubview(dateLabel)
        NSLayoutConstraint.activate([
            sourceIcon.widthAnchor.constraint(equalToConstant: 20),
            sourceIcon.heightAnchor.constraint(equalToConstant: 20),
        ])

        bodyRow.axis = .horizontal
        bodyRow.alignment = .top
        bodyRow.spacing = 8
        bodyRow.addArrangedSubview(descriptionLabel)
        bodyRow.addArrangedSubview(mediaView)
        NSLayoutConstraint.activate([
            mediaView.widthAnchor.constraint(equalToConstant: 72),
            mediaView.heightAnchor.constraint(equalToConstant: 72),
        ])

        stack.axis = .vertical
        stack.spacing = 8
        stack.alignment = .fill
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(headerRow)
        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(bodyRow)
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTap))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiTimelineV2.Feed) {
        self.data = data

        if let icon = data.source.icon, !icon.isEmpty, let url = URL(string: icon) {
            sourceIcon.isHidden = false
            sourceIcon.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage])
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
            mediaView.kf.setImage(with: url, options: [.transition(.fade(0.25)), .cacheOriginalImage])
        } else {
            mediaView.isHidden = true
            mediaView.image = nil
        }

        bodyRow.isHidden = descriptionLabel.isHidden && mediaView.isHidden
    }

    @objc private func onTap() {
        guard let data = data else { return }
        data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })))
    }
}

import SwiftUI // OpenURLAction
