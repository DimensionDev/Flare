import UIKit
import KotlinSharedUI

// MARK: - StatusActionsUIView
// Mirrors StatusActionsView/StatusActionView/StatusActionItemView in
// StatusActionView.swift — horizontal bar of action buttons with optional
// group (menu) children, optional text labels, Spacer insertion per
// postActionStyle, haptic feedback on tap.
final class StatusActionsUIView: UIView {
    var onOpenURL: ((URL) -> Void)?

    private let row = UIStackView()

    // Inputs
    private var data: [ActionMenu] = []
    private var useText: Bool = false
    private var allowSpacer: Bool = true
    private var postActionStyle: PostActionStyle = .leftAligned
    private var showNumbers: Bool = true
    private var fontSize: CGFloat = 13
    private var textStyle: UIFont.TextStyle = .footnote
    private var itemButtonPool: [ActionItemControl] = []
    private var groupButtonPool: [ActionItemControl] = []
    private var textGroupPool: [ActionGroupColumnView] = []
    private var dividerPool: [UIView] = []
    private var spacerPool: [UIView] = []
    private var itemButtonCursor = 0
    private var groupButtonCursor = 0
    private var textGroupCursor = 0
    private var dividerCursor = 0
    private var spacerCursor = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
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
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(
        data: [ActionMenu],
        useText: Bool,
        allowSpacer: Bool = true,
        postActionStyle: PostActionStyle,
        showNumbers: Bool,
        isDetail: Bool
    ) {
        self.data = data
        self.useText = useText
        self.allowSpacer = allowSpacer
        self.postActionStyle = postActionStyle
        self.showNumbers = showNumbers
        self.fontSize = UIFontMetrics(forTextStyle: .footnote).scaledValue(for: 13)
        self.textStyle = isDetail ? .body : .footnote
        rebuild()
    }

    private var actionFont: UIFont {
        .preferredFont(forTextStyle: textStyle)
    }

    private var actionIconSize: CGFloat {
        max(actionFont.pointSize + 1, fontSize * 1.2)
    }

    private func rebuild() {
        row.distribution = .fill
        row.spacing = 8
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        spacerCursor = 0
        var desired: [UIView] = []

        if useText {
            for item in data {
                if let v = makeActionView(for: item, isFixedWidth: false) {
                    desired.append(v)
                }
            }
            row.flareSyncArrangedSubviews(desired)
            return
        }

        if postActionStyle == .stretch, allowSpacer {
            row.distribution = .equalSpacing
            row.spacing = 0
            for (index, item) in data.enumerated() {
                if let v = makeActionView(for: item, isFixedWidth: index != data.count - 1) {
                    desired.append(v)
                }
            }
            row.flareSyncArrangedSubviews(desired)
            return
        }

        // Non-stretch modes mirror the SwiftUI Spacer-insertion rule.
        for (index, item) in data.enumerated() {
            let needsSpacerBefore =
                (index == data.count - 1 && postActionStyle == .leftAligned) ||
                (postActionStyle == .rightAligned && index == 0)

            if needsSpacerBefore, allowSpacer {
                desired.append(flexSpacer())
            }

            if let v = makeActionView(for: item, isFixedWidth: index != data.count - 1) {
                desired.append(v)
            }
        }
        row.flareSyncArrangedSubviews(desired)
    }

    func performDeferredPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func performLightweightPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func prepareForPoolRemoval() {
        row.flareSyncArrangedSubviews([])
        data = []
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        spacerCursor = 0
        trimPoolsToActiveCursors()
    }

    private func trimPoolsToActiveCursors() {
        Self.trimPool(&itemButtonPool, activeCount: itemButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&groupButtonPool, activeCount: groupButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&textGroupPool, activeCount: textGroupCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&dividerPool, activeCount: dividerCursor)
        Self.trimPool(&spacerPool, activeCount: spacerCursor)
    }

    private static func trimPool<View: UIView>(
        _ pool: inout [View],
        activeCount: Int,
        prepare: (View) -> Void = { _ in }
    ) {
        guard pool.count > activeCount else { return }
        for view in pool[activeCount...] {
            prepare(view)
            view.removeFromSuperview()
        }
        pool.removeLast(pool.count - activeCount)
    }

