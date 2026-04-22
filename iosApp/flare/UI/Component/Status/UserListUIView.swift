import UIKit
import KotlinSharedUI

/// UIKit port of `UserListView`.
///
/// Layout mirrors the SwiftUI body:
///   VStack {
///     ScrollView(.horizontal) { HStack { ForEach(users) { UserCompatView(...).frame(width: 280).overlay(border) } } }
///     if let post { StatusView(...).overlay(border) }
///   }
final class UserListUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?
    var appearance = StatusUIKitAppearance(settings: AppearanceSettings.companion.Default) {
        didSet { if data != nil { rebuild() } }
    }

    private static let spacing: CGFloat = 8
    private static let quoteInset: CGFloat = 8

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

    func prepareForFitting(width: CGFloat) {
        guard width > 0, width.isFinite else { return }
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        for chip in activeUserChips {
            chip.prepareForFitting(width: UserChipView.preferredWidth)
        }
        if data?.post != nil {
            quoteView.prepareForFitting(width: quoteContentWidth(for: width))
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite, data != nil else { return nil }
        prepareForFitting(width: width)

        var height = userRowHeight()
        if data?.post != nil {
            if height > 0 {
                height += Self.spacing
            }
            height += quoteHeight(for: width)
        }
        return ceil(height)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width: CGFloat
        if horizontalFittingPriority == .required, targetSize.width > 0, targetSize.width.isFinite {
            width = targetSize.width
        } else if bounds.width > 0 {
            width = bounds.width
        } else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        return CGSize(width: width, height: timelineHeight(for: width) ?? 0)
    }

    func prepareForPoolRemoval() {
        data = nil
        row.flareSyncArrangedSubviews([])
        column.flareSyncArrangedSubviews([])
        quoteView.prepareForPoolRemoval()
        trimUserChips(activeCount: 0)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
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
//            quoteView.appearance = appearance
            quoteView.configure(data: post, appearance: appearance, isQuote: true, forceHideActions: true)
            desired.append(quoteContainer)
        }
        column.flareSyncArrangedSubviews(desired)
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        let width = bounds.width
        guard width > 0, width.isFinite else { return }
        column.frame = bounds

        let rowHeight = userRowHeight()
        var y: CGFloat = 0
        scroll.frame = CGRect(x: 0, y: y, width: width, height: rowHeight)
        layoutUserRow(height: rowHeight)
        y += rowHeight

        if data?.post != nil {
            if y > 0 {
                y += Self.spacing
            }
            let quoteHeight = quoteHeight(for: width)
            quoteContainer.frame = CGRect(x: 0, y: y, width: width, height: quoteHeight)
            quoteView.frame = quoteContainer.bounds.insetBy(dx: Self.quoteInset, dy: Self.quoteInset)
        }
    }

    func performDeferredPoolCleanup() {
        let activeChipCount = data?.users.count ?? 0
        if data?.post != nil {
            quoteView.performDeferredPoolCleanup()
        } else {
            quoteView.prepareForPoolRemoval()
        }
        trimUserChips(activeCount: activeChipCount)
    }

    func performLightweightPoolCleanup() {
        let activeChipCount = data?.users.count ?? 0
        if data?.post != nil {
            quoteView.performLightweightPoolCleanup()
        } else {
            quoteView.prepareForPoolRemoval()
        }
        trimUserChips(activeCount: activeChipCount)
    }

    private func trimUserChips(activeCount: Int) {
        guard userChips.count > activeCount else { return }
        for chip in userChips[activeCount...] {
            chip.prepareForPoolRemoval()
            chip.removeFromSuperview()
        }
        userChips.removeLast(userChips.count - activeCount)
    }

    private var activeUserChips: ArraySlice<UserChipView> {
        userChips.prefix(data?.users.count ?? 0)
    }

    private func userRowHeight() -> CGFloat {
        activeUserChips.reduce(CGFloat(0)) { height, chip in
            max(height, chip.timelineHeight(for: UserChipView.preferredWidth) ?? 0)
        }
    }

    private func quoteContentWidth(for width: CGFloat) -> CGFloat {
        max(width - Self.quoteInset * 2, 1)
    }

    private func quoteHeight(for width: CGFloat) -> CGFloat {
        guard data?.post != nil else { return 0 }
        let contentWidth = quoteContentWidth(for: width)
        let contentHeight = quoteView.timelineHeight(for: contentWidth)
            ?? quoteView.sizeThatFits(CGSize(width: contentWidth, height: .greatestFiniteMagnitude)).height
        return ceil(contentHeight) + Self.quoteInset * 2
    }

    private func layoutUserRow(height: CGFloat) {
        var x: CGFloat = 0
        for chip in activeUserChips {
            chip.frame = CGRect(x: x, y: 0, width: UserChipView.preferredWidth, height: height)
            x += UserChipView.preferredWidth + row.spacing
        }
        row.frame = CGRect(x: 0, y: 0, width: max(x - row.spacing, 0), height: height)
        scroll.contentSize = row.frame.size
    }
}

private final class UserChipView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    static let preferredWidth: CGFloat = 280

    private static let inset: CGFloat = 8
    private static let minimumContentHeight: CGFloat = 44

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
            widthAnchor.constraint(equalToConstant: Self.preferredWidth),
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

    func prepareForFitting(width: CGFloat) {
        guard width > 0, width.isFinite else { return }
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        compat.bounds = CGRect(
            x: compat.bounds.minX,
            y: compat.bounds.minY,
            width: max(width - Self.inset * 2, 1),
            height: compat.bounds.height
        )
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let contentWidth = max(width - Self.inset * 2, 1)
        let contentSize = compat.systemLayoutSizeFitting(
            CGSize(width: contentWidth, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return ceil(max(contentSize.height, Self.minimumContentHeight)) + Self.inset * 2
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        compat.frame = bounds.insetBy(dx: Self.inset, dy: Self.inset)
    }

    func prepareForPoolRemoval() {
        user = nil
        onOpenURL = nil
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
