import UIKit
import KotlinSharedUI

/// UIKit port of StatusTopMessageView — HStack of icon + user name (rich text)
/// + localized message text, with a tap gesture.
final class StatusTopMessageUIView: UIStackView {
    var onOpenURL: ((URL) -> Void)? {
        didSet {
            nameView.onOpenURL = onOpenURL
        }
    }

    private let iconView: UIImageView = {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFit
        iv.tintColor = .secondaryLabel
        iv.setContentHuggingPriority(.required, for: .horizontal)
        iv.setContentCompressionResistancePriority(.required, for: .horizontal)
        return iv
    }()
    private let nameView = RichTextUIView()
    private let textLabel: UILabel = {
        let l = UILabel()
        l.numberOfLines = 0
        l.adjustsFontForContentSizeCategory = true
        l.setContentHuggingPriority(.defaultLow, for: .horizontal)
        l.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return l
    }()

    private var topMessage: UiTimelineV2.Message?

    override init(frame: CGRect) {
        super.init(frame: frame)
        axis = .horizontal
        alignment = .center
        spacing = 8
        nameView.setContentHuggingPriority(.required, for: .horizontal)
        nameView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)

        addArrangedSubview(iconView)
        addArrangedSubview(nameView)
        addArrangedSubview(textLabel)

        NSLayoutConstraint.activate([
            iconView.widthAnchor.constraint(equalToConstant: 15),
            iconView.heightAnchor.constraint(equalToConstant: 15),
        ])

        isUserInteractionEnabled = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(onTapped))
        addGestureRecognizer(tap)
    }
    required init(coder: NSCoder) { fatalError("init(coder:) not supported") }

    /// `topMessageOnly == false` applies `font(.caption)`, `lineLimit(1)`, and
    /// the secondary color — the SwiftUI modifiers applied for inline headers.
    func configure(message: UiTimelineV2.Message, topMessageOnly: Bool) {
        self.topMessage = message

        iconView.image = UIImage(named: message.icon.imageName)

        if let user = message.user {
            nameView.isHidden = false
            nameView.baseTextStyle = topMessageOnly ? .body : .caption1
            nameView.baseTextColor = topMessageOnly ? .label : .secondaryLabel
            nameView.text = user.name
            nameView.onOpenURL = onOpenURL
            nameView.lineLimit = topMessageOnly ? nil : 1
            nameView.fixedVertical = true
        } else {
            nameView.isHidden = true
        }

        if let text = message.type.localizedText {
            textLabel.isHidden = false
            textLabel.text = text
            if topMessageOnly {
                textLabel.font = .preferredFont(forTextStyle: .body)
                textLabel.textColor = .label
                textLabel.numberOfLines = 0
            } else {
                textLabel.font = .preferredFont(forTextStyle: .caption1)
                textLabel.textColor = .secondaryLabel
                textLabel.numberOfLines = 1
            }
        } else {
            textLabel.isHidden = true
        }

        iconView.tintColor = topMessageOnly ? .label : .secondaryLabel
    }

    @objc private func onTapped() {
        guard let topMessage = topMessage else { return }
        topMessage.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })))
    }
}

import SwiftUI // for OpenURLAction
// `UiIcon.imageName` is declared in StatusActionView.swift.
