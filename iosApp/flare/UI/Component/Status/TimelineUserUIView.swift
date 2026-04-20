import UIKit
import KotlinSharedUI

/// UIKit port of `TimelineUserView`.
///
/// Matches the SwiftUI body:
///   VStack {
///     UserCompatView(data: data.value).onTapGesture { ... }
///     if !data.button.isEmpty {
///       HStack { ForEach(button) { StatusActionItemView(useText: true, isFixedWidth: false) } }
///     }
///   }
final class TimelineUserUIView: UIView {
    var onOpenURL: ((URL) -> Void)?
    var appearance: AppearanceSettings = AppearanceSettings.companion.Default {
        didSet { if data != nil { rebuild() } }
    }

    private var data: UiTimelineV2.User?

    private let column = UIStackView()
    private let userView = UserCompatUIView()
    private let buttonRow = StatusActionsUIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
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
        ])
        buttonRow.onOpenURL = { [weak self] url in self?.onOpenURL?(url) }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiTimelineV2.User) {
        self.data = data
        rebuild()
    }

    private func rebuild() {
        guard let data = data else {
            column.flareSyncArrangedSubviews([])
            return
        }

        userView.configure(
            data: data.value,
            trailing: nil,
            onClicked: { [weak self] in
                guard let self = self else { return }
                data.value.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
                    self?.onOpenURL?(url)
                    return .handled
                })))
            }
        )

        var desired: [UIView] = [userView]
        if data.button.isEmpty {
            column.flareSyncArrangedSubviews(desired)
        } else {
            buttonRow.configure(
                data: data.button,
                useText: true,
                allowSpacer: false,
                postActionStyle: appearance.postActionStyle,
                showNumbers: appearance.showNumbers,
                isDetail: false
            )
            desired.append(buttonRow)
            column.flareSyncArrangedSubviews(desired)
        }
    }
}

import SwiftUI // OpenURLAction