    private func flexSpacer() -> UIView {
        while spacerPool.count <= spacerCursor {
            let v = UIView()
            v.setContentHuggingPriority(.defaultLow, for: .horizontal)
            v.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
            spacerPool.append(v)
        }
        let v = spacerPool[spacerCursor]
        spacerCursor += 1
        v.setContentHuggingPriority(.defaultLow, for: .horizontal)
        v.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return v
    }

    // MARK: - Action view factory (matches the switch in StatusActionView)

    private func makeActionView(for action: ActionMenu, isFixedWidth: Bool) -> UIView? {
        switch onEnum(of: action) {
        case .item(let item):
            return makeItemButton(item: item, isFixedWidth: isFixedWidth)
        case .group(let group):
            if useText {
                return textGroupView(group: group)
            } else {
                return makeGroupButton(group: group, isFixedWidth: isFixedWidth)
            }
        case .divider:
            return divider()
        }
    }

    private func divider() -> UIView {
        while dividerPool.count <= dividerCursor {
            let view = UIView()
            view.backgroundColor = .separator
            view.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale).isActive = true
            dividerPool.append(view)
        }
        let view = dividerPool[dividerCursor]
        dividerCursor += 1
        return view
    }

    private func makeItemButton(item: ActionMenu.Item, isFixedWidth: Bool) -> UIView {
        let title: String?
        if useText, let text = item.text?.resolvedString {
            title = String(localized: text)
        } else if showNumbers, let count = item.count?.humanized {
            title = count
        } else {
            title = nil
        }
        while itemButtonPool.count <= itemButtonCursor {
            itemButtonPool.append(ActionItemControl())
        }
        let control = itemButtonPool[itemButtonCursor]
        itemButtonCursor += 1
        control.configure(
            image: item.icon.flatMap { UIImage(named: $0.imageName) },
            title: title,
            tintColor: item.color?.uiColor ?? .secondaryLabel,
            font: actionFont,
            iconSize: actionIconSize,
            minimumTextWidth: isFixedWidth && title != nil ? fontSize * 2.5 : nil,
            minimumIconOnlySize: nil
        ) { [weak self] in
            guard let self = self else { return }
            let gen = UIImpactFeedbackGenerator(style: .medium)
            gen.prepare(); gen.impactOccurred()
            item.onClicked(ClickContext(launcher: self.makeLauncher()))
        }
        return control
    }

    private func makeGroupButton(group: ActionMenu.Group, isFixedWidth: Bool) -> UIView {
        let title: String?
        if let text = group.displayItem.count?.humanized, showNumbers {
            title = text
        } else {
            title = nil
        }
        while groupButtonPool.count <= groupButtonCursor {
            groupButtonPool.append(ActionItemControl())
        }
        let control = groupButtonPool[groupButtonCursor]
        groupButtonCursor += 1
        control.configure(
            image: group.displayItem.icon.flatMap { UIImage(named: $0.imageName) },
            title: title,
            tintColor: group.displayItem.color?.uiColor ?? .secondaryLabel,
            font: actionFont,
            iconSize: actionIconSize,
            minimumTextWidth: isFixedWidth && title != nil ? fontSize * 2.5 : nil,
            minimumIconOnlySize: title == nil ? actionIconSize : nil
        )
        control.showsMenuAsPrimaryAction = true
        control.menu = UIMenu(children: [
            UIDeferredMenuElement.uncached { [weak self] completion in
                guard let self else {
                    completion([])
                    return
                }
                completion(self.buildMenu(from: group.actions))
            }
        ])
        return control
    }

    private func textGroupView(group: ActionMenu.Group) -> UIView {
        while textGroupPool.count <= textGroupCursor {
            textGroupPool.append(ActionGroupColumnView())
        }
        let view = textGroupPool[textGroupCursor]
        textGroupCursor += 1
        view.configure(
            actions: Array(group.actions),
            postActionStyle: postActionStyle,
            showNumbers: showNumbers,
            onOpenURL: { [weak self] url in self?.onOpenURL?(url) }
        )
        return view
    }

    private func buildMenu(from actions: [ActionMenu]) -> [UIMenuElement] {
        
        var children: [UIMenuElement] = []
        var section: [UIMenuElement] = []
        var hasDivider = false

        func flushSection() {
            guard !section.isEmpty else { return }
            children.append(UIMenu(title: "", options: .displayInline, children: section))
            section = []
        }

        for action in actions {
            switch onEnum(of: action) {
            case .item(let item):
                let title: String
                if let text = item.text?.resolvedString {
                    title = String(localized: text)
                } else {
                    title = ""
                }
                let image = item.icon.flatMap { UIImage(named: $0.imageName) }
                let uiAction = UIAction(
                    title: title,
                    image: image,
                    attributes: item.color == .red ? .destructive : []
                ) { [weak self] _ in
                    guard let self = self else { return }
                    let gen = UIImpactFeedbackGenerator(style: .medium)
                    gen.prepare(); gen.impactOccurred()
                    item.onClicked(ClickContext(launcher: self.makeLauncher()))
                }
                section.append(uiAction)
            case .group(let g):
                let subtitle: String
                if let text = g.displayItem.text?.resolvedString {
                    subtitle = String(localized: text)
                } else {
                    subtitle = ""
                }
                section.append(UIMenu(title: subtitle, children: buildMenu(from: g.actions)))
            case .divider:
                hasDivider = true
                flushSection()
            }
        }
        if hasDivider {
            flushSection()
            return children
        }
        return section
    }

    private func makeLauncher() -> AppleUriLauncher {
        AppleUriLauncher(openUrl: SwiftUI.OpenURLAction { [weak self] url in
            self?.onOpenURL?(url)
            return .handled
        })
    }
}

