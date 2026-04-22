import UIKit
import KotlinSharedUI

// MARK: - StatusActionsUIView
// Mirrors StatusActionsView/StatusActionView/StatusActionItemView in
// StatusActionView.swift — horizontal bar of action buttons with optional
// group (menu) children, optional text labels, Spacer insertion per
// postActionStyle, haptic feedback on tap.
final class StatusActionsUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onOpenURL: ((URL) -> Void)?

    private var managedChildren: [UIView] = []

    // Inputs
    private var data: [ActionMenu] = []
    private var useText: Bool = false
    private var allowSpacer: Bool = true
    private var postActionStyle: PostActionStyle = .leftAligned
    private var showNumbers: Bool = true
    private var fontSize: CGFloat = 13
    private var textStyle: UIFont.TextStyle = .footnote
    private var isStretch: Bool = false
    private var spacerIndex: Int? = nil
    private var itemButtonPool: [ActionItemControl] = []
    private var groupButtonPool: [ActionItemControl] = []
    private var textGroupPool: [ActionGroupColumnView] = []
    private var dividerPool: [UIView] = []
    private var itemButtonCursor = 0
    private var groupButtonCursor = 0
    private var textGroupCursor = 0
    private var dividerCursor = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
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
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        isStretch = false
        spacerIndex = nil
        var desired: [UIView] = []

        if useText {
            for item in data {
                if let v = makeActionView(for: item, isFixedWidth: false) {
                    desired.append(v)
                }
            }
            syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
            invalidateIntrinsicContentSize()
            setNeedsLayout()
            return
        }

        if postActionStyle == .stretch, allowSpacer {
            isStretch = true
            for (index, item) in data.enumerated() {
                if let v = makeActionView(for: item, isFixedWidth: index != data.count - 1) {
                    desired.append(v)
                }
            }
            syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
            invalidateIntrinsicContentSize()
            setNeedsLayout()
            return
        }

        for (index, item) in data.enumerated() {
            let needsSpacerBefore =
                (index == data.count - 1 && postActionStyle == .leftAligned) ||
                (postActionStyle == .rightAligned && index == 0)

            if needsSpacerBefore, allowSpacer {
                spacerIndex = desired.count
            }

            if let v = makeActionView(for: item, isFixedWidth: index != data.count - 1) {
                desired.append(v)
            }
        }
        syncManagedSubviews(parent: self, current: &managedChildren, desired: desired)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let h = bounds.height
        guard !managedChildren.isEmpty else { return }

        if isStretch {
            // Equal spacing distribution
            let totalChildWidth = managedChildren.reduce(CGFloat(0)) { sum, child in
                sum + child.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: h)).width
            }
            let totalSpacing = max(w - totalChildWidth, 0)
            let gapCount = max(managedChildren.count - 1, 1)
            let gap = totalSpacing / CGFloat(gapCount)
            var x: CGFloat = 0
            for (i, child) in managedChildren.enumerated() {
                let childSize = child.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: h))
                child.frame = CGRect(
                    x: x,
                    y: (h - childSize.height) / 2,
                    width: ceil(childSize.width),
                    height: childSize.height
                )
                x += ceil(childSize.width)
                if i < managedChildren.count - 1 { x += gap }
            }
            return
        }

        // Compute total content width
        let spacing: CGFloat = 8
        var totalContentWidth: CGFloat = 0
        var childSizes: [CGSize] = []
        for child in managedChildren {
            let s = child.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: h))
            childSizes.append(s)
            totalContentWidth += ceil(s.width)
        }
        totalContentWidth += spacing * CGFloat(max(managedChildren.count - 1, 0))

        // Compute spacer width if needed
        let spacerWidth: CGFloat
        if let _ = spacerIndex, allowSpacer {
            spacerWidth = max(w - totalContentWidth, 0)
        } else {
            spacerWidth = 0
        }

        var x: CGFloat = 0
        for (i, child) in managedChildren.enumerated() {
            if i == spacerIndex {
                x += spacerWidth
            }
            let s = childSizes[i]
            child.frame = CGRect(
                x: x,
                y: (h - s.height) / 2,
                width: ceil(s.width),
                height: s.height
            )
            x += ceil(s.width)
            if i < managedChildren.count - 1 { x += spacing }
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard !managedChildren.isEmpty else { return .zero }
        var maxH: CGFloat = 0
        for child in managedChildren {
            if let provider = child as? TimelineHeightProviding,
               let h = provider.timelineHeight(for: .greatestFiniteMagnitude) {
                maxH = max(maxH, h)
            } else {
                let s = child.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
                maxH = max(maxH, s.height)
            }
        }
        return ceil(maxH)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }

    override var intrinsicContentSize: CGSize {
        sizeThatFits(CGSize(width: bounds.width > 0 ? bounds.width : 320, height: .greatestFiniteMagnitude))
    }

    func performDeferredPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func performLightweightPoolCleanup() {
        trimPoolsToActiveCursors()
    }

    func prepareForPoolRemoval() {
        syncManagedSubviews(parent: self, current: &managedChildren, desired: [])
        data = []
        itemButtonCursor = 0
        groupButtonCursor = 0
        textGroupCursor = 0
        dividerCursor = 0
        trimPoolsToActiveCursors()
    }

    private func trimPoolsToActiveCursors() {
        Self.trimPool(&itemButtonPool, activeCount: itemButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&groupButtonPool, activeCount: groupButtonCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&textGroupPool, activeCount: textGroupCursor) { $0.prepareForPoolRemoval() }
        Self.trimPool(&dividerPool, activeCount: dividerCursor)
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

private final class ActionGroupColumnView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let topDivider = UIView()
    private let nestedActions = StatusActionsUIView()
    private let bottomDivider = UIView()
    private static let dividerHeight: CGFloat = 1.0 / UIScreen.main.scale
    private static let spacing: CGFloat = 4

    override init(frame: CGRect) {
        super.init(frame: frame)
        topDivider.backgroundColor = .separator
        bottomDivider.backgroundColor = .separator
        addSubview(topDivider)
        addSubview(nestedActions)
        addSubview(bottomDivider)
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
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let w = bounds.width
        let dh = Self.dividerHeight
        var y: CGFloat = 0
        topDivider.frame = CGRect(x: 0, y: y, width: w, height: dh)
        y += dh + Self.spacing
        let actionsH = childHeight(of: nestedActions, for: w)
        nestedActions.frame = CGRect(x: 0, y: y, width: w, height: actionsH)
        y += actionsH + Self.spacing
        bottomDivider.frame = CGRect(x: 0, y: y, width: w, height: dh)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        let dh = Self.dividerHeight
        let actionsH = childHeight(of: nestedActions, for: width)
        let total = dh + Self.spacing + actionsH + Self.spacing + dh
        return ceil(total)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        sizeThatFits(CGSize(width: targetSize.width, height: .greatestFiniteMagnitude))
    }
}

