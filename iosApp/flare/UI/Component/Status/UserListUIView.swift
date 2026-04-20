import UIKit
import KotlinSharedUI

/// UIKit port of `UserListView`.
///
/// Layout mirrors the SwiftUI body:
///   VStack {
///     ScrollView(.horizontal) { HStack { ForEach(users) { UserCompatView(...).frame(width: 280).overlay(border) } } }
///     if let post { StatusView(...).overlay(border) }
///   }
final class UserListUIView: UIView {
    var onOpenURL: ((URL) -> Void)?
    var appearance: AppearanceSettings = AppearanceSettings.companion.Default {
        didSet { if data != nil { rebuild() } }
    }

    private var data: UiTimelineV2.UserList?

    private let column = UIStackView()
    private let scroll = UIScrollView()
    private let row = UIStackView()
    private let quoteContainer = UIView()
    private let quoteView = StatusUIKitView()
    private var userChips: [UserChipView] = []

    override init(frame: CGRect) {
        super.init(frame: frame)

        scroll.showsHorizontalScrollIndicator = false
        scroll.translatesAutoresizingMaskIntoConstraints = false

        row.axis = .horizontal
        row.spacing = 8
        row.alignment = .center
        row.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(row)

        quoteContainer.layer.cornerRadius = 16
        quoteContainer.layer.borderWidth = 1
        quoteContainer.layer.borderColor = UIColor.separator.cgColor
        quoteContainer.layer.masksToBounds = true
        quoteView.translatesAutoresizingMaskIntoConstraints = false
        quoteContainer.addSubview(quoteView)
        NSLayoutConstraint.activate([
            quoteView.topAnchor.constraint(equalTo: quoteContainer.topAnchor, constant: 8),
            quoteView.leadingAnchor.constraint(equalTo: quoteContainer.leadingAnchor, constant: 8),
            quoteView.trailingAnchor.constraint(equalTo: quoteContainer.trailingAnchor, constant: -8),
            quoteView.bottomAnchor.constraint(equalTo: quoteContainer.bottomAnchor, constant: -8),
        ])

        column.axis = .vertical
        column.spacing = 8
        column.alignment = .fill
        column.translatesAutoresizingMaskIntoConstraints = false

        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor),

            row.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            row.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            row.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            row.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
        ])

        quoteView.openURL = { [weak self] url in self?.onOpenURL?(url) }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        quoteContainer.layer.borderColor = UIColor.separator.cgColor
    }

    func configure(data: UiTimelineV2.UserList) {
        self.data = data
        rebuild()
    }

    private func rebuild() {
        guard let data = data else {
            row.flareSyncArrangedSubviews([])
            column.flareSyncArrangedSubviews([])
            return
        }

        while userChips.count < data.users.count {
            userChips.append(UserChipView())
        }
        var chipDesired: [UIView] = []
        for (index, user) in data.users.enumerated() {
            let chip = userChips[index]
            chip.configure(user: user, onOpenURL: { [weak self] url in
                self?.onOpenURL?(url)
            })
            chipDesired.append(chip)
        }
        row.flareSyncArrangedSubviews(chipDesired)

        var desired: [UIView] = [scroll]
        if let post = data.post {
            quoteView.appearance = appearance
            quoteView.configure(data: post, isQuote: true, forceHideActions: true)
            desired.append(quoteContainer)
        }
        column.flareSyncArrangedSubviews(desired)
    }
}

private final class UserChipView: UIView {
    private let compat = UserCompatUIView()
    private var user: UiProfile?
    private var onOpenURL: ((URL) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        layer.cornerRadius = 16
        layer.borderWidth = 1
        layer.borderColor = UIColor.separator.cgColor
        layer.masksToBounds = true
        translatesAutoresizingMaskIntoConstraints = false
        compat.translatesAutoresizingMaskIntoConstraints = false
        addSubview(compat)
        NSLayoutConstraint.activate([
            widthAnchor.constraint(equalToConstant: 280),
            compat.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            compat.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            compat.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            compat.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
        ])
        let tap = UITapGestureRecognizer(target: self, action: #selector(onChipTapped(_:)))
        addGestureRecognizer(tap)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(user: UiProfile, onOpenURL: ((URL) -> Void)?) {
        self.user = user
        self.onOpenURL = onOpenURL
        compat.configure(data: user, trailing: nil, onClicked: { [weak self] in
            self?.openUser()
        })
    }

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        layer.borderColor = UIColor.separator.cgColor
    }

    @objc private func onChipTapped(_ sender: UITapGestureRecognizer) {
        openUser()
    }

    private func openUser() {
        guard let user else { return }
        user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })))
    }
}

import SwiftUI // OpenURLAction