private final class ActionGroupColumnView: UIView {
    private let column = UIStackView()
    private let topDivider = UIView()
    private let nestedActions = StatusActionsUIView()
    private let bottomDivider = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        column.axis = .vertical
        column.spacing = 4
        column.alignment = .fill
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor),
            topDivider.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale),
            bottomDivider.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale),
        ])
        topDivider.backgroundColor = .separator
        bottomDivider.backgroundColor = .separator
        column.flareSyncArrangedSubviews([topDivider, nestedActions, bottomDivider])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func prepareForPoolRemoval() {
        nestedActions.prepareForPoolRemoval()
    }

    func configure(
        actions: [ActionMenu],
        postActionStyle: PostActionStyle,
        showNumbers: Bool,
        onOpenURL: ((URL) -> Void)?
    ) {
        nestedActions.onOpenURL = onOpenURL
        nestedActions.configure(
            data: actions,
            useText: true,
            allowSpacer: false,
            postActionStyle: postActionStyle,
            showNumbers: showNumbers,
            isDetail: false
        )
    }
}

private final class ActionItemControl: UIButton {
    private let stack = UIStackView()
    private let iconView = UIImageView()
    private let label = UILabel()
    private var minimumIconOnlySize: CGFloat?
    private var iconWidthConstraint: NSLayoutConstraint!
    private var iconHeightConstraint: NSLayoutConstraint!
    private var minimumTextWidthConstraint: NSLayoutConstraint?
    private var minimumWidthConstraint: NSLayoutConstraint?
    private var minimumHeightConstraint: NSLayoutConstraint?
    private var onTap: (() -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    convenience init(
        image: UIImage?,
        title: String?,
        tintColor: UIColor,
        font: UIFont,
        iconSize: CGFloat,
        minimumTextWidth: CGFloat?,
        minimumIconOnlySize: CGFloat?
    ) {
        self.init(frame: .zero)
        configure(
            image: image,
            title: title,
            tintColor: tintColor,
            font: font,
            iconSize: iconSize,
            minimumTextWidth: minimumTextWidth,
            minimumIconOnlySize: minimumIconOnlySize,
            onTap: nil
        )
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func commonInit() {
        translatesAutoresizingMaskIntoConstraints = false
        configuration = nil
        contentHorizontalAlignment = .leading
        contentVerticalAlignment = .center
        directionalLayoutMargins = .zero
        layoutMargins = .zero

        stack.axis = .horizontal
        stack.alignment = .center
        stack.spacing = 0
        stack.isUserInteractionEnabled = false
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        iconView.contentMode = .center
        iconView.setContentHuggingPriority(.required, for: .horizontal)
        iconView.setContentCompressionResistancePriority(.required, for: .horizontal)
        iconWidthConstraint = iconView.widthAnchor.constraint(equalToConstant: 0)
        iconHeightConstraint = iconView.heightAnchor.constraint(equalToConstant: 0)
        NSLayoutConstraint.activate([
            iconWidthConstraint,
            iconHeightConstraint,
        ])

        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        label.setContentHuggingPriority(.required, for: .horizontal)
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        stack.addArrangedSubview(iconView)
        stack.addArrangedSubview(label)
        setContentHuggingPriority(.required, for: .horizontal)
        setContentCompressionResistancePriority(.required, for: .horizontal)
        addTarget(self, action: #selector(onTapped), for: .touchUpInside)
    }

    func configure(
        image: UIImage?,
        title: String?,
        tintColor: UIColor,
        font: UIFont,
        iconSize: CGFloat,
        minimumTextWidth: CGFloat?,
        minimumIconOnlySize: CGFloat?,
        onTap: (() -> Void)? = nil
    ) {
        self.minimumIconOnlySize = minimumIconOnlySize
        self.onTap = onTap
        self.tintColor = tintColor
        menu = nil
        showsMenuAsPrimaryAction = false

        let symbolConfiguration = UIImage.SymbolConfiguration(pointSize: iconSize)
        iconView.preferredSymbolConfiguration = symbolConfiguration
        iconView.tintColor = tintColor
//        let templateImage = image?.withRenderingMode(.alwaysTemplate)
        iconView.image = image //templateImage?.applyingSymbolConfiguration(symbolConfiguration) ?? templateImage
        iconView.isHidden = image == nil
        iconWidthConstraint.constant = iconSize
        iconHeightConstraint.constant = iconSize

        label.text = title
        label.font = font
        label.textColor = tintColor
        label.isHidden = title == nil
        stack.spacing = title == nil ? 0 : 4
        minimumTextWidthConstraint?.isActive = false
        minimumTextWidthConstraint = nil
        if let minimumTextWidth {
            let constraint = label.widthAnchor.constraint(greaterThanOrEqualToConstant: minimumTextWidth)
            constraint.isActive = true
            minimumTextWidthConstraint = constraint
        }

        minimumWidthConstraint?.isActive = false
        minimumHeightConstraint?.isActive = false
        minimumWidthConstraint = nil
        minimumHeightConstraint = nil
        if let minimumIconOnlySize {
            let width = widthAnchor.constraint(greaterThanOrEqualToConstant: minimumIconOnlySize)
            let height = heightAnchor.constraint(greaterThanOrEqualToConstant: minimumIconOnlySize)
            NSLayoutConstraint.activate([width, height])
            minimumWidthConstraint = width
            minimumHeightConstraint = height
        }

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func prepareForPoolRemoval() {
        onTap = nil
        menu = nil
        showsMenuAsPrimaryAction = false
        iconView.image = nil
        label.text = nil
        minimumTextWidthConstraint?.isActive = false
        minimumWidthConstraint?.isActive = false
        minimumHeightConstraint?.isActive = false
        minimumTextWidthConstraint = nil
        minimumWidthConstraint = nil
        minimumHeightConstraint = nil
    }

    @objc private func onTapped() {
        onTap?()
    }

    override var intrinsicContentSize: CGSize {
        actionContentSize()
    }

    override func systemLayoutSizeFitting(_ targetSize: CGSize) -> CGSize {
        actionContentSize()
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        actionContentSize()
    }

    private func actionContentSize() -> CGSize {
        let size = stack.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        let minimum = minimumIconOnlySize ?? 0
        return CGSize(
            width: ceil(max(size.width, minimum)),
            height: ceil(max(size.height, minimum))
        )
    }

    override var isHighlighted: Bool {
        didSet { alpha = isHighlighted ? 0.55 : 1 }
    }
}

import SwiftUI // for OpenURLAction

// MARK: - Color mapping
extension ActionMenu.ItemColor {
    var uiColor: UIColor? {
        switch self {
        case .red:           return .systemRed
        case .contentColor:  return .label
        case .primaryColor:  return .tintColor
        default:             return nil
        }
    }
}