private final class ActionItemControl: UIButton, ManualLayoutMeasurable, TimelineHeightProviding {
    private let iconView = UIImageView()
    private let label = UILabel()
    private var minimumIconOnlySize: CGFloat?
    private var minimumTextWidth: CGFloat?
    private var iconSize: CGFloat = 0
    private var currentSpacing: CGFloat = 0
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
        configuration = nil
        contentHorizontalAlignment = .leading
        contentVerticalAlignment = .center
        directionalLayoutMargins = .zero
        layoutMargins = .zero

        iconView.contentMode = .center
        iconView.isUserInteractionEnabled = false
        addSubview(iconView)

        label.adjustsFontForContentSizeCategory = true
        label.numberOfLines = 1
        label.lineBreakMode = .byTruncatingTail
        label.isUserInteractionEnabled = false
        addSubview(label)

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
        self.minimumTextWidth = minimumTextWidth
        self.iconSize = iconSize
        self.onTap = onTap
        self.tintColor = tintColor
        self.currentSpacing = title == nil ? 0 : 4
        menu = nil
        showsMenuAsPrimaryAction = false

        let symbolConfiguration = UIImage.SymbolConfiguration(pointSize: iconSize)
        iconView.preferredSymbolConfiguration = symbolConfiguration
        iconView.tintColor = tintColor
        iconView.image = image
        iconView.isHidden = image == nil

        label.text = title
        label.font = font
        label.textColor = tintColor
        label.isHidden = title == nil

        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func prepareForPoolRemoval() {
        onTap = nil
        menu = nil
        showsMenuAsPrimaryAction = false
        iconView.image = nil
        label.text = nil
        minimumTextWidth = nil
        minimumIconOnlySize = nil
    }

    @objc private func onTapped() {
        onTap?()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let size = actionContentSize()
        var x: CGFloat = 0
        if !iconView.isHidden {
            iconView.frame = CGRect(
                x: x,
                y: (size.height - iconSize) / 2,
                width: iconSize,
                height: iconSize
            )
            x += iconSize + currentSpacing
        }
        if !label.isHidden {
            let labelSize = label.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: size.height))
            let labelW = max(labelSize.width, minimumTextWidth ?? 0)
            label.frame = CGRect(
                x: x,
                y: (size.height - labelSize.height) / 2,
                width: labelW,
                height: labelSize.height
            )
        }
    }

    override var intrinsicContentSize: CGSize {
        actionContentSize()
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        actionContentSize()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        actionContentSize().height
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
        let minimum = minimumIconOnlySize ?? 0
        var w: CGFloat = 0
        var h: CGFloat = 0
        if !iconView.isHidden {
            w += iconSize
            h = max(h, iconSize)
        }
        if !label.isHidden {
            let labelSize = label.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
            let labelW = max(labelSize.width, minimumTextWidth ?? 0)
            w += currentSpacing + labelW
            h = max(h, labelSize.height)
        }
        return CGSize(
            width: ceil(max(w, minimum)),
            height: ceil(max(h, minimum))
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
